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
 * <p>规则级卡的条级定位在 `answer.sourceLocator` 上；需转人工与拒答是不同事实，
 * 因此替身必须使用与真实链路一致的回复类型和引用 ID。</p>
 */
class FacadeAnswerTargetTest {

    @Test
    void shouldCollectClauseLocatorFromRuleLevelAnswerCard() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                ElectiveRetakeReplyType.ANSWER, AnswerRoute.HIGH_RISK_RULE, false,
                new ElectiveRetakeAnswer("结论", "依据", UUID.randomUUID(), "学生手册", "2025版",
                        "PDF 第 77 页，第 9、13 条", "全日制本科生"),
                List.of()));

        ObservedAnswer observed = target.answer("公共选修课选课调整怎么办理");

        assertThat(observed.route()).isEqualTo(AnswerRoute.HIGH_RISK_RULE);
        assertThat(observed.citationLocators()).containsExactly("PDF 第 77 页，第 9、13 条");
        assertThat(observed.handoffRequired()).isFalse();
        assertThat(observed.refused()).isFalse();
    }

    @Test
    void shouldCollectCitationLocatorsFromRetrievalLevelReply() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                ElectiveRetakeReplyType.ANSWER, AnswerRoute.RETRIEVAL_AUGMENTED, false, null,
                List.of(new LocalPreviewAnswerChain.CitationView(
                                "cit-1", "学生手册", "PDF 第 76 页，第 2、4 条", 76),
                        new LocalPreviewAnswerChain.CitationView(
                                "cit-2", "学生手册", "PDF 第 76 页，第 2、4 条", 76))));

        ObservedAnswer observed = target.answer("公共选修课一学期最多修多少学分");

        // 去重后只留一条：同一处被引两次不该影响比对结果。
        assertThat(observed.citationLocators()).containsExactly("PDF 第 76 页，第 2、4 条");
    }

    @Test
    void shouldRecognizeEvidenceRefusalAndHandoffSeparately() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                ElectiveRetakeReplyType.OUT_OF_SCOPE, AnswerRoute.INSUFFICIENT_EVIDENCE,
                true, null, List.of()));

        ObservedAnswer observed = target.answer("公共选修课奖学金怎么评定");

        assertThat(observed.route()).isEqualTo(AnswerRoute.INSUFFICIENT_EVIDENCE);
        assertThat(observed.handoffRequired()).isTrue();
        assertThat(observed.refused()).isTrue();
        assertThat(observed.citationLocators()).isEmpty();
    }

    @Test
    void shouldRecognizeUnavailableEvidenceAsRefusal() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE,
                AnswerRoute.INSUFFICIENT_EVIDENCE, true, null, List.of()));

        ObservedAnswer observed = target.answer("无有效依据的问题");

        assertThat(observed.handoffRequired()).isTrue();
        assertThat(observed.refused()).isTrue();
    }

    @Test
    void shouldNotCountAnAnsweredHandoffAsRefusal() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                ElectiveRetakeReplyType.ANSWER, AnswerRoute.RETRIEVAL_AUGMENTED,
                true, null, List.of(new LocalPreviewAnswerChain.CitationView(
                        "cit-human-check", "学生手册", "PDF 第 76 页", 76))));

        ObservedAnswer observed = target.answer("需人工核验的已答问题");

        assertThat(observed.handoffRequired()).isTrue();
        assertThat(observed.refused()).isFalse();
    }

    @Test
    void shouldNotCountSessionFailureAsContentRefusal() {
        FacadeAnswerTarget target = new FacadeAnswerTarget(question -> reply(
                ElectiveRetakeReplyType.SESSION_EXPIRED, AnswerRoute.INSUFFICIENT_EVIDENCE,
                true, null, List.of()));

        assertThat(target.answer("过期会话").refused()).isFalse();
    }

    private static LocalPreviewConversationFacade.ConversationReply reply(
            ElectiveRetakeReplyType type,
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
                type, "消息", card, route, handoffRequired,
                citations.stream().map(LocalPreviewAnswerChain.CitationView::citationId).toList(),
                citations);
    }
}
