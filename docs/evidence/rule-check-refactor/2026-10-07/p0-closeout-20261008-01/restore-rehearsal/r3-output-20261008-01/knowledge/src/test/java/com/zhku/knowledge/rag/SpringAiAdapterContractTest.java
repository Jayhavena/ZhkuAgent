package com.zhku.knowledge.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring AI 适配器的协议合同测试（mock OpenAI-compatible 端点，JDK HttpServer 桩）。
 *
 * <p>目的：证明按计划引入的 Spring AI 客户端在"本机桩"上说的是正确的协议
 * （Bearer 认证、证据入提示、结构化输出解析、embedding 维度校验），
 * 而不是靠"依赖装上了"推断可用。真实供应商 smoke test 仍需显式 opt-in。</p>
 */
class SpringAiAdapterContractTest {

    private static final String API_KEY = "sk-spring-ai-contract";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastChatBody = new AtomicReference<>();
    private String chatContent = "{}";
    private String embeddingJson = "[0.1,0.2,0.3]";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastChatBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, """
                    {"id":"1","object":"chat.completion","model":"stub","choices":[
                      {"index":0,"message":{"role":"assistant","content":%s}}],
                      "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """.formatted(jsonString(chatContent)));
        });
        server.createContext("/v1/embeddings", exchange -> respond(exchange, """
                {"object":"list","model":"stub-embed","data":[{"object":"embedding","index":0,"embedding":%s}],
                 "usage":{"prompt_tokens":1,"total_tokens":1}}
                """.formatted(embeddingJson)));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldSpeakChatProtocolAndParseStructuredOutput() {
        chatContent = """
                {"answer":"每学期最高修读 4 学分。","uncertainty":0.92,"citationIds":["cit-1"],"handoffRequired":false}
                """;
        SpringAiChatModelClient client = new SpringAiChatModelClient(chatModel("stub-chat"), "stub");

        ModelResponse response = client.call(new ModelRequest(
                "不可变系统约束",
                "每学期最多修多少学分？",
                List.of(new ApprovedEvidence("cit-1", "第一条 最高修读 4 学分。", "学生手册"))
        ));

        assertThat(response.answer()).contains("4 学分");
        assertThat(response.citationIds()).containsExactly("cit-1");
        assertThat(response.uncertainty()).isEqualTo(0.92);
        assertThat(lastAuthorization.get()).isEqualTo("Bearer " + API_KEY);
        assertThat(lastChatBody.get())
                .contains("cit-1")
                .contains("每学期最多修多少学分？")
                .contains("不可变系统约束");
    }

    @Test
    void shouldRejectMalformedChatOutput() {
        chatContent = "not-json";
        SpringAiChatModelClient client = new SpringAiChatModelClient(chatModel("stub-chat"), "stub");

        assertThatThrownBy(() -> client.call(new ModelRequest(
                "约束", "问题", List.of(new ApprovedEvidence("cit-1", "证据", "来源")))))
                .isInstanceOf(ModelOutputFormatException.class);
    }

    @Test
    void shouldReturnEmbeddingVector() {
        SpringAiEmbeddingModelClient client = new SpringAiEmbeddingModelClient(
                embedModel(3), 3);

        float[] vector = client.embed("第一条 最高修读 4 学分。");

        assertThat(vector).hasSize(3);
        assertThat(client.dimensions()).isEqualTo(3);
    }

    @Test
    void shouldFailLoudlyWhenEmbeddingDimensionsMismatchProfile() {
        // profile 声明 4 维、模型返回 3 维：必须整轮失败，禁止把不同维度写进同一索引。
        SpringAiEmbeddingModelClient client = new SpringAiEmbeddingModelClient(embedModel(3), 4);

        assertThatThrownBy(() -> client.embed("文本"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("维度");
    }

    @Test
    void shouldNotDoubleVersionSegmentWhenBaseUrlAlreadyContainsV1() throws IOException {
        // 真实厂商兼容模式端点常自带 /v1（如百炼 .../compatible-mode/v1）。
        // 断言方式：把"双 /v1"路径接成必然失败（500）；正常路径沿用 setUp 的成功桩——
        // 若归一化失效（请求打到 /v1/v1/chat/completions），本用例会以调用失败暴露。
        server.createContext("/v1/v1/chat/completions", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        chatContent = """
                {"answer":"ok","uncertainty":0.9,"citationIds":["cit-1"],"handoffRequired":false}
                """;
        String baseWithVersion = baseUrl + "/v1";
        // 走生产同一份归一化逻辑（而非在测试里重复实现），确保被测行为与线上一致。
        OpenAiApi api = OpenAiCompatibleApiPaths.builder(baseWithVersion, API_KEY).build();
        SpringAiChatModelClient client = new SpringAiChatModelClient(
                OpenAiChatModel.builder().openAiApi(api)
                        .defaultOptions(OpenAiChatOptions.builder().model("stub-chat").build())
                        .build(),
                "stub");

        ModelResponse response = client.call(new ModelRequest(
                "约束", "问题", List.of(new ApprovedEvidence("cit-1", "证据", "来源"))));

        assertThat(response.answer()).isEqualTo("ok");
        assertThat(lastAuthorization.get()).isEqualTo("Bearer " + API_KEY);
    }

    private OpenAiChatModel chatModel(String model) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(API_KEY)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
    }

    @SuppressWarnings("deprecation")
    private OpenAiEmbeddingModel embedModel(int requestedDimensions) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(API_KEY)
                .build();
        return new OpenAiEmbeddingModel(
                api,
                MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder()
                        .model("stub-embed")
                        .dimensions(requestedDimensions)
                        .build()
        );
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
