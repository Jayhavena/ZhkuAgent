package com.zhku.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 独立v2身份边界；实际Application和数据库装配另行验收。 */
@Configuration(proxyBeanMethods=false)
@EnableWebSecurity
@Profile("zhku-rule-check-preview")
public class RuleCheckPreviewSecurityConfiguration {
    public static final String PREFIX="/zhku/controlled-preview/v2";
    public static final String COOKIE_NAME="ZHKU_RULE_CHECK_SESSION";
    public static final String COOKIE_PATH="/api"+PREFIX;
    @Bean static BeanFactoryPostProcessor ruleCheckSecurityGuard(Environment environment) {
        validateEnvironment(environment); return factory->{};
    }
    public static void validateEnvironment(Environment environment) {
        var profiles=environment.getActiveProfiles();
        if (profiles.length!=1 || !"zhku-rule-check-preview".equals(profiles[0])
                || !"true".equals(environment.getProperty("zhku.rule-check-preview.enabled")))
            throw new IllegalArgumentException("查证身份要求独立profile与显式开关");
        new com.zhku.knowledge.web.LocalPreviewBindingGuard(environment.getProperty("server.address"));
        // 必须检查实际配置；默认值只在守卫中存在并不会替应用创建正确cookie。
        String[][] required={{"server.servlet.context-path","/api"},
                {"server.servlet.session.persistent","false"},{"server.servlet.session.tracking-modes","cookie"},
                {"server.servlet.session.cookie.name",COOKIE_NAME},{"server.servlet.session.cookie.path",COOKIE_PATH},
                {"server.servlet.session.cookie.http-only","true"},
                {"server.servlet.session.cookie.same-site","strict"}};
        for (var setting:required) if (!setting[1].equals(environment.getProperty(setting[0])))
            throw new IllegalArgumentException("查证会话配置必须显式绑定独立cookie");
        // 本地HTTP可显式关闭Secure；TLS部署显式启用，禁止遗漏配置。
        if (!java.util.Set.of("true","false").contains(environment.getProperty("server.servlet.session.cookie.secure","")))
            throw new IllegalArgumentException("查证Cookie必须显式声明Secure");
        String timeout=environment.getProperty("server.servlet.session.timeout");
        try {
            if (timeout==null || !java.time.Duration.ofMinutes(15).equals(org.springframework.boot.convert.DurationStyle.detectAndParse(timeout)))
                throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("查证会话闲置期固定15分钟");
        }
        for (String forbidden:new String[]{"server.servlet.session.cookie.domain","server.servlet.session.cookie.max-age",
                "server.servlet.session.store-dir","spring.session.store-type"})
            if(environment.getProperty(forbidden)!=null)throw new IllegalArgumentException("查证身份禁止域扩展或持久化");
        for (String forbidden:new String[]{"zhku.controlled-preview.enabled","zhku.controlled-preview.conversations.enabled",
                "zhku.local-preview.enabled","zhku.local-admin.enabled","zhku.rag.enabled","zhku.streaming.enabled",
                "zhku.campus-agent.enabled","zhku.campus-agent.archive.enabled"})
            if(!"false".equals(environment.getProperty(forbidden,"false")))
                throw new IllegalArgumentException("查证身份禁止其他预览或任务入口混用");
    }
    @Bean AuthenticationProvider ruleCheckAuthenticationProvider(PreviewIdentityRepository repository,Clock clock) {
        return new PreviewSecurityConfiguration.Provider(repository,clock);
    }
    @Bean SecurityFilterChain ruleCheckSecurityFilterChain(HttpSecurity http,AuthenticationProvider ruleCheckAuthenticationProvider,
            PreviewIdentityRepository repository,ObjectMapper mapper,Environment environment) throws Exception {
        var persistent=new PersistentPreviewLogin(repository,Boolean.parseBoolean(environment.getProperty("server.servlet.session.cookie.secure")));
        return PreviewSecurityConfiguration.buildChain(http,ruleCheckAuthenticationProvider,repository,mapper,PREFIX,COOKIE_NAME,false,true,persistent);
    }
}
