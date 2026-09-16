package com.zhku.knowledge.governance.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import java.util.Map;

/** 仲园 Agent 的本机资料治理专用启动入口。 */
@SpringBootApplication(
        scanBasePackageClasses = ZhkuLocalAdminController.class,
        exclude = DataSourceAutoConfiguration.class
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
