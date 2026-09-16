package com.zhku.knowledge.answer;

/** 有依据问答的两种唯一结果，避免调用方把“无资料”误当成普通回答。 */
public enum AnswerMode {
    /** 基于检索到的资料生成回答。 */
    GROUNDED,

    /** 资料不足，无法生成有依据的回答。 */
    INSUFFICIENT_EVIDENCE
}
