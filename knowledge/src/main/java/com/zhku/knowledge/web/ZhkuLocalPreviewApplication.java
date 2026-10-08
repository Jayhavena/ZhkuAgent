package com.zhku.knowledge.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import com.zhku.knowledge.rag.RagProviderConfiguration;

/**
 * 仲园 Agent 的本机问答预览专用启动入口。
 *
 * <p>它只扫描本机预览所需的校园问答组件，知识模块不引入模型依赖，验证链路不需要模型密钥。</p>
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.zhku.knowledge.web",
                "com.zhku.knowledge.feedback"
        },
        // Security依赖服务新受控入口；旧单人入口不能因此生成随机账号或默认登录跳转。
        exclude = { DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class }
)
// 预览只扫描 web/feedback；RAG 配置必须显式导入，内部开关仍保证默认不建模型 bean。
@Import(RagProviderConfiguration.class)
public class ZhkuLocalPreviewApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                ZhkuLocalPreviewApplication.class
        );
        // 专用入口必须自动激活本机限制，避免漏传 Profile 后接口被错误地理解为已启用。
        application.setAdditionalProfiles("zhku-local-preview");
        application.run(args);
    }
}
