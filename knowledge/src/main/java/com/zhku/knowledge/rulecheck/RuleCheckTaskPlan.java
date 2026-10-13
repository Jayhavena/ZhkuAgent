package com.zhku.knowledge.rulecheck;

import java.util.*;

/** 模型只提出证据内的处理动作；不能提交事实正文、资料权限或个人条件。 */
public record RuleCheckTaskPlan(List<Task> tasks,List<Task> rejectedTasks) {
    public RuleCheckTaskPlan(List<Task> tasks) {this(tasks,List.of());}
    public RuleCheckTaskPlan {
        tasks=List.copyOf(tasks);rejectedTasks=List.copyOf(rejectedTasks);
        var all=new ArrayList<>(tasks);all.addAll(rejectedTasks);
        if(tasks.isEmpty()||all.size()>4||new HashSet<>(all).size()!=all.size())
            throw new IllegalArgumentException("子任务必须为1至4项且不能重复");
    }
    public enum Action { EXPLAIN_RULE, MISSING_DETAIL, EXCLUDE_CONDITION, COMPARE_THRESHOLD, UNKNOWN_CURRENT, UNSUPPORTED }
    public record Task(String ruleId,Action action,String operand,String value) {
        public Task {
            Objects.requireNonNull(action);Objects.requireNonNull(ruleId);Objects.requireNonNull(operand);Objects.requireNonNull(value);
            if(ruleId.length()>128||operand.length()>64||value.length()>32||action!=Action.UNSUPPORTED&&ruleId.isBlank()
                    ||action==Action.UNSUPPORTED&&(!ruleId.isEmpty()||!operand.isEmpty()||!value.isEmpty()))
                throw new IllegalArgumentException("子任务字段超出协议");
        }
    }
    public record Option(String ruleId,String questionScope,List<String> verifiedFacts,List<Action> actions,List<String> operands) {
        public Option {
            verifiedFacts=List.copyOf(verifiedFacts);actions=List.copyOf(actions);operands=List.copyOf(operands);
        }
    }
    @FunctionalInterface public interface Planner { RuleCheckTaskPlan plan(String question,List<Option> verifiedOptions); }
    /** 一次请求的可重放产物；必须在返回前再次核对每个原始目录项。 */
    public record Binding(RuleCheckTaskPlan plan,List<RuleCheckApplicationService.CatalogEntry> entries) {
        public Binding { Objects.requireNonNull(plan);entries=List.copyOf(entries);if(entries.isEmpty()||entries.size()>4)throw new IllegalArgumentException("计划绑定不完整"); }
    }
}
