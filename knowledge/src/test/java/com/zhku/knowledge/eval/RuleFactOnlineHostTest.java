package com.zhku.knowledge.eval;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;

class RuleFactOnlineHostTest {
    @Test void relevanceDiagnosticStaysOutOfSharedEventsAndIsBoundInLocalSidecar() {
        var identity=new RuleFactExecutionEvaluation.Identity("RFE-001",java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"v1.0","a".repeat(64),"qwen3.8-flash","b".repeat(64));
        var detail=new com.zhku.knowledge.ruleagent.RulePublicationContract.RelevanceDiagnostic("q1",java.util.List.of("S02/GENERAL:known-fact"),null,java.util.List.of());
        var review=new com.zhku.knowledge.ruleagent.RulePublicationContract.SemanticAssessment(
                com.zhku.knowledge.ruleagent.RulePublicationContract.REVISION,"e".repeat(64),
                java.util.List.of(new com.zhku.knowledge.ruleagent.RulePublicationContract.Requirement("r1",
                        new com.zhku.knowledge.ruleagent.RulePublicationContract.DemandSpan("q1",0,1),
                        com.zhku.knowledge.ruleagent.RulePublicationContract.Dimension.RETAKE,
                        com.zhku.knowledge.ruleagent.RulePublicationContract.Subject.PUBLIC_ELECTIVE,
                        com.zhku.knowledge.ruleagent.RulePublicationContract.Recognition.IDENTIFIED,java.util.List.of())),
                java.util.List.of(),java.util.List.of());
        var event=new com.zhku.knowledge.ruleagent.RuleAgentRunner.Event(4,"PUBLICATION_REVIEW",null,
                com.zhku.knowledge.ruleagent.RuleAgentContract.ReasonCode.INVALID_MODEL_RESPONSE,
                com.zhku.knowledge.ruleagent.RulePublicationContract.Code.FACT_NOT_RELEVANT,detail,review);
        assertThat(JSON.valueToTree(event).has("relevanceDiagnostic")).isFalse();
        assertThat(JSON.valueToTree(event).has("review")).isFalse();
        var local=JSON.valueToTree(RuleFactOnlineHost.publicationDiagnostics(identity,"c".repeat(64),java.util.List.of(event)));
        assertThat(local.path("captureSha256").asText()).isEqualTo("c".repeat(64));
        assertThat(local.path("identity").path("runId").asText()).isEqualTo(identity.runId().toString());
        assertThat(local.path("semanticReviews").get(0).path("assessment").path("requirements").get(0).path("dimension").asText())
                .isEqualTo("RETAKE");
        var entry=local.path("relevanceRejections").get(0);
        assertThat(entry.path("attempt").asInt()).isEqualTo(4);
        assertThat(entry.path("detail").path("proposedFactIds").get(0).asText()).isEqualTo("S02/GENERAL:known-fact");
    }
    @Test void validatedFinishExclusionIsRecordedWithoutBeingCountedAsARejection() {
        var identity=new RuleFactExecutionEvaluation.Identity("RFE-014",java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"v1.0","a".repeat(64),"qwen3.8-flash","b".repeat(64));
        var detail=new com.zhku.knowledge.ruleagent.RulePublicationContract.RelevanceDiagnostic("q1",
                java.util.List.of("S10/DEFERRED_EXAM_ONLY:public-no-deferred-exam","S10/RETAKE_ONLY:public-failed-no-retake"),
                null,java.util.List.of());
        var event=new com.zhku.knowledge.ruleagent.RuleAgentRunner.Event(4,"PUBLICATION_REVIEW",null,null,null,detail);
        var local=JSON.valueToTree(RuleFactOnlineHost.publicationDiagnostics(identity,"d".repeat(64),java.util.List.of(event)));
        assertThat(local.path("relevanceRejections")).isEmpty();
        var exclusion=local.path("relevanceExclusions").get(0);
        assertThat(exclusion.path("attempt").asInt()).isEqualTo(4);
        assertThat(exclusion.path("detail").path("proposedFactIds").toString())
                .contains("S10/DEFERRED_EXAM_ONLY:public-no-deferred-exam","S10/RETAKE_ONLY:public-failed-no-retake");
    }
    @Test void databaseUrlMatchesExistingInstallerAllowlist() {
        assertThat(RuleFactOnlineHost.dedicatedDatabaseUrl("jdbc:postgresql://127.0.0.1:55519/zhkuagent_rulecheck_e2e"))
                .endsWith("/zhkuagent_rulecheck_e2e");
        for(String url:new String[]{"jdbc:postgresql://127.0.0.1:54329/zhkuagent_rulecheck_e2e",
                "jdbc:postgresql://127.0.0.1:70000/zhkuagent_rulecheck_e2e",
                "jdbc:postgresql://127.0.0.1:55519/zhkuagent_rulefact_eval_random",
                "jdbc:postgresql://remote:55519/zhkuagent_rulecheck_e2e"})
            assertThatThrownBy(()->RuleFactOnlineHost.dedicatedDatabaseUrl(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void contractSerializesIdenticallyAcrossSeparateJavaProcesses()throws Exception {
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.isDirectory(root.resolve(DIRECTORY)))root=root.getParent();
        assertThat(root).isNotNull();
        String expected=JSON.writeValueAsString(load(root).turns("RFE-001").getFirst());
        for(int i=0;i<3;i++) {
            var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),
                    "-Dfile.encoding=UTF-8","-cp",System.getProperty("java.class.path"),Serializer.class.getName(),root.toString());
            builder.environment().remove("DASHSCOPE_API_KEY");builder.environment().remove("ZHKU_DB_PASSWORD");
            var process=builder.start();
            try {
                assertThat(process.waitFor(15,TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).isZero();
                assertThat(new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(expected);
            } finally {if(process.isAlive())process.destroyForcibly();}
        }
    }
    Path root(){return new RuleFactBatchTest().root();}
    @Test void singleQuestionProbeBindsQwen4MaxEndToEndOffline()throws Exception {
        var pack=RuleFactOnlineHost.definition(root(),"RFE-003",com.zhku.knowledge.ruleagent.RuleAgentProvider.QWEN4_MAX);
        assertThat(pack.path("model").asText()).isEqualTo("qwen4-max");
        assertThat(pack.path("evaluationTurn").path("caseId").asText()).isEqualTo("RFE-003");
        assertThat(RuleFactOnlineHost.definition(root(),"RFE-004").path("model").asText()).isEqualTo("qwen4-max");
        assertThatThrownBy(()->RuleFactOnlineHost.definition(root(),"RFE-003",com.zhku.knowledge.ruleagent.RuleAgentProvider.DEEPSEEK_V3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->RuleFactOnlineHost.definition(root(),"RFE-003","unapproved-model"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    public static class Serializer {
        public static void main(String[] args)throws Exception {System.out.write(JSON.writeValueAsBytes(load(Path.of(args[0])).turns("RFE-001").getFirst()));}
    }
}
