package com.zhku.knowledge.conversation;

import javax.sql.DataSource;
import java.util.Objects;

/** 会话存储选择工厂。 */
public final class ConversationStoreFactory {

    private ConversationStoreFactory() {
    }

    /**
     * 按未来 {@code zhku.conversation.store} 配置值选择存储实现。
     *
     * <p>配置接线刻意不在此处完成：当前预览仍使用内存存储，直接切换 Bean 会与共享配置文件的
     * 并行改动冲突。后续接线必须先登记文件占用并独立提交。</p>
     */
    public static ConversationStore create(String configuredStore, DataSource dataSource) {
        if (configuredStore == null || configuredStore.isBlank()) {
            return new InMemoryConversationStore();
        }
        if ("jdbc".equals(configuredStore)) {
            return new JdbcConversationStore(Objects.requireNonNull(dataSource, "数据源不能为空"));
        }
        throw new IllegalArgumentException(
                "不支持的 zhku.conversation.store: " + configuredStore
        );
    }
}
