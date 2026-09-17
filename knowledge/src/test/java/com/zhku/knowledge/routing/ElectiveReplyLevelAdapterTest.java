package com.zhku.knowledge.routing;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 既有服务回复 → 路由级回答负载的映射测试（三级处理器接线的语义层）。
 *
 * <p>关键取舍：<b>"本主题无有效证据"必须短路为拒答 + 转人工</b>，不能回落给
 * 检索级——否则当期通知缺失时，旧手册会被后级检索拿来补答，正好违反
 * 冻结契约的"严格短路、后级不得覆盖前级"。</p>
 */
class ElectiveReplyLevelAdapterTest {

    private static final UUID SOURCE_ID = UUID.fromString("9a111111-1111-4111-8111-111111111111");

    @Test
    void shouldMapAnswerWithServerSideCitation() {
        Optional<LevelAnswer> answer = ElectiveReplyLevelAdapter.toLevelAnswer(new ElectiveRetakeReply(
                ElectiveRetakeReplyType.ANSWER,
                "退改选办理时间为 2026-09-10 至 2026-09-15。",
                new ElectiveRetakeAnswer("结论", "限制说明", SOURCE_ID, "选课通知", "2026 年第 1 号",
                        "网页快照", "2026-2027-1 学期本科生")
        ));

        assertThat(answer).isPresent();
        // 回答卡正文（结论 + 依据）优先：学生必须同时看到结论与其边界说明。
        assertThat(answer.get().message()).isEqualTo("结论（限制说明）");
        // 非模型路径：引用标识直接用服务端已有来源标识（不发给模型，无伪造风险）。
        assertThat(answer.get().citationIds()).containsExactly(SOURCE_ID.toString());
        assertThat(answer.get().handoffRequired()).isFalse();
    }

    @Test
    void shouldMapClarificationAsShortCircuitRequest() {
        Optional<LevelAnswer> answer = ElectiveReplyLevelAdapter.toLevelAnswer(
                new ElectiveRetakeReply(ElectiveRetakeReplyType.NEED_CLARIFICATION, "请补充你的年级", null));

        assertThat(answer).isPresent();
        assertThat(answer.get().handoffRequired()).isFalse();
    }

    @Test
    void shouldShortCircuitRefusalWhenEvidenceUnavailable() {
        Optional<LevelAnswer> answer = ElectiveReplyLevelAdapter.toLevelAnswer(
                new ElectiveRetakeReply(
                        ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE,
                        "当前没有可确认的有效选课通知，请以教务部门公告为准。",
                        null));

        // 必须短路：否则后级检索会用旧手册补答当期通知问题。
        assertThat(answer).isPresent();
        assertThat(answer.get().handoffRequired()).isTrue();
        assertThat(answer.get().citationIds()).isEmpty();
    }

    @Test
    void shouldMissWhenTopicOutOfScope() {
        assertThat(ElectiveReplyLevelAdapter.toLevelAnswer(
                new ElectiveRetakeReply(ElectiveRetakeReplyType.OUT_OF_SCOPE, "不属于本主题", null)))
                .isEmpty();
    }

    @Test
    void shouldMissOnSessionLifecycleProblemsLeavingThemToConversationFacade() {
        // 会话过期/取消/非法不是"能不能作答"的问题，交会话门面处理，路由继续往下走。
        for (ElectiveRetakeReplyType type : new ElectiveRetakeReplyType[]{
                ElectiveRetakeReplyType.SESSION_EXPIRED,
                ElectiveRetakeReplyType.SESSION_CANCELLED,
                ElectiveRetakeReplyType.SESSION_INVALID}) {
            assertThat(ElectiveReplyLevelAdapter.toLevelAnswer(
                    new ElectiveRetakeReply(type, "会话不可用", null)))
                    .as("会话生命周期问题不应在回答路由层短路：" + type)
                    .isEmpty();
        }
    }
}
