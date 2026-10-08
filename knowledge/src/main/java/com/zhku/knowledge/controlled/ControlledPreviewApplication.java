package com.zhku.knowledge.controlled;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** 受控本机入口；不扫描旧匿名Controller、治理或SSE组件。 */
@SpringBootApplication(scanBasePackages = { "com.zhku.knowledge.controlled", "com.zhku.knowledge.security" },
        exclude = { DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class })
public class ControlledPreviewApplication {
    public static SpringApplication application() {
        var application = new SpringApplication(ControlledPreviewApplication.class);
        application.setAdditionalProfiles("zhku-controlled-preview");
        // main、实际HTTP夹具和不同PID探针共用同一工厂，配置错误先于任何资源初始化失败。
        application.addInitializers(context -> ControlledPreviewConfiguration.validateApplicationEnvironment(context.getEnvironment()));
        return application;
    }
    public static void main(String[] args) { application().run(args); }
}
