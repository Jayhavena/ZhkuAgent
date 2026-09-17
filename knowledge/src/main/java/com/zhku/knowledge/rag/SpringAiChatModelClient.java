package com.zhku.knowledge.rag;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;

/**
 * Spring AI 对话模型适配器（计划任务 3 的 Chat 侧）。
 *
 * <p>把 {@link ModelRequest}（不可变系统约束 + 当轮获准证据）翻译成 Spring AI 的
 * system/user 消息，再沿用与自实现客户端同一份 {@link ModelOutputParser} 解析结构化输出。
 * 两条链路共用解析规则，降级判定才不会随实现漂移。</p>
 *
 * <p>异常映射：Spring AI 的超时/限流/其他失败统一翻译为生成层已定义的
 * {@link ModelCallTimeoutException} / {@link ModelCallRateLimitException} /
 * {@link ModelCallFailedException}，由 {@link GroundedAnswerComposer} 降级。</p>
 */
public final class SpringAiChatModelClient implements ModelChatClient {

    private final ChatModel chatModel;
    private final String profileName;

    public SpringAiChatModelClient(ChatModel chatModel, String profileName) {
        this.chatModel = Objects.requireNonNull(chatModel, "对话模型不能为空");
        this.profileName = Objects.requireNonNull(profileName, "profile 名称不能为空");
    }

    @Override
    public ModelResponse call(ModelRequest request) {
        Objects.requireNonNull(request, "模型请求不能为空");
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(request.systemConstraints()),
                new UserMessage(buildUserMessage(request))
        ));

        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
        String content = response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new ModelOutputFormatException("模型响应为空（profile=" + profileName + "）");
        }
        return ModelOutputParser.parse(content);
    }

    /** 证据以 [citationId] 前缀列出：模型只能回填这些标识，不能自造。 */
    private static String buildUserMessage(ModelRequest request) {
        StringBuilder builder = new StringBuilder(request.question()).append("\n\n获准证据：\n");
        for (ApprovedEvidence evidence : request.evidence()) {
            builder.append('[').append(evidence.citationId()).append("] ")
                    .append(evidence.text()).append('\n');
        }
        return builder.toString();
    }

    private RuntimeException translate(RuntimeException exception) {
        // Spring AI 把底层失败包进自己的异常，因此要看整条 cause 链而不是只看最外层：
        // 只判最外层会把超时误报成"其他失败"，诊断口径就丢了。
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof java.net.http.HttpTimeoutException) {
                return new ModelCallTimeoutException("模型调用超时（profile=" + profileName + "）", exception);
            }
        }
        String lowered = messagesOf(exception).toLowerCase(java.util.Locale.ROOT);
        if (lowered.contains("429") || lowered.contains("rate limit") || lowered.contains("too many requests")) {
            return new ModelCallRateLimitException("模型限流（profile=" + profileName + "）", exception);
        }
        if (lowered.contains("timeout") || lowered.contains("timed out")) {
            return new ModelCallTimeoutException("模型调用超时（profile=" + profileName + "）", exception);
        }
        return new ModelCallFailedException("模型调用失败（profile=" + profileName + "）", exception);
    }

    private static String messagesOf(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            builder.append(current.getMessage()).append(' ');
        }
        return builder.toString();
    }
}
