package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.knowledge.eval.chain.EvalEvidenceCollector;
import com.zhku.knowledge.eval.chain.EvalChainRunner;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.Timeout(20)
class RuleAgentProbeFailureCaptureTest {
    @TempDir Path temporary;
    final ObjectMapper json=new ObjectMapper();
    TaskSnapshot failed(UUID task,UUID run,long version){
        var issue=new Issue("failure",IssueKind.PROCESSING_FAILURE,ReasonCode.INVALID_MODEL_RESPONSE,"合成失败","重新查证");
        var result=new ResultEnvelope(version,run,"synthetic","2025.9.2",Coverage.NONE,List.of(new Subquestion("q1",Coverage.NONE,List.of(),List.of(issue),List.of())),List.of(),List.of());
        return new TaskSnapshot(SCHEMA_VERSION,task,UUID.randomUUID(),run,version,TaskStatus.FAILED,com.zhku.agent.agent.runtime.AgentRunState.FAILED,true,result,null,new Execution(Mode.ONLINE,1,0,false,AnswerSource.NONE,ReplayCoverage.NOT_APPLICABLE));
    }
    @Test void appendPreservesFirstRunAndRejectsForeignDuplicateOrThirdRun()throws Exception{
        Path evidence=Files.createDirectory(temporary.resolve("append"));var prior=new ArrayList<TaskSnapshot>();UUID task=UUID.randomUUID();
        var first=failed(task,UUID.randomUUID(),2);RuleAgentOnlineProbeHost.appendPublishedSnapshot(evidence,prior,first);
        byte[] saved=Files.readAllBytes(evidence.resolve("task-results.json"));
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.appendPublishedSnapshot(evidence,prior,failed(UUID.randomUUID(),UUID.randomUUID(),4))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.appendPublishedSnapshot(evidence,prior,failed(task,first.runId(),4))).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(evidence.resolve("task-results.json"))).isEqualTo(saved);
        var second=failed(task,UUID.randomUUID(),4);RuleAgentOnlineProbeHost.appendPublishedSnapshot(evidence,prior,second);
        var array=json.readTree(Files.readAllBytes(evidence.resolve("task-results.json")));
        assertThat(array.size()).isEqualTo(2);assertThat(array.get(0)).isEqualTo(json.readTree(json.writeValueAsBytes(first)));assertThat(array.get(1)).isEqualTo(json.readTree(json.writeValueAsBytes(second)));
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.appendPublishedSnapshot(evidence,prior,failed(task,UUID.randomUUID(),6))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void existingHistoryCannotBeOverwritten()throws Exception{
        var prior=new ArrayList<TaskSnapshot>();Path file=temporary.resolve("task-results.json");Files.writeString(file,"PREVIOUS_EVIDENCE");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.appendPublishedSnapshot(temporary,prior,failed(UUID.randomUUID(),UUID.randomUUID(),2))).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(file)).isEqualTo("PREVIOUS_EVIDENCE");assertThat(prior).isEmpty();
    }
    @Test void pendingCollisionLeavesPublishedPrefixAndForeignPendingFileUntouched()throws Exception{
        var prior=new ArrayList<TaskSnapshot>();UUID task=UUID.randomUUID();RuleAgentOnlineProbeHost.appendPublishedSnapshot(temporary,prior,failed(task,UUID.randomUUID(),2));
        var file=temporary.resolve("task-results.json");byte[] saved=Files.readAllBytes(file);var pending=temporary.resolve("task-results.next.json");Files.writeString(pending,"FOREIGN_PENDING");
        assertThatThrownBy(()->RuleAgentOnlineProbeHost.appendPublishedSnapshot(temporary,prior,failed(task,UUID.randomUUID(),4))).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(prior).hasSize(1);assertThat(Files.readAllBytes(file)).isEqualTo(saved);assertThat(Files.readString(pending)).isEqualTo("FOREIGN_PENDING");
    }
    @Test void firstRunFailureIsPublishedBeforeProbeGuardAndNoContinuationIsInvented()throws Exception{
        var wire=new RuleAgentOnlineFactoryTest();wire.invalidIssuePair=true;wire.start(10);
        try {
        var fixture=wire.fixture;var outcomes=new HashMap<UUID,RuleAgentRunner.Outcome>();
        Path evidence=Files.createDirectory(temporary.resolve("live-synthetic"));
        RuleAgentTaskStore.Factory factory=(owner,task)->{
            var actual=wire.factory.open(owner,task);
            return new RuleAgentTaskStore.Work(){
                public RuleAgentRunner.Outcome run(AgentRunContext context,RuleAgentRunner.Input input){
                    var result=actual.run(context,input);RuleAgentOnlineProbeHost.recordOutcome(evidence,outcomes,result);return result;
                }
                public boolean revalidate(ResultEnvelope prior){throw new AssertionError("失败首轮不能续答");}
            };
        };
        try(var store=new RuleAgentTaskStore(Runnable::run,factory,Clock.systemUTC(),RuleAgentTaskStore.DEFAULT_LIMITS)){
            var cases=json.readTree("[{\"question\":\"选修课程类别是什么？\",\"reply\":\"PUBLIC\"}]");
            assertThatThrownBy(()->RuleAgentOnlineProbeHost.runP02Tasks(store,fixture.owner,cases,evidence,outcomes)).isInstanceOf(IllegalStateException.class);
            var published=json.readTree(Files.readAllBytes(evidence.resolve("task-results.json")));
            assertThat(published.size()).isEqualTo(1);assertThat(published.get(0).path("taskStatus").asText()).isEqualTo("FAILED");
            UUID task=UUID.fromString(published.get(0).path("taskId").asText());
            assertThat(published.get(0)).isEqualTo(json.readTree(json.writeValueAsBytes(store.snapshot(fixture.owner,task))));
            assertThat(store.history(fixture.owner,task)).hasSize(1);assertThat(wire.calls).hasValue(2);assertThat(wire.factory.resourcesClosed()).isTrue();
            var collected=EvalEvidenceCollector.collect(evidence,"synthetic-failed-probe",temporary.resolve("collected"),temporary);
            assertThat(collected.status()).isEqualTo(EvalEvidenceCollector.Status.COMPLETE);
            assertThat(collected.runs()).singleElement().satisfies(r->assertThat(r.taskStatus()).isEqualTo("FAILED"));
            // 组合真实Collector/Grader核对失败可评分；Python对首轮绑定的测试独立运行。
            Path repo=Path.of("").toAbsolutePath();while(!Files.exists(repo.resolve("knowledge/pom.xml")))repo=repo.getParent();
            var suite=json.readTree(Files.readAllBytes(repo.resolve("docs/agent/eval/m2-minimal-20261114/suite-template.json")));
            var block=(com.fasterxml.jackson.databind.node.ObjectNode)suite.path("blocks").get(0);
            block.put("evidenceDir","live-synthetic");
            ((com.fasterxml.jackson.databind.node.ObjectNode)block.path("cases").get(0)).put("runId",published.get(0).path("runId").asText());
            Path suitePath=temporary.resolve("suite.json");Files.write(suitePath,json.writeValueAsBytes(suite));
            EvalChainRunner.run(temporary,suitePath,temporary.resolve("report")).write(temporary.resolve("report"));
            var report=json.readTree(Files.readAllBytes(temporary.resolve("report/report.json")));
            assertThat(report.path("failed").asInt()).isEqualTo(1);assertThat(report.path("notEvaluable").asInt()).isEqualTo(1);
            assertThat(report.path("passed").asInt()).isZero();assertThat(report.path("regressionPassed").asBoolean()).isFalse();
        }
        } finally {wire.cleanup();}
    }
}
