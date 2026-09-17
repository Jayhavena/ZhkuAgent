package com.zhku.knowledge.rag;

/** 受控生成的最终结论状态。 */
public enum ComposedAnswerStatus {

    /** 通过全部守卫校验，可下发给学生。 */
    ANSWERED,

    /** 任一守卫未通过或模型不可用：拒答并转人工，不返回无出处结论。 */
    DECLINED
}
