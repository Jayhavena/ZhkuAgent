package com.zhku.knowledge.tools.modelcall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.tools.CampusTool;
import com.zhku.knowledge.tools.CampusToolDescriptor;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CampusToolResult;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 本机协议合同：真实 Spring AI 适配器只能连 127.0.0.1 假供应商，不接外网或数据库。 */
class CourseCreditSpringAiProtocolContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FAKE_KEY = "sk-local-contract-only";
    private static final String ARGUMENTS = """
            {"courses":[
              {"courseId":"MA101","category":"必修","credits":3},
              {"courseId":"GE002","category":"通识","credits":1.5}
            ]}
            """;

    private HttpServer server;
    private RagProviderProfile profile;
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private final AtomicInteger executions = new AtomicInteger();
    private volatile int responseStatus;
    private volatile String responseBody;
    private Logger springAiLogger;
    private Level previousLogLevel;

    @BeforeEach
    void setUp() throws IOException {
        LoggerContext logContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        springAiLogger = logContext.getLogger("org.springframework.ai");
        previousLogLevel = springAiLogger.getLevel();
        // 1.0.9 的重试/异常分支会记录响应细节；与 CLI 一样先关闭该包日志。
        springAiLogger.setLevel(Level.OFF);
        responseStatus = 200;
        responseBody = textReply("未提供工具调用");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            // 仅在内存解析请求；原始问题和假鉴权值均不打印、不断言回显。
            try {
                request.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
                respond(exchange, responseStatus, responseBody);
            } catch (IOException exception) {
                respond(exchange, 400, "{\"error\":{\"message\":\"bad request\"}}");
            }
        });
        server.start();
        profile = new RagProviderProfile("local-contract",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "ZHKU_LOCAL_CONTRACT_KEY", "stub-chat", "stub-embed", 1,
                Duration.ofSeconds(8), "stub-index");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        springAiLogger.setLevel(previousLogLevel);
    }

    @Test
    void sendsOnlyApprovedFullSchemaAndExecutesProviderToolCall() throws Exception {
        responseBody = toolReply("summarize_course_credits", ARGUMENTS);

        ModelCallResult result = run();

        assertThat(result.status()).isEqualTo(ModelCallStatus.SUCCESS);
        assertThat(result.toolName()).isEqualTo("summarize_course_credits");
        assertThat(result.resultJson()).contains("\"totalCredits\":4.5");
        assertThat(result.boundaryMessage()).contains("不能确认选课资格");
        assertThat(executions.get()).isEqualTo(1);

        JsonNode tools = request.get().path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(1);
        JsonNode function = tools.get(0).path("function");
        assertThat(function.path("name").asText()).isEqualTo("summarize_course_credits");
        JsonNode schema = function.path("parameters");
        assertThat(schema).isEqualTo(MAPPER.readTree(new CourseCreditSummaryTool()
                .descriptor().inputSchemaJson()));
        assertThat(schema.path("required").toString()).isEqualTo("[\"courses\"]");
        JsonNode item = schema.path("properties").path("courses").path("items");
        assertThat(item.path("required").toString())
                .isEqualTo("[\"courseId\",\"category\",\"credits\"]");
        assertThat(item.path("properties").path("courseId").path("type").asText()).isEqualTo("string");
        assertThat(item.path("properties").path("category").path("type").asText()).isEqualTo("string");
        assertThat(item.path("properties").path("credits").path("type").asText()).isEqualTo("number");
        assertThat(item.path("properties").path("credits").path("minimum").intValue()).isZero();
    }

    @Test
    void plainTextResponseDoesNotInventCredits() {
        responseBody = textReply("这些课程共 99 学分");
        ModelCallResult result = run();
        assertThat(result.status()).isEqualTo(ModelCallStatus.NO_TOOL_CALL);
        assertThat(result.resultJson()).isNull();
        assertThat(executions.get()).isZero();
    }

    @Test
    void unknownProviderToolDoesNotExecuteRegisteredTool() {
        responseBody = toolReply("remove_course", ARGUMENTS);
        ModelCallResult result = run();
        assertThat(result.status()).isEqualTo(ModelCallStatus.UNKNOWN_TOOL);
        assertThat(result.resultJson()).isNull();
        assertThat(executions.get()).isZero();
    }

    @Test
    void invalidProviderArgumentsDoNotReachRegisteredExecutor() {
        responseBody = toolReply("summarize_course_credits",
                "{\"courses\":[{\"courseId\":\"MA101\",\"category\":\"必修\",\"credits\":-1}]}");
        ModelCallResult result = run();
        assertThat(result.status()).isEqualTo(ModelCallStatus.INVALID_ARGUMENT);
        assertThat(result.resultJson()).isNull();
        assertThat(executions.get()).isZero();
    }

    @Test
    void providerErrorDoesNotInventCredits() {
        responseStatus = 400;
        responseBody = "{\"error\":{\"message\":\"provider rejected request\"}}";
        ModelCallResult result = run();
        assertThat(result.status()).isEqualTo(ModelCallStatus.MODEL_FAILURE);
        assertThat(result.resultJson()).isNull();
        assertThat(executions.get()).isZero();
    }

    private ModelCallResult run() {
        var model = CourseCreditChatModelFactory.create(
                profile, new ApiKeyResolver(Map.of(profile.apiKeyEnvVar(), FAKE_KEY)));
        CampusTool real = new CourseCreditSummaryTool();
        CampusTool counted = new CampusTool() {
            @Override
            public CampusToolDescriptor descriptor() {
                return real.descriptor();
            }

            @Override
            public CampusToolResult execute(String argumentsJson) {
                executions.incrementAndGet();
                return real.execute(argumentsJson);
            }
        };
        return new CourseCreditModelCallService(
                model, new CampusToolRegistry(List.of(counted)), profile.timeout())
                .run("请汇总我给出的课程学分");
    }

    private static String toolReply(String name, String arguments) {
        ObjectNode root = reply("tool_calls");
        ObjectNode message = (ObjectNode) root.path("choices").get(0).path("message");
        message.putNull("content");
        ObjectNode toolCall = message.putArray("tool_calls").addObject();
        toolCall.put("id", "call-local-1");
        toolCall.put("type", "function");
        ObjectNode function = toolCall.putObject("function");
        function.put("name", name);
        function.put("arguments", arguments);
        return root.toString();
    }

    private static String textReply(String content) {
        ObjectNode root = reply("stop");
        ((ObjectNode) root.path("choices").get(0).path("message")).put("content", content);
        return root.toString();
    }

    private static ObjectNode reply(String finishReason) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "local-stub");
        root.put("object", "chat.completion");
        root.put("created", 1);
        root.put("model", "stub-chat");
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        choice.put("finish_reason", finishReason);
        choice.putObject("message").put("role", "assistant");
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", 1);
        usage.put("completion_tokens", 1);
        usage.put("total_tokens", 2);
        return root;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
