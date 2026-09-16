package com.zhku.knowledge.conversation.publicelective;

import java.util.Optional;

/** 只有读取到当前已发布证据时，学分上限规则才允许输出确定性结论。 */
@FunctionalInterface
public interface CreditLimitEvidenceFinder {

    Optional<CreditLimitEvidence> find();
}
