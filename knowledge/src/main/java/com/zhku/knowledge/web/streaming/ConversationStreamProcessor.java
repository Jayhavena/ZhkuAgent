package com.zhku.knowledge.web.streaming;

import com.zhku.knowledge.conversation.ConversationState;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * 把一轮完整的会话作答投影为固定 SSE 事件序列。
 *
 * <p>它只调用既有门面并读取快照，不能直接写入存储；否则流式分支会绕过同步接口已有的
 * revision 与路由保护，造成同一消息在两种传输方式下语义不一致。</p>
 */
public final class ConversationStreamProcessor {

    private final LocalPreviewConversationFacade facade;
    private final ConversationStore store;
    private final Clock clock;

    public ConversationStreamProcessor(
            LocalPreviewConversationFacade facade,
            ConversationStore store,
            Clock clock
    ) {
        this.facade = Objects.requireNonNull(facade, "会话门面不能为空");
        this.store = Objects.requireNonNull(store, "会话存储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public void process(UUID sessionId, String message, ConversationStreamWriter writer)
            throws IOException {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        Objects.requireNonNull(message, "消息不能为空");
        Objects.requireNonNull(writer, "流事件写入器不能为空");

        writer.write("accepted", ConversationStreamEvent.accepted(sessionId, currentState(sessionId)));
        LocalPreviewConversationFacade.ConversationReply reply = facade.sendMessage(sessionId, message);
        ConversationStreamEvent replied = ConversationStreamEvent.reply(
                sessionId,
                currentState(sessionId),
                reply
        );
        writer.write("reply", replied);
        writer.write("complete", replied);
    }

    private ConversationState currentState(UUID sessionId) {
        return store.find(sessionId, clock).state();
    }
}
