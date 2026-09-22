package com.zhku.knowledge.conversation.publicelective;

import java.util.Optional;

/** 只有在读到"选课调整办理"的当前已发布证据时，该主题才允许给出确定性结论。 */
@FunctionalInterface
public interface AdjustmentEvidenceFinder {

    Optional<AdjustmentEvidence> find();
}
