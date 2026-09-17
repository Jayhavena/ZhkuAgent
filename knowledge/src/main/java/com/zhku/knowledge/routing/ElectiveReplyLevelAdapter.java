package com.zhku.knowledge.routing;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 既有回答服务（澄清 / 当期通知 / 规则）与五级路由之间的语义适配层。
 *
 * <p>只做语义映射，不调用会话存储、不生成内容：让"能不能作答"的判定留在路由层，
 * 会话生命周期（过期/取消/非法）交会话门面——两类问题混在一起会导致
 * 会话状态异常被误当成"问题不可作答"。</p>
 *
 * <p>映射规则：
 * <ul>
 *   <li>{@code ANSWER} → 命中，引用标识取服务端已有来源标识（非模型路径，无伪造风险）；</li>
 *   <li>{@code NEED_CLARIFICATION} → 命中（第一级优先）；</li>
 *   <li>{@code EVIDENCE_UNAVAILABLE} → 命中但必须转人工：**不可回落给后级**，
 *       否则当期通知缺失时旧手册会被检索级拿来补答；</li>
 *   <li>{@code OUT_OF_SCOPE}、{@code SESSION_*} → 不命中，交由下一级/会话门面。</li>
 * </ul>
 */
public final class ElectiveReplyLevelAdapter {

    private ElectiveReplyLevelAdapter() {
    }

    public static Optional<LevelAnswer> toLevelAnswer(ElectiveRetakeReply reply) {
        Objects.requireNonNull(reply, "回复不能为空");
        return switch (reply.type()) {
            case ANSWER -> Optional.of(new LevelAnswer(
                    renderAnswer(reply),
                    List.of(reply.answer().sourceId().toString()),
                    false
            ));
            case NEED_CLARIFICATION -> Optional.of(new LevelAnswer(reply.message(), List.of(), false));
            case EVIDENCE_UNAVAILABLE -> Optional.of(new LevelAnswer(reply.message(), List.of(), true));
            case OUT_OF_SCOPE, SESSION_EXPIRED, SESSION_CANCELLED, SESSION_INVALID -> Optional.empty();
        };
    }

    /** 回答卡正文优先（结论 + 依据），保证学生看到"结论 + 边界"而不是只有结论。 */
    private static String renderAnswer(ElectiveRetakeReply reply) {
        ElectiveRetakeAnswer answer = reply.answer();
        if (answer == null) {
            return reply.message();
        }
        String conclusion = answer.conclusion() == null ? "" : answer.conclusion();
        String basis = answer.basis() == null ? "" : answer.basis();
        return basis.isBlank() ? conclusion : conclusion + "（" + basis + "）";
    }
}
