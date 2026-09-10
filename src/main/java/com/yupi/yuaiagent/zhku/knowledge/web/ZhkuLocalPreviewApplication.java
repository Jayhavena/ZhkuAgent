package com.yupi.yuaiagent.zhku.knowledge.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 仲园 Agent 的本机问答预览专用启动入口。
 *
 * <p>它只扫描本机预览所需的校园问答组件，并显式关闭项目中其他功能使用的模型自动配置，确保验证链路不需要模型密钥。</p>
 */
@SpringBootApplication(
        scanBasePackageClasses = ZhkuLocalPreviewController.class,
        exclude = DataSourceAutoConfiguration.class,
        excludeName = {
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration",
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration",
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration",
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration",
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration",
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration",
                "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration",
                "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
                "org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration"
        }
)
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
