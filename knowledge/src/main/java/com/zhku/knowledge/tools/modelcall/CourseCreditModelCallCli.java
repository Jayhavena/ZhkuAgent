package com.zhku.knowledge.tools.modelcall;

import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.rag.RagProviderProfileResolver;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 仅由开发者在本机显式 --live 调用；默认不连接供应商。 */
public final class CourseCreditModelCallCli {

    private CourseCreditModelCallCli() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || !"--live".equals(args[0])) {
            System.out.println("status=DISABLED; use --live for an explicit provider smoke");
            return;
        }
        try {
            if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext logContext)) {
                throw new IllegalStateException("本机日志实现不受控");
            }
            // Spring AI 1.0.9 的异常响应分支会把 Prompt 写入 WARN；本机冒烟必须先关掉该包日志。
            logContext.getLogger("org.springframework.ai").setLevel(Level.OFF);
            RagProviderProfile profile = configuredProfile();
            // 在读取问题前验证配置和密钥，失败时不让原始问题进入任何模型对象。
            OpenAiChatModel model = CourseCreditChatModelFactory.create(
                    profile, ApiKeyResolver.systemEnvironment());
            System.out.println("请输入一行课程学分问题，然后按回车：");
            String question = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            var service = new CourseCreditModelCallService(
                    model,
                    new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                    profile.timeout());
            ModelCallResult result = service.run(question);
            System.out.println("tool=" + (result.toolName() == null ? "-" : result.toolName()));
            System.out.println("status=" + result.status());
            if (result.resultJson() != null) {
                System.out.println("result=" + result.resultJson());
            }
            System.out.println("boundary=" + result.boundaryMessage());
        } catch (IllegalStateException exception) {
            System.out.println("status=CONFIG_UNAVAILABLE");
            System.out.println("boundary=仅按输入汇总，不能确认课程资格；本次未取得工具计算结果。");
        } catch (Exception exception) {
            System.out.println("status=MODEL_FAILURE");
            System.out.println("boundary=仅按输入汇总，不能确认课程资格；本次未取得工具计算结果。");
        }
    }

    private static RagProviderProfile configuredProfile() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "zhku-local-preview", new ClassPathResource("application-zhku-local-preview.yml"));
        Map<String, String> values = new HashMap<>();
        for (PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    Object value = enumerable.getProperty(name);
                    if (value != null) {
                        values.put(name, value.toString());
                    }
                }
            }
        }
        String active = values.get("zhku.rag.active-profile");
        if (active == null || active.isBlank()) {
            throw new IllegalStateException("未配置模型 profile");
        }
        return new RagProviderProfileResolver(values).profile(active);
    }
}
