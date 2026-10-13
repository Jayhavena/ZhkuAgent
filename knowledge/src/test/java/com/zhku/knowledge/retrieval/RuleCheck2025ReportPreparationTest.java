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

/** 冻结后只投影报告问句；经过实际应用的候选才可生成外发请求，合成null不算模型成绩。 */
class RuleCheck2025ReportPreparationTest {
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private Reviewed2025ReportCatalogue trial;
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    @Test void preparesFrozenQuestionsWithoutReferenceAnswersAndOnlyActualScopedCandidates() throws Exception {
        Path freezePath=root.resolve("docs/evidence/rule-check-refactor/2026-10-10/package-03/system-freeze.json");
        assertThat(Files.exists(freezePath)).as("先冻结运行系统，后读取报告问句").isTrue();
        var freeze=mapper.readTree(Files.readAllBytes(freezePath));verifyFreeze(freeze);
        String url=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(url);
        var ds=new DriverManagerDataSource(url,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(url,user,ZhkuDatabaseTestConfig.databasePassword());
        trial=new Reviewed2025ReportCatalogue(root);trial.installOnIsolatedDatabase(ds);
        // 整体参考文件只为保持冻结身份读入；下面仅提取caseId/question，不使用正确状态、事实或证据进行选题。
        var report=mapper.readTree(Files.readAllBytes(root.resolve("docs/product/rule-check-2025-human-reference-report.json")));
        var requests=new ArrayList<Map<String,Object>>();var blocked=new ArrayList<Map<String,Object>>();
        var questions=new ArrayList<Map<String,String>>();
        for(var item:report.path("cases")) {
            String caseId=item.path("caseId").asText(),question=item.path("question").asText();
            assertThat(caseId).isEqualTo("R%02d".formatted(questions.size()+1));questions.add(Map.of("caseId",caseId,"question",question));
            var observed=new AtomicReference<BoundedRuleSelector.Request>();
            var worker=new BoundedRuleSelector(request->{observed.set(request);return "{\"ruleId\":null}";},Duration.ofSeconds(5),1);
            var selector=new RuleCheckApplicationService.RuleSelector() {
                public boolean requiresScopedCandidates(){return true;}
                public String select(String q,List<RuleCheckApplicationService.RuleOption> options){return worker.select(q,options);}
            };
            RuleCheckDecision decision;
            try {
                var app=RuleCheckRuntimeFactory.createControlled2025(ds,trial.settings(),()->{
                    try{return trial.reviewedCatalogue(root);}catch(Exception failure){throw new IllegalStateException(failure);}
                },selector,()->UUID.randomUUID().toString(),Clock.systemUTC());
                decision=app.check(question,Map.of());
            } finally {
                worker.close();long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
                while(worker.liveWorkers()!=0&&System.nanoTime()<deadline)Thread.sleep(2);
                assertThat(worker.liveWorkers()).isZero();assertThat(worker.inFlight()).isZero();
            }
            assertThat(decision.status()).isEqualTo(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE);
            var request=observed.get();
            if(request==null) {
                blocked.add(Map.of("caseId",caseId,"question",question,"localBlocked",true,"decision",decision,"providerCalls",0));continue;
            }
            var payload=mapper.readTree(request.userPayload());
            assertThat(payload.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("question","options");
            assertThat(payload.path("question").asText()).isEqualTo(question);assertThat(payload.path("options").size()).isBetween(1,26);
            for(var option:payload.path("options"))assertThat(option.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("ruleId","questionScope");
            var body=Map.<String,Object>of("model","qwen-plus","messages",List.of(Map.of("role","system","content",request.systemConstraints()),
                    Map.of("role","user","content",request.userPayload())),"response_format",Map.of("type","json_object"),"enable_thinking",false,"stream",false,"temperature",0,"max_tokens",128);
            assertThat(mapper.writeValueAsBytes(body).length).isLessThanOrEqualTo(BoundedRuleSelector.MAX_REQUEST_BYTES);
            requests.add(Map.of("caseId",caseId,"kind","FROZEN_REPORT_QUESTION","body",body,"maxInnerResponseBytes",request.maxResponseBytes(),"humanSemanticGradeAvailable",false));
        }
        assertThat(questions).hasSize(16);assertThat(requests.size()+blocked.size()).isEqualTo(16);verifyFreeze(freeze);
        var packet=new LinkedHashMap<String,Object>();
        packet.put("authority","PREPARED_NOT_AUTHORIZED_FOR_TRANSFER");packet.put("referenceRevision","2025.9.2");
        packet.put("endpoint","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");packet.put("model","qwen-plus");
        packet.put("systemFreezeSha256",Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(freezePath)));
        packet.put("maxCalls",requests.size());packet.put("retryCount",0);packet.put("maxOutputTokens",128);
        packet.put("reportDenominator",16);packet.put("requests",requests);packet.put("localBlockedCases",blocked);
        packet.put("questionProjection",questions);packet.put("providerCalls",0);packet.put("gradesProduced",false);
        Path out=Path.of(System.getProperty("zhku.rulecheck.evidenceDir"));
        Files.write(out.resolve("report-request-package.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(packet),StandardOpenOption.CREATE_NEW);
        Files.write(out.resolve("report-assembly-observation.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
                "clauseCount",trial.bindings.size(),"catalogueCount",trial.reviewedCatalogue(root).size(),"bindings",trial.bindings.values(),
                "settings",trial.settings(),"providerCalls",0,"referenceAnswersUsedForCatalogue",false,"questionExposureAfterFreeze",true)),StandardOpenOption.CREATE_NEW);
    }
    private void verifyFreeze(JsonNode freeze) throws Exception {
        assertThat(freeze.path("status").asText()).isEqualTo("FROZEN_BEFORE_REPORT_QUESTION_PREPARATION");
        for(var row:freeze.path("files"))assertThat(Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(root.resolve(row.path("path").asText()))))
                .as(row.path("path").asText()).isEqualTo(row.path("sha256").asText());
    }
}
