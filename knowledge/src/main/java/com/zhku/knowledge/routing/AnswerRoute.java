package com.zhku.knowledge.routing;

/**
 * 统一回答路由（冻结契约，SYNC-STATUS 第五节）。
 *
 * <p>枚举声明顺序即判定顺序：澄清 → 当期通知 → 高风险规则 → 检索增强 → 拒答/人工。
 * 该顺序与"严格短路"语义已冻结：前级命中即返回，后级不得覆盖前级结论。
 * 新增路由级别只允许追加在末尾并重新评审，不得重排既有顺序。</p>
 */
public enum AnswerRoute {

    /** 存在待澄清事项：先向提问者索取缺失事实，不作任何结论。 */
    AWAITING_CLARIFICATION,

    /** 当期通知命中：动态通知优先于静态规则，避免旧手册被误当作当期办理依据。 */
    CURRENT_NOTICE,

    /** 高风险规则命中：目录中声明为高风险的确定性规则直接给结论。 */
    HIGH_RISK_RULE,

    /** 检索增强：由受控检索 + 生成层在获准证据内作答。 */
    RETRIEVAL_AUGMENTED,

    /** 证据不足：拒答并转人工渠道，不允许无出处结论。 */
    INSUFFICIENT_EVIDENCE
}
