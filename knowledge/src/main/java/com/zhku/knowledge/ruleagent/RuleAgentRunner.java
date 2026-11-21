package com.zhku.knowledge.ruleagent;

import com.zhku.agent.agent.runtime.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.contract.AgentToolCall;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 领域循环只消费下一动作，运行生命周期仍交给共享runtime。 */
public final class RuleAgentRunner {
    public record Question(String id, String text) {}

    public record Input(
            Owner owner,
            UUID taskId,
            long version,
            String question,
            List<Question> questions,
            Map<String, String> userConditions) {
        public Input {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(taskId);
            new CreateTask(question, UUID.randomUUID());
            if (version < 1 || version > MAX_SAFE_VERSION)
                throw new IllegalArgumentException("任务版本无效");
            questions = List.copyOf(questions);
            userConditions = Map.copyOf(userConditions);
            if (questions.isEmpty()
                    || questions.size() > 4
                    || questions.stream().map(Question::id).distinct().count() != questions.size()
                    || userConditions.size() > 16) throw new IllegalArgumentException("输入超出任务边界");
            // 子问题只能保留原句，不能由模型悄悄缩小请求；语义拆分质量仍需T05/T08验收。
            int cursor = 0;
            for (var q : questions) {
                new Search(q.id(), q.text());
                int start = question.indexOf(q.text(), cursor);
                if (start < 0 || !onlySeparators(question.substring(cursor, start)))
                    throw new IllegalArgumentException("子问题遗漏原请求");
                cursor = start + q.text().length();
            }
            if (!onlySeparators(question.substring(cursor)))
                throw new IllegalArgumentException("子问题遗漏原请求");
        }

        public static Input single(Owner owner, UUID task, String text) {
            return new Input(owner, task, 1, text, List.of(new Question("q1", text)), Map.of());
        }
    }

    public record RuleContext(
            String ruleId,
            String questionScope,
            List<com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.Condition> conditions,
            List<String> evidenceGaps) {}

    public record CandidateReadState(UUID handle, boolean alreadyRead) {}

    public record ActionState(
            List<String> allowedActions,
            List<CandidateReadState> candidates,
            int remainingEvidenceFragments,
            int remainingEvidenceBytes,
            long runMillisRemaining,
            int reservedReviewMillis,
            int reservedPublicationMillis) {}

    public record View(
            String question,
            List<Question> questions,
            Map<String, String> userConditions,
            List<ToolObservation> observations,
            List<RuleAgentEvidenceTools.VerifiedFact> facts,
            List<RuleContext> ruleContexts,
            List<Event> events,
            int attemptsRemaining,
            int stepsRemaining,
            ActionState state,
            List<String> publicationFactIds) {
        public View {
            if (publicationFactIds != null) publicationFactIds = List.copyOf(publicationFactIds);
        }

        public View(
                String question,
                List<Question> questions,
                Map<String, String> userConditions,
                List<ToolObservation> observations,
                List<RuleAgentEvidenceTools.VerifiedFact> facts,
                List<RuleContext> ruleContexts,
                List<Event> events,
                int attemptsRemaining,
                int stepsRemaining,
                ActionState state) {
            this(
                    question,
                    questions,
                    userConditions,
                    observations,
                    facts,
                    ruleContexts,
                    events,
                    attemptsRemaining,
                    stepsRemaining,
                    state,
                    null);
        }

        public View(
                String question,
                List<Question> questions,
                Map<String, String> userConditions,
                List<ToolObservation> observations,
                List<RuleAgentEvidenceTools.VerifiedFact> facts,
                List<RuleContext> ruleContexts,
                List<Event> events,
                int attemptsRemaining,
                int stepsRemaining) {
            this(
                    question,
                    questions,
                    userConditions,
                    observations,
                    facts,
                    ruleContexts,
                    events,
                    attemptsRemaining,
                    stepsRemaining,
                    null);
        }
    }

    public record Reply(String actionJson, boolean providerResponseReceived) {}

    public interface DecisionPort extends AutoCloseable {
        Reply decide(View view, Duration timeout);

        default void close() {}
    }

    public record Event(
            int attempt,
            String action,
            ToolObservation observation,
            ReasonCode reason,
            RulePublicationContract.Code publicationReason,
            @com.fasterxml.jackson.annotation.JsonIgnore RulePublicationContract.RelevanceDiagnostic relevanceDiagnostic,
            @com.fasterxml.jackson.annotation.JsonIgnore RulePublicationContract.SemanticAssessment review) {
        public Event(int attempt, String action, ToolObservation observation, ReasonCode reason) {
            this(attempt, action, observation, reason, null, null, null);
        }
        public Event(int attempt, String action, ToolObservation observation, ReasonCode reason,
                     RulePublicationContract.Code publicationReason) {
            this(attempt, action, observation, reason, publicationReason, null, null);
        }
        public Event(int attempt, String action, ToolObservation observation, ReasonCode reason,
                     RulePublicationContract.Code publicationReason,
                     RulePublicationContract.RelevanceDiagnostic relevanceDiagnostic) {
            this(attempt, action, observation, reason, publicationReason, relevanceDiagnostic, null);
        }
    }

    public record Outcome(TaskSnapshot snapshot, List<Event> events) {
        public Outcome {
            events = List.copyOf(events);
        }
    }

    static ActionState constrainClarification(
            ActionState state,
            List<RulePublicationContract.ReviewedCondition> conditions,
            Map<String, String> known) {
        // 只收窄动作，不创建发布能力；未知的已审条件仍须由独立核验判断是否必要。
        if (conditions.stream().anyMatch(c -> !known.containsKey(c.conditionId()))) return state;
        return new ActionState(
                state.allowedActions().stream()
                        .filter(a -> !a.equals("ASK_NECESSARY_CONDITION"))
                        .toList(),
                state.candidates(),
                state.remainingEvidenceFragments(),
                state.remainingEvidenceBytes(),
                state.runMillisRemaining(),
                state.reservedReviewMillis(),
                state.reservedPublicationMillis());
    }

    public static final class Rejected extends RuntimeException {
        final ReasonCode reason;

        public Rejected(ReasonCode reason) {
            this.reason = reason;
        }
    }

    private final DecisionPort decision;
    private final RuleActionBroker broker;
    private final Clock clock;
    private final RuleSemanticReviewPort publicationReview;

    /** 旧工程路径保留原行为；它不能作为新独立核验链路的验收身份。 */
    public RuleAgentRunner(DecisionPort decision, RuleActionBroker broker, Clock clock) {
        this.decision = decision;
        this.broker = broker;
        this.clock = clock;
        this.publicationReview = null;
    }

    /** 新路径必须显式提供核验端口，不能缺配置后静默退回旧门禁。 */
    public RuleAgentRunner(
            DecisionPort decision,
            RuleActionBroker broker,
            Clock clock,
            RuleSemanticReviewPort publicationReview) {
        this.decision = Objects.requireNonNull(decision);
        this.broker = Objects.requireNonNull(broker);
        this.clock = Objects.requireNonNull(clock);
        this.publicationReview = Objects.requireNonNull(publicationReview);
    }

    private static boolean onlySeparators(String value) {
        return value.matches("[\\s，,；;。！？!?]*");
    }

    public Outcome run(AgentRunContext context, Input input) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(input);
        if (context.state() != AgentRunState.CREATED
                && context.state() != AgentRunState.STOP_REQUESTED)
            throw new IllegalArgumentException("运行上下文不能复用");
        int attempts = 0, steps = 0;
        boolean received = false;
        var events = new ArrayList<Event>();
        String previous = null;
        ReasonCode lastRejectedTerminal = null;
        int decisionLimit =
                publicationReview == null
                        ? DEFAULT_BUDGET.maxModelAttemptsPerRun()
                        : RulePublicationContract.MAX_DECISION_CALLS;
        Instant deadline = clock.instant().plusMillis(DEFAULT_BUDGET.maxRunMillis());
        RuleResultPublisher.Published published = null;
        try {
            if (!context.start() && !context.isStopRequested())
                throw new IllegalStateException("运行未启动");
            while (published == null) {
                if (context.isStopRequested()) {
                    published =
                            new RuleResultPublisher(broker, input, context.runId())
                                    .failed(ReasonCode.CANCELLED);
                    break;
                }
                if (attempts >= decisionLimit
                        || steps >= DEFAULT_BUDGET.maxToolStepsPerRun()
                        || !clock.instant().isBefore(deadline)) {
                    var stopReason = attempts >= decisionLimit && clock.instant().isBefore(deadline)
                            && lastRejectedTerminal == ReasonCode.INVALID_MODEL_RESPONSE
                            ? lastRejectedTerminal : ReasonCode.BUDGET_EXCEEDED;
                    published =
                            new RuleResultPublisher(broker, input, context.runId())
                                    .failed(stopReason);
                    break;
                }
                var entries =
                        broker.facts().stream()
                                .map(
                                        f ->
                                                broker.tools
                                                        .reviewedEntry(f.ruleId())
                                                        .orElseThrow(
                                                                () ->
                                                                        new Rejected(
                                                                                ReasonCode
                                                                                        .EVIDENCE_CHANGED)))
                                .distinct()
                                .toList();
                var capabilities =
                        publicationReview == null
                                ? null
                                : RuleFactCapabilityCatalogue.fromReviewed(entries);
                var conditions =
                        capabilities == null
                                ? List.<RulePublicationContract.ReviewedCondition>of()
                                : capabilities.conditions(broker.facts());
                var contexts =
                        entries.stream()
                                .map(
                                        e ->
                                                new RuleContext(
                                                        e.template().ruleId(),
                                                        e.questionScope(),
                                                        e.template().conditions(),
                                                        e.template().evidenceGaps()))
                                .toList();
                var state =
                        publicationReview == null
                                ? null
                                : constrainClarification(
                                        broker.actionState(
                                                decisionLimit - attempts,
                                                DEFAULT_BUDGET.maxToolStepsPerRun() - steps,
                                                Duration.between(clock.instant(), deadline)
                                                        .toMillis()),
                                        conditions,
                                        input.userConditions());
                var view =
                        new View(
                                input.question(),
                                input.questions(),
                                input.userConditions(),
                                broker.observations(),
                                broker.facts(),
                                contexts,
                                List.copyOf(events),
                                decisionLimit - attempts,
                                DEFAULT_BUDGET.maxToolStepsPerRun() - steps,
                                state,
                                capabilities == null
                                        ? null
                                        : capabilities.publicationFactIds(broker.facts()));
                if (json(view).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        > DEFAULT_BUDGET.maxRequestBytes())
                    throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                long reserve =
                        publicationReview == null ? 0 : DEFAULT_BUDGET.maxModelMillis() + 5000L;
                long decisionMillis =
                        Duration.between(clock.instant(), deadline).toMillis() - reserve;
                if (decisionMillis <= 0
                        || view.state() != null && view.state().allowedActions().isEmpty())
                    throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                attempts++;
                context.advanceStep();
                Instant called = clock.instant();
                int decisionAttempt = attempts;
                Reply reply =
                        decision.decide(
                                view,
                                Duration.ofMillis(
                                        Math.min(
                                                DEFAULT_BUDGET.maxModelMillis(),
                                                Math.max(
                                                        1,
                                                        Duration.between(called, deadline)
                                                                        .toMillis()
                                                                - reserve))));
                // 不能在取消或超时之后发布迟到结果；阻塞调用的强制退出由T05适配器负责。
                if (context.isStopRequested()) {
                    published =
                            new RuleResultPublisher(broker, input, context.runId())
                                    .failed(ReasonCode.CANCELLED);
                    break;
                }
                if (Duration.between(called, clock.instant()).toMillis()
                        > DEFAULT_BUDGET.maxModelMillis())
                    throw new Rejected(ReasonCode.MODEL_TIMEOUT);
                if (!clock.instant().isBefore(deadline))
                    throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                if (reply == null) throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                received |= reply.providerResponseReceived();
                String name = "INVALID";
                try {
                    var action = RuleAgentJson.readAction(reply.actionJson());
                    name = name(action);
                    String fingerprint = json(action);
                    if (view.state() != null && !view.state().allowedActions().contains(name)) {
                        // 没有候选/事实/观察就不能读取/追问/发布；与为独立核验预留资源而禁用动作分开归因。
                        boolean missingInput =
                                action instanceof Read && view.state().candidates().isEmpty()
                                        || action instanceof Ask ask
                                                && (view.facts().isEmpty()
                                                        || conditions.stream()
                                                                .noneMatch(
                                                                        c ->
                                                                                c.conditionId()
                                                                                                .equals(
                                                                                                        ask
                                                                                                                .conditionId())
                                                                                        && !input
                                                                                                .userConditions()
                                                                                                .containsKey(
                                                                                                        c
                                                                                                                .conditionId())))
                                        || action instanceof Finish
                                                && view.observations().isEmpty();
                        ReasonCode rejected =
                                missingInput
                                        ? ReasonCode.INVALID_MODEL_RESPONSE
                                        : ReasonCode.BUDGET_EXCEEDED;
                        events.add(new Event(decisionAttempt, name, null, rejected));
                        published =
                                new RuleResultPublisher(broker, input, context.runId())
                                        .failed(rejected);
                        break;
                    }
                    if (fingerprint.equals(previous)
                            && (publicationReview == null || action instanceof Search || action instanceof Read)) {
                        published =
                                new RuleResultPublisher(broker, input, context.runId())
                                        .failed(ReasonCode.NO_PROGRESS);
                        events.add(new Event(attempts, name, null, ReasonCode.NO_PROGRESS));
                        break;
                    }
                    previous = fingerprint;
                    String q =
                            action instanceof Search s
                                    ? s.subquestionId()
                                    : action instanceof Read r
                                            ? r.subquestionId()
                                            : action instanceof Ask a ? a.subquestionId() : null;
                    if (q != null && input.questions().stream().noneMatch(x -> x.id().equals(q)))
                        throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                    steps++;
                    var call = context.proposeToolCall(null, name, fingerprint);
                    ToolObservation observation = null;
                    ReasonCode reason = null;
                    RulePublicationContract.Code publicationReason = null;
                    Event reviewEvent = null;
                    boolean precheckRejected = false;
                    try {
                        if (action instanceof Search || action instanceof Read)
                            observation = broker.execute(action, input, context.runId(), steps);
                        else if (action instanceof Ask || action instanceof Finish) {
                            if (publicationReview == null) {
                                var publisher =
                                        new RuleResultPublisher(broker, input, context.runId());
                                published =
                                        action instanceof Ask a
                                                ? publisher.ask(a)
                                                : publisher.finish((Finish) action);
                            } else {
                                var precheck =
                                        new RuleResultPublisher(broker, input, context.runId());
                                try {
                                    if (action instanceof Ask a) precheck.precheckAsk(a);
                                    else precheck.precheckFinish((Finish) action);
                                } catch (Rejected precheckInvalid) {
                                    // 核验前的提议内容级拒绝保留分支侧语义：记事件后允许模型重复改提，
                                    // 预算耗尽时按 lastRejectedTerminal 收敛为 INVALID_MODEL_RESPONSE，
                                    // 不在此立即发布失败（与 f5393c36 的确定性拒绝立即终止语义按来源分流）。
                                    precheckRejected = true;
                                    throw precheckInvalid;
                                }
                                if (attempts >= DEFAULT_BUDGET.maxModelAttemptsPerRun()
                                        || !clock.instant().isBefore(deadline))
                                    throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                                // 在核验消费前拒绝无发布能力的提议；保留实际ID，不做别名替换。
                                capabilities.requireProposal(action, broker.facts());
                                var safe =
                                        RulePublicationContract.input(
                                                input, broker.facts(), capabilities);
                                if (json(safe)
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                                .length
                                        > DEFAULT_BUDGET.maxRequestBytes())
                                    throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                                if (Duration.between(clock.instant(), deadline).toMillis()
                                        < DEFAULT_BUDGET.maxModelMillis() + 5000L)
                                    throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                                attempts++;
                                context.advanceStep();
                                Instant reviewStarted = clock.instant();
                                RulePublicationContract.SemanticAssessment assessed = null;
                                try {
                                    publicationReview.captureInput(
                                            input, broker.facts(), capabilities);
                                    assessed =
                                            publicationReview.assess(
                                                    safe,
                                                    Duration.ofMillis(
                                                            Math.min(
                                                                    DEFAULT_BUDGET.maxModelMillis(),
                                                                    Math.max(
                                                                            1,
                                                                            Duration.between(
                                                                                                    reviewStarted,
                                                                                                    deadline)
                                                                                            .toMillis()
                                                                                    - 5000))));
                                    if (context.isStopRequested())
                                        throw new Rejected(ReasonCode.CANCELLED);
                                    if (Duration.between(reviewStarted, clock.instant()).toMillis()
                                            > DEFAULT_BUDGET.maxModelMillis())
                                        throw new Rejected(ReasonCode.MODEL_TIMEOUT);
                                    if (!clock.instant().isBefore(deadline))
                                        throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                                    var gate = new RulePublicationGate(capabilities);
                                    var reviewedFinish = action instanceof Finish finish
                                            ? gate.reviewFinish(input, finish, assessed, broker.facts()) : null;
                                    var coverage = reviewedFinish == null
                                            ? gate.assess(input, action, assessed, broker.facts()) : reviewedFinish.assessment();
                                    reviewEvent = new Event(attempts, "PUBLICATION_REVIEW", null, null, null,
                                            reviewedFinish == null ? null : reviewedFinish.exclusion(), assessed);
                                    var publisher =
                                            new RuleResultPublisher(
                                                    broker, input, context.runId(), coverage);
                                    published =
                                            action instanceof Ask a
                                                    ? publisher.ask(a)
                                                    : publisher.finish(reviewedFinish.proposal());
                                } catch (RulePublicationContract.Rejected invalid) {
                                    reviewEvent =
                                            new Event(
                                                    attempts,
                                                    "PUBLICATION_REVIEW",
                                                    null,
                                                    ReasonCode.INVALID_MODEL_RESPONSE,
                                                    invalid.code,
                                                    invalid.relevance,
                                                    assessed);
                                    throw invalid;
                                }
                            }
                        }
                        // 发布会fresh重读原文；预留5秒不等于读取有硬超时，迟到结果必须在终态前撤销。
                        if (published != null && !clock.instant().isBefore(deadline)) {
                            published =
                                    new RuleResultPublisher(broker, input, context.runId())
                                            .failed(ReasonCode.BUDGET_EXCEEDED);
                            throw new Rejected(ReasonCode.BUDGET_EXCEEDED);
                        }
                        var toolStatus =
                                observation == null
                                                || observation.status()
                                                        == ObservationStatus.SUCCEEDED
                                                || observation.status()
                                                        == ObservationStatus.NO_MATCH
                                        ? AgentToolCall.Status.SUCCEEDED
                                        : observation.status() == ObservationStatus.UNAVAILABLE
                                                ? AgentToolCall.Status.FAILED
                                                : AgentToolCall.Status.REJECTED;
                        context.recordToolResult(
                                call,
                                new AgentToolCall.Result(
                                        toolStatus,
                                        json(
                                                observation == null
                                                        ? published.result()
                                                        : observation),
                                        AgentToolCall.PayloadFormat.JSON,
                                        toolStatus == AgentToolCall.Status.SUCCEEDED
                                                ? null
                                                : com.zhku.agent.agent.runtime.contract
                                                        .AgentRuntimeError.of(
                                                        com.zhku.agent.agent.runtime.contract
                                                                .AgentRuntimeError.Category
                                                                .TOOL_FAILURE)));
                    } catch (RulePublicationContract.Rejected invalid) {
                        publicationReason = invalid.code;
                        reason = ReasonCode.INVALID_MODEL_RESPONSE;
                        published =
                                new RuleResultPublisher(broker, input, context.runId())
                                        .failed(reason);
                        context.recordToolResult(
                                call,
                                new AgentToolCall.Result(
                                        AgentToolCall.Status.REJECTED,
                                        null,
                                        null,
                                        com.zhku.agent.agent.runtime.contract.AgentRuntimeError.of(
                                                com.zhku.agent.agent.runtime.contract
                                                        .AgentRuntimeError.Category
                                                        .DOMAIN_FAILURE)));
                    } catch (RuleAgentProvider.Failure invalid) {
                        // Provider失败越过原领域异常分支；先留下提议和核验失败，不能在外层收敛时丢失动作。
                        events.add(new Event(decisionAttempt, name, null, invalid.reason));
                        if (attempts > decisionAttempt)
                            events.add(
                                    new Event(
                                            attempts, "PUBLICATION_REVIEW", null, invalid.reason));
                        context.recordToolResult(
                                call,
                                new AgentToolCall.Result(
                                        AgentToolCall.Status.FAILED,
                                        null,
                                        null,
                                        com.zhku.agent.agent.runtime.contract.AgentRuntimeError.of(
                                                com.zhku.agent.agent.runtime.contract
                                                        .AgentRuntimeError.Category.TOOL_FAILURE)));
                        throw invalid;
                    } catch (Rejected invalid) {
                        reason = invalid.reason;
                        // f5393c36 侧语义：非提议内容级的确定性 INVALID_MODEL_RESPONSE 拒绝立即失败，
                        // 防止重试后被 NO_PROGRESS 覆盖成伪造的资源不足原因。
                        // 分支侧语义：precheckRejected 标记的核验前内容级拒绝不立即终止，
                        // 保留失败事件并允许模型重复改提，由 lastRejectedTerminal 在预算耗尽时收敛。
                        if (reason == ReasonCode.INVALID_MODEL_RESPONSE && !precheckRejected)
                            published =
                                    new RuleResultPublisher(broker, input, context.runId())
                                            .failed(reason);
                        context.recordToolResult(
                                call,
                                new AgentToolCall.Result(
                                        AgentToolCall.Status.REJECTED,
                                        null,
                                        null,
                                        com.zhku.agent.agent.runtime.contract.AgentRuntimeError.of(
                                                com.zhku.agent.agent.runtime.contract
                                                        .AgentRuntimeError.Category
                                                        .DOMAIN_FAILURE)));
                    }
                    events.add(
                            new Event(
                                    decisionAttempt, name, observation, reason, publicationReason));
                    // 决策耗尽时保留终结提议的真实拒因，避免将校验错误掩盖成预算或重复检索问题。
                    lastRejectedTerminal = publicationReview != null && (action instanceof Finish || action instanceof Ask)
                            ? reason : null;
                    if (reviewEvent != null) events.add(reviewEvent);
                    if (observation != null
                            && observation.status() == ObservationStatus.SUCCEEDED
                            && !broker.progressed()) {
                        events.add(new Event(decisionAttempt, name, null, ReasonCode.NO_PROGRESS));
                        published =
                                new RuleResultPublisher(broker, input, context.runId())
                                        .failed(ReasonCode.NO_PROGRESS);
                    }
                } catch (IllegalArgumentException malformed) {
                    ReasonCode reason = ReasonCode.INVALID_MODEL_RESPONSE;
                    try {
                        String type =
                                new ObjectMapper()
                                        .readTree(reply.actionJson())
                                        .path("type")
                                        .asText();
                        if (!Set.of(
                                        "SEARCH_REVIEWED_RULES",
                                        "READ_VERIFIED_RULE",
                                        "ASK_NECESSARY_CONDITION",
                                        "FINISH_CHECK")
                                .contains(type)) reason = ReasonCode.UNKNOWN_TOOL;
                    } catch (Exception ignored) {
                    }
                    events.add(new Event(attempts, name, null, reason));
                } catch (Rejected invalid) {
                    events.add(new Event(attempts, name, null, invalid.reason));
                }
            }
        } catch (RuleAgentProvider.Failure failure) {
            received |= failure.received;
            published =
                    new RuleResultPublisher(broker, input, context.runId()).failed(failure.reason);
        } catch (Rejected failure) {
            published =
                    new RuleResultPublisher(broker, input, context.runId()).failed(failure.reason);
        } catch (RuntimeException unavailable) {
            published =
                    new RuleResultPublisher(broker, input, context.runId())
                            .failed(ReasonCode.INVALID_MODEL_RESPONSE);
        } finally {
            // 等待学生不占用工作线程；端口必须是本轮独占资源，不关闭宿主共享线程池。
            if (context.isStopRequested())
                published =
                        new RuleResultPublisher(broker, input, context.runId())
                                .failed(ReasonCode.CANCELLED);
            // 清理失败须在终态CAS前确定，避免runtime成功但结果失败的不可发布快照。
            try {
                context.cleanupOnce(this::closePorts);
            } catch (RuntimeException cleanupFailure) {
                /* runtime已记录清理异常，仍需完成失败状态与终止收尾。 */
            }
            if (context.hasCleanupFailure())
                published =
                        new RuleResultPublisher(broker, input, context.runId())
                                .failed(ReasonCode.TASK_EXECUTION_UNAVAILABLE);
            if (published != null
                    && (published.status() == TaskStatus.RESOLVED
                            || published.status() == TaskStatus.WAITING_USER)
                    && !clock.instant().isBefore(deadline))
                published =
                        new RuleResultPublisher(broker, input, context.runId())
                                .failed(ReasonCode.BUDGET_EXCEEDED);
            var target = published == null ? TaskStatus.FAILED : published.status();
            var state =
                    target == TaskStatus.STOPPED
                            ? (context.isStopRequested()
                                    ? AgentRunState.CANCELLED
                                    : AgentRunState.INCOMPLETE)
                            : target == TaskStatus.FAILED
                                    ? AgentRunState.FAILED
                                    : target == TaskStatus.WAITING_USER
                                                    || published.result().coverage()
                                                            != Coverage.COMPLETE
                                            ? AgentRunState.INCOMPLETE
                                            : AgentRunState.SUCCEEDED;
            if (!context.finish(state) && context.isStopRequested()) {
                published =
                        new RuleResultPublisher(broker, input, context.runId())
                                .failed(ReasonCode.CANCELLED);
                context.finish(AgentRunState.CANCELLED);
            }
            context.signalTermination();
            context.publishTermination();
        }
        var execution =
                new Execution(
                        Mode.ONLINE,
                        attempts,
                        steps,
                        received,
                        published.result().evidence().isEmpty()
                                ? AnswerSource.NONE
                                : received
                                        ? AnswerSource.LIVE_AGENT
                                        : AnswerSource.VERIFIED_RULE_ONLY,
                        ReplayCoverage.NOT_APPLICABLE);
        var snapshot =
                new TaskSnapshot(
                        SCHEMA_VERSION,
                        input.taskId(),
                        UUID.randomUUID(),
                        context.runId(),
                        input.version(),
                        published.status(),
                        context.state(),
                        true,
                        published.result(),
                        published.continuation(),
                        execution);
        return new Outcome(snapshot, events);
    }

    private void closePorts() {
        try {
            decision.close();
        } finally {
            if (publicationReview != null && (Object) publicationReview != decision)
                publicationReview.close();
        }
    }

    private static String json(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception invalid) {
            throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
        }
    }

    private static String name(NextAction action) {
        return action instanceof Search
                ? "SEARCH_REVIEWED_RULES"
                : action instanceof Read
                        ? "READ_VERIFIED_RULE"
                        : action instanceof Ask ? "ASK_NECESSARY_CONDITION" : "FINISH_CHECK";
    }
}
