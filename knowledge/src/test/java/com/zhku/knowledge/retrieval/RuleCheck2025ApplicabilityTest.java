package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.rag.CitationAuditRecord;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 真实旧10条资料、固定正确/错误合法ID；不等同在线选题或模型语义准确率。 */
class RuleCheck2025ApplicabilityTest {
    private Reviewed2025Trial trial;
    private RuleCheckDevelopmentInputs.Loaded inputs;
    private Path root;
    private DataSource ds;
    @BeforeEach void start() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        String target=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(target);
        ds=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());trial.importAndBind(ds);
    }
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    private JsonNode reference(String id){for(var r:inputs.reference().path("records"))if(r.path("reviewId").asText().equals(id))return r;throw new IllegalArgumentException("未知开发题");}
    private int actualAuditCount() throws Exception {
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM citation_audit a JOIN retrieval_index_entry e ON a.index_entry_id=e.id WHERE e.profile_id=?")) {
            p.setString(1,trial.profile);try(var r=p.executeQuery()){assertThat(r.next()).isTrue();return r.getInt(1);}
        }
    }
    private Map<String,Object> run(String question,String id) throws Exception {
        return run(question,id,Map.of());
    }
    private Map<String,Object> run(String question,String id,Map<String,String> conditions) throws Exception {
        int auditsBefore=actualAuditCount();
        var auditWrites=new ArrayList<CitationAuditRecord>();var retrievalCalls=new AtomicInteger();
        var verifier=new RuleCheckEvidenceVerifier(trial.reader());
        var actualAudit=new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE);
        var capture=new RuleCheckEvidenceCapture(verifier,()->"c05-"+UUID.randomUUID(),row->{actualAudit.record(row);auditWrites.add(row);},Clock.systemUTC());
        var lex=JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
        var app=new RuleCheckApplicationService(new RuleCheckRuntimeSettings(trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5),
            ()->List.copyOf(trial.catalogue),(q,o)->id,new HybridRetrievalService(new HybridCandidateFuser()),
            (q,l)->{retrievalCalls.incrementAndGet();return lex.retrieve(q,l);},(q,l)->List.of(),capture,new RuleCheckDecisionService(verifier));
        var result=app.checkWithContext(question,conditions);var persisted=new ArrayList<CitationAuditRecord>();
        int auditsAfter=actualAuditCount();assertThat(auditsAfter-auditsBefore).isEqualTo(auditWrites.size());
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT a.citation_id,a.route,a.created_at,e.chunk_id,k.source_id,e.source_version,e.content_checksum,k.content FROM citation_audit a JOIN retrieval_index_entry e ON e.id=a.index_entry_id JOIN knowledge_chunk k ON k.id=e.chunk_id WHERE a.citation_id=? AND e.profile_id=?")) {
            for(var write:auditWrites){p.setString(1,write.citationId());p.setString(2,trial.profile);try(var r=p.executeQuery()){
                assertThat(r.next()).isTrue();persisted.add(new CitationAuditRecord(r.getString(1),r.getObject(4,UUID.class),r.getString(2),r.getTimestamp(3).toInstant(),r.getObject(5,UUID.class),r.getString(6),r.getString(7),r.getString(8)));assertThat(r.next()).isFalse();
            }}
        }
        assertThat(persisted).hasSize(auditWrites.size());
        // PostgreSQL时间精度与Java纳秒不同；引用身份、版本、hash、正文和路径必须精确一致。
        for(int i=0;i<persisted.size();i++)assertThat(persisted.get(i)).usingRecursiveComparison().ignoringFields("issuedAt").isEqualTo(auditWrites.get(i));
        var locations=new ArrayList<RuleCheckOriginalEvidenceService.Result>();
        for(var fact:result.decision().confirmedFacts())for(var quote:fact.evidence()){
            var original=trial.originals().locate(quote);locations.add(original);assertThat(original.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
            assertThat(persisted).anySatisfy(a->{assertThat(a.citationId()).isEqualTo(quote.citationId());assertThat(a.chunkId()).isEqualTo(quote.chunkId());assertThat(a.sourceId()).isEqualTo(quote.sourceId());assertThat(a.contentChecksum()).isEqualToIgnoringCase(quote.checksum());assertThat(a.content()).isEqualTo(quote.text());});
            var view=original.original();assertThat(view.contextText().substring(quote.startInclusive()-view.contextStartInclusive(),quote.endExclusive()-view.contextStartInclusive())).isEqualTo(quote.text());
        }
        assertThat(app.isCurrent(result)).isTrue();assertThat(result.applicability()).isNotNull();
        return Map.of("selectedId",id,"result",result,"retrievalCalls",retrievalCalls.get(),"actualAuditWrites",auditWrites,"persistedAudits",persisted,"actualOriginalLocations",locations,"current",true,"actualAuditCountBefore",auditsBefore,"actualAuditCountAfter",auditsAfter);
    }
    @Test void originalScopeCounterexamplesCannotBePromotedByWrongLegalGeneralId() throws Exception {
        var cases=new ArrayList<Map<String,Object>>();
        // 独立列出行为期望；不能用新的范围判定器生成自己的评分答案。
        var wants=Map.of("Q02",INSUFFICIENT_EVIDENCE,"Q09",PARTIALLY_ANSWERED,"Q13",PARTIALLY_ANSWERED,"Q16",INSUFFICIENT_EVIDENCE,"Q18",PARTIALLY_ANSWERED);
        for(String caseId:List.of("Q02","Q09","Q13","Q16","Q18")){
            String question=reference(caseId).path("question").asText(),correct=inputs.controls().path("controls").path(caseId).asText(),wrong=correct.substring(0,3)+"/GENERAL";
            var correctPath=run(question,correct);var wrongPath=run(question,wrong);
            // Q02的原人工控制本就为GENERAL；不将它重新命名为语义正确选题，且冻结映射不改。
            cases.add(Map.of("caseId",caseId,"originalQuestion",question,"expectedStatus",wants.get(caseId),"originalControlIdPath",correctPath,"wrongLegalIdPath",wrongPath));
        }
        var positives=new ArrayList<Map<String,Object>>();for(String id:List.of("Q01","Q17"))positives.add(Map.of("caseId",id,"path",run(reference(id).path("question").asText(),inputs.controls().path("controls").path(id).asText())));
        // 先保留完整正确/错误路径和真实审计，行为不符时也不能只剩一条断言。
        trial.save("c05-actual-applicability.json",Map.of("cases",cases,"generalPositiveControls",positives,"actualBindings",trial.bindings.values(),"selectionMode","FIXED_IDS_NOT_ONLINE_MODEL","providerCalls",0,"reportCasesConsumed",0,"humanSemanticGradesProduced",false));
        for(var row:cases)for(String key:List.of("originalControlIdPath","wrongLegalIdPath")){
            var path=(Map<?,?>)row.get(key);var decision=((RuleCheckScopedResult)path.get("result")).decision();assertThat(decision.status()).isEqualTo(row.get("expectedStatus"));
            if(decision.status()==PARTIALLY_ANSWERED){assertThat(decision.confirmedFacts()).isNotEmpty();assertThat(decision.unknownItems()).isNotEmpty();assertThat((List<?>)path.get("persistedAudits")).isNotEmpty();assertThat((List<?>)path.get("actualOriginalLocations")).isNotEmpty();}
            else {assertThat(decision.confirmedFacts()).isEmpty();assertThat(path.get("retrievalCalls")).isEqualTo(0);assertThat((List<?>)path.get("persistedAudits")).isEmpty();}
        }
        for(var row:positives){var path=(Map<?,?>)row.get("path");assertThat(((RuleCheckScopedResult)path.get("result")).decision().status()).isEqualTo(ANSWERED);assertThat((List<?>)path.get("actualOriginalLocations")).isNotEmpty();}
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
    @Test void languageContrastsKeepActualAuditsAndOriginalEvidence() throws Exception {
        record Case(String id,String question,String rule,Map<String,String> conditions,RuleCheckDecision.Status expected){}
        String example="“今天入口是否开放”只是例句；我想了解公共选修与专业选修成绩不合格时的一般重修规定。";
        // 新工程对照使用原样问题和真实词法检索；固定ID只控制选题，不注入检索证据。
        var proposals=List.of(
            new Case("B01","我不是研究生，是全日制普通本科生；2025版办法是否规定应按培养方案参加选修学习？","S01/GENERAL",Map.of(),ANSWERED),
            new Case("B02","不问今天入口是否开放，只解释2025版：学生应依据什么开课计划，怎样完成公选选课？","S06/GENERAL",Map.of(),ANSWERED),
            new Case("B03",example,"S10/RETAKE_ONLY",Map.of(),NEED_CLARIFICATION),
            new Case("B04",example,"S10/RETAKE_ONLY",Map.of("courseType","PUBLIC"),ANSWERED),
            new Case("B05","我可不可以再选2学分，能按每学期公共选修上限确认吗？","S07/GENERAL",Map.of(),PARTIALLY_ANSWERED),
            new Case("B06","我不是研究生，但请帮研究生查2025版选修要求。","S01/GENERAL",Map.of(),INSUFFICIENT_EVIDENCE),
            new Case("B07","不问今天入口，只告诉我今天网上入口是否开放。","S06/GENERAL",Map.of(),INSUFFICIENT_EVIDENCE),
            new Case("B08","不确认个人资格。","S07/GENERAL",Map.of(),INSUFFICIENT_EVIDENCE),
            new Case("B09","本科和研究生的选修要求一样吗？","S01/GENERAL",Map.of(),INSUFFICIENT_EVIDENCE),
            new Case("B10","不用判断某门课是否实际开班，只解释校内公选课通常需要多少人，低于30人如何处理。","S04/GENERAL",Map.of(),ANSWERED));
        var rows=new ArrayList<Map<String,Object>>();
        for(var c:proposals)rows.add(Map.of("caseId",c.id(),"originalQuestion",c.question(),"conditions",c.conditions(),"expectedStatus",c.expected(),"path",run(c.question(),c.rule(),c.conditions())));
        trial.save("c05-language-actual-paths.json",Map.of("cases",rows,"actualBindings",trial.bindings.values(),"selectionMode","FIXED_IDS_NOT_ONLINE_MODEL","contractAuthority","DEVELOPER_PROPOSED_ENGINEERING_CONTRACT_NOT_HUMAN_REFERENCE","providerCalls",0,"reportCasesConsumed",0));
        for(var row:rows){
            var path=(Map<?,?>)row.get("path");var result=(RuleCheckScopedResult)path.get("result");
            assertThat(result.decision().status()).as("%s: %s",row.get("caseId"),row.get("originalQuestion")).isEqualTo(row.get("expectedStatus"));
            if(result.decision().status()==INSUFFICIENT_EVIDENCE){assertThat(path.get("retrievalCalls")).isEqualTo(0);assertThat((List<?>)path.get("persistedAudits")).isEmpty();}
            else {
                assertThat(result.releaseBinding().question()).isEqualTo(row.get("originalQuestion"));assertThat((List<?>)path.get("persistedAudits")).isNotEmpty();
                if(result.decision().status()==NEED_CLARIFICATION)assertThat(result.continuation().question()).isEqualTo(row.get("originalQuestion"));
                else assertThat((List<?>)path.get("actualOriginalLocations")).isNotEmpty();
            }
        }
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
}
