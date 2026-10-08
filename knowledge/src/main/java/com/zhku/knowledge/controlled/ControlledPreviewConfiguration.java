package com.zhku.knowledge.controlled;

import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.web.LocalPreviewBindingGuard;
import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.convert.DurationStyle;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class ControlledPreviewConfiguration {
    @Bean static BeanFactoryPostProcessor controlledPreviewEarlyGuard(Environment environment) {
        // 直接装配也要守边界，不能只依赖main的initializer。
        validateApplicationEnvironment(environment);
        return factory -> {
            if("true".equals(environment.getProperty("zhku.controlled-preview.conversations.enabled"))
                    && !factory.containsBeanDefinition("conversationJdbcAccess"))
                throw new IllegalArgumentException("3B开关要求显式受控会话装配");
            // Bean定义已注册而singleton尚未创建；缺生产模型条件不能先连接数据库/占宿主锁。
            if (factory.containsBeanDefinition("controlledCampusModelTransport"))
                ControlledCampusAgentConfiguration.validateProductionModel(environment);
        };
    }

    public static void validateEnvironment(Environment environment) {
        // 保留4A独立守卫契约；开启3B必须走Application的明确条件装配入口。
        validateEnvironment(environment,false);
    }
    static void validateApplicationEnvironment(Environment environment) {
        if("true".equals(environment.getProperty("zhku.controlled-preview.conversations.enabled")))
            ControlledConversationConfiguration.validateEnvironment(environment);
        else validateEnvironment(environment);
    }
    static void validateEnvironment(Environment environment,boolean conversationsAllowed) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (!profiles.contains("zhku-controlled-preview") || profiles.contains("zhku-local-preview")
                || profiles.contains("zhku-local-admin")
                || !"true".equals(environment.getProperty("zhku.controlled-preview.enabled")))
            throw new IllegalArgumentException("受控预览要求独立profile与显式开关");
        new LocalPreviewBindingGuard(environment.getProperty("server.address"));
        // 登录会话只有内存Cookie路径；配置覆盖不能把它变成跨入口凭据、磁盘恢复或URL凭据。
        String[][] required = {
                {"server.servlet.context-path", "/api"},
                {"server.servlet.session.persistent", "false"},
                {"server.servlet.session.tracking-modes", "cookie"},
                {"server.servlet.session.cookie.name", "ZHKU_CONTROLLED_SESSION"},
                {"server.servlet.session.cookie.path", "/api/zhku/controlled-preview/v1"},
                {"server.servlet.session.cookie.http-only", "true"},
                {"server.servlet.session.cookie.secure", "false"},
                {"server.servlet.session.cookie.same-site", "strict"}};
        for (String[] setting : required) if (!setting[1].equals(environment.getProperty(setting[0], setting[1])))
            throw new IllegalArgumentException("受控登录会话配置不可覆盖");
        if (!Duration.ofMinutes(15).equals(DurationStyle.detectAndParse(
                environment.getProperty("server.servlet.session.timeout", "15m"))))
            throw new IllegalArgumentException("受控登录会话闲置期固定15分钟");
        for (String forbidden : new String[] { "server.servlet.session.cookie.domain", "server.servlet.session.cookie.max-age",
                "server.servlet.session.store-dir", "spring.session.store-type" })
            if (environment.getProperty(forbidden) != null)
                throw new IllegalArgumentException("受控登录会话禁止域扩展或持久化");
        for (String forbidden : new String[] { "zhku.local-preview.enabled", "zhku.local-admin.enabled",
                "zhku.rag.enabled", "zhku.streaming.enabled" }) {
            if (!"false".equals(environment.getProperty(forbidden, "false")))
                throw new IllegalArgumentException("4A受控预览禁止匿名、治理、RAG、SSE和业务会话混用");
        }
        String conversationSwitch=environment.getProperty("zhku.controlled-preview.conversations.enabled","false");
        if(!"false".equals(conversationSwitch) && !(conversationsAllowed && "true".equals(conversationSwitch)))
            throw new IllegalArgumentException("业务会话开关仅允许明确3B条件装配");
        if (!"true".equals(environment.getProperty("zhku.campus-agent.enabled"))
                || !"true".equals(environment.getProperty("zhku.campus-agent.archive.enabled")))
            throw new IllegalArgumentException("受控校园任务必须显式启用Agent与归档");
        String username = environment.getProperty("zhku.db.username");
        if (username == null || !username.matches("[A-Za-z_][A-Za-z0-9_]{0,62}"))
            throw new IllegalArgumentException("受控预览要求显式数据库用户名");
        String alias = environment.getProperty("zhku.db.user");
        if (alias != null && !username.equals(alias))
            throw new IllegalArgumentException("数据库用户名别名不可指向其他身份");
        try {
            String configured = environment.getProperty("zhku.db.url");
            if (configured == null || !configured.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI target = URI.create(configured.substring(5));
            if (!"127.0.0.1".equals(target.getHost()) || target.getPort() < 1024 || target.getPort() > 65535
                    || target.getPort() == 54329 || target.getPath() == null
                    || !target.getPath().matches("/[a-z][a-z0-9_]{0,62}") || target.getUserInfo() != null
                    || target.getQuery() != null || target.getFragment() != null) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            // URI解析异常可能含原地址甚至凭据；对外只给有限消息，不保留cause。
            throw new IllegalArgumentException("受控预览要求显式回环专用数据库地址，禁止凭据和查询参数");
        }
        for (String override : new String[] { "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.flyway.url", "spring.flyway.user", "spring.flyway.password" }) {
            if (environment.getProperty(override) != null)
                throw new IllegalArgumentException("受控数据库目标与凭据不接受其他装配覆盖");
        }
        if (environment instanceof org.springframework.core.env.ConfigurableEnvironment configured) {
            for (var source : configured.getPropertySources()) {
                // SystemEnvironment会把合法ZHKU_DB_PASSWORD映射为zhku.db.password；只拒绝真实配置键，
                // 不从聚合configurationProperties反推来源，避免把进程凭据误判成配置覆盖。
                if (source instanceof org.springframework.core.env.EnumerablePropertySource<?> enumerable
                        && !(source instanceof org.springframework.core.env.SystemEnvironmentPropertySource)) {
                    for (String key : enumerable.getPropertyNames())
                        if (key.equals("zhku.db.password") || key.equals("ZHKU_DB_PASSWORD"))
                            throw new IllegalArgumentException("受控数据库密码仅来自进程环境");
                }
            }
        }
    }

    @Bean public DataSource controlledDataSource(Environment environment) {
        validateApplicationEnvironment(environment);
        if (System.getProperty("ZHKU_DB_PASSWORD") != null)
            throw new IllegalArgumentException("数据库密码仅通过进程环境继承");
        String password = System.getenv("ZHKU_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("缺少数据库环境凭据");
        var source = new DriverManagerDataSource(environment.getProperty("zhku.db.url"),
                environment.getProperty("zhku.db.username"), password);
        var settings = new Properties();
        settings.setProperty("connectTimeout", "1"); settings.setProperty("socketTimeout", "2");
        settings.setProperty("cancelSignalTimeout", "1");
        source.setConnectionProperties(settings);
        return source;
    }
    @Bean(initMethod = "migrate") public Flyway controlledMigration(DataSource controlledDataSource) {
        return Flyway.configure().dataSource(controlledDataSource).locations("classpath:db/migration")
                .outOfOrder(false).load();
    }
    @Bean(destroyMethod = "close") public PreviewIdentityRepository previewIdentityRepository(
            DataSource controlledDataSource, Flyway controlledMigration) {
        // 迁移完成才构造身份边界；共用显式数据目标，不创建第二身份库。
        return new PreviewIdentityRepository(controlledDataSource);
    }
    @Bean public Clock previewIdentityClock() { return Clock.systemUTC(); }

    @Bean public Jackson2ObjectMapperBuilderCustomizer controlledStrictRequests() {
        // 仅受控Application生效；body中身份/Session关联等未知字段不能被默默忽略。
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
