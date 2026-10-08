package com.zhku.knowledge.campusagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.AgentRunObservationDispatcher;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.*;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
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
import java.util.function.Consumer;

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
    private final ToolCallingChatOptions budgetOptions;
    private final Consumer<AgentRunEvent> observer;
    private final AgentRunObservationDispatcher observationDispatcher;
    private enum ObservationMode { NONE, DEFAULT, EXPLICIT }
    private final ObservationMode observationMode;

    public CampusCreditAgentService(ChatModel model, CampusToolRegistry tools,
            Function<String, LocalPreviewConversationFacade.ConversationReply> policyGateway,
            int maxSteps) {
        this(model, tools, policyGateway, maxSteps, null, null, ObservationMode.NONE);
    }

    public CampusCreditAgentService(ChatModel model, CampusToolRegistry tools,
            Function<String, LocalPreviewConversationFacade.ConversationReply> policyGateway,
            int maxSteps, Consumer<AgentRunEvent> observer) {
        this(model, tools, policyGateway, maxSteps, Objects.requireNonNull(observer), null,
                ObservationMode.DEFAULT);
    }

    public CampusCreditAgentService(ChatModel model, CampusToolRegistry tools,
            Function<String, LocalPreviewConversationFacade.ConversationReply> policyGateway,
            int maxSteps, Consumer<AgentRunEvent> observer, AgentRunObservationDispatcher dispatcher) {
        this(model, tools, policyGateway, maxSteps, Objects.requireNonNull(observer),
                Objects.requireNonNull(dispatcher), ObservationMode.EXPLICIT);
    }

    private CampusCreditAgentService(ChatModel model, CampusToolRegistry tools,
            Function<String, LocalPreviewConversationFacade.ConversationReply> policyGateway,
            int maxSteps, Consumer<AgentRunEvent> observer, AgentRunObservationDispatcher dispatcher,
            ObservationMode mode) {
        this.observer = observer;
        this.observationDispatcher = dispatcher;
        this.observationMode = mode;
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
                .toolCallbacks(
                        advertise(LOOKUP, "查询已发布且学生可见的公共选修课单学期学分上限",
                                "{\"type\":\"object\",\"additionalProperties\":false}"),
                        advertise(descriptors.get(0).name(), descriptors.get(0).description(),
                                descriptors.get(0).inputSchemaJson()))
                .internalToolExecutionEnabled(false)
                .build();
        // 受控请求没有可覆盖的模型参数；每次Prompt和工厂默认都固定同一上限。
        this.budgetOptions=ToolCallingChatOptions.builder().toolCallbacks(options.getToolCallbacks())
                .internalToolExecutionEnabled(false).maxTokens(2048).build();
    }

    public Result run(Request request) {
        Objects.requireNonNull(request, "请求不能为空");
        AgentRunContext context = createContext(request);
        Result result = run(context, request);
        // 直接入口自己承担宿主发布；Manager 入口需先保存其完整结果，再调用相同发布边界。
        context.publishTermination();
        return result;
    }

    AgentRunContext createContext(Request request) {
        // 四参数入口不创建诊断资源；五参数仅在创建Context时懒借用默认owner。
        return switch (observationMode) {
            case NONE -> AgentRunContext.create(request.question());
            case DEFAULT -> AgentRunContext.create(request.question(), observer);
            case EXPLICIT -> AgentRunContext.create(request.question(), observer, observationDispatcher);
        };
    }
    AgentRunContext createContext(UUID runId, Request request) {
        return switch(observationMode) {
            case NONE -> AgentRunContext.create(runId,request.question());
            case DEFAULT -> AgentRunContext.create(runId,request.question(),observer);
            case EXPLICIT -> AgentRunContext.create(runId,request.question(),observer,observationDispatcher);
        };
    }

    /** 供有界执行器传入预先绑定的上下文；真正终止只在 finally 清理后发出。 */
    public Result run(AgentRunContext context, Request request, CampusExecutionBudget budget) {
        Objects.requireNonNull(context, "运行上下文不能为空");
        Objects.requireNonNull(request, "请求不能为空");
        List<Step> steps = new ArrayList<>();
        Outcome outcome;
        try {
            checkExecution(context,budget);
            if (!context.start()) {
                if (!context.isStopRequested()) {
                    throw new IllegalStateException("运行上下文无法启动");
                }
                outcome = new Outcome(Status.CANCELLED, HANDOFF, "CANCELLED", true, null);
            } else {
                outcome = decide(context, request, steps,budget);
            }
        } catch(CampusExecutionBudget.Failure budgetFailure) {
            outcome=new Outcome(Status.HANDOFF,HANDOFF,budgetFailure.code(),true,null);
        } catch (RuntimeException exception) {
            // 供应商异常原文可能含问题或凭据，回答和步骤均只保留固定分类。
            outcome = new Outcome(Status.HANDOFF, HANDOFF, "INTERNAL_FAILURE", true, null);
        }
        try {
            if (context.isStopRequested()) {
                outcome = new Outcome(Status.CANCELLED, HANDOFF, "CANCELLED", true, null);
                context.finish(AgentRunState.CANCELLED);
            } else {
                AgentRunState state = "STEP_LIMIT".equals(outcome.failureCategory()) ? AgentRunState.INCOMPLETE
                        : outcome.failed() ? AgentRunState.FAILED
                                : outcome.status() == Status.ANSWERED ? AgentRunState.SUCCEEDED
                                        : AgentRunState.INCOMPLETE;
                EndReason reason = state == AgentRunState.SUCCEEDED ? EndReason.COMPLETED
                        : state == AgentRunState.FAILED ? EndReason.ERROR
                                : "STEP_LIMIT".equals(outcome.failureCategory()) ? EndReason.STEP_LIMIT
                                        : EndReason.DOMAIN_DECISION;
                if (!context.finish(state, reason,
                        state == AgentRunState.FAILED ? runtimeError(outcome.failureCategory()) : null)
                        && context.isStopRequested()) {
                    outcome = new Outcome(Status.CANCELLED, HANDOFF, "CANCELLED", true, null);
                    context.finish(AgentRunState.CANCELLED);
                }
            }
        } finally {
            try {
                context.cleanupOnce(() -> {
                });
            } finally {
                context.signalTermination();
            }
        }
        var runtime = context.snapshot();
        return new Result(runtime.runId(), runtime.state(), runtime.stopReason(),
                context.termination().isDone(), outcome.status(), outcome.answer(),
                outcome.failureCategory(), List.copyOf(steps), outcome.source());
    }

    public Result run(AgentRunContext context, Request request) {return run(context,request,null);}

    private static void checkExecution(AgentRunContext context,CampusExecutionBudget budget) {
        if(budget==null)return;
        if(context.isStopRequested())throw new CampusExecutionBudget.Failure("CANCELLED");
        try{budget.checkDeadline();}
        catch(CampusExecutionBudget.Failure failure){
            if("RUN_TIMEOUT".equals(failure.code()))context.requestStop(AgentStopReason.RUN_TIMEOUT);
            throw failure;
        }
    }

    private Outcome decide(AgentRunContext context, Request request, List<Step> steps,CampusExecutionBudget budget) {
        if (request.courses() != null && !request.courses().isEmpty() && !validCourses(request.courses())) {
            return new Outcome(Status.NEED_CLARIFICATION, ASK, "INVALID_INPUT", false, null);
        }
        String prompt = "你是校园公选课学分助手。只能选择获准工具，不得自行计算或推断个人资格。"
                + "先调用政策查询工具，再调用学分汇总工具。缺课程或学分时不调用工具并追问。"
                + "问题：" + request.question() + "\n用户提供的虚构课程：" + toJson(request.courses());
        boolean lookedUp = false;
        LocalPreviewConversationFacade.ConversationReply policy = null;
        String calculation = null;
        // 当前合法轨迹只有“查询→计算”两次决策；步骤上限保留作防御，不能用空步骤伪造耗尽。
        for (int i = 0; i < maxSteps; i++) {
            checkExecution(context,budget);
            if (context.isStopRequested()) {
                return new Outcome(Status.CANCELLED, HANDOFF, "CANCELLED", true, null);
            }
            int number = context.advanceStep();
            List<AssistantMessage.ToolCall> calls;
            try {
                if(budget!=null)budget.reserveModelCall(prompt);
                calls = toolCalls(model.call(new Prompt(prompt, budget==null?options:budgetOptions)));
            } catch(CampusExecutionBudget.Failure budgetFailure) {
                if("RUN_TIMEOUT".equals(budgetFailure.code()))context.requestStop(AgentStopReason.RUN_TIMEOUT);
                throw budgetFailure;
            } catch (RuntimeException exception) {
                checkExecution(context,budget);
                return new Outcome(Status.HANDOFF, HANDOFF, "MODEL_FAILURE", true, null);
            }
            checkExecution(context,budget);
            if (calls.isEmpty()) {
                return request.courses() == null || request.courses().isEmpty()
                        ? new Outcome(Status.NEED_CLARIFICATION, ASK, "NONE", false, null)
                        : new Outcome(Status.HANDOFF, HANDOFF, "NO_TOOL_CALL", true, null);
            }
            if (calls.size() != 1) {
                for (var call : calls)
                    reject(context, proposed(context, call), "TOOL_CALL_LIMIT");
                return new Outcome(Status.HANDOFF, HANDOFF, "TOOL_CALL_LIMIT", true, null);
            }
            AssistantMessage.ToolCall call = calls.get(0);
            checkExecution(context,budget);
            AgentToolCall runtimeCall = proposed(context, call);
            if (LOOKUP.equals(call.name())) {
                if (lookedUp || !emptyObject(call.arguments())) {
                    reject(context, runtimeCall, "INVALID_TOOL_ARGUMENT");
                    return new Outcome(Status.HANDOFF, HANDOFF, "INVALID_TOOL_ARGUMENT", true, null);
                }
                try {
                    // 模型只能提出查询；五级链负责当期通知、高风险规则及资格过滤的最终裁决。
                    policy = policyGateway.apply(request.question());
                } catch (RuntimeException exception) {
                    checkExecution(context,budget);
                    failed(context, runtimeCall, "RETRIEVAL_FAILURE");
                    return new Outcome(Status.HANDOFF, HANDOFF, "RETRIEVAL_FAILURE", true, null);
                }
                checkExecution(context,budget);
                if (policy == null || policy.route() == null) {
                    failed(context, runtimeCall, "RETRIEVAL_FAILURE");
                    return new Outcome(Status.HANDOFF, HANDOFF, "RETRIEVAL_FAILURE", true, null);
                }
                // 查询有结果不等于证据合格；资格及转人工仍由下方原有领域守卫决定。
                var policyPayload = new java.util.LinkedHashMap<String, Object>();
                policyPayload.put("route", policy.route().name());
                policyPayload.put("handoffRequired", policy.handoffRequired());
                policyPayload.put("source", policy.answer());
                policyPayload.put("boundaryMessage", "政策查询不能确认个人选课资格或办理结果");
                context.recordToolResult(runtimeCall, new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED,
                        toJson(policyPayload), AgentToolCall.PayloadFormat.JSON, null));
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
                // 每轮使用新Prompt；只追加正文会让模型重复首步，必须反馈已经执行的服务端事实。
                // 这是下一步建议，不是执行许可；重复查询、课程匹配与工具顺序守卫继续裁决模型调用。
                prompt += "\n服务端已执行工具 lookup_public_elective_credit_limit({})，政策证据已核验合格。"
                        + "不要再次查询；下一步选择 summarize_course_credits，参数仅使用原始课程，"
                        + "保留 courseId、category、credits 的值，不增加其他字段。";
            } else if (CALCULATE.equals(call.name())) {
                if (calculation != null || request.courses() == null || request.courses().isEmpty()
                        || !CourseCreditSummaryTool.acceptsArguments(call.arguments())
                        || !sameCourses(call.arguments(), request.courses())) {
                    reject(context, runtimeCall, "INVALID_TOOL_ARGUMENT");
                    return new Outcome(Status.HANDOFF, HANDOFF, "INVALID_TOOL_ARGUMENT", true, null);
                }
                if (!lookedUp) {
                    reject(context, runtimeCall, "POLICY_REQUIRED");
                    return new Outcome(Status.HANDOFF, HANDOFF, "POLICY_REQUIRED", true, null);
                }
                try {
                    checkExecution(context,budget);
                    CampusToolResult result = tools.execute(new CampusToolCall(call.name(), call.arguments()));
                    checkExecution(context,budget);
                    if (result.status() != CampusToolStatus.SUCCESS) {
                        String code = result.status() == CampusToolStatus.INVALID_ARGUMENT ? "INVALID_TOOL_ARGUMENT"
                                : "TOOL_FAILURE";
                        context.recordToolResult(runtimeCall, new AgentToolCall.Result(
                                result.status() == CampusToolStatus.INVALID_ARGUMENT ? AgentToolCall.Status.REJECTED
                                        : AgentToolCall.Status.FAILED,
                                toJson(result), AgentToolCall.PayloadFormat.JSON, runtimeError(code)));
                        return new Outcome(Status.HANDOFF, HANDOFF,
                                result.status() == CampusToolStatus.INVALID_ARGUMENT ? "INVALID_TOOL_ARGUMENT"
                                        : "TOOL_FAILURE",
                                true, null);
                    }
                    calculation = result.resultJson();
                    decimalTotal(calculation);
                    // 原 CampusToolResult 整体作为载荷，不能在统一过程中丢掉 boundaryMessage。
                    context.recordToolResult(runtimeCall, new AgentToolCall.Result(AgentToolCall.Status.SUCCEEDED,
                            toJson(result), AgentToolCall.PayloadFormat.JSON, null));
                } catch(CampusExecutionBudget.Failure budgetFailure) {throw budgetFailure;}
                catch (RuntimeException exception) {
                    checkExecution(context,budget);
                    failed(context, runtimeCall, "TOOL_FAILURE");
                    // 计算工具和结果解析故障与模型故障分开，异常原文不进入回答。
                    return new Outcome(Status.HANDOFF, HANDOFF, "TOOL_FAILURE", true, null);
                }
                steps.add(new Step(number, CALCULATE, "SUCCESS", calculation));
                prompt += "\n纯计算结果：" + calculation;
            } else {
                reject(context, runtimeCall, "UNKNOWN_TOOL");
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

    private static AgentToolCall proposed(AgentRunContext context, AssistantMessage.ToolCall call) {
        return context.proposeToolCall(call.id(), call.name(), call.arguments());
    }

    private static void reject(AgentRunContext context, AgentToolCall call, String code) {
        context.recordToolResult(call,
                new AgentToolCall.Result(AgentToolCall.Status.REJECTED, null, null, runtimeError(code)));
    }

    private static void failed(AgentRunContext context, AgentToolCall call, String code) {
        context.recordToolResult(call,
                new AgentToolCall.Result(AgentToolCall.Status.FAILED, null, null, runtimeError(code)));
    }

    private static AgentRuntimeError runtimeError(String code) {
        // 领域错误码在 knowledge 解释；共享层不认识政策、证据资格或工具白名单。
        AgentRuntimeError.Category category = switch (code) {
            case "MODEL_FAILURE", "NO_TOOL_CALL", "MODEL_INPUT_LIMIT", "MODEL_CALL_BUDGET_EXCEEDED" -> AgentRuntimeError.Category.MODEL_FAILURE;
            case "RETRIEVAL_FAILURE" -> AgentRuntimeError.Category.DOMAIN_FAILURE;
            case "TOOL_FAILURE", "INVALID_TOOL_ARGUMENT", "UNKNOWN_TOOL", "TOOL_CALL_LIMIT", "POLICY_REQUIRED" ->
                AgentRuntimeError.Category.TOOL_FAILURE;
            default -> AgentRuntimeError.Category.INTERNAL_FAILURE;
        };
        return AgentRuntimeError.of(category, code);
    }

    private static boolean validCourses(List<CourseInput> courses) {
        if (courses.size() > 20)
            return false;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (CourseInput course : courses) {
            if (course == null || course.courseId() == null || course.courseId().isBlank()
                    || !ids.add(course.courseId()) || !"公共选修课".equals(course.category())
                    || course.credits() == null || course.credits().signum() < 0)
                return false;
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
        // 模型可以重排，但每个请求项只能消费一次；去重或只比较总数会漏过重复/篡改。
        try {
            JsonNode root = JSON.readTree(arguments);
            if (!root.isObject() || root.size() != 1 || !root.path("courses").isArray()
                    || root.path("courses").size() != expected.size())
                return false;
            boolean[] consumed = new boolean[expected.size()];
            for (JsonNode item : root.path("courses")) {
                if (!item.isObject() || item.size() != 3
                        || !item.path("courseId").isTextual() || !item.path("category").isTextual()
                        || !item.path("credits").isNumber())
                    return false;
                boolean matched = false;
                for (int i = 0; i < expected.size(); i++) {
                    CourseInput course = expected.get(i);
                    if (!consumed[i] && course.courseId().equals(item.path("courseId").asText())
                            && course.category().equals(item.path("category").asText())
                            && course.credits().compareTo(item.path("credits").decimalValue()) == 0) {
                        consumed[i] = true;
                        matched = true;
                        break;
                    }
                }
                if (!matched)
                    return false;
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
            JsonNode total = JSON.readTree(result).path("totalCredits");
            if (!total.isNumber() || total.decimalValue().signum() < 0)
                throw new IllegalStateException("计算结果缺少有效学分合计");
            return total.decimalValue();
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
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String arguments) {
                // 禁用 Spring AI 自动执行：所有调用必须经过次数和参数守卫。
                throw new IllegalStateException("校园工具只能由服务端受控执行");
            }
        };
    }

    public enum Status {
        ANSWERED, NEED_CLARIFICATION, HANDOFF, CANCELLED
    }

    public record CourseInput(String courseId, String category, BigDecimal credits) {
    }

    public record Request(String question, List<CourseInput> courses) {
        public Request {
            if (question == null || question.isBlank() || question.length() > 500)
                throw new IllegalArgumentException("问题不能为空且不能超过 500 字");
            courses = courses == null ? List.of() : List.copyOf(courses);
        }
    }

    public record Step(int number, String name, String status, String detail) {
    }

    public record Result(UUID runId, AgentRunState state, AgentStopReason stopReason,
            boolean terminated, Status status, String answer, String failureCategory,
            List<Step> steps, LocalPreviewConversationFacade.ConversationAnswer source) {
    }

    private record Outcome(Status status, String answer, String failureCategory, boolean failed,
            LocalPreviewConversationFacade.ConversationAnswer source) {
    }
}
