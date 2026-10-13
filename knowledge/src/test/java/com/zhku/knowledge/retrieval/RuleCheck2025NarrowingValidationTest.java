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

/** 本地目录草案的真实依据控制；固定ID不代表模型已改进，原开发参考保持。 */
class RuleCheck2025NarrowingValidationTest {
    private List<RuleCheckApplicationService.CatalogEntry> proposed;
    Reviewed2025Trial trial;
    RuleCheckDevelopmentInputs.Loaded inputs;
    Path root;
    private DataSource ds;
    @BeforeEach void start() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        String target=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(target);
        ds=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());trial.importAndBind(ds);proposed=RuleCheckTopicCatalogueDraft.loadWithReviewedLimits(root,trial);
    }
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    private JsonNode reference(String id){for(var r:inputs.reference().path("records"))if(r.path("reviewId").asText().equals(id))return r;throw new IllegalArgumentException("未知开发题");}
    private int actualAuditCount() throws Exception {
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM citation_audit a JOIN retrieval_index_entry e ON a.index_entry_id=e.id WHERE e.profile_id=?")) {
            p.setString(1,trial.profile);try(var r=p.executeQuery()){assertThat(r.next()).isTrue();return r.getInt(1);}
        }
    }
    private Map<String,Object> run(String question,String id,boolean enabled,boolean forceSelectedEvidence) throws Exception {
        return run(question,id,enabled,forceSelectedEvidence,null);
    }
    /** 外部响应只替换选题传输，继续复用真实SQL审计/定位检查，不能替换事实决定。 */
    Map<String,Object> run(String question,String id,boolean enabled,boolean forceSelectedEvidence,
            RuleCheckApplicationService.RuleSelector external) throws Exception {
        var catalogue=enabled?proposed:List.copyOf(trial.catalogue);
        var candidateIds=new ArrayList<String>();
        var selector=new RuleCheckApplicationService.RuleSelector(){
            public boolean requiresScopedCandidates(){return true;}
            public String select(String q,List<RuleCheckApplicationService.RuleOption> options){options.forEach(o->candidateIds.add(o.ruleId()));return external==null?id:external.select(q,options);}
        };
        int auditsBefore=actualAuditCount();
        var auditWrites=new ArrayList<CitationAuditRecord>();var retrievalCalls=new AtomicInteger();
        var verifier=new RuleCheckEvidenceVerifier(trial.reader());
        var actualAudit=new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE);
        var capture=new RuleCheckEvidenceCapture(verifier,()->"c05-"+UUID.randomUUID(),row->{actualAudit.record(row);auditWrites.add(row);},Clock.systemUTC());
        var lex=JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
        var app=new RuleCheckApplicationService(new RuleCheckRuntimeSettings(trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5),
            ()->catalogue,selector,new HybridRetrievalService(new HybridCandidateFuser()),
            (q,l)->{retrievalCalls.incrementAndGet();
                if(!forceSelectedEvidence)return lex.retrieve(q,l);
                // 反例控制明确把错选规则自身的真实依据放入检索，排除“没检到证据”掩盖主题缺陷。
                var template=catalogue.stream().filter(e->e.template().ruleId().equals(id)).findFirst().orElseThrow().template();
                UUID chunk=template.facts().getFirst().evidenceBindings().getFirst().chunkId();var binding=trial.bindings.values().stream().filter(v->v.chunkId().equals(chunk)).findFirst().orElseThrow();
                return List.of(new RetrievedChunk(binding.chunkId(),binding.text(),"2025本地已审资料",binding.locator(),binding.physicalPage(),Reviewed2025Trial.REVISION,binding.checksum(),binding.sourceId()));
            },(q,l)->List.of(),capture,new RuleCheckDecisionService(verifier));
        var result=app.checkWithContext(question,Map.of());var persisted=new ArrayList<CitationAuditRecord>();
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
        assertThat(app.isCurrent(result)).isTrue();
        return Map.of("selectedId",id,"result",result,"retrievalCalls",retrievalCalls.get(),"actualAuditWrites",auditWrites,"persistedAudits",persisted,"actualOriginalLocations",locations,"current",true,"actualAuditCountBefore",auditsBefore,"actualAuditCountAfter",auditsAfter,"selectionCandidateIds",candidateIds);
    }
    @Test void verifiesReviewedEvidenceControlsAndExcludedChoicesWithoutAnyModelCalls() throws Exception {
        var originals=new ArrayList<Map<String,Object>>();
        for(var ref:inputs.reference().path("records")) {
            String id=ref.path("reviewId").asText();var path=run(ref.path("question").asText(),inputs.controls().path("controls").path(id).asText(),true,false);
            assertThat(((RuleCheckScopedResult)path.get("result")).decision().status().name()).as(id).isEqualTo(ref.path("humanReview").path("acceptedStatus").asText());
            originals.add(Map.of("caseId",id,"path",path));
        }
        var wrong=run(reference("Q10").path("question").asText(),"S01/GENERAL",true,false);
        assertThat((List<String>)wrong.get("selectionCandidateIds")).containsExactly("S05/GENERAL","S05/PERSONAL_CREDIT_DEFICIT");
        assertThat(((RuleCheckScopedResult)wrong.get("result")).decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(wrong.get("retrievalCalls")).isEqualTo(0);assertThat((List<?>)wrong.get("persistedAudits")).isEmpty();
        var alias=run("研一学生的选修要求按什么办法？","S01/GENERAL",true,false);
        assertThat((List<?>)alias.get("selectionCandidateIds")).isEmpty();
        assertThat(alias.get("retrievalCalls")).isEqualTo(0);assertThat((List<?>)alias.get("persistedAudits")).isEmpty();
        var correct=run(reference("Q10").path("question").asText(),"S05/GENERAL",true,false);
        assertThat(((RuleCheckScopedResult)correct.get("result")).decision().confirmedFacts()).anyMatch(f->f.id().equals("plan-selection-basis"));
        assertThat((List<?>)correct.get("actualOriginalLocations")).isNotEmpty();
        var researchVerb=run("请帮我调研一下全日制普通本科公共选修课的学时上限。","S03/GENERAL",true,false);
        assertThat(((RuleCheckScopedResult)researchVerb.get("result")).decision().status()).isEqualTo(ANSWERED);
        assertThat((List<?>)researchVerb.get("actualOriginalLocations")).isNotEmpty();
        trial.save("narrowing-actual-paths.json",Map.of("originalQuestionControls",originals,"wrongQ10Choice",wrong,"postgraduateAlias",alias,"correctQ10Control",correct,"researchVerbUndergraduateControl",researchVerb,"providerCalls",0,"reportCasesConsumed",0,"humanSemanticGradesProduced",false,"selectionMode","FIXED_ID_LOCAL_CONTROLS_WITH_NARROWED_CANDIDATES_NOT_MODEL_SELECTION"));
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
    @Test void ratioOutputCarriesAlreadyReviewedUnknownExceptionBoundaryWithoutInventingFacts() throws Exception {
        var path=run(reference("Q12").path("question").asText(),"S05/GENERAL",true,false);
        var result=(RuleCheckScopedResult)path.get("result");
        trial.save("narrowing-boundary-Q12.json",Map.of("path",path,"providerCalls",0,"humanSemanticGradeProduced",false));
        assertThat(result.decision().status()).isEqualTo(ANSWERED);
        assertThat(result.scope().questionScope()).contains("第8条没有列出具体例外及审批办法。");
        assertThat(result.decision().confirmedFacts()).anyMatch(f->f.id().equals("online-general-half")&&f.text().contains("一般不能"));
        // 覆盖说明属于已审核的解释边界，不能添加或改写政策事实和证据绑定。
        for(var entry:proposed)assertThat(entry.template()).isEqualTo(trial.catalogue.stream().filter(old->old.template().ruleId().equals(entry.template().ruleId())).findFirst().orElseThrow().template());
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
    @Test void preparesScopedRequestsFromActualApplicationWithoutGroundTruthOrProviderCalls() throws Exception {
        var rows=RuleCheckSelectionPreparation.build(root,trial,inputs,q->{
            try {
                var path=run(q,"PREPARATION/ABSTAIN",true,false);
                assertThat(path.get("retrievalCalls")).isEqualTo(0);
                assertThat((List<?>)path.get("persistedAudits")).isEmpty();
                var ids=(List<String>)path.get("selectionCandidateIds");
                return proposed.stream().filter(e->ids.contains(e.template().ruleId()))
                    .map(e->new RuleCheckApplicationService.RuleOption(e.template().ruleId(),e.questionScope())).toList();
            }catch(Exception failure){throw new IllegalStateException(failure);}
        });
        assertThat(rows).hasSize(24);
        var requests=rows.stream().filter(r->r.containsKey("body")).toList();
        var blocked=rows.stream().filter(r->Boolean.TRUE.equals(r.get("localBlocked"))).toList();
        assertThat(blocked).extracting(r->r.get("caseId")).contains("Q02","O01","O02","O03");
        var q10=requests.stream().filter(r->r.get("caseId").equals("Q10")).findFirst().orElseThrow();
        var body=trial.mapper.valueToTree(q10.get("body"));
        var payload=trial.mapper.readTree(body.path("messages").get(1).path("content").asText());
        assertThat(payload.path("options").findValuesAsText("ruleId")).containsExactly("S05/GENERAL","S05/PERSONAL_CREDIT_DEFICIT");
        trial.save("scoped-selection-outbound-package.json",Map.of("authority","PREPARED_NOT_AUTHORIZED_FOR_TRANSFER",
            "requests",requests,"localBlockedCases",blocked,"caseCount",24,"maxCalls",requests.size(),
            "providerCalls",0,"reportCasesConsumed",0,"humanSemanticGradeProduced",false));
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
}
