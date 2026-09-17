package com.zhku.knowledge.web.streaming;

import java.io.IOException;

/** 流传输边界，处理器借此保持可测且不耦合 Spring MVC 的 emitter 生命周期。 */
@FunctionalInterface
public interface ConversationStreamWriter {

    void write(String name, ConversationStreamEvent payload) throws IOException;
}
