package com.zhku.knowledge.campusagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusToolCall;
import com.zhku.knowledge.tools.CampusToolDescriptor;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CampusToolResult;
import com.zhku.knowledge.tools.CampusToolStatus;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** 公选课学分纵切：模型只选择步骤，政策与算术结论均由服务端的受控结果组成。 */
public final class CampusCreditAgentService {
    private static final String LOOKUP = "lookup_public_elective_credit_limit";
    private static final String CALCULATE = "summarize_course_credits";
    private static final String HANDOFF = "本次无法核验资料或计算结果，不能推断个人选课资格或办理结果；请向教务部门人工核实。";
    private static final String ASK = "请提供每门虚构公共选修课的课程编号和学分，再为你计算合计。";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatModel model;
    private final CampusToolRegistry tools;
    private final Function<String, LocalPreviewConversationFacade.ConversationReply> policyGateway;
    private final int maxSteps;
    private final ToolCallingChatOptions options;

    public CampusCreditAgentService(ChatModel model, CampusToolRegistry tools,
            Function<String, LocalPreviewConversationFacade.ConversationReply> policyGateway,
            int maxSteps) {
        this.model = Objects.requireNonNull(model, "模型不能为空");
        this.tools = Objects.requireNonNull(tools, "工具注册表不能为空");
        this.policyGateway = Objects.requireNonNull(policyGateway, "政策入口不能为空");
        if (maxSteps < 2 || maxSteps > 5) {
            throw new IllegalArgumentException("校园 Agent 最大步骤数必须为 2—5");
        }
        this.maxSteps = maxSteps;
        // 学生入口不能使用默认工具注册表；即使未来新增校园工具也不能自动暴露。
        List<CampusToolDescriptor> descriptors = tools.descriptors();
        if (descriptors.size() != 1 || !CALCULATE.equals(descriptors.get(0).name())) {
            throw new IllegalArgumentException("校园入口只允许学分纯计算工具");
        }
        this.options = ToolCallingChatOptions.builder()
                .toolCallbacks(advertise(LOOKUP, "查询已发布且学生可见的公共选修课单学期学分上限", "{\"type\":\"object\",\"additionalProperties\":false}"),
                        advertise(descriptors.get(0).name(), descriptors.get(0).description(),
                                descriptors.get(0).inputSchemaJson()))
                .internalToolExecutionEnabled(false)
                .build();
    }

    public Result run(Request request) {
        Objects.requireNonNull(request, "请求不能为空");
        AgentRunContext context = AgentRunContext.create(request.question());
        return run(context, request);
    }

    /** 供有界执行器传入预先绑定的上下文；真正终止只在 finally 清理后发出。 */
    public Result run(AgentRunContext context, Request request) {
        Objects.requireNonNull(context, "运行上下文不能为空");
        Objects.requireNonNull(request, "请求不能为空");
        List<Step> steps = new ArrayList<>();
        Outcome outcome;
        try {
            if (!context.start()) {
                if (!context.isStopRequested()) {
                    throw new IllegalStateException("运行上下文无法启动");
                }
                outcome = new Outcome(Status.CANCELLED, HANDOFF, "CANCELLED", true, null);
            } else {
                outcome = decide(context, request, steps);
            }
        } catch (RuntimeException exception) {
            // 供应商异常原文可能含问题或凭据，回答和步骤均只保留固定分类。
            outcome = new Outcome(Status.HANDOFF, HANDOFF, "MODEL_FAILURE", true, null);
        }
        try {
            if (context.isStopRequested()) {
                outcome = new Outcome(Status.CANCELLED, HANDOFF, "CANCELLED", true, null);
                context.finish(AgentRunState.CANCELLED);
            } else {
                context.finish(outcome.failed() ? AgentRunState.FAILED : AgentRunState.SUCCEEDED);
            }
        } finally {
            context.cleanupOnce(() -> { });
            context.signalTermination();
        }
        return new Result(context.runId(), context.state(), context.stopReason(),
                context.termination().isDone(), outcome.status(), outcome.answer(),
                outcome.failureCategory(), List.copyOf(steps), outcome.source());
    }

    private Outcome decide(AgentRunContext context, Request request, List<Step> steps) {
        if (request.courses() != null && !request.courses().isEmpty() && !validCourses(request.courses())) {
            return new Outcome(Status.NEED_CLARIFICATION, ASK, "INVALID_INPUT", false, null);
        }
        String prompt = "你是校园公选课学分助手。只能选择获准工具，不得自行计算或推断个人资格。"
                + "先调用政策查询工具，再调用学分汇总工具。缺课程或学分时不调用工具并追问。"
                + "问题：" + request.question() + "\n用户提供的虚构课程：" + toJson(request.courses());
        boolean lookedUp = false;
        LocalPreviewConversationFacade.ConversationReply policy = null;
        String calculation = null;
        for (int i = 0; i < maxSteps; i++) {
            if (context.isStopRequested()) {
                return new Outcome(Status.CANCELLED, HANDOFF, "CANCELLED", true, null);
            }
            int number = context.advanceStep();
            ChatResponse response = model.call(new Prompt(prompt, options));
            List<AssistantMessage.ToolCall> calls = toolCalls(response);
            if (calls.isEmpty()) {
                return request.courses() == null || request.courses().isEmpty()
                        ? new Outcome(Status.NEED_CLARIFICATION, ASK, "NONE", false, null)
                        : new Outcome(Status.HANDOFF, HANDOFF, "NO_TOOL_CALL", true, null);
            }
            if (calls.size() != 1) {
                return new Outcome(Status.HANDOFF, HANDOFF, "TOOL_CALL_LIMIT", true, null);
            }
            AssistantMessage.ToolCall call = calls.get(0);
            if (LOOKUP.equals(call.name())) {
                if (lookedUp || !emptyObject(call.arguments())) {
                    return new Outcome(Status.HANDOFF, HANDOFF, "INVALID_TOOL_ARGUMENT", true, null);
                }
                try {
                    // 模型只能提出查询；五级链负责当期通知、高风险规则及资格过滤的最终裁决。
                    policy = policyGateway.apply(request.question());
                } catch (RuntimeException exception) {
                    return new Outcome(Status.HANDOFF, HANDOFF, "RETRIEVAL_FAILURE", true, null);
                }
                if (policy == null) {
                    return new Outcome(Status.HANDOFF, HANDOFF, "RETRIEVAL_FAILURE", true, null);
                }
                steps.add(new Step(number, "POLICY_ROUTE", policy.route().name(), "五级回答链裁决政策证据"));
                if (policy.route() == AnswerRoute.AWAITING_CLARIFICATION) {
                    return new Outcome(Status.NEED_CLARIFICATION, policy.message(), "NONE", false, null);
                }
                if (policy.route() != AnswerRoute.HIGH_RISK_RULE || policy.answer() == null
                        || policy.handoffRequired() || !completeSource(policy.answer())) {
                    return new Outcome(Status.HANDOFF, HANDOFF, "INSUFFICIENT_EVIDENCE", false, null);
                }
                lookedUp = true;
                steps.add(new Step(number, LOOKUP, "SUCCESS", policy.answer().sourceLocator()));
                prompt += "\n政策查询结果：" + policy.answer().conclusion() + "；来源："
                        + policy.answer().sourceTitle() + "，" + policy.answer().sourceLocator();
            } else if (CALCULATE.equals(call.name())) {
                if (calculation != null || request.courses() == null || request.courses().isEmpty()
                        || !CourseCreditSummaryTool.acceptsArguments(call.arguments())
                        || !sameCourses(call.arguments(), request.courses())) {
                    return new Outcome(Status.HANDOFF, HANDOFF, "INVALID_TOOL_ARGUMENT", true, null);
                }
                if (!lookedUp) {
                    return new Outcome(Status.HANDOFF, HANDOFF, "POLICY_REQUIRED", true, null);
                }
                CampusToolResult result = tools.execute(new CampusToolCall(call.name(), call.arguments()));
                if (result.status() != CampusToolStatus.SUCCESS) {
                    return new Outcome(Status.HANDOFF, HANDOFF, "INVALID_TOOL_ARGUMENT", true, null);
                }
                calculation = result.resultJson();
                steps.add(new Step(number, CALCULATE, "SUCCESS", calculation));
                prompt += "\n纯计算结果：" + calculation;
            } else {
                return new Outcome(Status.HANDOFF, HANDOFF, "UNKNOWN_TOOL", true, null);
            }
            if (lookedUp && calculation != null) {
                BigDecimal total = decimalTotal(calculation);
                var source = policy.answer();
                String answer = "按你提供的虚构公共选修课数据，合计 " + total.stripTrailingZeros().toPlainString()
                        + " 学分。已发布规则：" + source.conclusion() + " 来源：" + source.sourceTitle()
                        + "（" + source.sourceVersion() + "），" + source.sourceLocator()
                        + "。这只是输入求和，不能确认课程归属、个人选课资格或办理结果；请核对培养方案和当期通知，必要时向教务部门人工核实。";
                return new Outcome(Status.ANSWERED, answer, "NONE", false, source);
            }
        }
        return new Outcome(Status.HANDOFF, HANDOFF, "STEP_LIMIT", true, null);
    }

    private static List<AssistantMessage.ToolCall> toolCalls(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("模型无有效输出");
        }
        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
        for (Generation generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null) {
                throw new IllegalStateException("模型无有效输出");
            }
            if (generation.getOutput().getToolCalls() != null) {
                calls.addAll(generation.getOutput().getToolCalls());
            }
        }
        return calls;
    }

    private static boolean validCourses(List<CourseInput> courses) {
        if (courses.size() > 20) return false;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CourseInput course : courses) {
            if (course == null || course.courseId() == null || course.courseId().isBlank()
                    || !ids.add(course.courseId()) || !"公共选修课".equals(course.category())
                    || course.credits() == null || course.credits().signum() < 0) return false;
        }
        return true;
    }

    private static boolean completeSource(LocalPreviewConversationFacade.ConversationAnswer answer) {
        return answer.conclusion() != null && !answer.conclusion().isBlank()
                && answer.sourceTitle() != null && !answer.sourceTitle().isBlank()
                && answer.sourceVersion() != null && !answer.sourceVersion().isBlank()
                && answer.sourceLocator() != null && !answer.sourceLocator().isBlank();
    }

    private static boolean sameCourses(String arguments, List<CourseInput> expected) {
        try {
            JsonNode root = JSON.readTree(arguments);
            if (!root.isObject() || root.size() != 1 || !root.path("courses").isArray()
                    || root.path("courses").size() != expected.size()) return false;
            for (int i = 0; i < expected.size(); i++) {
                JsonNode item = root.path("courses").get(i);
                CourseInput course = expected.get(i);
                if (!item.isObject() || item.size() != 3
                        || !course.courseId().equals(item.path("courseId").asText())
                        || !course.category().equals(item.path("category").asText())
                        || !item.path("credits").isNumber()
                        || course.credits().compareTo(item.path("credits").decimalValue()) != 0) return false;
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean emptyObject(String arguments) {
        try {
            JsonNode node = JSON.readTree(arguments);
            return node.isObject() && node.isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    private static BigDecimal decimalTotal(String result) {
        try {
            return JSON.readTree(result).path("totalCredits").decimalValue();
        } catch (Exception exception) {
            throw new IllegalStateException("计算结果格式非法", exception);
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("课程输入无法解析", exception);
        }
    }

    private static ToolCallback advertise(String name, String description, String schema) {
        ToolDefinition definition = ToolDefinition.builder().name(name).description(description)
                .inputSchema(schema).build();
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return definition; }
            @Override public String call(String arguments) {
                // 禁用 Spring AI 自动执行：所有调用必须经过次数和参数守卫。
                throw new IllegalStateException("校园工具只能由服务端受控执行");
            }
        };
    }

    public enum Status { ANSWERED, NEED_CLARIFICATION, HANDOFF, CANCELLED }
    public record CourseInput(String courseId, String category, BigDecimal credits) { }
    public record Request(String question, List<CourseInput> courses) {
        public Request {
            if (question == null || question.isBlank() || question.length() > 500)
                throw new IllegalArgumentException("问题不能为空且不能超过 500 字");
            courses = courses == null ? List.of() : List.copyOf(courses);
        }
    }
    public record Step(int number, String name, String status, String detail) { }
    public record Result(UUID runId, AgentRunState state, AgentStopReason stopReason,
            boolean terminated, Status status, String answer, String failureCategory,
            List<Step> steps, LocalPreviewConversationFacade.ConversationAnswer source) { }
    private record Outcome(Status status, String answer, String failureCategory, boolean failed,
            LocalPreviewConversationFacade.ConversationAnswer source) { }
}
