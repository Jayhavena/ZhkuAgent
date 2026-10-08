package com.zhku.knowledge.campusagent;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.tools.CampusTool;
import com.zhku.knowledge.tools.CampusToolDescriptor;
import com.zhku.knowledge.tools.CampusToolResult;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampusCreditAgentServiceTest {
    private static final String REVERSED_COURSES = """
            {"courses":[{"courseId":"HIS02","category":"公共选修课","credits":2},
                        {"courseId":"ART01","category":"公共选修课","credits":1.5}]}
            """;

    @Test
    void reorderedCoursesStillUseExactlyTwoModelDecisions() throws Exception {
        var fixture = new RunFixture(request(), REVERSED_COURSES, 3);
        var result = fixture.run();
        // 合成夹具摘要在成功断言前输出，RED 的实际计数不能由源码或事件数推测。
        System.out.printf("B1-T01 status=%s category=%s state=%s M/P/C=%d/%d/%d steps=%s numbers=%s "
                        + "currentStep=%d endReason=%s sourcePresent=%s%n",
                result.status(), result.failureCategory(), result.state(), fixture.modelCalls.get(),
                fixture.policyCalls.get(), fixture.calculator.calls.get(),
                result.steps().stream().map(CampusCreditAgentService.Step::name).toList(),
                result.steps().stream().map(CampusCreditAgentService.Step::number).toList(),
                fixture.context.snapshot().currentStep(), fixture.context.snapshot().endReason(),
                result.source() != null);
        fixture.assertSuccess(result, "3.5");
        // 校园 CampusToolCall 去除首尾空白；完整 JSON 与数组顺序必须仍是模型提出的 BA。
        assertThat(fixture.calculator.arguments).isEqualTo(REVERSED_COURSES.strip());
        assertThat(new ObjectMapper().readTree(fixture.calculator.arguments))
                .isEqualTo(new ObjectMapper().readTree(REVERSED_COURSES));
        assertThat(fixture.request.courses()).containsExactly(
                new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")),
                new CampusCreditAgentService.CourseInput("HIS02", "公共选修课", new BigDecimal("2")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORIGINAL", "REVERSED", "ZERO"})
    void numericallyEquivalentCreditsRemainAccepted(String mode) throws Exception {
        var input = request();
        String arguments = mode.equals("REVERSED") ? REVERSED_COURSES : COURSES;
        String total = "3.5";
        if (mode.equals("ZERO")) {
            input = new CampusCreditAgentService.Request(input.question(), List.of(
                    new CampusCreditAgentService.CourseInput("ART01", "公共选修课", BigDecimal.ZERO),
                    input.courses().get(1)));
            arguments = COURSES.replace("1.5", "0.0");
            total = "2";
        } else {
            arguments = arguments.replace("1.5", "1.50").replace("\"credits\":2", "\"credits\":2.00");
        }
        var fixture = new RunFixture(input, arguments, 3);
        fixture.assertSuccess(fixture.run(), total);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tamperedArguments")
    void mismatchedOrMalformedCoursesNeverEnterCalculator(String label, String arguments) {
        var fixture = new RunFixture(request(), arguments, 3);
        var result = fixture.run();
        assertThat(result.status()).as(label).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.failureCategory()).isEqualTo("INVALID_TOOL_ARGUMENT");
        assertThat(result.state().name()).isEqualTo("FAILED");
        assertThat(fixture.modelCalls.get()).isEqualTo(2);
        assertThat(fixture.policyCalls.get()).isEqualTo(1);
        assertThat(fixture.calculator.calls.get()).isZero();
        assertThat(fixture.calculator.arguments).isNull();
        assertThat(fixture.calculator.result).isNull();
        assertThat(result.source()).isNull();
        assertThat(result.answer()).doesNotContain("合计 3.5");
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .containsExactly("POLICY_ROUTE", "lookup_public_elective_credit_limit");
        assertThat(fixture.request.courses()).isEqualTo(request().courses());
    }

    private static Stream<Arguments> tamperedArguments() {
        String a = "{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":1.5}";
        String b = "{\"courseId\":\"HIS02\",\"category\":\"公共选修课\",\"credits\":2}";
        return Stream.of(
                Arguments.of("T03 added", courseArguments(a, b, b.replace("HIS02", "NEW03"))),
                Arguments.of("T03 removed", courseArguments(a)),
                Arguments.of("T03 empty", courseArguments()),
                Arguments.of("T03 reuse AA", courseArguments(a, a)),
                Arguments.of("T03 reuse AAB", courseArguments(a, a, b)),
                Arguments.of("T04 changed ID", courseArguments(a.replace("ART01", "ART99"), b)),
                Arguments.of("T04 changed category", courseArguments(a.replace("公共选修课", "必修"), b)),
                Arguments.of("T04 changed credits", courseArguments(a.replace("1.5", "1.6"), b)),
                Arguments.of("T04 padded ID", courseArguments(a.replace("ART01", " ART01 "), b)),
                Arguments.of("T04 lowercase ID", courseArguments(a.replace("ART01", "art01"), b)),
                Arguments.of("T05 extra root", "{\"extra\":true,\"courses\":[" + a + "," + b + "]}"),
                Arguments.of("T05 extra item", courseArguments(a.replace("}", ",\"extra\":true}"), b)),
                Arguments.of("T05 root array", "[]"),
                Arguments.of("T05 root null", "null"),
                Arguments.of("T05 courses object", "{\"courses\":{}}"),
                Arguments.of("T05 courses null", "{\"courses\":null}"),
                Arguments.of("T05 item nonobject", courseArguments("[]", b)),
                Arguments.of("T05 missing ID", courseArguments(a.replace("\"courseId\":\"ART01\",", ""), b)),
                Arguments.of("T05 missing category", courseArguments(a.replace("\"category\":\"公共选修课\",", ""), b)),
                Arguments.of("T05 missing credits", courseArguments(a.replace(",\"credits\":1.5", ""), b)),
                Arguments.of("T06 number ID", courseArguments(a.replace("\"ART01\"", "123"), b)),
                Arguments.of("T06 null ID", courseArguments(a.replace("\"ART01\"", "null"), b)),
                Arguments.of("T06 number category", courseArguments(a.replace("\"公共选修课\"", "123"), b)),
                Arguments.of("T06 null category", courseArguments(a.replace("\"公共选修课\"", "null"), b)),
                Arguments.of("T06 string credits", courseArguments(a.replace("1.5", "\"1.5\""), b)),
                Arguments.of("T06 boolean credits", courseArguments(a.replace("1.5", "true"), b)),
                Arguments.of("T06 null credits", courseArguments(a.replace("1.5", "null"), b)),
                Arguments.of("T06 negative credits", courseArguments(a.replace("1.5", "-1"), b)),
                Arguments.of("T06 blank JSON", " "),
                Arguments.of("T06 malformed JSON", "{"),
                Arguments.of("T06 duplicate root key", "{\"courses\":[],\"courses\":[" + a + "," + b + "]}"),
                Arguments.of("T06 duplicate item key", courseArguments(
                        a.replace("\"credits\":1.5", "\"credits\":1.5,\"credits\":1.5"), b)));
    }

    private static String courseArguments(String... courses) {
        return "{\"courses\":[" + String.join(",", courses) + "]}";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequestCourses")
    void invalidCampusRequestNeverCallsModelPolicyOrCalculator(String label,
            List<CampusCreditAgentService.CourseInput> courses) {
        var input = new CampusCreditAgentService.Request(request().question(), courses);
        var fixture = new RunFixture(input, COURSES, 3);
        var result = fixture.run();
        assertThat(result.status()).as(label).isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
        assertThat(result.failureCategory()).isEqualTo("INVALID_INPUT");
        assertThat(result.state().name()).isEqualTo("INCOMPLETE");
        assertThat(fixture.modelCalls.get()).isZero();
        assertThat(fixture.policyCalls.get()).isZero();
        assertThat(fixture.calculator.calls.get()).isZero();
        assertThat(result.steps()).isEmpty();
        assertThat(result.source()).isNull();
    }

    private static Stream<Arguments> invalidRequestCourses() {
        var a = request().courses().get(0);
        var many = new ArrayList<CampusCreditAgentService.CourseInput>();
        for (int i = 0; i < 21; i++) {
            many.add(new CampusCreditAgentService.CourseInput("C" + i, "公共选修课", BigDecimal.ONE));
        }
        return Stream.of(
                Arguments.of("T07 duplicate same credits", List.of(a, a)),
                Arguments.of("T07 duplicate different credits", List.of(a,
                        new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("2")))),
                Arguments.of("T07 21 courses", many),
                Arguments.of("T07 blank ID", List.of(new CampusCreditAgentService.CourseInput(
                        " ", "公共选修课", BigDecimal.ONE))),
                Arguments.of("T07 wrong category", List.of(new CampusCreditAgentService.CourseInput(
                        "ART01", "必修", BigDecimal.ONE))),
                Arguments.of("T07 negative", List.of(new CampusCreditAgentService.CourseInput(
                        "ART01", "公共选修课", new BigDecimal("-1")))),
                Arguments.of("T07 missing credits", List.of(new CampusCreditAgentService.CourseInput(
                        "ART01", "公共选修课", null))));
    }

    @Test
    void nullCourseElementIsRejectedDuringRequestConstruction() {
        // 构造拒绝不能冒充 Service 已执行后得到 INVALID_INPUT。
        assertThatThrownBy(() -> new CampusCreditAgentService.Request(request().question(),
                Arrays.asList((CampusCreditAgentService.CourseInput) null))).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    void freshContextCompletesInTwoDecisionsBeforeHostPublication(int maxSteps) throws Exception {
        for (String arguments : List.of(COURSES, REVERSED_COURSES)) {
            var fixture = new RunFixture(request(), arguments, maxSteps);
            assertThat(fixture.context.snapshot().state().name()).isEqualTo("CREATED");
            assertThat(fixture.context.snapshot().currentStep()).isZero();
            var result = fixture.run();
            fixture.assertSuccess(result, "3.5");
            assertThat(fixture.context.termination()).isCompleted();
            assertThat(result.terminated()).isTrue();
            assertThat(fixture.context.snapshot().terminated()).isFalse();
            // 模拟宿主先保存完整结果，再发布唯一 final；不把清理信号当发布。
            var savedResult = result;
            assertThat(savedResult.source()).isSameAs(fixture.policyReply.answer());
            assertThat(fixture.context.publishTermination()).isTrue();
            assertThat(fixture.context.snapshot().terminated()).isTrue();
            assertThat(fixture.context.snapshot().currentStep()).isEqualTo(2);
            assertThat(fixture.context.snapshot().state()).isEqualTo(result.state());
            assertThat(fixture.context.snapshot().endReason()).isEqualTo(EndReason.COMPLETED);
            assertThat(fixture.context.publishTermination()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 6})
    void invalidStepBudgetIsRejectedInsteadOfManufacturingExhaustion(int maxSteps) {
        assertThatThrownBy(() -> new RunFixture(request(), COURSES, maxSteps))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("secondDecisionFailures")
    void secondDecisionGuardFailuresAreNotStepExhaustion(String category, ChatResponse second) {
        var fixture = new RunFixture(request(), 2,
                List.of(response(call("lookup_public_elective_credit_limit", "{}")), second), policy());
        var result = fixture.run();
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.failureCategory()).isEqualTo(category);
        assertThat(result.state().name()).isEqualTo("FAILED");
        assertThat(fixture.context.snapshot().endReason()).isEqualTo(EndReason.ERROR);
        assertThat(fixture.context.snapshot().currentStep()).isEqualTo(2);
        assertThat(fixture.modelCalls.get()).isEqualTo(2);
        assertThat(fixture.policyCalls.get()).isEqualTo(1);
        assertThat(fixture.calculator.calls.get()).isZero();
        assertThat(result.source()).isNull();
    }

    private static Stream<Arguments> secondDecisionFailures() {
        return Stream.of(
                Arguments.of("INVALID_TOOL_ARGUMENT", response(call("lookup_public_elective_credit_limit", "{}"))),
                Arguments.of("NO_TOOL_CALL", response()),
                Arguments.of("UNKNOWN_TOOL", response(call("terminal", "{}"))),
                Arguments.of("TOOL_CALL_LIMIT", response(call("lookup_public_elective_credit_limit", "{}"),
                        call("summarize_course_credits", COURSES))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstDecisionFailures")
    void firstDecisionKeepsArgumentChecksAheadOfPolicyRequirement(String category, ChatResponse first) {
        var fixture = new RunFixture(request(), 2, List.of(first), policy());
        var result = fixture.run();
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.failureCategory()).isEqualTo(category);
        assertThat(result.state().name()).isEqualTo("FAILED");
        assertThat(fixture.modelCalls.get()).isEqualTo(1);
        assertThat(fixture.policyCalls.get()).isZero();
        assertThat(fixture.calculator.calls.get()).isZero();
        assertThat(result.source()).isNull();
    }

    private static Stream<Arguments> firstDecisionFailures() {
        return Stream.of(
                Arguments.of("POLICY_REQUIRED", response(call("summarize_course_credits", COURSES))),
                Arguments.of("INVALID_TOOL_ARGUMENT", response(call("summarize_course_credits", "{}"))),
                Arguments.of("INVALID_TOOL_ARGUMENT", response(call("lookup_public_elective_credit_limit",
                        "{\"extra\":true}"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusablePolicies")
    void policyDomainDecisionPreventsCalculation(String label,
            LocalPreviewConversationFacade.ConversationReply policyReply, String category, String state) {
        var fixture = new RunFixture(request(), 2,
                List.of(response(call("lookup_public_elective_credit_limit", "{}"))), policyReply);
        var result = fixture.run();
        assertThat(result.failureCategory()).as(label).isEqualTo(category);
        assertThat(result.state().name()).isEqualTo(state);
        assertThat(fixture.modelCalls.get()).isEqualTo(1);
        assertThat(fixture.policyCalls.get()).isEqualTo(1);
        assertThat(fixture.calculator.calls.get()).isZero();
        assertThat(result.status()).isEqualTo(label.equals("clarification")
                ? CampusCreditAgentService.Status.NEED_CLARIFICATION : CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.source()).isNull();
        assertThat(fixture.context.snapshot().endReason()).isEqualTo(state.equals("FAILED")
                ? EndReason.ERROR : EndReason.DOMAIN_DECISION);
    }

    private static Stream<Arguments> unusablePolicies() {
        var source = policy().answer();
        var cases = new ArrayList<Arguments>();
        cases.add(Arguments.of("clarification", policyReply(AnswerRoute.AWAITING_CLARIFICATION, source, false),
                "NONE", "INCOMPLETE"));
        cases.add(Arguments.of("wrong route", policyReply(AnswerRoute.INSUFFICIENT_EVIDENCE, source, false),
                "INSUFFICIENT_EVIDENCE", "INCOMPLETE"));
        cases.add(Arguments.of("missing answer", policyReply(AnswerRoute.HIGH_RISK_RULE, null, false),
                "INSUFFICIENT_EVIDENCE", "INCOMPLETE"));
        cases.add(Arguments.of("handoff required", policyReply(AnswerRoute.HIGH_RISK_RULE, source, true),
                "INSUFFICIENT_EVIDENCE", "INCOMPLETE"));
        cases.add(Arguments.of("null reply", null, "RETRIEVAL_FAILURE", "FAILED"));
        cases.add(Arguments.of("null route", policyReply(null, source, false), "RETRIEVAL_FAILURE", "FAILED"));
        for (String missing : List.of("conclusion", "title", "version", "locator")) {
            var incomplete = new LocalPreviewConversationFacade.ConversationAnswer(
                    missing.equals("conclusion") ? " " : source.conclusion(), source.basis(), source.sourceId(),
                    missing.equals("title") ? " " : source.sourceTitle(),
                    missing.equals("version") ? " " : source.sourceVersion(),
                    missing.equals("locator") ? " " : source.sourceLocator(), source.applicableScope());
            cases.add(Arguments.of("missing " + missing, policyReply(AnswerRoute.HIGH_RISK_RULE, incomplete, false),
                    "INSUFFICIENT_EVIDENCE", "INCOMPLETE"));
        }
        return cases.stream();
    }

    private static LocalPreviewConversationFacade.ConversationReply policyReply(AnswerRoute route,
            LocalPreviewConversationFacade.ConversationAnswer source, boolean handoff) {
        return new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.ANSWER,
                "合成政策裁决", source, route, handoff, List.of(), List.of());
    }

    @Test
    void malformedModelOutputFailsBeforeAnyPolicyOrCalculation() {
        var nullGeneration = mock(ChatResponse.class);
        when(nullGeneration.getResults()).thenReturn(Arrays.asList((Generation) null));
        var emptyOutput = mock(Generation.class);
        // 供应商边界替身只模拟缺失输出；Service、Runtime 和计算器仍为真实实现。
        for (ChatResponse output : Arrays.asList(null, new ChatResponse(List.of()), nullGeneration,
                new ChatResponse(List.of(emptyOutput)))) {
            var fixture = new RunFixture(request(), 2, Arrays.asList(output), policy());
            var result = fixture.run();
            assertThat(result.failureCategory()).isEqualTo("MODEL_FAILURE");
            assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
            assertThat(result.state().name()).isEqualTo("FAILED");
            assertThat(fixture.modelCalls.get()).isEqualTo(1);
            assertThat(fixture.policyCalls.get()).isZero();
            assertThat(fixture.calculator.calls.get()).isZero();
            assertThat(result.source()).isNull();
            assertThat(fixture.context.snapshot().endReason()).isEqualTo(EndReason.ERROR);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_ARGUMENT", "UNKNOWN_TOOL", "NULL_RESULT", "NEGATIVE_TOTAL",
            "STRING_TOTAL", "BOOLEAN_TOTAL", "NULL_TOTAL"})
    void calculatorResultFailuresKeepTheirDomainCategory(String mode) {
        var calculatorCalls = new AtomicInteger();
        var modelCalls = new AtomicInteger();
        var policyCalls = new AtomicInteger();
        CampusTool calculator = new CampusTool() {
            @Override
            public CampusToolDescriptor descriptor() {
                return new CourseCreditSummaryTool().descriptor();
            }

            @Override
            public CampusToolResult execute(String arguments) {
                calculatorCalls.incrementAndGet();
                return switch (mode) {
                    case "INVALID_ARGUMENT" -> CampusToolResult.failure(descriptor().name(),
                            com.zhku.knowledge.tools.CampusToolStatus.INVALID_ARGUMENT, "{}", "合成错误");
                    case "UNKNOWN_TOOL" -> CampusToolResult.failure(descriptor().name(),
                            com.zhku.knowledge.tools.CampusToolStatus.UNKNOWN_TOOL, "{}", "合成错误");
                    case "NULL_RESULT" -> null;
                    default -> CampusToolResult.success(descriptor().name(), switch (mode) {
                        case "NEGATIVE_TOTAL" -> "{\"totalCredits\":-1}";
                        case "STRING_TOTAL" -> "{\"totalCredits\":\"3.5\"}";
                        case "BOOLEAN_TOTAL" -> "{\"totalCredits\":true}";
                        case "NULL_TOTAL" -> "{\"totalCredits\":null}";
                        default -> throw new AssertionError("未定义测试模式");
                    }, "合成错误");
                };
            }
        };
        var service = new CampusCreditAgentService(prompt -> switch (modelCalls.incrementAndGet()) {
            case 1 -> response(call("lookup_public_elective_credit_limit", "{}"));
            case 2 -> response(call("summarize_course_credits", COURSES));
            default -> throw new AssertionError("失败轨迹不能进入第三次决策");
        }, new CampusToolRegistry(List.of(calculator)), question -> {
            policyCalls.incrementAndGet();
            return policy();
        }, 2);
        var context = service.createContext(request());
        var result = service.run(context, request());
        assertThat(result.failureCategory()).isEqualTo(mode.equals("INVALID_ARGUMENT")
                ? "INVALID_TOOL_ARGUMENT" : "TOOL_FAILURE");
        assertThat(result.state().name()).isEqualTo("FAILED");
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(modelCalls.get()).isEqualTo(2);
        assertThat(policyCalls.get()).isEqualTo(1);
        assertThat(calculatorCalls.get()).isEqualTo(1);
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .containsExactly("POLICY_ROUTE", "lookup_public_elective_credit_limit");
        assertThat(result.source()).isNull();
        assertThat(result.answer()).doesNotContain("合计 3.5");
        assertThat(context.snapshot().endReason()).isEqualTo(EndReason.ERROR);
    }

    private static final class CountingCalculator implements CampusTool {
        private final CourseCreditSummaryTool delegate = new CourseCreditSummaryTool();
        private final AtomicInteger calls = new AtomicInteger();
        private String arguments;
        private CampusToolResult result;

        @Override
        public CampusToolDescriptor descriptor() {
            return delegate.descriptor();
        }

        @Override
        public CampusToolResult execute(String arguments) {
            calls.incrementAndGet();
            this.arguments = arguments;
            this.result = delegate.execute(arguments);
            return result;
        }
    }

    private static final class RunFixture {
        private final CampusCreditAgentService.Request request;
        private final LocalPreviewConversationFacade.ConversationReply policyReply;
        private final AtomicInteger modelCalls = new AtomicInteger();
        private final AtomicInteger policyCalls = new AtomicInteger();
        private final CountingCalculator calculator = new CountingCalculator();
        private final CampusCreditAgentService service;
        private final AgentRunContext context;

        private RunFixture(CampusCreditAgentService.Request request, String arguments, int maxSteps) {
            this(request, maxSteps, List.of(response(call("lookup_public_elective_credit_limit", "{}")),
                    response(call("summarize_course_credits", arguments))), policy());
        }

        private RunFixture(CampusCreditAgentService.Request request, int maxSteps,
                List<ChatResponse> decisions, LocalPreviewConversationFacade.ConversationReply policyReply) {
            this.request = request;
            this.policyReply = policyReply;
            this.service = new CampusCreditAgentService(prompt -> {
                int index = modelCalls.getAndIncrement();
                if (index >= decisions.size()) {
                    throw new AssertionError("合法校园轨迹不能发起多余模型决策");
                }
                return decisions.get(index);
            }, new CampusToolRegistry(List.of(calculator)), question -> {
                policyCalls.incrementAndGet();
                return policyReply;
            }, maxSteps);
            this.context = service.createContext(request);
        }

        private CampusCreditAgentService.Result run() {
            return service.run(context, request);
        }

        private void assertSuccess(CampusCreditAgentService.Result result, String total) throws Exception {
            assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.ANSWERED);
            assertThat(result.failureCategory()).isEqualTo("NONE");
            assertThat(result.state().name()).isEqualTo("SUCCEEDED");
            assertThat(modelCalls.get()).isEqualTo(2);
            assertThat(policyCalls.get()).isEqualTo(1);
            assertThat(calculator.calls.get()).isEqualTo(1);
            assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name).containsExactly(
                    "POLICY_ROUTE", "lookup_public_elective_credit_limit", "summarize_course_credits");
            assertThat(result.steps()).extracting(CampusCreditAgentService.Step::number).containsExactly(1, 1, 2);
            assertThat(new ObjectMapper().readTree(calculator.result.resultJson()).path("totalCredits").decimalValue())
                    .isEqualByComparingTo(total);
            assertThat(result.source()).isSameAs(policyReply.answer());
            assertThat(result.source().sourceId()).isEqualTo(policyReply.answer().sourceId());
            assertThat(result.source().sourceVersion()).isEqualTo(policyReply.answer().sourceVersion());
            assertThat(result.source().sourceLocator()).isEqualTo(policyReply.answer().sourceLocator());
            assertThat(result.answer()).contains(total, policyReply.answer().conclusion(),
                    policyReply.answer().sourceTitle(), policyReply.answer().sourceVersion(),
                    policyReply.answer().sourceLocator(), "不能确认");
            assertThat(context.snapshot().currentStep()).isEqualTo(2);
            assertThat(context.snapshot().endReason()).isEqualTo(EndReason.COMPLETED);
            assertThat(context.snapshot().error()).isNull();
        }
    }

    @Test
    void calculatorExceptionIsToolFailureRatherThanModelFailure() {
        for (String mode : List.of("THROW", "DATA_UNAVAILABLE", "INVALID_JSON", "MISSING_TOTAL")) {
            var badTool = new com.zhku.knowledge.tools.CampusTool() {
                public com.zhku.knowledge.tools.CampusToolDescriptor descriptor() {
                    return new CourseCreditSummaryTool().descriptor();
                }

                public com.zhku.knowledge.tools.CampusToolResult execute(String args) {
                    if (mode.equals("THROW"))
                        throw new IllegalStateException("SECRET-CALCULATOR");
                    if (mode.equals("DATA_UNAVAILABLE"))
                        return com.zhku.knowledge.tools.CampusToolResult.failure(
                                descriptor().name(), com.zhku.knowledge.tools.CampusToolStatus.DATA_UNAVAILABLE, "{}",
                                "测试失败");
                    return com.zhku.knowledge.tools.CampusToolResult.success(descriptor().name(),
                            mode.equals("INVALID_JSON") ? "not-json" : "{}", "测试失败");
                }
            };
            var calls = new AtomicInteger();
            var service = new CampusCreditAgentService(prompt -> response(calls.incrementAndGet() == 1
                    ? call("lookup_public_elective_credit_limit", "{}")
                    : call("summarize_course_credits", COURSES)),
                    new CampusToolRegistry(List.of(badTool)), question -> policy(), 3);
            var result = service.run(request());
            assertThat(result.state().name()).isEqualTo("FAILED");
            assertThat(result.failureCategory()).isEqualTo("TOOL_FAILURE");
            assertThat(result.answer()).doesNotContain("SECRET-CALCULATOR");
        }
    }

    @Test
    void nullModelOutputIsModelFailure() {
        var result = service(prompt -> null, policy()).run(request());
        assertThat(result.state().name()).isEqualTo("FAILED");
        assertThat(result.failureCategory()).isEqualTo("MODEL_FAILURE");
    }

    private static final String COURSES = """
            {"courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},
                        {"courseId":"HIS02","category":"公共选修课","credits":2}]}
            """;

    @Test
    void modelSelectsPolicyAndCalculationBeforeGroundedAnswer() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
            assertThat(options.getInternalToolExecutionEnabled()).isFalse();
            assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                    .containsExactlyInAnyOrder("lookup_public_elective_credit_limit", "summarize_course_credits");
            return response(calls.incrementAndGet() == 1
                    ? call("lookup_public_elective_credit_limit", "{}")
                    : call("summarize_course_credits", COURSES));
        };
        var result = service(model, policy()).run(request());
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.ANSWERED);
        assertThat(result.state().name()).isEqualTo("SUCCEEDED");
        assertThat(result.answer()).contains("3.5", "4 学分", "学生手册", "第 8、10 条", "不能确认");
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .contains("POLICY_ROUTE", "lookup_public_elective_credit_limit", "summarize_course_credits");
        assertThat(result.terminated()).isTrue();
    }

    @Test
    void missingCourseInputLeadsToClarificationWithoutClaimingCredits() {
        AtomicInteger policyCalls = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        var calculator = new CountingCalculator();
        var service = new CampusCreditAgentService(prompt -> {
            modelCalls.incrementAndGet();
            return response();
        }, new CampusToolRegistry(List.of(calculator)),
                question -> {
                    policyCalls.incrementAndGet();
                    return policy();
                }, 3);
        var result = service.run(
                new CampusCreditAgentService.Request("公选课本学期上限是多少？帮我算合计", List.of()));
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.NEED_CLARIFICATION);
        assertThat(result.state().name()).isEqualTo("INCOMPLETE");
        assertThat(result.answer()).contains("课程", "学分").doesNotContain("合计 0");
        assertThat(modelCalls.get()).isEqualTo(1);
        assertThat(policyCalls.get()).isZero();
        assertThat(calculator.calls.get()).isZero();
        assertThat(result.source()).isNull();
    }

    @Test
    void absentPublishedPolicyPreventsCalculation() {
        AtomicInteger calls = new AtomicInteger();
        var result = service(prompt -> {
            calls.incrementAndGet();
            return response(call("lookup_public_elective_credit_limit", "{}"));
        },
                new LocalPreviewConversationFacade.ConversationReply(
                        ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE, "资料不足", null,
                        AnswerRoute.INSUFFICIENT_EVIDENCE, true, List.of(), List.of()))
                .run(request());
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.state().name()).isEqualTo("INCOMPLETE");
        assertThat(result.answer()).contains("人工").doesNotContain("4 学分");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .doesNotContain("summarize_course_credits");
    }

    @Test
    void illegalToolParametersAreRejected() {
        var result = service(prompt -> response(call("summarize_course_credits",
                "{\"courses\":[{\"courseId\":\"ART01\",\"category\":\"公共选修课\",\"credits\":-1}]}")),
                policy()).run(request());
        assertThat(result.status()).isEqualTo(CampusCreditAgentService.Status.HANDOFF);
        assertThat(result.failureCategory()).isEqualTo("INVALID_TOOL_ARGUMENT");
        assertThat(result.answer()).doesNotContain("合计");
    }

    @Test
    void validCalculationCannotRunBeforePolicyLookup() {
        var result = service(prompt -> response(call("summarize_course_credits", COURSES)), policy())
                .run(request());
        assertThat(result.failureCategory()).isEqualTo("POLICY_REQUIRED");
        assertThat(result.steps()).extracting(CampusCreditAgentService.Step::name)
                .doesNotContain("summarize_course_credits");
    }

    @Test
    void unknownToolAndModelFailureDoNotInventAnswer() {
        var unknown = service(prompt -> response(call("terminal", "{}")), policy()).run(request());
        assertThat(unknown.failureCategory()).isEqualTo("UNKNOWN_TOOL");
        var failed = service(prompt -> {
            throw new IllegalStateException("secret provider text");
        },
                policy()).run(request());
        assertThat(failed.failureCategory()).isEqualTo("MODEL_FAILURE");
        assertThat(failed.answer()).doesNotContain("secret provider text", "4 学分");
    }

    @Test
    void fabricatedCourseCreditsAndMultipleCallsAreRejectedBeforeCalculation() {
        String fabricated = COURSES.replace("1.5", "2.5");
        var mismatch = service(prompt -> response(call("summarize_course_credits", fabricated)), policy())
                .run(request());
        assertThat(mismatch.failureCategory()).isEqualTo("INVALID_TOOL_ARGUMENT");
        var multiple = service(prompt -> response(call("lookup_public_elective_credit_limit", "{}"),
                call("summarize_course_credits", COURSES)), policy()).run(request());
        assertThat(multiple.failureCategory()).isEqualTo("TOOL_CALL_LIMIT");
        assertThat(multiple.steps()).extracting(CampusCreditAgentService.Step::name)
                .doesNotContain("summarize_course_credits");
    }

    @Test
    void policyFailureIsClassifiedWithoutLeakingException() {
        var service = new CampusCreditAgentService(prompt -> response(
                call("lookup_public_elective_credit_limit", "{}")),
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> {
                    throw new IllegalStateException("private database detail");
                }, 3);
        var result = service.run(request());
        assertThat(result.failureCategory()).isEqualTo("RETRIEVAL_FAILURE");
        assertThat(result.answer()).doesNotContain("private database detail");
    }

    private static CampusCreditAgentService.Request request() {
        return new CampusCreditAgentService.Request("这两门虚构公选课共多少学分，本学期制度上限是多少？",
                List.of(new CampusCreditAgentService.CourseInput("ART01", "公共选修课", new BigDecimal("1.5")),
                        new CampusCreditAgentService.CourseInput("HIS02", "公共选修课", new BigDecimal("2"))));
    }

    private static LocalPreviewConversationFacade.ConversationReply policy() {
        return new LocalPreviewConversationFacade.ConversationReply(
                ElectiveRetakeReplyType.ANSWER, "每学期公共选修课最高不得超过 4 学分。",
                new LocalPreviewConversationFacade.ConversationAnswer(
                        "每学期公共选修课最高不得超过 4 学分。", "以培养方案为准", UUID.randomUUID(),
                        "本科生学生手册（2025版）", "2025版", "PDF 第 76—77 页，第 8、10 条", "本校本科生"),
                AnswerRoute.HIGH_RISK_RULE, false, List.of(), List.of());
    }

    private static CampusCreditAgentService service(ChatModel model,
            LocalPreviewConversationFacade.ConversationReply policy) {
        return new CampusCreditAgentService(model,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())), question -> policy, 3);
    }

    private static AssistantMessage.ToolCall call(String name, String args) {
        return new AssistantMessage.ToolCall("call-1", "function", name, args);
    }

    private static ChatResponse response(AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("", java.util.Map.of(), List.of(calls)))));
    }
}
