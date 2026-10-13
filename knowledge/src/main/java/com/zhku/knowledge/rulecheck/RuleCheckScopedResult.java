package com.zhku.knowledge.rulecheck;

import java.util.*;

/** 服务端选择产物；Continuation不是请求DTO，公开构造器也不是真实性证明。 */
public record RuleCheckScopedResult(RuleCheckDecision decision,Scope scope,Continuation continuation,ReleaseBinding releaseBinding,
                                   RuleCheckApplicabilityGuard.Assessment applicability,RuleCheckTaskPlan.Binding evidencePlan,RetrievalTrace retrievalTrace) {
    public RuleCheckScopedResult {Objects.requireNonNull(decision);}
    public RuleCheckScopedResult(RuleCheckDecision decision,Scope scope,Continuation continuation,ReleaseBinding binding,
                                RuleCheckApplicabilityGuard.Assessment applicability,RuleCheckTaskPlan.Binding plan) {
        this(decision,scope,continuation,binding,applicability,plan,null);
    }
    public RuleCheckScopedResult(RuleCheckDecision decision,Scope scope,Continuation continuation) {this(decision,scope,continuation,null,null,null);}
    public RuleCheckScopedResult(RuleCheckDecision decision,Scope scope,Continuation continuation,ReleaseBinding binding) {this(decision,scope,continuation,binding,null,null);}
    public RuleCheckScopedResult(RuleCheckDecision decision,Scope scope,Continuation continuation,ReleaseBinding binding,RuleCheckApplicabilityGuard.Assessment applicability) {this(decision,scope,continuation,binding,applicability,null);}
    /** 仅请求内存；元数据不是HTTP授权，完整来源仍须fresh reader核验。 */
    public record CapturedReference(String citationId,UUID sourceId,UUID chunkId,String revision,String checksum) { }
    /** 实际取证审计，不是发布许可或已回答事实；拒答也必须保留，不能用安全状态冒充召回成功。 */
    public record RetrievalTrace(List<CapturedReference> references,List<String> originalLocators,String plannerStatus,RuleCheckTaskPlan proposedPlan) {
        public RetrievalTrace {
            references=List.copyOf(references);originalLocators=List.copyOf(originalLocators);
            if(references.size()>50||references.size()!=originalLocators.size())throw new IllegalArgumentException("取证记录超出预算或定位缺失");
        }
    }
    public record ReleaseBinding(RuleCheckRuntimeSettings settings,RuleCheckApplicationService.CatalogEntry entry,
                                 String question,Map<String,String> confirmedConditions,List<CapturedReference> references) {
        public ReleaseBinding {Objects.requireNonNull(settings);Objects.requireNonNull(entry);Objects.requireNonNull(question);confirmedConditions=Map.copyOf(confirmedConditions);references=List.copyOf(references);}
    }
    public record Scope(String ruleId,String questionScope,List<RuleCheckRuleTemplate.Condition> declaredConditions,
                        Map<String,Map<String,String>> factPreconditions) {
        public Scope {
            declaredConditions=List.copyOf(declaredConditions);
            var copy=new LinkedHashMap<String,Map<String,String>>();factPreconditions.forEach((key,value)->copy.put(key,Map.copyOf(value)));
            factPreconditions=Map.copyOf(copy);
        }
    }
    public record Continuation(RuleCheckRuntimeSettings settings,RuleCheckApplicationService.CatalogEntry entry,
                               String question,Map<String,String> confirmedConditions,RuleCheckRuleTemplate.Condition nextCondition,RuleCheckTaskPlan evidencePlan,
                               RuleCheckTaskPlan.Binding originalPlanBinding,ReleaseBinding originalCapture) {
        public Continuation(RuleCheckRuntimeSettings settings,RuleCheckApplicationService.CatalogEntry entry,String question,Map<String,String> confirmedConditions,RuleCheckRuleTemplate.Condition nextCondition) {this(settings,entry,question,confirmedConditions,nextCondition,null,null,null);}
        public Continuation(RuleCheckRuntimeSettings settings,RuleCheckApplicationService.CatalogEntry entry,String question,Map<String,String> confirmedConditions,RuleCheckRuleTemplate.Condition nextCondition,RuleCheckTaskPlan evidencePlan) {this(settings,entry,question,confirmedConditions,nextCondition,evidencePlan,null,null);}
        public Continuation {Objects.requireNonNull(settings);Objects.requireNonNull(entry);Objects.requireNonNull(question);Objects.requireNonNull(nextCondition);confirmedConditions=Map.copyOf(confirmedConditions);}
    }
}
