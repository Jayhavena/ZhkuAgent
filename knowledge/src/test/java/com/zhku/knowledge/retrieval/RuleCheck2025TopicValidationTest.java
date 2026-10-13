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
class RuleCheck2025TopicValidationTest {
    private List<RuleCheckApplicationService.CatalogEntry> proposed;
    private Reviewed2025Trial trial;
    private RuleCheckDevelopmentInputs.Loaded inputs;
    private Path root;
    private DataSource ds;
    @BeforeEach void start() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        String target=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(target);
        ds=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());trial.importAndBind(ds);proposed=RuleCheckTopicCatalogueDraft.load(root,trial);
    }
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    private JsonNode reference(String id){for(var r:inputs.reference().path("records"))if(r.path("reviewId").asText().equals(id))return r;throw new IllegalArgumentException("未知开发题");}
    private int actualAuditCount() throws Exception {
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM citation_audit a JOIN retrieval_index_entry e ON a.index_entry_id=e.id WHERE e.profile_id=?")) {
            p.setString(1,trial.profile);try(var r=p.executeQuery()){assertThat(r.next()).isTrue();return r.getInt(1);}
        }
    }
    private Map<String,Object> run(String question,String id,boolean enabled,boolean forceSelectedEvidence) throws Exception {
        var catalogue=enabled?proposed:List.copyOf(trial.catalogue);
        int auditsBefore=actualAuditCount();
        var auditWrites=new ArrayList<CitationAuditRecord>();var retrievalCalls=new AtomicInteger();
        var verifier=new RuleCheckEvidenceVerifier(trial.reader());
        var actualAudit=new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE);
        var capture=new RuleCheckEvidenceCapture(verifier,()->"c05-"+UUID.randomUUID(),row->{actualAudit.record(row);auditWrites.add(row);},Clock.systemUTC());
        var lex=JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
        var app=new RuleCheckApplicationService(new RuleCheckRuntimeSettings(trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5),
            ()->catalogue,(q,o)->id,new HybridRetrievalService(new HybridCandidateFuser()),
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
        return Map.of("selectedId",id,"result",result,"retrievalCalls",retrievalCalls.get(),"actualAuditWrites",auditWrites,"persistedAudits",persisted,"actualOriginalLocations",locations,"current",true,"actualAuditCountBefore",auditsBefore,"actualAuditCountAfter",auditsAfter);
    }
    @Test void rejectsThreeWrongLegalIdsWithTheirOwnRealEvidenceWhileCorrectControlsRemainUsable() throws Exception {
        var contrasts=new ArrayList<Map<String,Object>>();
        for(String caseId:List.of("Q07","Q10","Q12")) {
            String question=reference(caseId).path("question").asText(),wrong=caseId.equals("Q10")?"S01/GENERAL":"S07/GENERAL";
            var before=run(question,wrong,false,true);var after=run(question,wrong,true,true);
            var correct=run(question,inputs.controls().path("controls").path(caseId).asText(),true,false);
            var row=Map.<String,Object>of("caseId",caseId,"originalQuestion",question,"wrongLegalIdEvidenceAvailableBefore",before,"topicCheckEnabledAfter",after,"correctControl",correct);
            contrasts.add(row);trial.save("topic-contrast-"+caseId+".json",row);
        }
        var originals=new ArrayList<Map<String,Object>>();
        for(var ref:inputs.reference().path("records")) {
            String id=ref.path("reviewId").asText();var path=run(ref.path("question").asText(),inputs.controls().path("controls").path(id).asText(),true,false);
            originals.add(Map.of("caseId",id,"originalQuestion",ref.path("question").asText(),"referenceStatus",ref.path("humanReview").path("acceptedStatus").asText(),"path",path));
        }
        trial.save("topic-actual-paths.json",Map.of("contrasts",contrasts,"originalQuestionControls",originals,"actualBindings",trial.bindings.values(),"providerCalls",0,"reportCasesConsumed",0,"selectionMode","FIXED_ID_LOCAL_CONTROLS_NOT_MODEL_SELECTION","humanSemanticGradesProduced",false));
        for(var row:contrasts) {
            var before=(Map<?,?>)row.get("wrongLegalIdEvidenceAvailableBefore");var after=(Map<?,?>)row.get("topicCheckEnabledAfter");var correct=(Map<?,?>)row.get("correctControl");
            assertThat(((RuleCheckScopedResult)before.get("result")).decision().status()).isEqualTo(ANSWERED);
            assertThat((List<?>)before.get("persistedAudits")).isNotEmpty();assertThat((List<?>)before.get("actualOriginalLocations")).isNotEmpty();
            assertThat(((RuleCheckScopedResult)after.get("result")).decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
            assertThat(((RuleCheckScopedResult)after.get("result")).decision().unknownItems()).anyMatch(t->t.contains("主题"));
            assertThat(after.get("retrievalCalls")).isEqualTo(0);assertThat((List<?>)after.get("persistedAudits")).isEmpty();
            assertThat(((RuleCheckScopedResult)correct.get("result")).decision().status()).isEqualTo(ANSWERED);
            assertThat((List<?>)correct.get("actualOriginalLocations")).isNotEmpty();
        }
        for(var row:originals)assertThat(((RuleCheckScopedResult)((Map<?,?>)row.get("path")).get("result")).decision().status().name()).as("%s",row.get("caseId")).isEqualTo(row.get("referenceStatus"));
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
    @Test void exportsOnlyOriginalTwentyQuestionsAndFourProbesUsingDraftScopeWithoutChangingFacts() throws Exception {
        for(int i=0;i<proposed.size();i++) {
            assertThat(proposed.get(i).template()).isEqualTo(trial.catalogue.get(i).template());
            assertThat(proposed.get(i).coverage()).isEqualTo(trial.catalogue.get(i).coverage());
            assertThat(proposed.get(i).topicCoverage().enabled()).isTrue();
        }
        var original=List.copyOf(trial.catalogue);trial.catalogue.clear();trial.catalogue.addAll(proposed);
        try {
            var requests=RuleCheckSelectionPreparation.build(root,trial,inputs);
            trial.save("topic-outbound-package.json",Map.of("authority","LOCAL_DRAFT_NOT_AUTHORIZED_FOR_TRANSFER_OR_PUBLICATION","requests",requests,"providerCalls",0,"reportCasesConsumed",0,"humanSemanticGradesProduced",false));
            trial.save("topic-catalogue-observation.json",proposed);
            assertThat(requests).hasSize(24);
        }finally {trial.catalogue.clear();trial.catalogue.addAll(original);}
    }
}
