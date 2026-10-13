package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 真实响应逐条回到同一新应用；机械验收不对预期状态造通过断言。 */
class RuleCheck2025QualityReplayTest {
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private Reviewed2025ReportCatalogue trial;
    @AfterEach void close()throws Exception {if(trial!=null)trial.close();}
    @Test void recordsEveryOutcomeAndFreshOriginalLocationIncludingFailedPlans()throws Exception {
        Path base=root.resolve(SealedRuleTaskReplay.BASE);
        var packet=read(base.resolve("request-package.json"));var auth=read(base.resolve("external-transfer-authorization.json"));
        assertThat(auth.path("authority").asText()).isEqualTo("DIRECT_USER_MESSAGE");assertThat(auth.path("decision").asText()).isEqualTo("CONFIRMED");
        assertThat(Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(base.resolve("request-package.json")))).isEqualTo(auth.path("packageSha256").asText());
        // 供应商失败不能阻断全分母报告；完整批次是放行条件，不是记录失败结果的条件。
        var summary=read(base.resolve("provider-capture/summary.json"));
        assertThat(summary.path("retryCount").asInt(-1)).isZero();assertThat(summary.path("sourceUnchanged").asBoolean()).isTrue();
        var responses=new HashMap<String,JsonNode>();
        for(var row:summary.path("records")) {
            String id=row.path("caseId").asText();assertThat(row.path("path").asText()).isEqualTo(id+".json");
            byte[] raw=Files.readAllBytes(base.resolve("provider-capture/"+id+".json"));
            assertThat(Reviewed2025ControlledCatalogue.sha(raw)).isEqualTo(row.path("sha256").asText());responses.put(id,mapper.readTree(raw));
        }
        String url=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");RuleCheckDatabaseTestTarget.verify(url);
        var ds=new DriverManagerDataSource(url,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(url,user,ZhkuDatabaseTestConfig.databasePassword());
        trial=new Reviewed2025ReportCatalogue(root);trial.installOnIsolatedDatabase(ds);
        var requestRows=new HashMap<String,JsonNode>();packet.path("requests").forEach(row->requestRows.put(row.path("caseId").asText(),row));
        var outcomes=new ArrayList<Map<String,Object>>();
        for(var question:packet.path("questionProjection")) {
            String id=question.path("caseId").asText(),q=question.path("question").asText();var requestRow=requestRows.get(id);var response=responses.get(id);
            var worker=new BoundedRuleTaskPlanner(request->{
                try {
                assertThat(requestRow).isNotNull();
                var messages=requestRow.path("body").path("messages");assertThat(request.systemConstraints()).isEqualTo(messages.get(0).path("content").asText());
                assertThat(mapper.readTree(request.userPayload())).isEqualTo(mapper.readTree(messages.get(1).path("content").asText()));
                return response==null||!response.path("status").asText().equals("RESPONSE_RECEIVED")?null:response.path("rawModelContent").asText();
                }catch(java.io.IOException invalid){throw new IllegalArgumentException(invalid);}
            },Duration.ofSeconds(5),1);
            var row=new LinkedHashMap<String,Object>();
            try {
                var app=RuleCheckRuntimeFactory.createEvidenceFirst2025(ds,trial.settings(),()->{
                    try{return trial.reviewedCatalogue(root);}catch(Exception failure){throw new IllegalStateException(failure);}
                },worker,()->UUID.randomUUID().toString(),Clock.systemUTC());
                long start=System.nanoTime();var result=app.checkWithContext(q,Map.of());
                row.put("caseId",id);row.put("kind",question.path("kind").asText());row.put("question",q);row.put("result",result);
                row.put("providerRecordStatus",requestRow==null?"LOCAL_BLOCKED":response==null?"NOT_EXECUTED":response.path("status").asText());
                row.put("qualityEligible",response!=null&&response.path("status").asText().equals("RESPONSE_RECEIVED"));
                row.put("offlineReplayElapsedMs",(System.nanoTime()-start)/1e6);row.put("plannerAttempts",worker.attempts());
                assertThat(worker.attempts()).isEqualTo(requestRow==null?0:1);assertThat(app.isCurrent(result)).isTrue();
                var locations=new ArrayList<Map<String,Object>>();
                for(var fact:result.decision().confirmedFacts())for(var excerpt:fact.evidence()) {
                    assertThat(app.isReferenceApproved(excerpt)).isTrue();var located=trial.originals().locate(excerpt);
                    assertThat(located.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
                    assertThat(located.original().contextText()).contains(excerpt.text());locations.add(Map.of("reference",excerpt,"located",located));
                }
                row.put("actualOriginalLocations",locations);row.put("freshReleaseValidated",true);row.put("humanSemanticGradeProduced",false);outcomes.add(row);
            } finally {
                worker.close();long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
                while(worker.liveWorkers()!=0&&System.nanoTime()<deadline)Thread.sleep(2);
                assertThat(worker.liveWorkers()).isZero();assertThat(worker.inFlight()).isZero();
            }
        }
        assertThat(outcomes).hasSize(43);
        Files.write(Path.of(System.getProperty("zhku.rulecheck.evidenceDir")).resolve("quality-actual-results.json"),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("cases",outcomes,"regressionDenominator",16,"confirmationDenominator",24,
                "repeatDenominator",3,"modelCallsInThisReplay",0,"humanSemanticGradesProduced",false,"onlineEndToEndTimingMeasured",false)),StandardOpenOption.CREATE_NEW);
    }
    private JsonNode read(Path path)throws Exception{return mapper.readTree(Files.readAllBytes(path));}
}
