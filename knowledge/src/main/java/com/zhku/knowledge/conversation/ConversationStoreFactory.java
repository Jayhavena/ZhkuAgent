package com.zhku.knowledge.conversation;

import javax.sql.DataSource;
import com.zhku.knowledge.security.TrustedActor;
import java.time.Clock;
import java.util.Objects;

/** 会话存储选择工厂。 */
public final class ConversationStoreFactory {

    private ConversationStoreFactory() {
    }

    public static ConversationStore createOwned(String configuredStore, ConversationJdbcAccess access,
            TrustedActor actor, Clock clock) {
        requireOwnedJdbc(configuredStore);
        return new JdbcConversationStore(access, actor, clock);
    }

    public static ConversationStore createOwned(String configuredStore, ConversationJdbcAccess access,
            TrustedActor actor, Clock clock, ConversationOperationBudget budget) {
        requireOwnedJdbc(configuredStore);
        return new JdbcConversationStore(access, actor, clock, budget);
    }

    private static void requireOwnedJdbc(String configuredStore) {
        // 用户归属必须持久化；缺配置/内存/未知值都不能静默回退单人匿名预览。
        if (!"jdbc".equals(configuredStore)) throw new IllegalArgumentException("受控会话必须显式使用jdbc存储");
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
