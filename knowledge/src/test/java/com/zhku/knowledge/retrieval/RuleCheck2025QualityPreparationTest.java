package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** 真实条款用于请求装配，不用替身计划宣称模型或产品通过。 */
class RuleCheck2025QualityPreparationTest {
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private Reviewed2025ReportCatalogue trial;
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    @Test void preparesEvidenceFirstBatchWithFrozenQuestionsAndNoReferenceAnswers() throws Exception {
        String url=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(url);
        var ds=new DriverManagerDataSource(url,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(url,user,ZhkuDatabaseTestConfig.databasePassword());
        trial=new Reviewed2025ReportCatalogue(root);trial.installOnIsolatedDatabase(ds);
        Path confirmation=root.resolve("docs/evidence/rule-check-quality/2026-10-11/confirmation-cases.json");
        var lock=mapper.readTree(Files.readAllBytes(confirmation.resolveSibling("confirmation-cases-lock.json")));
        String confirmationHash=Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(confirmation));
        assertThat(lock.toString()).contains(confirmationHash);
        // 仅投影问题，预期行为与人工参考不进入规划请求。
        var questions=new ArrayList<Map<String,String>>();
        appendQuestions(root.resolve("docs/product/rule-check-2025-human-reference-report.json"),"EXPOSED_REGRESSION",questions);
        assertThat(questions).hasSize(16);
        appendQuestions(confirmation,"UNTUNED_CONFIRMATION",questions);assertThat(questions).hasSize(40);
        String repeated=questions.stream().filter(q->q.get("caseId").equals("N08")).findFirst().orElseThrow().get("question");
        for(int i=1;i<=3;i++)questions.add(Map.of("caseId","T0"+i,"kind","INDEPENDENT_REPEAT","question",repeated));
        var requests=new ArrayList<Map<String,Object>>();var blocked=new ArrayList<Map<String,Object>>();
        for(var item:questions) {
            var observed=new AtomicReference<BoundedRuleSelector.Request>();
            var planner=new BoundedRuleTaskPlanner(r->{observed.set(r);return "{\"tasks\":[{\"ruleId\":\"\",\"action\":\"UNSUPPORTED\",\"operand\":\"\",\"value\":\"\"}]}";},Duration.ofSeconds(5),1);
            try {
                var app=RuleCheckRuntimeFactory.createEvidenceFirst2025(ds,trial.settings(),()->{
                    try{return trial.reviewedCatalogue(root);}catch(Exception failure){throw new IllegalStateException(failure);}
                },planner,()->UUID.randomUUID().toString(),Clock.systemUTC());
                var result=app.checkWithContext(item.get("question"),Map.of());
                assertThat(result.decision().confirmedFacts()).isEmpty();
                var request=observed.get();
                if(request==null){blocked.add(Map.of("caseId",item.get("caseId"),"kind",item.get("kind"),"decision",result.decision(),"providerCalls",0));continue;}
                var payload=mapper.readTree(request.userPayload());
                assertThat(payload.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("question","evidenceOptions");
                assertThat(payload.path("evidenceOptions").size()).isBetween(1,26);
                for(var option:payload.path("evidenceOptions"))assertThat(option.properties()).extracting(Map.Entry::getKey)
                    .containsExactlyInAnyOrder("ruleId","questionScope","verifiedFacts","actions","operands");
                var body=Map.<String,Object>of("model","qwen-plus","messages",List.of(Map.of("role","system","content",request.systemConstraints()),
                    Map.of("role","user","content",request.userPayload())),"response_format",Map.of("type","json_object"),"enable_thinking",false,"stream",false,"temperature",0,"max_tokens",512);
                assertThat(mapper.writeValueAsBytes(body).length).isLessThanOrEqualTo(BoundedRuleTaskPlanner.MAX_REQUEST_BYTES);
                requests.add(Map.of("caseId",item.get("caseId"),"kind",item.get("kind"),"body",body,"maxInnerResponseBytes",request.maxResponseBytes()));
                assertThat(planner.attempts()).isEqualTo(1);
            } finally {
                planner.close();long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
                while(planner.liveWorkers()!=0&&System.nanoTime()<deadline)Thread.sleep(2);
                assertThat(planner.liveWorkers()).isZero();assertThat(planner.inFlight()).isZero();
            }
        }
        assertThat(requests.size()+blocked.size()).isEqualTo(43);
        var packet=new LinkedHashMap<String,Object>();
        packet.put("authority","PREPARED_NOT_AUTHORIZED_FOR_TRANSFER");packet.put("referenceRevision","2025.9.2");
        packet.put("endpoint","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");packet.put("model","qwen-plus");
        packet.put("confirmationCasesSha256",confirmationHash);packet.put("maxCalls",requests.size());packet.put("retryCount",0);packet.put("maxOutputTokens",512);
        packet.put("regressionDenominator",16);packet.put("confirmationDenominator",24);packet.put("repeatDenominator",3);
        packet.put("requests",requests);packet.put("localBlockedCases",blocked);packet.put("questionProjection",questions);packet.put("providerCalls",0);packet.put("gradesProduced",false);
        Path out=Path.of(System.getProperty("zhku.rulecheck.evidenceDir"));
        Files.write(out.resolve("quality-request-package.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(packet),StandardOpenOption.CREATE_NEW);
        Files.write(out.resolve("quality-assembly-observation.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
            "clauseCount",trial.bindings.size(),"catalogueCount",trial.reviewedCatalogue(root).size(),"settings",trial.settings(),"providerCalls",0,"referenceAnswersUsedForPlanning",false)),StandardOpenOption.CREATE_NEW);
    }
    private void appendQuestions(Path file,String kind,List<Map<String,String>> target)throws Exception {
        for(var row:mapper.readTree(Files.readAllBytes(file)).path("cases"))target.add(Map.of("caseId",row.path("caseId").asText(),"question",row.path("question").asText(),"kind",kind));
    }
}
