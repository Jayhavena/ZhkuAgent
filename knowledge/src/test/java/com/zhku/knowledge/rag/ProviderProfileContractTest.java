package com.zhku.knowledge.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * provider profile 合同测试：配置隔离、密钥遮蔽、mock OpenAI-compatible 协议。
 *
 * <p>该测试通过不代表任何真实供应商"已支持"：真实供应商 smoke test 必须
 * 显式 opt-in 且不入库。这里只锁定协议与配置契约。</p>
 */
class ProviderProfileContractTest {

    private static final String SECRET = "sk-test-secret-value";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private int statusCode = 200;
    private String responseBody = "{}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldIsolateProfileConfigurationAndPickActiveProfile() {
        Map<String, String> properties = new HashMap<>(baseProperties("kimi"));
        properties.putAll(profileKeys("deepseek", "https://api.deepseek.com", "deepseek-chat"));
        properties.putAll(profileKeys("kimi", "https://api.moonshot.cn", "moonshot-v1-8k"));
        RagProviderProfileResolver resolver = new RagProviderProfileResolver(properties);

        RagProviderProfile active = resolver.activeProfile().orElseThrow();

        // 激活 profile 的字段不得串到另一个 profile。
        assertThat(active.name()).isEqualTo("kimi");
        assertThat(active.baseUrl()).isEqualTo("https://api.moonshot.cn");
        assertThat(active.chatModel()).isEqualTo("moonshot-v1-8k");
        assertThat(resolver.profile("deepseek").baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(resolver.configuredProfileNames()).contains("deepseek", "kimi");
    }

    @Test
    void shouldNotReportIncompleteProfileAsConfigured() {
        Map<String, String> properties = new HashMap<>(baseProperties("half"));
        properties.put("zhku.rag.profiles.half.base-url", "https://api.example.com");
        RagProviderProfileResolver resolver = new RagProviderProfileResolver(properties);

        // 只写了部分键的 profile 不算"已支持"，也不允许被激活。
        assertThat(resolver.configuredProfileNames()).doesNotContain("half");
        assertThatIllegalStateException().isThrownBy(() -> resolver.profile("half"));
    }

    @Test
    void shouldRejectPlainHttpEndpointOutsideLoopback() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RagProviderProfile(
                "unsafe", "http://api.example.com", "KEY_ENV", "chat", "embed",
                1024, Duration.ofSeconds(5), "index-1"));
    }

    @Test
    void shouldNotLeakApiKeyInDescription() {
        RagProviderProfile profile = stubProfile();
        ApiKeyResolver resolver = new ApiKeyResolver(Map.of("ZHKU_RAG_TEST_KEY", SECRET));

        assertThat(resolver.requireApiKey(profile)).isEqualTo(SECRET);
        assertThat(profile.describe()).doesNotContain(SECRET).contains("ZHKU_RAG_TEST_KEY");
    }

    @Test
    void shouldFailLoudlyAndNotEchoSecretWhenApiKeyMissing() {
        RagProviderProfile profile = stubProfile();
        ApiKeyResolver resolver = new ApiKeyResolver(Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.requireApiKey(profile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZHKU_RAG_TEST_KEY")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void shouldSpeakOpenAiCompatibleProtocolWithEvidenceInPrompt() {
        responseBody = """
                {"choices":[{"message":{"content":"{\\"answer\\":\\"最高 4 学分。\\",\\"uncertainty\\":0.9,\\"citationIds\\":[\\"cit-1\\"],\\"handoffRequired\\":false}"}}]}
                """;
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                stubProfile(), new ApiKeyResolver(Map.of("ZHKU_RAG_TEST_KEY", SECRET)));

        ModelResponse response = client.call(new ModelRequest(
                "系统约束",
                "每学期最多修多少学分？",
                List.of(new ApprovedEvidence("cit-1", "第一条 最高修读 4 学分。", "学生手册"))));

        assertThat(response.answer()).isEqualTo("最高 4 学分。");
        assertThat(response.citationIds()).containsExactly("cit-1");
        assertThat(response.handoffRequired()).isFalse();
        // 协议契约：Bearer 认证 + 当轮获准证据随提示下发。
        assertThat(lastAuthorization.get()).isEqualTo("Bearer " + SECRET);
        assertThat(lastBody.get()).contains("cit-1").contains("每学期最多修多少学分？");
    }

    @Test
    void shouldMapRateLimitAndServerErrorToTypedFailures() {
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                stubProfile(), new ApiKeyResolver(Map.of("ZHKU_RAG_TEST_KEY", SECRET)));

        statusCode = 429;
        responseBody = "{\"error\":\"rate limited\"}";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.call(simpleRequest()))
                .isInstanceOf(ModelCallRateLimitException.class);

        statusCode = 503;
        responseBody = "{\"error\":\"unavailable\"}";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.call(simpleRequest()))
                .isInstanceOf(ModelCallFailedException.class);
    }

    @Test
    void shouldRejectMalformedModelOutput() {
        statusCode = 200;
        responseBody = "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}";
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(
                stubProfile(), new ApiKeyResolver(Map.of("ZHKU_RAG_TEST_KEY", SECRET)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.call(simpleRequest()))
                .isInstanceOf(ModelOutputFormatException.class);
    }

    private static ModelRequest simpleRequest() {
        return new ModelRequest("系统约束", "问题",
                List.of(new ApprovedEvidence("cit-1", "证据", "来源")));
    }

    private RagProviderProfile stubProfile() {
        return new RagProviderProfile(
                "stub", baseUrl, "ZHKU_RAG_TEST_KEY", "stub-chat", "stub-embed",
                3, Duration.ofSeconds(5), "index-stub");
    }

    private static Map<String, String> baseProperties(String activeProfile) {
        Map<String, String> properties = new HashMap<>();
        properties.put("zhku.rag.enabled", "true");
        properties.put("zhku.rag.active-profile", activeProfile);
        return properties;
    }

    private static Map<String, String> profileKeys(String name, String url, String chatModel) {
        Map<String, String> keys = new HashMap<>();
        keys.put("zhku.rag.profiles." + name + ".base-url", url);
        keys.put("zhku.rag.profiles." + name + ".api-key-env-var", "ZHKU_RAG_" + name.toUpperCase() + "_KEY");
        keys.put("zhku.rag.profiles." + name + ".chat-model", chatModel);
        keys.put("zhku.rag.profiles." + name + ".embedding-model", "embed-model");
        keys.put("zhku.rag.profiles." + name + ".embedding-dimensions", "1536");
        keys.put("zhku.rag.profiles." + name + ".timeout-seconds", "10");
        keys.put("zhku.rag.profiles." + name + ".index-profile", "index-" + name);
        return keys;
    }
}
