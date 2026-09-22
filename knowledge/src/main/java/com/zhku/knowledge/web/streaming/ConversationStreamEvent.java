package com.zhku.knowledge.web.streaming;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.ConversationStatus;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.serviceguide.ServiceGuideCard;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;

import java.util.List;
import java.util.UUID;

/** SSE 事件的稳定载荷；仅在既有 HTTP 回复上追加会话快照，不能重定义业务结论。 */
public record ConversationStreamEvent(
        UUID sessionId,
        Long revision,
        ConversationStatus conversationStatus,
        ElectiveRetakeReplyType type,
        String message,
        LocalPreviewConversationFacade.ConversationAnswer answer,
        AnswerRoute route,
        boolean handoffRequired,
        List<String> citationIds,
        List<LocalPreviewAnswerChain.CitationView> citations,
        ServiceGuideCard guideCard
) {

    public ConversationStreamEvent {
        citationIds = List.copyOf(citationIds);
        citations = List.copyOf(citations);
    }

    static ConversationStreamEvent accepted(UUID sessionId, ConversationState state) {
        return new ConversationStreamEvent(
                sessionId,
                revisionOf(state),
                statusOf(state),
                null,
                null,
                null,
                null,
                false,
                List.of(),
                List.of(),
                null
        );
    }

    static ConversationStreamEvent reply(
            UUID sessionId,
            ConversationState state,
            LocalPreviewConversationFacade.ConversationReply reply
    ) {
        return new ConversationStreamEvent(
                sessionId,
                revisionOf(state),
                statusOf(state),
                reply.type(),
                reply.message(),
                reply.answer(),
                reply.route(),
                reply.handoffRequired(),
                reply.citationIds(),
                reply.citations(),
                reply.guideCard()
        );
    }

    private static Long revisionOf(ConversationState state) {
        return state == null ? null : state.revision();
    }

    private static ConversationStatus statusOf(ConversationState state) {
        return state == null ? null : state.status();
    }
}
