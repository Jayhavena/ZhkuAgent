package com.zhku.knowledge.feedback;

/** 匿名反馈持久化边界；返回值让服务在并发写入时也能可靠地识别重复提交。 */
public interface FeedbackRepository {

    boolean saveIfAbsent(AnonymousFeedbackEvent event);
}
