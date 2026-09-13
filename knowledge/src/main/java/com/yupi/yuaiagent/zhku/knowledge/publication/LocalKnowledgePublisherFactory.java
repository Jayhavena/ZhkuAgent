package com.yupi.yuaiagent.zhku.knowledge.publication;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 本地开发命令行共用的数据库连接工厂。
 *
 * <p>将连接配置集中在此处，避免导入、检索和问答命令因默认值不同而操作不同数据库。</p>
 */
public final class LocalKnowledgePublisherFactory {

    private LocalKnowledgePublisherFactory() {
    }

    public static ReviewedKnowledgePublisher create() {
        return new ReviewedKnowledgePublisher(createDataSource());
    }

    /** 本机管理入口与问答预览必须复用同一套受控数据库连接配置。 */
    public static DriverManagerDataSource createDataSource() {
        return new DriverManagerDataSource(
                databaseValue(
                        "zhku.db.url",
                        "ZHKU_DB_URL",
                        "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
                ),
                databaseValue("zhku.db.user", "ZHKU_DB_USER", "zhkuagent"),
                databaseValue("zhku.db.password", "ZHKU_DB_PASSWORD", null)
        );
    }

    private static String databaseValue(
            String systemProperty,
            String environmentVariable,
            String defaultValue
    ) {
        String fromSystemProperty = System.getProperty(systemProperty);
        if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
            return fromSystemProperty;
        }
        String fromEnvironment = System.getenv(environmentVariable);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        throw new IllegalArgumentException(
                "缺少数据库配置：请设置系统属性 " + systemProperty
                        + " 或环境变量 " + environmentVariable
        );
    }
}
