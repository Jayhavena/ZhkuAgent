package com.zhku.knowledge.eval;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真链路适配器的映射逻辑（离线替身测死，不连库）。
 *
 * <p>重点两条：① **规则级卡的条级定位**（在 `answer.sourceLocator` 上）必须被采集——
 * 只读 `citations` 会让规则级用例永远"引用未命中"；② 拒答/转人工以 `handoffRequired` 为准。</p>
 */
class FacadeAnswerTargetTest {

    @Test
    void shouldCollectClauseLocatorFromRuleLevelAnswerCard() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                AnswerRoute.HIGH_RISK_RULE, false,
                new ElectiveRetakeAnswer("结论", "依据", UUID.randomUUID(), "学生手册", "2025版",
                        "PDF 第 77 页，第 9、13 条", "全日制本科生"),
                List.of()));

        ObservedAnswer observed = target.answer("公共选修课选课调整怎么办理");

        assertThat(observed.route()).isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(observed.citationLocators()).containsExactly("PDF 第 77 页，第 9、13 条");
        assertThat(observed.refused()).isFalse();
    }

    @Test
    void shouldCollectCitationLocatorsFromRetrievalLevelReply() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                AnswerRoute.RETRIEVAL_AUGMENTED, false, null,
                List.of(new LocalPreviewAnswerChain.CitationView(
                                "cit-1", "学生手册", "PDF 第 76 页，第 2、4 条", 76),
                        new LocalPreviewAnswerChain.CitationView(
                                "cit-2", "学生手册", "PDF 第 76 页，第 2、4 条", 76))));

        ObservedAnswer observed = target.answer("公共选修课一学期最多修多少学分");

        // 去重后只留一条：同一处被引两次不该影响比对结果。
        assertThat(observed.citationLocators()).containsExactly("PDF 第 76 页，第 2、4 条");
    }

    @Test
    void shouldTreatHandoffAsRefusal() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                AnswerRoute.INSUFFICIENT_EVIDENCE, true, null, List.of()));

        ObservedAnswer observed = target.answer("公共选修课奖学金怎么评定");

        assertThat(observed.route()).isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
        assertThat(observed.refused()).isTrue();
        assertThat(observed.citationLocators()).isEmpty();
    }

    private static LocalPreviewConversationFacade.ConversationReply reply(
            AnswerRoute route,
            boolean handoffRequired,
            ElectiveRetakeAnswer answer,
            List<LocalPreviewAnswerChain.CitationView> citations) {
        LocalPreviewConversationFacade.ConversationAnswer card = answer == null
                ? null
                : new LocalPreviewConversationFacade.ConversationAnswer(
                        answer.conclusion(), answer.basis(), answer.sourceId(), answer.sourceTitle(),
                        answer.sourceVersion(), answer.sourceLocator(), answer.applicableScope());
        return new LocalPreviewConversationFacade.ConversationReply(
                ElectiveRetakeReplyType.ANSWER, "消息", card, route, handoffRequired,
                List.of(), citations);
    }
}
