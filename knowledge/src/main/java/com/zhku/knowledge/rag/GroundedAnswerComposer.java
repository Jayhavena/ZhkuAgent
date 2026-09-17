package com.zhku.knowledge.rag;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 受控回答生成器。
 *
 * <p>模型只能接收当轮获准片段与不可变系统约束；输出必须经
 * {@link AnswerEvidenceGuard} 校验后才可下发。超时、限流、格式非法、
 * 无证据、低分一律安全降级为"拒答 + 转人工"，不返回无出处结论。</p>
 */
public final class GroundedAnswerComposer {

    /** 不可变系统约束：定义模型能做什么、不能做什么。修改即基线变更，须登记证据账本。 */
    static final String SYSTEM_CONSTRAINTS =
            "你是校园教务问答助手。只能依据提供的获准证据回答；"
                    + "不得推断个人资格或办理结果；证据不足时如实说明并建议人工渠道；"
                    + "输出 JSON：answer、uncertainty、citationIds、handoffRequired。";

    private static final String DECLINE_MESSAGE =
            "当前没有可核验的已发布证据支持该问题的结论，请通过人工渠道咨询教务部门。";

    private final ModelChatClient chatClient;
    private final AnswerEvidenceGuard guard;

    public GroundedAnswerComposer(ModelChatClient chatClient, AnswerEvidenceGuard guard) {
        this.chatClient = Objects.requireNonNull(chatClient, "模型调用端不能为空");
        this.guard = Objects.requireNonNull(guard, "证据守卫不能为空");
    }

    public ComposedAnswer compose(String question, List<ApprovedEvidence> approved) {
        Objects.requireNonNull(question, "问题不能为空");
        Objects.requireNonNull(approved, "获准证据列表不能为空");
        if (approved.isEmpty()) {
            // 无获准证据直接拒答：不发起模型调用，杜绝"凭印象回答"。
            return ComposedAnswer.declined(DECLINE_MESSAGE);
        }

        ModelResponse response;
        try {
            response = chatClient.call(new ModelRequest(SYSTEM_CONSTRAINTS, question, approved));
        } catch (ModelCallTimeoutException | ModelCallRateLimitException | ModelOutputFormatException e) {
            return ComposedAnswer.declined(DECLINE_MESSAGE);
        }

        Optional<String> rejection = guard.verify(response, approved);
        if (rejection.isPresent()) {
            return ComposedAnswer.declined(DECLINE_MESSAGE);
        }
        return ComposedAnswer.answered(response.answer(), response.citationIds(), response.handoffRequired());
    }
}
