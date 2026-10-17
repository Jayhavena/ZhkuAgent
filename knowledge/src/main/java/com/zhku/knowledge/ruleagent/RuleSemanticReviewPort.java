package com.zhku.knowledge.ruleagent;

import java.time.Duration;

/** 独立核验只有数据判断能力；在线工厂须显式装配，不能用默认放行实现兜底。 */
public interface RuleSemanticReviewPort extends AutoCloseable {
    RulePublicationContract.SemanticAssessment assess(RulePublicationContract.SemanticInput input,Duration timeout);
    default void captureInput(RuleAgentRunner.Input input,java.util.List<RuleAgentEvidenceTools.VerifiedFact> facts,RuleFactCapabilityCatalogue catalogue) {}
    default void close() {}
}
