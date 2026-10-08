package com.zhku.knowledge.governance.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

import java.util.Map;

/** 仲园 Agent 的本机资料治理专用启动入口。 */
@SpringBootApplication(
        scanBasePackageClasses = ZhkuLocalAdminController.class,
        // 新依赖不代表旧治理台获得受控账号能力；这里保留原本机入口行为。
        exclude = { DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class }
)
public class ZhkuLocalAdminApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                ZhkuLocalAdminApplication.class
        );
        application.setAdditionalProfiles("zhku-local-admin");
        application.setDefaultProperties(Map.of(
                "server.port", "8124",
                "zhku.local-admin.enabled", "true"
        ));
        application.run(args);
    }
}
