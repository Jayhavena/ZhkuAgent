package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** C03真实开发问句采集；显式选题/同尝试候选回放控制，绝不充当端到端自然语言成绩。 */
class RuleCheck2025DevelopmentCaptureTest {
    private Reviewed2025Trial trial;
    private javax.sql.DataSource dataSource;
    private JsonNode references,controls;
    private Path root;
    private RuleCheckDevelopmentInputs.Loaded loaded;
    @BeforeEach void setUp() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();trial=new Reviewed2025Trial(root);
        loaded=RuleCheckDevelopmentInputs.load(root);controls=loaded.controls();references=loaded.reference();
        String target=System.getProperty("zhku.db.url");RuleCheckDatabaseTestTarget.verify(target);
        dataSource=new DriverManagerDataSource(target,System.getProperty("zhku.db.user","zhkuagent"),ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,System.getProperty("zhku.db.user","zhkuagent"),ZhkuDatabaseTestConfig.databasePassword());
    }
    @AfterEach void cleanUp() throws Exception {if(trial!=null)trial.close();}
    private static void require(boolean okay,String reason){if(!okay)throw new IllegalArgumentException(reason);}

    @Test void collectTwentyOriginalDevelopmentQuestionsWithoutQualityPassAssertions() throws Exception {
        trial.importAndBind(dataSource);
        var settings=new RuleCheckRuntimeSettings(trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5);
        var lexical=new JdbcLexicalCandidateRetriever(dataSource,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
        ChunkCandidateRetriever disabledVector=(q,l)->{throw new RetrieverUnavailableException("本实验配置禁用向量，没有供应商调用");};
        var records=new ArrayList<Map<String,Object>>();var errors=new ArrayList<Map<String,Object>>();
        for(JsonNode reference:references.path("records")) {
            String id=reference.path("reviewId").asText(),question=reference.path("question").asText();
            String ruleId=controls.path("controls").path(id).asText();
            require(trial.catalogue.stream().anyMatch(e->e.template().ruleId().equals(ruleId)),"控制目录ID不在已审目录");
            var needed=new LinkedHashSet<Integer>();for(JsonNode e:reference.path("humanReview").path("acceptedEvidence"))needed.add(e.path("article").asInt());
            var record=captureRecord(id,question,ruleId,needed,settings,lexical,disabledVector);
            record.put("split","DEVELOPMENT");record.put("expectedStatus",reference.path("humanReview").path("acceptedStatus").asText());
            records.add(record);
            if(!record.get("mechanicalFailureAttribution").equals("NO_MECHANICAL_RETRIEVAL_FAILURE"))errors.add(Map.of("caseId",id,"attemptId",((RuleCheckDevelopmentCapture.Attempt)record.get("attempt")).attemptId(),"category",record.get("mechanicalFailureAttribution"),"stageErrors",record.get("stageErrors")));
        }
        var metadata=new LinkedHashMap<String,Object>();metadata.put("datasetId",controls.path("experimentId").asText());metadata.put("runId",UUID.randomUUID());
        metadata.put("referenceSha256",controls.path("referenceSha256").asText());metadata.put("controlsSha256",loaded.controlsSha256());
        metadata.put("queryOrigin",controls.path("queryOrigin").asText());metadata.put("selectionMode",controls.path("selectionMode").asText());metadata.put("candidateReuse",controls.path("candidateReuse").asText());
        metadata.put("sourceId",trial.source);metadata.put("profileId",trial.profile);metadata.put("indexRunId",trial.run);metadata.put("bindings",trial.bindings.values());metadata.put("settings",settings);
        metadata.put("lifetime","ACTUAL_TEMPORARY_ROWS_REMOVED_AFTER_TEST");metadata.put("model","NONE");metadata.put("providerCalls",0);metadata.put("vectorMode","CONFIGURED_DISABLED");
        metadata.put("cases",records);metadata.put("qualityScoresProduced",false);metadata.put("humanSemanticScoresProduced",false);metadata.put("reportCasesConsumed",0);
        // 所有开发样本先落盘；后面的词面/应用控制失败不得抹去坏答案证据。
        trial.save("development-capture.json",metadata);
        saveLines("development-outputs.jsonl",records);saveLines("development-errors.jsonl",errors);
        var positiveControls=new ArrayList<Map<String,Object>>();
        for(var b:trial.bindings.values()) {
            String query=b.text().substring(0,3);var row=new LinkedHashMap<String,Object>();row.put("article",b.article());row.put("query",query);
            try {var outcome=new HybridRetrievalService(new HybridCandidateFuser()).retrieve(query,20,5,lexical,disabledVector);
                row.put("outcome",outcome);row.put("expectedChunkFound",outcome.chunks().stream().anyMatch(c->c.chunkId().equals(b.chunkId())));
            }catch(RuntimeException failure){row.put("errorType",failure.getClass().getSimpleName());row.put("expectedChunkFound",false);}
            positiveControls.add(row);
        }
        trial.save("lexical-positive-controls.json",positiveControls);
        // 非空独立应用控制覆盖capture/decide/审计/定位，不计入20题或政策语义通过率。
        var applicationControl=captureRecord("CONTROL-APPLICATION","每学期","S07/GENERAL",Set.of(10),settings,lexical,disabledVector);
        applicationControl.put("split","CONTROL_NOT_QUALITY_CASE");trial.save("application-positive-control.json",applicationControl);
        RuleCheckDevelopmentInputs.verifyUnchanged(root,loaded);
        assertThat(positiveControls).allSatisfy(c->assertThat(c.get("expectedChunkFound")).isEqualTo(true));
        var positiveAttempt=(RuleCheckDevelopmentCapture.Attempt)applicationControl.get("attempt");
        assertThat(positiveAttempt.output().decision().confirmedFacts()).isNotEmpty();
        assertThat(applicationControl.get("mechanicalFailureAttribution")).isEqualTo("NO_MECHANICAL_RETRIEVAL_FAILURE");
        assertThat((List<?>)applicationControl.get("captureAuditRecords")).isNotEmpty();
        assertThat(records).hasSize(20);assertThat(records).extracting(r->r.get("caseId")).doesNotHaveDuplicates();
        assertThat(records).allSatisfy(r->{
            var a=(RuleCheckDevelopmentCapture.Attempt)r.get("attempt");assertThat(a.question()).isEqualTo(r.get("question"));
            assertThat(a.status()).isEqualTo("COLLECTED");assertThat(a.totalNanos()).isGreaterThan(0);assertThat(a.output()).isNotNull();
        });
    }
    private Map<String,Object> captureRecord(String id,String question,String ruleId,Set<Integer> needed,RuleCheckRuntimeSettings settings,
        ChunkCandidateRetriever lexical,ChunkCandidateRetriever vector){
        long start=System.nanoTime();var stageErrors=new ArrayList<Map<String,Object>>();var auditRecords=new ArrayList<Object>();var stageCalls=new ArrayList<Map<String,Object>>();
        var actualAudit=new JdbcRuleCheckCitationAuditRepository(dataSource,trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE);
        var verifier=new RuleCheckEvidenceVerifier((s,c)->{
            long begin=System.nanoTime();try{return trial.reader().read(s,c);}catch(RuntimeException failure){stageErrors.add(Map.of("stage","CURRENT_READER","errorType",failure.getClass().getSimpleName()));throw failure;}
            finally {stageCalls.add(Map.of("stage","CURRENT_READER","nanos",System.nanoTime()-begin));}
        });
        var capture=new RuleCheckEvidenceCapture(verifier,()->"c03-"+UUID.randomUUID(),r->{
            long begin=System.nanoTime();try{actualAudit.record(r);auditRecords.add(r);}catch(RuntimeException failure){stageErrors.add(Map.of("stage","CITATION_AUDIT","errorType",failure.getClass().getSimpleName()));throw failure;}
            finally {stageCalls.add(Map.of("stage","CITATION_AUDIT","nanos",System.nanoTime()-begin));}
        },Clock.systemUTC());
        var attempt=new RuleCheckDevelopmentCapture().collect(new RuleCheckDevelopmentCapture.Input(id,question,ruleId,Map.of()),lexical,vector,(lexReplay,vectorReplay)->{
            var app=new RuleCheckApplicationService(settings,()->List.copyOf(trial.catalogue),(q,options)->ruleId,
                new HybridRetrievalService(new HybridCandidateFuser()),lexReplay,vectorReplay,capture,new RuleCheckDecisionService(verifier));return app::checkWithContext;
        });
        var locations=new ArrayList<RuleCheckDevelopmentCapture.LocationTrace>();
        if(attempt.output()!=null)for(var fact:attempt.output().decision().confirmedFacts())for(var quote:fact.evidence()){
            var location=RuleCheckDevelopmentCapture.locate(quote,trial.reader()::readLocated);locations.add(location);
            if(location.errorType()!=null)stageErrors.add(Map.of("stage","ORIGINAL_LOCATION","errorType",location.errorType()));
        }
        Set<UUID> obtained=new HashSet<>();if(attempt.retrieval()!=null)for(var c:attempt.retrieval().chunks())obtained.add(c.chunkId());
        var missed=needed.stream().filter(a->!obtained.contains(trial.bindings.get(a).chunkId())).toList();
        boolean failedLocation=locations.stream().anyMatch(l->l.result().code()!=RuleCheckOriginalEvidenceService.Code.FOUND);
        String cause=attempt.errorType()!=null||!stageErrors.isEmpty()?"EXECUTION_ERROR":!attempt.lexical().status().equals("AVAILABLE")?"RETRIEVAL_UNAVAILABLE":!missed.isEmpty()?"NECESSARY_EVIDENCE_NOT_RETRIEVED":failedLocation?"ORIGINAL_LOCATION_FAILED":"NO_MECHANICAL_RETRIEVAL_FAILURE";
        var record=new LinkedHashMap<String,Object>();record.put("caseId",id);record.put("question",question);record.put("queryTerms",QueryTerms.extract(question));record.put("attempt",attempt);
        record.put("manualSemanticJudgment","UNJUDGEABLE_NOT_SCORED");record.put("selectionQuality","NOT_EVALUATED");record.put("confirmedConditionsSupplied",Map.of());
        record.put("captureAuditRecords",auditRecords);record.put("stageErrors",stageErrors);record.put("stageCalls",stageCalls);
        record.put("captureDecisionTimingStatus",stageCalls.isEmpty()?"NOT_EXECUTED_NO_EVIDENCE_CALLS":"NOT_SEPARATELY_OBSERVED_APPLICATION_COMBINED");
        record.put("requiredArticles",needed);record.put("necessaryArticlesMissingAt5",attempt.retrieval()==null?null:missed);record.put("mechanicalFailureAttribution",cause);
        record.put("tokens",null);record.put("monetaryCost",null);record.put("costStatus","NOT_APPLICABLE_NO_PROVIDER");record.put("providerCalls",0);
        record.put("actualOriginalLocations",locations);record.put("originalLocationStatus",locations.isEmpty()?"NOT_EXECUTED_NO_OUTPUT_QUOTES":"ACTUAL_LOCATE_RESULTS_RECORDED");
        record.put("collectionIncludingLocateNanos",System.nanoTime()-start);record.put("latencyUse","CONTROLLED_DIAGNOSTIC_NOT_ONLINE_P95");return record;
    }
    private void saveLines(String filename,List<?> rows) throws Exception {
        Path file=Path.of(System.getProperty("zhku.rulecheck.evidenceDir")).resolve(filename);
        var lines=new ArrayList<String>();for(var row:rows)lines.add(trial.mapper.writeValueAsString(row));
        RuleCheckDevelopmentCapture.persistBeforeValidation(file,lines,()->{});
    }
}
