package com.zhku.knowledge.ruleagent.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.ruleagent.web.RuleAgentTaskToolsRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 只由独立演示宿主显式装配；固定输入不是自然语言模型，也不是在线失败后的替代品。 */
public final class RuleAgentSimulatedDemoFactory implements RuleAgentTaskStore.Factory {
    public static final String QUESTION = "我这门选修课没过，能不能重修？";
    static final String PUBLIC_FACT = "S10/RETAKE_ONLY:public-failed-no-retake";
    static final String PROFESSIONAL_FACT = "S10/RETAKE_ONLY:professional-failed-retake-application";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RuleAgentTaskToolsRegistry registry;
    private final Clock clock;
    private final AtomicInteger simulatedPortCalls = new AtomicInteger();

    public RuleAgentSimulatedDemoFactory(RuleAgentTaskToolsRegistry registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry); this.clock = Objects.requireNonNull(clock);
    }

    @Override public RuleAgentTaskStore.Work open(Owner owner, UUID task) {
        var tools = registry.open(owner, task);
        return new RuleAgentTaskStore.Work() {
            @Override public boolean revalidate(ResultEnvelope previous) {
                return previous.evidence().stream().allMatch(e -> registry.read(owner, task, e.originalToken()).isPresent());
            }
            @Override public RuleAgentRunner.Outcome run(AgentRunContext context, RuleAgentRunner.Input input) {
                RuleAgentRunner.DecisionPort decision = (view, timeout) -> {
                    simulatedPortCalls.incrementAndGet(); return decide(view);
                };
                RuleSemanticReviewPort review = (safe, timeout) -> {
                    simulatedPortCalls.incrementAndGet(); return assess(safe);
                };
                // 使用带独立核验端口的真实循环：Gate、fresh 引用、适用性、取消和发布都不另写一套。
                var outcome = new RuleAgentRunner(decision, new RuleActionBroker(tools), clock, review).run(context, input);
                var s = outcome.snapshot();
                // Runner 的端口调用次数不是模型调用次数；只投影模式元数据，保留结果、状态和事件原样。
                var execution = new Execution(Mode.ENGINEERING_REPLAY, 0, s.execution().toolStepCount(), false,
                        s.result().evidence().isEmpty() ? AnswerSource.NONE : AnswerSource.VERIFIED_RULE_ONLY, ReplayCoverage.HIT);
                return new RuleAgentRunner.Outcome(new TaskSnapshot(s.schemaVersion(), s.taskId(), s.turnId(), s.runId(),
                        s.version(), s.taskStatus(), s.runState(), s.terminated(), s.result(), s.continuationToken(), execution), outcome.events());
            }
        };
    }

    public int simulatedPortCalls() { return simulatedPortCalls.get(); }

    static RuleAgentRunner.Reply decide(RuleAgentRunner.View view) {
        if (!QUESTION.equals(view.question()) || view.questions().size() != 1)
            throw new RuleAgentRunner.Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
        String q = view.questions().getFirst().id();
        NextAction action;
        if (view.observations().isEmpty()) action = new Search(q, "选修课考核不及格重修");
        else if (view.facts().isEmpty()) {
            var found = view.observations().getLast();
            if (found.status() != ObservationStatus.SUCCEEDED) throw new RuleAgentRunner.Rejected(ReasonCode.RETRIEVAL_UNAVAILABLE);
            // 仅选含重修条文的真实候选，不制造句柄；目录变化/缺失由真实工具和 Gate 拒绝。
            var handles = found.candidates().stream().filter(c -> c.excerpt().contains("重修"))
                    .limit(DEFAULT_BUDGET.maxReadHandles()).map(Candidate::handle).toList();
            if (handles.isEmpty()) throw new RuleAgentRunner.Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            action = new Read(q, handles);
        } else {
            Set<String> read = new HashSet<>(view.facts().stream().map(RuleAgentEvidenceTools.VerifiedFact::id).toList());
            if (!view.userConditions().containsKey("courseType")) {
                if (!read.containsAll(List.of(PUBLIC_FACT, PROFESSIONAL_FACT))) throw new RuleAgentRunner.Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                action = new Ask(q, "courseType", "工程模拟：重修规则依赖课程类别", List.of(PUBLIC_FACT, PROFESSIONAL_FACT));
            } else {
                String id = switch (view.userConditions().get("courseType")) {
                    case "PUBLIC" -> PUBLIC_FACT; case "PROFESSIONAL" -> PROFESSIONAL_FACT;
                    default -> throw new RuleAgentRunner.Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                };
                if (!read.contains(id)) throw new RuleAgentRunner.Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                action = new Finish(List.of(new Proposal(q, List.of(id), List.of(), null)));
            }
        }
        try { return new RuleAgentRunner.Reply(JSON.writeValueAsString(action), false); }
        catch (java.io.IOException invalid) { throw new IllegalStateException("模拟动作编码失败", invalid); }
    }

    static SemanticAssessment assess(SemanticInput input) {
        if (input.questions().size() != 1 || !QUESTION.equals(input.questions().getFirst().text()))
            throw new Rejected(Code.REVIEW_SCHEMA_INVALID);
        var span = new DemandSpan(input.questions().getFirst().id(), 0, QUESTION.length());
        var requirements = new ArrayList<Requirement>(); var bindings = new ArrayList<SemanticBinding>();
        String known = input.userConditions().get("courseType");
        for (String value : known == null ? List.of("PUBLIC", "PROFESSIONAL") : List.of(known)) {
            String fact = switch (value) { case "PUBLIC" -> PUBLIC_FACT; case "PROFESSIONAL" -> PROFESSIONAL_FACT;
                default -> throw new Rejected(Code.REVIEW_BINDING_INVALID); };
            // 不是默认放行核验器：固定需求和事实 ID 仍须通过实际能力、正文指纹、条件与引用守卫。
            requirements.add(new Requirement(value, span, Dimension.RETAKE,
                    value.equals("PUBLIC") ? Subject.PUBLIC_ELECTIVE : Subject.PROFESSIONAL_ELECTIVE,
                    Recognition.IDENTIFIED, List.of("courseType")));
            bindings.add(new SemanticBinding(value, fact, BindingRole.CORE));
        }
        return new SemanticAssessment(REVISION, input.inputFingerprint(), requirements, bindings, List.of());
    }
}
