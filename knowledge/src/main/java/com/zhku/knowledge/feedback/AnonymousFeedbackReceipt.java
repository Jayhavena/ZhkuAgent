package com.zhku.knowledge.feedback;

/** 面向调用方的最小回执，刻意不含匿名散列、去重键和逐条事件标识。 */
public record AnonymousFeedbackReceipt(boolean accepted, boolean duplicate) {
}
