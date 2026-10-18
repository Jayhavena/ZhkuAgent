package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.RuleAgentRunner;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 测试宿主的单题外发边界；校验发生在真实 factory.run 创建 Provider 之前。 */
final class RuleAgentOnlineHttpScope {
    private static final String QUESTION = "我这门选修课没过，能不能重修？";
    private Owner owner;
    private UUID task;
    private RuleAgentRunner.Input active;
    private int runs;
    private boolean firstAsked, complete, failed;
    private final Set<UUID> runIds = new HashSet<>();

    synchronized void begin(RuleAgentRunner.Input input, String rawMessage) {
        // TaskStore 会把“公选课”等同义回复规范化；外发包批准的是逐字 PUBLIC，不能只看规范化条件。
        if (!Objects.equals(runs == 0 ? QUESTION : "PUBLIC",rawMessage)) reject();
        begin(input);
    }

    synchronized void begin(RuleAgentRunner.Input input) {
        // 拒绝也封闭本批次：失败后不能靠换主体、换任务或重发把同一授权变成试错额度。
        if (failed || active != null || runs >= 2 || !QUESTION.equals(input.question())
                || input.questions().size() != 1 || !"q1".equals(input.questions().getFirst().id())
                || !QUESTION.equals(input.questions().getFirst().text())) reject();
        if (runs == 0) {
            if (input.version() != 2 || !input.userConditions().isEmpty()) reject();
            owner = input.owner(); task = input.taskId();
        } else if (!firstAsked || !owner.equals(input.owner()) || !task.equals(input.taskId())
                || input.version() != 4 || !Map.of("courseType", "PUBLIC").equals(input.userConditions())) reject();
        active = input; runs++;
    }

    synchronized void complete(RuleAgentRunner.Outcome outcome) {
        if (active == null) reject();
        var snapshot = outcome.snapshot();
        if (!task.equals(snapshot.taskId()) || active.version() != snapshot.version() || !runIds.add(snapshot.runId())) reject();
        active = null;
        boolean online = snapshot.execution().mode() == Mode.ONLINE && snapshot.execution().providerResponseReceived()
                && snapshot.execution().modelAttemptCount() > 0 && snapshot.terminated();
        if (runs == 1) {
            var conditions = snapshot.result() == null ? List.<Clarification>of()
                    : snapshot.result().items().stream().flatMap(item -> item.clarification().stream()).toList();
            firstAsked = online && snapshot.taskStatus() == TaskStatus.WAITING_USER && conditions.size() == 1
                    && "courseType".equals(conditions.getFirst().conditionId()) && conditions.getFirst().allowedValues().contains("PUBLIC");
            if (!firstAsked) failed = true;
        } else {
            complete = online && firstAsked && snapshot.taskStatus() == TaskStatus.RESOLVED
                    && snapshot.result() != null && snapshot.result().coverage() == Coverage.COMPLETE;
            if (!complete) failed = true;
        }
    }

    synchronized void requireSuccessfulAskPublicComplete() {
        if (failed || active != null || runs != 2 || !firstAsked || !complete) throw new IllegalStateException("本批未完成固定 ONLINE ASK→PUBLIC→COMPLETE，不计为通过");
    }
    private void reject() { failed = true; throw new IllegalStateException("请求超出本批唯一问题、主体、任务或续答范围；未创建供应商端口"); }
}
