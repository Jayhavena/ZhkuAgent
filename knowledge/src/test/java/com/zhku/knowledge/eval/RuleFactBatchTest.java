package com.zhku.knowledge.eval;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.eval.RuleFactExecutionEvaluation.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

class RuleFactBatchTest {
    @Test void repairSelectionFreezesNineCasesAndOnlyOneConditionalContinuation()throws Exception {
        var plan=RuleFactBatchPlan.load(root(),RuleFactBatchPlan.Selection.REPAIR_9);
        assertThat(plan.cases()).extracting(RuleFactBatchPlan.CasePlan::caseId).containsExactlyElementsOf(RuleFactBatchPlan.REPAIR_IDS);
        assertThat(plan.maximumTasks()).isEqualTo(9);
        assertThat(plan.maximumRuns()).isEqualTo(10);
        assertThat(plan.maximumCalls()).isEqualTo(50);
        assertThat(plan.ownedProcessMillis()).isEqualTo(1_250_000);
        assertThat(plan.cases().stream().filter(c->c.turns().size()==2)).singleElement()
                .satisfies(c->{assertThat(c.caseId()).isEqualTo("RFE-013");assertThat(c.clarificationReply()).isEqualTo("PUBLIC");});
        assertThat(RuleFactBatchReport.aggregate(plan,List.of()).unjudgeable()).isEqualTo(9);
    }
    Path root(){Path p=Path.of("").toAbsolutePath();while(p!=null&&!Files.isDirectory(p.resolve(RuleFactExecutionContract.DIRECTORY)))p=p.getParent();return Objects.requireNonNull(p);}
    @Test void freezesExistingTenCasesAndOnePublicContinuation()throws Exception {
        var plan=RuleFactBatchPlan.load(root());assertThat(plan.cases()).extracting(RuleFactBatchPlan.CasePlan::caseId).containsExactlyElementsOf(RuleFactBatchPlan.IDS);
        assertThat(plan.maximumTasks()).isEqualTo(10);assertThat(plan.maximumRuns()).isEqualTo(11);assertThat(plan.maximumCalls()).isEqualTo(55);
        var ask=plan.cases().stream().filter(c->c.caseId().equals("RFE-013")).findFirst().orElseThrow();
        assertThat(ask.clarificationReply()).isEqualTo("PUBLIC");assertThat(ask.conditionId()).isEqualTo("courseType");assertThat(ask.turns()).hasSize(2);
        assertThat(ask.turns().get(1).factIds()).containsExactly("public-failed-no-retake");
    }
    Result result(Verdict v){var dims=new LinkedHashMap<String,Dimension>();DIMENSIONS.forEach(k->dims.put(k,new Dimension(v,List.of("TEST_EVIDENCE"))));return new Result(null,v==Verdict.FAIL?"FAIL":v==Verdict.UNJUDGEABLE?"UNJUDGEABLE":"PASS",dims);}
    @Test void keepsMissingAndFailedCasesAndSeparatesFullEvaluability()throws Exception {
        var plan=RuleFactBatchPlan.load(root());
        var partial=new LinkedHashMap<>(result(Verdict.PASS).dimensions());partial.put("retrievalRecall",new Dimension(Verdict.UNJUDGEABLE,List.of("OBSERVATION_MISSING")));partial.put("scopeAndApplicability",new Dimension(Verdict.FAIL,List.of("SCOPE")));
        var report=RuleFactBatchReport.aggregate(plan,List.of(
                new RuleFactBatchReport.TurnRecord("RFE-001","ANSWER",result(Verdict.PASS),"capture-1","sha-1",null),
                new RuleFactBatchReport.TurnRecord("RFE-002","ANSWER",new Result(null,"FAIL",partial),"capture-2","sha-2",null)));
        assertThat(report.denominator()).isEqualTo(10);assertThat(report.passed()).isEqualTo(1);assertThat(report.failed()).isEqualTo(1);assertThat(report.unjudgeable()).isEqualTo(8);
        assertThat(report.knownOutcomes()).isEqualTo(2);assertThat(report.fullyEvaluable()).isEqualTo(1);assertThat(report.cases()).hasSize(10);
    }
    @Test void clarificationCannotPassWithoutItsPlannedContinuation()throws Exception {
        var plan=RuleFactBatchPlan.load(root());var r=RuleFactBatchReport.aggregate(plan,List.of(new RuleFactBatchReport.TurnRecord("RFE-013","ASK",result(Verdict.PASS),"capture","sha",null)));
        assertThat(r.cases().get("RFE-013").outcome()).isEqualTo("UNJUDGEABLE");
        var failed=RuleFactBatchReport.aggregate(plan,List.of(new RuleFactBatchReport.TurnRecord("RFE-013","ASK",result(Verdict.FAIL),"capture","sha",null)));
        assertThat(failed.cases().get("RFE-013").outcome()).isEqualTo("FAIL");assertThat(failed.cases().get("RFE-013").fullyEvaluable()).isFalse();
    }
    @Test void duplicateTurnsAreAnErrorAndEmptyBatchNeverPasses()throws Exception {
        var plan=RuleFactBatchPlan.load(root());var row=new RuleFactBatchReport.TurnRecord("RFE-001","ANSWER",result(Verdict.PASS),"capture","sha",null);
        assertThat(RuleFactBatchReport.aggregate(plan,List.of(row,row)).cases().get("RFE-001").outcome()).isEqualTo("ERROR");
        var empty=RuleFactBatchReport.aggregate(plan,List.of());assertThat(empty.passed()).isZero();assertThat(empty.unjudgeable()).isEqualTo(10);
    }
    @Test void approvalRequiresExactBatchBudgetAndEnoughValidityForWholeBatch()throws Exception {
        var plan=RuleFactBatchPlan.load(root());var now=java.time.Instant.now();String sha="a".repeat(64);
        var approval=RuleFactExecutionContract.JSON.createObjectNode().put("status","APPROVED").put("packageSha256",sha)
                .put("maximumTasks",10).put("maximumRuns",11).put("maximumCalls",55).put("automaticRetries",0).put("validUntil",now.plusSeconds(86400).toString());
        assertThatCode(()->RuleFactBatchHost.validateApproval(approval,sha,plan,now)).doesNotThrowAnyException();
        approval.put("maximumCalls",54);assertThatThrownBy(()->RuleFactBatchHost.validateApproval(approval,sha,plan,now)).isInstanceOf(IllegalArgumentException.class);
        approval.put("maximumCalls",55).put("validUntil",now.plusSeconds(60).toString());
        assertThatThrownBy(()->RuleFactBatchHost.validateApproval(approval,sha,plan,now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reportLoadsFailedCaptureWithoutAnnotationsAndRejectsStaleReview(@org.junit.jupiter.api.io.TempDir Path directory)throws Exception {
        var plan=RuleFactBatchPlan.load(root());String sha="a".repeat(64);var json=RuleFactExecutionContract.JSON;
        json.writeValue(directory.resolve("batch-plan.json").toFile(),Map.of("packageSha256",sha,"plan",plan));
        var turn=plan.cases().getFirst().turns().getFirst();UUID task=UUID.randomUUID(),run=UUID.randomUUID(),turnId=UUID.randomUUID();
        var trace=new com.zhku.knowledge.ruleagent.RuleAgentRunTrace(task,turnId,run,2,"FAILED","FAILED",true,false,List.of(),
                new com.zhku.knowledge.ruleagent.RuleAgentRunTrace.ResultView("NONE",List.of(),List.of(),List.of(),List.of()));
        var execution=new Execution(new Identity("RFE-001",task,run,"v1.0",plan.datasetSha256(),"qwen3.8-flash",sha),"processing failure",trace,null,false,null,false,null,false,Map.of());
        var captured=new RuleFactBoundEvaluation.Captured(plan.datasetSha256(),turn,turnId,2,execution,json.createObjectNode(),List.of(),List.of());
        Path folder=directory.resolve("RFE-001/ANSWER");Files.createDirectories(folder);RuleFactBoundEvaluation.write(folder.resolve("capture.json"),captured);
        var sources=new TreeMap<String,String>();var report=RuleFactBatchReport.collect(root(),directory,sources);
        assertThat(report.failed()).isEqualTo(1);assertThat(report.unjudgeable()).isEqualTo(9);assertThat(report.fullyEvaluable()).isZero();assertThat(sources).containsKey("RFE-001/ANSWER/capture.json");
        json.writeValue(folder.resolve("annotation-reviewed.json").toFile(),new RuleFactBoundEvaluation.Annotation("b".repeat(64),"bad",false,Set.of(),false,Map.of(),Map.of()));
        var stale=RuleFactBatchReport.collect(root(),directory,new TreeMap<>());assertThat(stale.errors()).isEqualTo(1);assertThat(stale.passed()).isZero();
    }
    @Test void continuationRetrievalRetainsPriorFailureInsteadOfInventingNewSearch() {
        var child=result(Verdict.PASS);var unknown=new LinkedHashMap<>(child.dimensions());unknown.put("retrievalRecall",new Dimension(Verdict.UNJUDGEABLE,List.of("NO_NEW_SEARCH")));
        var current=new Result(null,"UNJUDGEABLE",unknown);
        assertThat(RuleFactBatchReport.inheritRetrieval(current,result(Verdict.FAIL),"parent-sha").dimensions().get("retrievalRecall").verdict()).isEqualTo(Verdict.FAIL);
        assertThat(RuleFactBatchReport.inheritRetrieval(current,result(Verdict.PASS),"parent-sha").outcome()).isEqualTo("PASS");
    }
    @Test void inheritanceRequiresSameTaskExactParentHashAndDeclaredPublicReply()throws Exception {
        var plan=RuleFactBatchPlan.load(root());var c=plan.cases().stream().filter(p->p.caseId().equals("RFE-013")).findFirst().orElseThrow();
        var json=RuleFactExecutionContract.JSON;UUID task=UUID.randomUUID(),firstRun=UUID.randomUUID(),nextRun=UUID.randomUUID();String config="a".repeat(64);
        var beforeTrace=new com.zhku.knowledge.ruleagent.RuleAgentRunTrace(task,UUID.randomUUID(),firstRun,2,"WAITING_USER","INCOMPLETE",false,false,List.of(),
                new com.zhku.knowledge.ruleagent.RuleAgentRunTrace.ResultView("NONE",List.of(),List.of(),List.of(),List.of()));
        var beforeExecution=new Execution(new Identity("RFE-013",task,firstRun,"v1.0",plan.datasetSha256(),"model",config),"ask",beforeTrace,null,false,null,false,null,false,Map.of());
        var afterExecution=new Execution(new Identity("RFE-013",task,nextRun,"v1.0",plan.datasetSha256(),"model",config),"reply",null,null,false,null,false,null,false,Map.of());
        var pub=json.createObjectNode();var clarification=pub.putArray("items").addObject().putArray("clarification").addObject().put("conditionId","courseType");clarification.putArray("allowedValues").add("PUBLIC").add("PROFESSIONAL");
        var before=new RuleFactBoundEvaluation.Captured(plan.datasetSha256(),c.turns().get(0),beforeTrace.turnId(),2,beforeExecution,pub,List.of(),List.of());
        var after=new RuleFactBoundEvaluation.Captured(plan.datasetSha256(),c.turns().get(1),UUID.randomUUID(),4,afterExecution,json.createObjectNode(),List.of(),List.of());
        var link=json.createObjectNode().put("previousCaptureSha256","parent").put("taskId",task.toString()).put("previousRunId",firstRun.toString()).put("runId",nextRun.toString()).put("conditionId","courseType").put("reply","PUBLIC");
        assertThat(RuleFactBatchReport.validContinuation(before,after,"parent",link)).isTrue();
        assertThat(RuleFactBatchReport.validContinuation(before,after,"changed",link)).isFalse();
        link.put("taskId",UUID.randomUUID().toString());assertThat(RuleFactBatchReport.validContinuation(before,after,"parent",link)).isFalse();
        link.put("taskId",task.toString()).put("reply","PROFESSIONAL");assertThat(RuleFactBatchReport.validContinuation(before,after,"parent",link)).isFalse();
    }
    @Test void cleanupAndFatalStopPolicyHaveExplicitBounds() {
        assertThat(RuleFactBatchHost.resourceCloseWaitSeconds()).isGreaterThanOrEqualTo(com.zhku.knowledge.ruleagent.RuleAgentContract.DEFAULT_BUDGET.maxModelMillis()/1000L+5);
        assertThat(RuleFactBatchHost.batchFatal("MODEL_AUTH_FAILURE")).isTrue();assertThat(RuleFactBatchHost.batchFatal("MODEL_CONNECTION_FAILURE")).isTrue();
        assertThat(RuleFactBatchHost.batchFatal("MODEL_UNAVAILABLE")).isTrue();assertThat(RuleFactBatchHost.batchFatal("MODEL_TIMEOUT")).isFalse();
    }
    @Test void storeFailuresAndMissingOutcomeKeepTypedReasons() {
        var failure=new com.zhku.knowledge.ruleagent.RuleAgentTaskStore.Failure(com.zhku.knowledge.ruleagent.RuleAgentTaskStore.Code.EXPIRED);
        assertThat(RuleFactBatchHost.failureCode(failure)).isEqualTo("TASK_STORE_EXPIRED");
        assertThatThrownBy(()->RuleFactBatchHost.requireOutcome(null)).isInstanceOf(RuleFactBatchHost.OutcomeMissing.class);
        assertThat(RuleFactBatchHost.failureCode(new RuleFactBatchHost.OutcomeMissing())).isEqualTo("RUN_OUTCOME_MISSING");
    }
    @Test void createFailureReachesPerTurnReportRatherThanGenericMissing(@org.junit.jupiter.api.io.TempDir Path directory)throws Exception {
        var plan=RuleFactBatchPlan.load(root());var json=RuleFactExecutionContract.JSON;
        json.writeValue(directory.resolve("batch-plan.json").toFile(),Map.of("packageSha256","a".repeat(64),"plan",plan));
        Path caseDir=directory.resolve("RFE-001");Files.createDirectories(caseDir);
        json.writeValue(caseDir.resolve("case-error.json").toFile(),Map.of("code","TASK_STORE_CAPACITY","phase","CREATE"));
        var report=RuleFactBatchReport.collect(root(),directory,new TreeMap<>());
        assertThat(report.cases().get("RFE-001").outcome()).isEqualTo("ERROR");
        assertThat(report.cases().get("RFE-001").dimensions().get("executionSafety").reasons()).contains("ANSWER:ERROR:CASE_TASK_STORE_CAPACITY");
    }
    @Test void reportFailureLeavesRecoverableArtifact(@org.junit.jupiter.api.io.TempDir Path directory)throws Exception {
        assertThatCode(()->RuleFactBatchHost.writeReports(root(),directory)).doesNotThrowAnyException();
        var artifact=RuleFactExecutionContract.JSON.readTree(Files.readAllBytes(directory.resolve("report-error.json")));
        assertThat(artifact.path("executionEvidenceRetained").asBoolean()).isTrue();assertThat(artifact.path("offlineRecovery").asText()).isEqualTo("RuleFactBatchReport.main");
    }
}
