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
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;

/** 曝光响应的本地执行回归：不验证新提示下模型是否会生成同样计划，也不充当独立确认集。 */
class RuleCheck2025TargetedRepairTest {
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private Reviewed2025ReportCatalogue trial;
    @AfterEach void close()throws Exception {if(trial!=null)trial.close();}
    @Test void reexecutesExposedResponsesWithFreshEvidenceAndPreservesAllOutcomes()throws Exception {
        Path base=root.resolve(SealedRuleTaskReplay.BASE);
        var packet=read(base.resolve("request-package.json"));var summary=read(base.resolve("provider-capture/summary.json"));
        var responseRows=new HashMap<String,JsonNode>();
        for(var row:summary.path("records")) {
            byte[] bytes=Files.readAllBytes(base.resolve("provider-capture/"+row.path("path").asText()));
            assertThat(Reviewed2025ControlledCatalogue.sha(bytes)).isEqualTo(row.path("sha256").asText());
            responseRows.put(row.path("caseId").asText(),mapper.readTree(bytes));
        }
        String url=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(url);
        var ds=new DriverManagerDataSource(url,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(url,user,ZhkuDatabaseTestConfig.databasePassword());
        trial=new Reviewed2025ReportCatalogue(root);trial.installOnIsolatedDatabase(ds);
        var outcomes=new ArrayList<Map<String,Object>>();var decisions=new HashMap<String,RuleCheckDecision>();
        var requestRows=new HashMap<String,JsonNode>();packet.path("requests").forEach(row->requestRows.put(row.path("caseId").asText(),row));
        for(var question:packet.path("questionProjection")) {
            String id=question.path("caseId").asText(),q=question.path("question").asText();var original=responseRows.get(id);
            assertThat(original.path("status").asText()).isEqualTo("RESPONSE_RECEIVED");
            try(var worker=new BoundedRuleTaskPlanner(request->{
                // 仅借用曝光响应，不冒充新提示的真实调用；请求问题与已核验目录仍必须完全一致。
                try {
                    assertThat(mapper.readTree(request.userPayload())).isEqualTo(mapper.readTree(requestRows.get(id).path("body").path("messages").get(1).path("content").asText()));
                    return original.path("rawModelContent").asText();
                }catch(java.io.IOException invalid){throw new IllegalArgumentException(invalid);}
            },Duration.ofSeconds(5),1)) {
                var app=RuleCheckRuntimeFactory.createEvidenceFirst2025(ds,trial.settings(),()->{
                    try{return trial.reviewedCatalogue(root);}catch(Exception failure){throw new IllegalStateException(failure);}
                },worker,()->UUID.randomUUID().toString(),Clock.systemUTC());
                var result=app.checkWithContext(q,Map.of());assertThat(app.isCurrent(result)).as(id).isTrue();
                assertThat(worker.attempts()).isEqualTo(1);
                var locations=new ArrayList<Map<String,Object>>();
                for(var fact:result.decision().confirmedFacts())for(var reference:fact.evidence()) {
                    assertThat(app.isReferenceApproved(reference)).isTrue();var located=trial.originals().locate(reference);
                    assertThat(located.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
                    assertThat(located.original().contextText()).contains(reference.text());locations.add(Map.of("reference",reference,"located",located));
                }
                outcomes.add(Map.of("caseId",id,"question",q,"result",result,"originalLocations",locations,"freshReleaseValidated",true));
                decisions.put(id,result.decision());
            }
        }
        // 先写实际输出，再断言修复效果；失败时也留下全分母结果，不用预期值替换实际输出。
        Files.write(Path.of(System.getProperty("zhku.rulecheck.evidenceDir")).resolve("exposed-response-reexecution.json"),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("mode","EXPOSED_CAPTURE_REEXECUTION_AFTER_REPAIR_NOT_NEW_MODEL",
                "cases",outcomes,"newProviderCalls",0,"confirmationGradeProduced",false,"humanSemanticGradesProduced",false,
                "currentPromptSha256",Reviewed2025ControlledCatalogue.sha(BoundedRuleTaskPlanner.SYSTEM.getBytes(java.nio.charset.StandardCharsets.UTF_8)))),StandardOpenOption.CREATE_NEW);
        assertThat(outcomes).hasSize(43);
        assertThat(decisions.get("R04").status()).isEqualTo(ANSWERED);
        assertThat(decisions.get("R04").confirmedFacts()).hasSize(1).allMatch(f->f.text().contains("30%"));
        for(String id:List.of("R10","N14")) {
            assertThat(decisions.get(id).status()).as(id).isEqualTo(PARTIALLY_ANSWERED);
            assertThat(decisions.get(id).confirmedFacts()).anyMatch(f->f.text().contains("本人申请")&&f.text().contains("教务处审核同意"));
            assertThat(decisions.get(id).unknownItems()).isNotEmpty();
            assertThat(outcomes.stream().filter(row->row.get("caseId").equals(id)).findFirst().orElseThrow().get("result"))
                .isInstanceOfSatisfying(RuleCheckScopedResult.class,result->assertThat(result.evidencePlan().plan().rejectedTasks()).isNotEmpty());
        }
        assertThat(decisions.get("N23").status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(decisions.get("N23").clarificationQuestions()).isEmpty();
        assertThat(decisions.get("N23").confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly("公共选修课成绩不合格者不安排重修。");
        assertThat(decisions.get("N23").unknownItems()).isNotEmpty();
    }
    private JsonNode read(Path path)throws Exception {return mapper.readTree(Files.readAllBytes(path));}
}
