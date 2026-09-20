package com.zhku.knowledge.publication;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 数据库密码只能由进程环境提供，避免构建日志或启动命令泄露系统属性中的明文。 */
class LocalKnowledgePublisherFactoryTest {

    private static final String PASSWORD_PROPERTY = "zhku.db.password";
    private static final String REJECTION_MESSAGE = "密码不接受系统属性注入，请使用环境变量 ZHKU_DB_PASSWORD";

    @AfterEach
    void clearPasswordSystemProperty() {
        System.clearProperty(PASSWORD_PROPERTY);
    }

    @Test
    void shouldRejectDatabasePasswordSystemPropertyAtRuntime() {
        System.setProperty(PASSWORD_PROPERTY, "test-only-system-property-secret");

        assertThatThrownBy(LocalKnowledgePublisherFactory::createDataSource)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(REJECTION_MESSAGE);
    }

    @Test
    void shouldRejectDatabasePasswordSystemPropertyInTestConfiguration() {
        System.setProperty(PASSWORD_PROPERTY, "test-only-system-property-secret");

        assertThatThrownBy(ZhkuDatabaseTestConfig::databasePassword)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(REJECTION_MESSAGE);
    }
}
