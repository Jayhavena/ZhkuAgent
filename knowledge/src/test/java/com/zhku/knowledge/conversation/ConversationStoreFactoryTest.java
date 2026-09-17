package com.zhku.knowledge.conversation;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 存储选择必须显式启用 JDBC，避免未接线时破坏当前内存预览。 */
class ConversationStoreFactoryTest {

    private static final DataSource DATA_SOURCE = new DriverManagerDataSource();

    @Test
    void shouldUseInMemoryStoreWhenZhkuConversationStoreIsUnset() {
        assertThat(create(null)).isInstanceOf(InMemoryConversationStore.class);
        assertThat(create(" ")).isInstanceOf(InMemoryConversationStore.class);
    }

    @Test
    void shouldUseJdbcStoreWhenZhkuConversationStoreIsJdbc() {
        assertThat(create("jdbc")).isInstanceOf(JdbcConversationStore.class);
    }

    @Test
    void shouldRejectUnknownZhkuConversationStoreValue() {
        assertThatIllegalArgumentException().isThrownBy(() -> create("redis"))
                .withMessage("不支持的 zhku.conversation.store: redis");
    }

    private static ConversationStore create(String configuredStore) {
        try {
            Class<?> factory = Class.forName(
                    "com.zhku.knowledge.conversation.ConversationStoreFactory"
            );
            Method create = factory.getDeclaredMethod("create", String.class, DataSource.class);
            return ConversationStore.class.cast(create.invoke(null, configuredStore, DATA_SOURCE));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new AssertionError("会话存储工厂调用失败", cause);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("必须提供 ConversationStoreFactory", exception);
        }
    }
}
