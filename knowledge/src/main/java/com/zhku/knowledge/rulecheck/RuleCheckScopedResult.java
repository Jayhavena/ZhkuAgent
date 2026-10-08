package com.zhku.knowledge.rulecheck;

import java.util.*;

/** 服务端选择产物；Continuation不是请求DTO，公开构造器也不是真实性证明。 */
public record RuleCheckScopedResult(RuleCheckDecision decision,Scope scope,Continuation continuation,ReleaseBinding releaseBinding) {
    public RuleCheckScopedResult {Objects.requireNonNull(decision);}
    public RuleCheckScopedResult(RuleCheckDecision decision,Scope scope,Continuation continuation) {this(decision,scope,continuation,null);}
    /** 仅请求内存；元数据不是HTTP授权，完整来源仍须fresh reader核验。 */
    public record CapturedReference(String citationId,UUID sourceId,UUID chunkId,String revision,String checksum) { }
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
                               String question,Map<String,String> confirmedConditions,RuleCheckRuleTemplate.Condition nextCondition) {
        public Continuation {Objects.requireNonNull(settings);Objects.requireNonNull(entry);Objects.requireNonNull(question);Objects.requireNonNull(nextCondition);confirmedConditions=Map.copyOf(confirmedConditions);}
    }
}
