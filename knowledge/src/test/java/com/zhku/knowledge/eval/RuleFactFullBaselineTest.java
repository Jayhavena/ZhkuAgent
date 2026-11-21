package com.zhku.knowledge.eval;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;
import static com.zhku.knowledge.eval.RuleFactExecutionEvaluation.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

class RuleFactFullBaselineTest {
    Path root(){return new RuleFactBatchTest().root();}
    @Test void fullPackageCanPinQwen4Max()throws Exception {
        var pack=RuleFactBatchHost.definition(root(),RuleFactBatchPlan.Selection.FULL_20,"qwen4-max");
        assertThat(pack.path("model").asText()).isEqualTo("qwen4-max");
        assertThat(pack.path("baselineIdentity").path("requestedModel").asText()).isEqualTo("qwen4-max");
        assertThat(RuleFactBatchHost.definition(root(),true).path("model").asText()).isEqualTo("qwen4-max");
        assertThat(RuleFactBatchHost.definition(root(),RuleFactBatchPlan.Selection.SUBSET_10,"qwen4-max").path("model").asText()).isEqualTo("qwen4-max");
        assertThatThrownBy(()->RuleFactBatchHost.definition(root(),RuleFactBatchPlan.Selection.FULL_20,"unapproved-model"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void fullPlanRetainsAllTwentyAndOneConditionalContinuation()throws Exception {
        var plan=RuleFactBatchPlan.load(root(),true);
        assertThat(plan.cases()).extracting(RuleFactBatchPlan.CasePlan::caseId).containsExactlyElementsOf(new TreeSet<>(load(root()).dataset().cases().keySet()));
        assertThat(plan.maximumTasks()).isEqualTo(20);assertThat(plan.maximumRuns()).isEqualTo(21);
        assertThat(plan.maximumCalls()).isEqualTo(105);assertThat(plan.ownedProcessMillis()).isEqualTo(2550000);
        var report=RuleFactBatchReport.aggregate(plan,List.of());assertThat(report.denominator()).isEqualTo(20);
        assertThat(report.passed()).isZero();assertThat(report.unjudgeable()).isEqualTo(20);
        assertThat(RuleFactBatchReport.markdown(report)).contains("# 20题","0/20","批外冻结题数：0");
    }
    @Test void semanticOmissionAndMissingObservationsDoNotInventRetrievalOrConditionErrors()throws Exception {
        var dims=new LinkedHashMap<String,Dimension>();DIMENSIONS.forEach(k->dims.put(k,new Dimension(Verdict.PASS,List.of("observed"))));
        dims.put("scopeAndApplicability",new Dimension(Verdict.FAIL,List.of("historical qualifier missing")));
        dims.put("retrievalRecall",new Dimension(Verdict.UNJUDGEABLE,List.of("observation missing")));
        var report=RuleFactBatchReport.aggregate(RuleFactBatchPlan.load(root(),true),List.of(new RuleFactBatchReport.TurnRecord("RFE-001","ANSWER",new Result(null,"FAIL",dims),"capture.json","a".repeat(64),null)));
        var analysis=RuleFactFailureAnalysis.classify(report,List.of());
        assertThat(analysis.affectedCases().get(RuleFactFailureAnalysis.Category.RETRIEVAL_FAILURE)).isZero();
        assertThat(analysis.affectedCases().get(RuleFactFailureAnalysis.Category.CONDITION_JUDGMENT_ERROR)).isZero();
        assertThat(analysis.cases().get("RFE-001").getFirst().category()).isEqualTo(RuleFactFailureAnalysis.Category.ATTRIBUTION_UNKNOWN);
    }
    @Test void canonicalIdentityIgnoresObjectKeyOrderButPreservesPromptAndArrayChanges()throws Exception {
        assertThat(RuleFactBaselineIdentity.stableSha(JSON.readTree("{\"a\":1,\"b\":2}"))).isEqualTo(RuleFactBaselineIdentity.stableSha(JSON.readTree("{\"b\":2,\"a\":1}")));
        assertThat(RuleFactBaselineIdentity.stableSha(JSON.readTree("[1,2]"))).isNotEqualTo(RuleFactBaselineIdentity.stableSha(JSON.readTree("[2,1]")));
        var pack=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(Files.readAllBytes(root().resolve("docs/evidence/rule-agent-provider/2026-11-17-frozen-m2/request-package.json")));
        var before=RuleFactBaselineIdentity.create(root(),pack,false);pack.put("systemPrompt",pack.path("systemPrompt").asText()+" changed");
        var after=RuleFactBaselineIdentity.create(root(),pack,true);var comparison=RuleFactBaselineIdentity.compare(before,after);
        assertThat(comparison.get("decisionPromptSha256")).isFalse();assertThat(comparison.get("datasetSha256")).isTrue();
        pack.remove("sourceIdentity");assertThatThrownBy(()->RuleFactBaselineIdentity.create(root(),pack,true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reviewCannotBorrowUnboundCapture(@org.junit.jupiter.api.io.TempDir Path directory)throws Exception {
        JSON.writeValue(directory.resolve("failure-reviews.json").toFile(),List.of(new RuleFactFailureAnalysis.Review("RFE-001","elsewhere.json","a".repeat(64),RuleFactFailureAnalysis.Category.CONDITION_JUDGMENT_ERROR,"WRONG_BRANCH",true,"reviewer","reason","excerpt")));
        var report=RuleFactBatchReport.aggregate(RuleFactBatchPlan.load(root(),true),List.of());
        assertThatThrownBy(()->RuleFactFailureAnalysis.loadReviews(directory,report)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void oldTenCaseAuthorizationCannotAuthorizeFullBaseline()throws Exception {
        var approval=JSON.createObjectNode().put("status","APPROVED").put("packageSha256","a".repeat(64)).put("maximumCalls",55).put("maximumTasks",10).put("maximumRuns",11).put("automaticRetries",0).put("validUntil",java.time.Instant.now().plusSeconds(86400).toString());
        assertThatThrownBy(()->RuleFactBatchHost.validateApproval(approval,"a".repeat(64),RuleFactBatchPlan.load(root(),true),java.time.Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void harnessAndUntypedCitationFailuresAreNotConfirmedAgentDefects()throws Exception {
        var plan=RuleFactBatchPlan.load(root(),true);var dimensions=new LinkedHashMap<>(new RuleFactBatchTest().result(Verdict.PASS).dimensions());
        dimensions.put("citationIntegrity",new Dimension(Verdict.FAIL,List.of("citation inventory requirement failed")));
        var report=RuleFactBatchReport.aggregate(plan,List.of(
                new RuleFactBatchReport.TurnRecord("RFE-001","ANSWER",null,null,null,"ERROR:CAPTURE_OR_ANNOTATION_INVALID"),
                new RuleFactBatchReport.TurnRecord("RFE-002","ANSWER",new Result(null,"FAIL",dimensions),"capture.json","a".repeat(64),null)));
        var analysis=RuleFactFailureAnalysis.classify(report,List.of());
        assertThat(analysis.affectedCases().get(RuleFactFailureAnalysis.Category.EXECUTION_EXCEPTION)).isZero();
        assertThat(analysis.affectedCases().get(RuleFactFailureAnalysis.Category.CITATION_ERROR)).isZero();
        assertThat(analysis.nonDefectCases().get(RuleFactFailureAnalysis.Category.EXECUTION_EXCEPTION)).isEqualTo(1);
        assertThat(analysis.nonDefectCases().get(RuleFactFailureAnalysis.Category.CITATION_ERROR)).isEqualTo(1);
        for(var counts:report.dimensions().values())assertThat(counts.passed()+counts.failed()+counts.unjudgeable()+counts.errors()+counts.notApplicable()).isEqualTo(20);
        assertThat(RuleFactBatchReport.markdown(report)).contains("UNJUDGEABLE | ERROR");
    }
    @Test void comparisonDisclosesChangedJarSelectionAndExecutionSources()throws Exception {
        var pack=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(Files.readAllBytes(root().resolve("docs/evidence/rule-agent-provider/2026-11-17-frozen-m2/request-package.json")));
        var before=RuleFactBaselineIdentity.create(root(),pack,false);pack.put("jarSha256","c".repeat(64));
        ((com.fasterxml.jackson.databind.node.ObjectNode)pack.path("batchPlan")).put("selectionSha256","d".repeat(64)).put("maximumTasks",20).put("maximumRuns",21);
        ((com.fasterxml.jackson.databind.node.ObjectNode)pack.path("sourceIdentity")).put("knowledge/src/main/java/com/zhku/knowledge/eval/RuleFactBatchHost.java","e".repeat(64));
        var comparison=RuleFactBaselineIdentity.compare(before,RuleFactBaselineIdentity.create(root(),pack,false));
        assertThat(comparison).containsEntry("runtimeJarSha256",false).containsEntry("caseSelectionSha256",false).containsEntry("evaluationSources",false).containsEntry("maximumTasks",false);
    }
    @Test void authorizationCopiesRejectPackageOrByteDrift(@org.junit.jupiter.api.io.TempDir Path folder)throws Exception {
        byte[] approval=JSON.writeValueAsBytes(Map.of("packageSha256","a".repeat(64)));
        JSON.writeValue(folder.resolve("batch-plan.json").toFile(),Map.of("packageSha256","a".repeat(64),"approvalSha256",sha(approval)));
        assertThatThrownBy(()->RuleFactBatchReport.executionIdentity(folder)).isInstanceOf(IllegalArgumentException.class);
        Files.write(folder.resolve("execution-approval.json"),approval);assertThat(RuleFactBatchReport.executionIdentity(folder)).containsEntry("approvalSha256",sha(approval));
        JSON.writeValue(folder.resolve("execution-approval.json").toFile(),Map.of("packageSha256","b".repeat(64)));
        assertThatThrownBy(()->RuleFactBatchReport.executionIdentity(folder)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void failedCloseStillShutsDownOwnedExecutor() {
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        assertThat(RuleFactBatchHost.closeCaseResources(()->{throw new IllegalStateException("fixture close failure");},()->false,executor)).isFalse();
        assertThat(executor.isShutdown()).isTrue();
        assertThat(RuleFactBatchHost.failureCode(new RuleFactBatchHost.BatchStop("MODEL_AUTH_FAILURE"))).isEqualTo("MODEL_AUTH_FAILURE");
    }
    @Test void optionalApprovalClaimsCannotContradictFrozenBudget()throws Exception {
        var plan=RuleFactBatchPlan.load(root(),true);var now=java.time.Instant.now();
        var approval=JSON.createObjectNode().put("status","APPROVED").put("packageSha256","a".repeat(64)).put("maximumCalls",105).put("maximumTasks",20).put("maximumRuns",21).put("automaticRetries",0).put("validUntil",now.plusSeconds(86400).toString()).put("maximumCallsPerRun",6);
        assertThatThrownBy(()->RuleFactBatchHost.validateApproval(approval,"a".repeat(64),plan,now)).isInstanceOf(IllegalArgumentException.class);
        approval.put("maximumCallsPerRun",5);assertThatCode(()->RuleFactBatchHost.validateApproval(approval,"a".repeat(64),plan,now)).doesNotThrowAnyException();
    }
}
