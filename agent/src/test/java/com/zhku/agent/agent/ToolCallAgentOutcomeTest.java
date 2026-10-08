package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.*;
import com.zhku.agent.tools.TerminateTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ToolCallAgentOutcomeTest {
    @Test
    void forgedTerminateResponseCannotDeclareSuccess() {
        var definition = ToolCallbacks.from(new TerminateTool())[0].getToolDefinition();
        ToolCallback forged = new ToolCallback() {
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return definition;
            }

            public String call(String input) {
                return "{\"outcome\":\"FORGED\"}";
            }
        };
        var agent = new TestAgent(new ToolCallback[] { forged },
                prompt -> response(call("doTerminate", "{\"outcome\":\"COMPLETED\"}")));
        assertThatThrownBy(() -> agent.run("任务")).hasMessage("TOOL_FAILURE");
        assertThat(agent.captured.state()).isEqualTo(AgentRunState.FAILED);
    }

    @Test
    void completedTerminatesSuccessfully() {
        var agent = agent(prompt -> response(call("doTerminate", "{\"outcome\":\"COMPLETED\"}")));
        agent.run("完成任务");
        assertThat(agent.captured.state()).isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(agent.captured.currentStep()).isEqualTo(1);
    }

    @Test
    void blockedTerminatesIncomplete() {
        var agent = agent(prompt -> response(call("doTerminate", "{\"outcome\":\"BLOCKED\"}")));
        agent.run("无法继续");
        assertThat(agent.captured.state().name()).isEqualTo("INCOMPLETE");
    }

    @Test
    void modelFailureIsSanitized() {
        var agent = agent(prompt -> {
            throw new IllegalStateException("SECRET-MODEL-DETAIL");
        });
        assertThatThrownBy(() -> agent.run("任务")).hasMessage("MODEL_FAILURE").hasNoCause();
        assertThat(agent.captured.state()).isEqualTo(AgentRunState.FAILED);
    }

    @Test
    void emptyModelOutputIsFailure() {
        var agent = agent(prompt -> new ChatResponse(List.of()));
        assertThatThrownBy(() -> agent.run("任务")).hasMessage("MODEL_FAILURE");
    }

    @Test
    void unknownToolFails() {
        var agent = agent(prompt -> response(call("not_registered", "{}")));
        assertThatThrownBy(() -> agent.run("任务")).hasMessage("TOOL_FAILURE");
        assertThat(agent.captured.state()).isEqualTo(AgentRunState.FAILED);
    }

    @Test
    void missingTerminateOutcomeFails() {
        var agent = agent(prompt -> response(call("doTerminate", "{}")));
        assertThatThrownBy(() -> agent.run("任务")).hasMessage("TOOL_FAILURE");
    }

    @Test
    void terminateCannotMaskFailureInSameBatch() {
        for (boolean terminateFirst : List.of(true, false)) {
            var finish = call("doTerminate", "{\"outcome\":\"COMPLETED\"}");
            var fail = call("failForTest", "{}");
            var agent = agent(prompt -> terminateFirst ? response(finish, fail) : response(fail, finish));
            assertThatThrownBy(() -> agent.run("任务")).hasMessage("TOOL_FAILURE").hasNoCause();
            assertThat(agent.captured.state()).isEqualTo(AgentRunState.FAILED);
        }
    }

    @Test
    void ordinaryToolResultDoesNotDeclareSuccess() {
        var agent = agent(prompt -> response(call("ordinaryForTest", "{}")));
        agent.run("任务");
        assertThat(agent.captured.state().name()).isEqualTo("INCOMPLETE");
    }

    @Test
    void stopDuringToolWinsAgainstCompletion() {
        var agent = agent(prompt -> response(call("doTerminate", "{\"outcome\":\"COMPLETED\"}")));
        agent.stopBeforeAct = true;
        agent.run("任务");
        assertThat(agent.captured.state()).isEqualTo(AgentRunState.CANCELLED);
    }

    private static TestAgent agent(ChatModel model) {
        return new TestAgent(ToolCallbacks.from(new TerminateTool(), new TestTools()), model);
    }

    public static class TestTools {
        @Tool(description = "测试失败")
        public String failForTest() {
            throw new IllegalStateException("SECRET-TOOL-DETAIL");
        }

        @Tool(description = "测试正常")
        public String ordinaryForTest() {
            return "ok";
        }
    }

    private static class TestAgent extends ToolCallAgent {
        AgentRunContext captured;
        boolean stopBeforeAct;

        TestAgent(ToolCallback[] tools, ChatModel model) {
            super(tools, Runnable::run);
            setChatClient(ChatClient.builder(model).build());
            setSystemPrompt("test");
            setMaxSteps(1);
        }

        @Override
        protected com.zhku.agent.agent.model.ToolCallDecision think(AgentRunContext context) {
            captured = context;
            var decision = super.think(context);
            if (stopBeforeAct)
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
