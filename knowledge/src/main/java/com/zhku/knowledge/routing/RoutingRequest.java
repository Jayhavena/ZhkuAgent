package com.zhku.knowledge.routing;

import java.util.Objects;

/**
 * 一轮提问的路由请求。
 *
 * <p>刻意只携带消息文本：路由判定不依赖会话内部状态，会话状态的解读
 * （例如是否存在待澄清）由各路由级别自行完成，避免编排器与 conversation 包耦合。</p>
 */
public record RoutingRequest(String message) {

    private static final int MAX_MESSAGE_LENGTH = 500;

    public RoutingRequest {
        Objects.requireNonNull(message, "消息不能为空");
        message = message.strip();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("消息不能超过 500 个字符");
        }
    }
}
