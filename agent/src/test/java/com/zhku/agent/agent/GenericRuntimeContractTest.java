package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.agent.runtime.contract.*;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.agent.tools.TerminateTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.tool.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GenericRuntimeContractTest {
    private AgentRunObservationDispatcher observations;
    @org.junit.jupiter.api.BeforeEach
    void openObservations() { observations = AgentRunObservationDispatcher.createOwned(); }
    @org.junit.jupiter.api.AfterEach
    void closeObservations() throws Exception {
        observations.close();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (observations.status().liveThreadCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertThat(observations.status().liveThreadCount()).isZero();
    }
    private static void awaitObserved(AgentRunContext context) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            var status = context.observationStatus();
            if (status.lastReturnedSequence() == status.lastProducedSequence() && !status.inFlight()) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("正常观察交付超过固定1000ms");
    }

    @Test
    void completedAndBlockedKeepOriginalTextAndProduceSharedFinalSnapshots() {
        for (String outcome : List.of("COMPLETED", "BLOCKED")) {
            var agent = agent(prompt -> response(call("doTerminate", "{\"outcome\":\"" + outcome + "\"}")));
            assertThat(agent.run("任务")).contains("Step 1:", "doTerminate", outcome);
            awaitObserved(agent.context);
            assertThat(agent.events).isNotEmpty();
            var snapshot = agent.events.getLast().runSnapshot();
            assertThat(snapshot.endReason())
                    .isEqualTo(outcome.equals("COMPLETED") ? EndReason.COMPLETED : EndReason.BLOCKED);
            assertThat(snapshot.terminated()).isTrue();
            awaitObserved(agent.context);
        assertThat(agent.events.stream().filter(e -> e.type() == AgentRunEvent.Type.RUN_TERMINATED)).hasSize(1);
        }
    }

    @Test
    void ordinaryToolSuccessStillEndsAtStepLimitAndPreservesTextPayload() {
        var agent = agent(prompt -> response(call("ordinary", "{}")));
        agent.run("任务");
        awaitObserved(agent.context);
        assertThat(agent.context.snapshot().endReason()).isEqualTo(EndReason.STEP_LIMIT);
        assertThat(agent.events).anySatisfy(e -> {
            assertThat(e.type()).isEqualTo(AgentRunEvent.Type.TOOL_CALL_RESULT);
            // 原框架将 String 返回值序列化成带引号的文本；契约必须原样保留。
            assertThat(e.toolCall().result().payload()).isEqualTo("\"plain text\"");
            assertThat(e.toolCall().result().payloadFormat()).isEqualTo(AgentToolCall.PayloadFormat.TEXT);
        });
    }

    @Test
    void batchFailureDoesNotInventIndividualResultsOrSuccess() {
        var agent = agent(prompt -> response(call("doTerminate", "{\"outcome\":\"COMPLETED\"}"), call("fail", "{}")));
        assertThatThrownBy(() -> agent.run("任务")).hasMessage("TOOL_FAILURE");
        assertThat(agent.context.snapshot().error().category()).isEqualTo(AgentRuntimeError.Category.TOOL_FAILURE);
        awaitObserved(agent.context);
        assertThat(agent.events.stream().filter(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_PROPOSED)).hasSize(2);
        assertThat(agent.events).noneMatch(e -> e.type() == AgentRunEvent.Type.TOOL_CALL_RESULT);
        assertThat(agent.events.toString()).doesNotContain("SECRET");
    }

    @Test
    void modelFailureAndCancellationHaveDifferentSharedFacts() {
        var failed = agent(prompt -> {
            throw new IllegalStateException("SECRET");
        });
        assertThatThrownBy(() -> failed.run("任务")).hasMessage("MODEL_FAILURE");
        assertThat(failed.context.snapshot().error().category()).isEqualTo(AgentRuntimeError.Category.MODEL_FAILURE);
        var stopped = agent(prompt -> response(call("doTerminate", "{\"outcome\":\"COMPLETED\"}")));
        stopped.stop = true;
        stopped.run("任务");
        assertThat(stopped.context.snapshot().endReason()).isEqualTo(EndReason.STOPPED);
        assertThat(stopped.context.snapshot().error()).isNull();
    }

    private TestAgent agent(ChatModel model) {
        return new TestAgent(model, observations);
    }

    public static class Tools {
        @Tool(description = "正常测试")
        public String ordinary() {
            return "plain text";
        }

        @Tool(description = "失败测试")
        public String fail() {
            throw new IllegalStateException("SECRET");
        }
    }

    private static class TestAgent extends ToolCallAgent {
        final List<AgentRunEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AgentRunObservationDispatcher observations;
        AgentRunContext context;
        boolean stop;

        TestAgent(ChatModel model, AgentRunObservationDispatcher observations) {
            super(ToolCallbacks.from(new TerminateTool(), new Tools()), Runnable::run);
            this.observations = observations;
            setChatClient(ChatClient.builder(model).build());
            setSystemPrompt("test");
            setMaxSteps(1);
        }

        protected AgentRunObservationDispatcher observationDispatcher() { return observations; }

        protected void onRunEvent(AgentRunEvent event) {
            events.add(event);
        }

        @Override
        protected com.zhku.agent.agent.model.ToolCallDecision think(AgentRunContext context) {
            this.context = context;
            var decision = super.think(context);
            if (stop)
                context.requestStop(AgentStopReason.USER_REQUEST);
            return decision;
        }
    }

    private static AssistantMessage.ToolCall call(String name, String args) {
        return new AssistantMessage.ToolCall(UUID.randomUUID().toString(), "function", name, args);
    }

    private static ChatResponse response(AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", Map.of(), List.of(calls)))));
    }
}
