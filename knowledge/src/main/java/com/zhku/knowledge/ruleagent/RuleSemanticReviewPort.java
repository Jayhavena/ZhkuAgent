package com.zhku.knowledge.ruleagent;

import java.time.Duration;

/** 独立核验只有数据判断能力；供应商与预算装配尚未接线，不能用默认放行实现兜底。 */
public interface RuleSemanticReviewPort {
    RulePublicationContract.SemanticAssessment assess(RulePublicationContract.SemanticInput input,Duration timeout);
}
