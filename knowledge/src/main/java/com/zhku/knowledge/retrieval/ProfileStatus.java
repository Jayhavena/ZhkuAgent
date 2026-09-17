package com.zhku.knowledge.retrieval;

/**
 * embedding profile 生命周期。
 *
 * <p>BUILDING → ACTIVE → RETIRED 单向流转（回滚时 RETIRED → ACTIVE 为唯一反向通道）；
 * 与 V9 迁移中 embedding_profile.status 的 CHECK 约束一一对应。</p>
 */
public enum ProfileStatus {

    /** 索引重建/评测中，不可用于召回。 */
    BUILDING,

    /** 当前在役，全局唯一。 */
    ACTIVE,

    /** 已退役但保留在回滚窗口内，可用于快速回退。 */
    RETIRED
}
