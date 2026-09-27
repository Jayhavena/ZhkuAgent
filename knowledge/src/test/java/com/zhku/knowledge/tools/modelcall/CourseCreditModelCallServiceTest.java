package com.zhku.knowledge.tools.modelcall;

import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CampusTool;
import com.zhku.knowledge.tools.CampusToolDescriptor;
import com.zhku.knowledge.tools.CampusToolResult;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CourseCreditModelCallServiceTest {

    private static final String ARGUMENTS = """
            {"courses":[
              {"courseId":"MATH01","category":"必修","credits":2},
              {"courseId":"ART02","category":"选修","credits":1.5}
            ]}
            """;

    @Test
    void executesOnlyApprovedNativeToolCallAndPreservesBoundary() {
        AtomicReference<ToolCallingChatOptions> options = new AtomicReference<>();
        ChatModel model = prompt -> {
            options.set((ToolCallingChatOptions) prompt.getOptions());
            return response(call("summarize_course_credits", ARGUMENTS));
        };

        var result = service(model).run("请汇总我给出的几门课程学分");

        assertThat(result.status()).isEqualTo(ModelCallStatus.SUCCESS);
        assertThat(result.toolName()).isEqualTo("summarize_course_credits");
        assertThat(result.resultJson()).contains("\"totalCredits\":3.5");
        assertThat(result.boundaryMessage()).contains("不能确认选课资格");
        assertThat(options.get().getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.get().getToolCallbacks()).hasSize(1);
        var definition = options.get().getToolCallbacks().get(0).getToolDefinition();
        assertThat(definition.name()).isEqualTo("summarize_course_credits");
        assertThat(definition.inputSchema()).contains("\"courseId\"", "\"category\"", "\"credits\"");
    }

    @Test
    void unknownToolDoesNotExecute() {
        var result = service(prompt -> response(call("delete_course", ARGUMENTS))).run("汇总");
        assertThat(result.status()).isEqualTo(ModelCallStatus.UNKNOWN_TOOL);
        assertThat(result.resultJson()).isNull();
    }

    @Test
    void invalidArgumentsDoNotProduceCalculation() {
        var result = service(prompt -> response(call("summarize_course_credits",
                "{\"courses\":[{\"courseId\":\"MATH01\",\"category\":\"必修\",\"credits\":-1}]}")))
                .run("汇总");
        assertThat(result.status()).isEqualTo(ModelCallStatus.INVALID_ARGUMENT);
        assertThat(result.resultJson()).isNull();
    }

    @Test
    void invalidArgumentsNeverReachRegisteredExecutor() {
        AtomicInteger executions = new AtomicInteger();
        CampusTool actual = new CourseCreditSummaryTool();
        CampusTool guarded = new CampusTool() {
            @Override
            public CampusToolDescriptor descriptor() {
                return actual.descriptor();
            }

            @Override
            public CampusToolResult execute(String argumentsJson) {
                executions.incrementAndGet();
                return actual.execute(argumentsJson);
            }
        };
        var service = new CourseCreditModelCallService(
                prompt -> response(call("summarize_course_credits",
                        "{\"courses\":[{\"courseId\":\"MATH01\",\"category\":\"必修\",\"credits\":-1}]}")),
                new CampusToolRegistry(List.of(guarded)), Duration.ofSeconds(2));

        assertThat(service.run("汇总").status()).isEqualTo(ModelCallStatus.INVALID_ARGUMENT);
        assertThat(executions.get()).isZero();
    }

    @Test
    void noToolCallDoesNotInventCredits() {
        var result = service(prompt -> response()).run("汇总");
        assertThat(result.status()).isEqualTo(ModelCallStatus.NO_TOOL_CALL);
        assertThat(result.resultJson()).isNull();
    }

    @Test
    void modelFailureDoesNotInventCredits() {
        var result = service(prompt -> { throw new IllegalStateException("private provider text"); }).run("汇总");
        assertThat(result.status()).isEqualTo(ModelCallStatus.MODEL_FAILURE);
        assertThat(result.resultJson()).isNull();
        assertThat(result.boundaryMessage()).doesNotContain("private provider text");
    }

    @Test
    void multipleToolCallsAreRejectedBeforeExecution() {
        var result = service(prompt -> response(
                call("summarize_course_credits", ARGUMENTS),
                call("summarize_course_credits", ARGUMENTS))).run("汇总");
        assertThat(result.status()).isEqualTo(ModelCallStatus.TOOL_CALL_LIMIT);
        assertThat(result.resultJson()).isNull();
    }

    @Test
    void toolCallsAcrossModelChoicesAlsoCountTowardLimit() {
        ChatModel model = prompt -> new ChatResponse(List.of(
                new Generation(new AssistantMessage("", java.util.Map.of(),
                        List.of(call("summarize_course_credits", ARGUMENTS)))),
                new Generation(new AssistantMessage("", java.util.Map.of(),
                        List.of(call("summarize_course_credits", ARGUMENTS))))));

        var result = service(model).run("汇总");
        assertThat(result.status()).isEqualTo(ModelCallStatus.TOOL_CALL_LIMIT);
        assertThat(result.resultJson()).isNull();
    }

    @Test
    void timeoutDoesNotInventCredits() {
        ChatModel model = prompt -> {
            try {
                Thread.sleep(Duration.ofSeconds(2));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return response(call("summarize_course_credits", ARGUMENTS));
        };
        var result = new CourseCreditModelCallService(model, registry(), Duration.ofMillis(50)).run("汇总");
        assertThat(result.status()).isEqualTo(ModelCallStatus.MODEL_TIMEOUT);
        assertThat(result.resultJson()).isNull();
    }

    private static CourseCreditModelCallService service(ChatModel model) {
        return new CourseCreditModelCallService(model, registry(), Duration.ofSeconds(2));
    }

    private static CampusToolRegistry registry() {
        return new CampusToolRegistry(List.of(new CourseCreditSummaryTool()));
    }

    private static AssistantMessage.ToolCall call(String name, String arguments) {
        return new AssistantMessage.ToolCall("call-1", "function", name, arguments);
    }

    private static ChatResponse response(AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(
                new AssistantMessage("", java.util.Map.of(), List.of(calls)))));
    }
}
