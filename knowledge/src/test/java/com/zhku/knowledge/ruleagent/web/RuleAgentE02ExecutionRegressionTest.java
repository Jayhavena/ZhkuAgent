package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.ruleagent.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 真正经过宿主发送闸门和 Provider HTTP 端口；仅回环模拟供应商，合成包不构成 Live 授权。 */
@Timeout(90)
class RuleAgentE02ExecutionRegressionTest {
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);
    private static final String PER_CALL_RESERVE = "0.8027918";
    @TempDir static Path temporary;
    static Path fixture, assets;
    Path batch;
    ObjectNode plan;
    HttpServer server;
    ExecutorService serverWorkers;
    final List<RuleAgentProvider> providers = new ArrayList<>();
    final AtomicInteger wireCalls = new AtomicInteger(), credentialReads = new AtomicInteger();
    final AtomicReference<String> requestBody = new AtomicReference<>();
    String response;
    int status = 200;

    @BeforeAll static void syntheticSourceIdentity() throws Exception {
        assets = Path.of("").toAbsolutePath();
        while (!Files.isRegularFile(assets.resolve(RuleAgentE02Policy.PLAN))) assets = assets.getParent();
        fixture = temporary.resolve("synthetic-source-not-a-live-package");
        Files.createDirectories(fixture);
        git("init", "--quiet");
        git("-c", "user.name=E02 offline fixture", "-c", "user.email=offline@example.invalid", "commit", "--quiet", "--allow-empty", "-m", "synthetic offline fixture only");
        Files.createDirectories(fixture.resolve("agent/frontend/dist-e02-page-live"));
        Files.writeString(fixture.resolve("agent/frontend/dist-e02-page-live/index.html"), "SYNTHETIC OFFLINE TEST ONLY");
    }
    static void git(String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", fixture.toString())); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IllegalStateException("离线合成源码夹具失败: " + output);
    }
    @BeforeEach void configureRealHostCallbacks() throws Exception {
        System.setProperty("zhku.three.profile", "E02");
        plan = (ObjectNode) RuleAgentThreeQuestionPackage.JSON.readTree(Files.readAllBytes(assets.resolve(RuleAgentE02Policy.PLAN)));
        // 时间和核价证据是明确的合成夹具；测试不能依赖当天官方价格是否已重新核对。
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        ((ObjectNode) plan.path("money")).put("tariffCheckedDate", today).put("tariffValidThrough", today)
                .put("evidenceFile", "docs/synthetic-tariff.json");
        writePlan();
        Files.createDirectories(fixture.resolve("docs"));
        Files.writeString(fixture.resolve("docs/synthetic-tariff.json"), "{\"syntheticOfflineOnly\":true}");
        batch = temporary.resolve("batch-" + UUID.randomUUID());
        RuleAgentThreeQuestionHostTest.root = fixture;
        RuleAgentThreeQuestionHostTest.assets = assets;
        RuleAgentThreeQuestionHostTest.batch = batch;
        RuleAgentThreeQuestionHostTest.plan = plan;
        RuleAgentThreeQuestionHostTest.single = true;
        // 仅让审计闸门接受本地测试调用；没有调用宿主 live 分派、claim 或环境凭证路径。
        RuleAgentThreeQuestionHostTest.live = true;
        RuleAgentThreeQuestionHostTest.stopped = false;
        RuleAgentThreeQuestionHostTest.hardBudgetRmb = "4.02";
        RuleAgentThreeQuestionHostTest.maximumCalls = 5;
        RuleAgentThreeQuestionHostTest.scope = null;
        RuleAgentThreeQuestionHostTest.e02Scope = new RuleAgentE02Scope(plan);
        RuleAgentThreeQuestionHostTest.reserved = BigDecimal.ZERO;
        RuleAgentThreeQuestionHostTest.calls.set(0);
        response = RuleAgentThreeQuestionPackage.JSON.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "{\"type\":\"SEARCH_REVIEWED_RULES\",\"subquestionId\":\"q1\",\"query\":\"公共选修学时\"}"))), "usage", Map.of("prompt_tokens", 25, "completion_tokens", 12)));
    }
    void writePlan() throws Exception {
        Path file = fixture.resolve(RuleAgentE02Policy.PLAN); Files.createDirectories(file.getParent());
        Files.write(file, RuleAgentThreeQuestionPackage.JSON.writeValueAsBytes(plan));
    }
    void freezeSyntheticFixture() throws Exception {
        RuleAgentThreeQuestionHostTest.hash = RuleAgentThreeQuestionPackage.prepare(fixture, assets, batch);
    }
    RuleAgentRunner.View view(String question) {
        return new RuleAgentRunner.View(question, List.of(new RuleAgentRunner.Question("q1", question)), Map.of(), List.of(), List.of(), List.of(), List.of(), 4, 6);
    }
    RuleAgentRunner.View view() { return view(RuleAgentE02Policy.QUESTION); }
    RuleAgentProvider provider() throws Exception {
        if (server == null) {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            serverWorkers = Executors.newFixedThreadPool(2);
            server.setExecutor(serverWorkers);
            server.createContext("/chat/completions", exchange -> {
                wireCalls.incrementAndGet();
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                try { exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); }
                finally { exchange.close(); }
            });
            server.start();
        }
        var provider = new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"), RuleAgentProvider.MODEL, "LOCAL_TEST_ONLY"), UUID.randomUUID(), UUID.randomUUID(),
                () -> { credentialReads.incrementAndGet(); return "local-synthetic-not-a-real-key"; },
                RuleAgentThreeQuestionHostTest::audit, RuleAgentThreeQuestionHostTest::exchange, new AtomicInteger(10));
        providers.add(provider); return provider;
    }
    void deniedWithoutAnotherSend(RuleAgentProvider provider, int sent, int reads, ReasonCode reason) {
        assertThatThrownBy(() -> provider.decide(view(), CALL_TIMEOUT))
                .isInstanceOf(RuleAgentProvider.Failure.class).hasMessage(reason.name());
        assertThat(wireCalls).hasValue(sent); assertThat(credentialReads).hasValue(reads);
        assertThat(RuleAgentThreeQuestionHostTest.stopped).isTrue();
        assertThat(RuleAgentThreeQuestionHostTest.e02Scope.closed()).isTrue();
    }
    List<JsonNode> traces() throws Exception {
        var result = new ArrayList<JsonNode>();
        for (String line : Files.readAllLines(batch.resolve("provider-traces.jsonl"), StandardCharsets.UTF_8))
            result.add(RuleAgentThreeQuestionPackage.JSON.readTree(line));
        return result;
    }
    void assertDecisionWireContract() throws Exception {
        byte[] bytes = requestBody.get().getBytes(StandardCharsets.UTF_8);
        assertThat(bytes.length).isLessThanOrEqualTo(65536);
        JsonNode wire = RuleAgentThreeQuestionPackage.JSON.readTree(bytes);
        var fields = new HashSet<String>(); wire.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("model", "stream", "enable_thinking", "max_completion_tokens", "response_format", "messages");
        assertThat(wire.path("model").asText()).isEqualTo("qwen3.8-flash");
        assertThat(wire.path("stream").isBoolean()).isTrue();
        assertThat(wire.path("stream").asBoolean()).isFalse();
        assertThat(wire.path("enable_thinking").isBoolean()).isTrue();
        assertThat(wire.path("enable_thinking").asBoolean()).isFalse();
        assertThat(wire.path("max_completion_tokens").isIntegralNumber()).isTrue();
        assertThat(wire.path("max_completion_tokens").asInt()).isEqualTo(1024);
        assertThat(wire.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(wire.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        JsonNode messages = wire.path("messages");
        assertThat(messages.isArray()).isTrue(); assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        for (JsonNode message : messages) {
            assertThat(message.size()).isEqualTo(2); assertThat(message.path("content").isTextual()).isTrue();
        }
        JsonNode input = RuleAgentThreeQuestionPackage.JSON.readTree(messages.get(1).path("content").asText());
        assertThat(input.path("question").asText()).isEqualTo(RuleAgentE02Policy.QUESTION);
        assertThat(requestBody.get()).doesNotContain("local-synthetic-not-a-real-key");
    }
    @AfterEach void cleanOnlyOwnedResources() throws Exception {
        for (var provider : providers) { provider.close(); assertThat(provider.isTerminated()).isTrue(); }
        if (server != null) server.stop(0);
        if (serverWorkers != null) { serverWorkers.shutdownNow(); assertThat(serverWorkers.awaitTermination(3, TimeUnit.SECONDS)).isTrue(); }
        RuleAgentThreeQuestionHostTest.live = false;
        RuleAgentThreeQuestionHostTest.single = false;
        RuleAgentThreeQuestionHostTest.stopped = false;
        RuleAgentThreeQuestionHostTest.scope = null;
        RuleAgentThreeQuestionHostTest.e02Scope = null;
        RuleAgentThreeQuestionHostTest.reserved = BigDecimal.ZERO;
        RuleAgentThreeQuestionHostTest.calls.set(0);
        System.clearProperty("zhku.three.profile");
    }

    @Test void expiredQuoteCannotPrepareOrCreateAnExecutionBatch() throws Exception {
        ((ObjectNode)plan.path("money")).put("tariffCheckedDate", "2000-01-01").put("tariffValidThrough", "2000-01-01"); writePlan();
        assertThatThrownBy(this::freezeSyntheticFixture).isInstanceOf(IllegalStateException.class).hasMessageContaining("核价");
        assertThat(batch).doesNotExist();
    }

    @ParameterizedTest(name = "历史预留 {0} 元时不得新增外发")
    @ValueSource(strings = {"4.02", "4.0200001", "3.2172083"})
    void exhaustedOrInsufficientRealBudgetRejectsBeforeCredentialsAndPreservesReservation(String historical) throws Exception {
        freezeSyntheticFixture();
        // 不缩小真实硬帽：只构造历史账本；最后一项距一次完整预留恰差0.0000001元。
        RuleAgentThreeQuestionHostTest.reserved = new BigDecimal(historical);
        deniedWithoutAnotherSend(provider(), 0, 0, ReasonCode.AUDIT_UNAVAILABLE);
        assertThat(RuleAgentThreeQuestionHostTest.hardBudgetRmb).isEqualTo("4.02");
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo(historical);
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(0);
        assertThat(batch.resolve("provider-traces.jsonl")).doesNotExist();
        assertThat(batch.resolve("provider-exchanges.jsonl")).doesNotExist();
    }

    @Test void exactlyOneReservationRemainingSendsOnceWithTheRealWireContractAndThenStops() throws Exception {
        freezeSyntheticFixture();
        RuleAgentThreeQuestionHostTest.reserved = new BigDecimal("3.2172082");
        var outer = (ObjectNode) RuleAgentThreeQuestionPackage.JSON.readTree(response);
        ((ObjectNode) outer.path("usage")).put("prompt_tokens", 1000000).put("completion_tokens", 1034);
        response = outer.toString();
        var provider = provider();
        assertThat(provider.decide(view(), CALL_TIMEOUT).actionJson()).contains("SEARCH_REVIEWED_RULES");
        assertThat(wireCalls).hasValue(1); assertThat(credentialReads).hasValue(1);
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo("4.02");
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(1);
        assertThat(RuleAgentThreeQuestionHostTest.stopped).isFalse();
        assertThat(RuleAgentThreeQuestionHostTest.e02Scope.closed()).isFalse();
        assertDecisionWireContract();
        JsonNode finished = traces().getLast();
        assertThat(finished.path("phase").asText()).isEqualTo("FINISHED");
        assertThat(finished.path("reason").isNull()).isTrue();
        assertThat(finished.path("inputTokens").asLong()).isEqualTo(1000000);
        assertThat(finished.path("outputTokens").asLong()).isEqualTo(1034);
        deniedWithoutAnotherSend(provider, 1, 1, ReasonCode.AUDIT_UNAVAILABLE);
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo("4.02");
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(1);
    }

    @Test void fifthHttpCallIsAllowedButANewProviderCannotSpendASixthCall() throws Exception {
        freezeSyntheticFixture();
        var first = provider();
        for (int count = 1; count <= 5; count++) {
            assertThat(first.decide(view(), CALL_TIMEOUT).actionJson()).contains("SEARCH_REVIEWED_RULES");
            assertThat(wireCalls).hasValue(count); assertThat(credentialReads).hasValue(count);
            assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(count);
        }
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo("4.013959");
        assertThat(RuleAgentThreeQuestionHostTest.stopped).isFalse();
        // 新端口仍有本地allowance，确保第六次是宿主全批5次门禁拒绝，而非原Provider的run计数巧合。
        deniedWithoutAnotherSend(provider(), 5, 5, ReasonCode.AUDIT_UNAVAILABLE);
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(5);
        assertThat(RuleAgentThreeQuestionHostTest.maximumCalls).isEqualTo(5);
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo("4.013959");
        assertThat(traces().stream().filter(t -> "BEGIN".equals(t.path("phase").asText())).count()).isEqualTo(5);
    }

    @ParameterizedTest(name = "{0} 不重试、不退款、封闭scope")
    @CsvSource({
            "HTTP_429, MODEL_RATE_LIMIT",
            "HTTP_500, MODEL_UNAVAILABLE",
            "INVALID_OUTER_JSON, INVALID_MODEL_RESPONSE",
            "INVALID_ACTION_JSON, INVALID_MODEL_RESPONSE",
            "USAGE_MISSING, AUDIT_UNAVAILABLE",
            "INPUT_USAGE_EXCEEDED, AUDIT_UNAVAILABLE",
            "OUTPUT_USAGE_EXCEEDED, AUDIT_UNAVAILABLE"
    })
    void providerAndUsageFailuresStopWithoutRetryOrRefund(String failure, ReasonCode reason) throws Exception {
        freezeSyntheticFixture();
        var outer = (ObjectNode) RuleAgentThreeQuestionPackage.JSON.readTree(response);
        switch (failure) {
            case "HTTP_429" -> status = 429;
            case "HTTP_500" -> status = 500;
            case "INVALID_OUTER_JSON" -> response = "not-json";
            case "INVALID_ACTION_JSON" -> ((ObjectNode) outer.path("choices").get(0).path("message")).put("content", "not-an-action-json");
            case "USAGE_MISSING" -> outer.remove("usage");
            case "INPUT_USAGE_EXCEEDED" -> ((ObjectNode) outer.path("usage")).put("prompt_tokens", 1000001);
            case "OUTPUT_USAGE_EXCEEDED" -> ((ObjectNode) outer.path("usage")).put("completion_tokens", 1035);
            default -> throw new IllegalArgumentException("未声明的离线失败夹具");
        }
        if (!failure.equals("INVALID_OUTER_JSON")) response = outer.toString();
        var provider = provider();
        deniedWithoutAnotherSend(provider, 1, 1, reason);
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(1);
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo(PER_CALL_RESERVE);
        JsonNode finished = traces().getLast();
        assertThat(finished.path("phase").asText()).isEqualTo("FINISHED");
        assertThat(finished.path("reason").asText()).isEqualTo(reason.name());
        assertThat(finished.path("status").asInt()).isEqualTo(status);
        // 主动再调用一次只用于证明封批不可续杯；不能产生第二次网络或密钥读取，也不退回第一次预留。
        deniedWithoutAnotherSend(provider, 1, 1, ReasonCode.AUDIT_UNAVAILABLE);
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(1);
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo(PER_CALL_RESERVE);
        assertThat(traces().stream().filter(t -> "BEGIN".equals(t.path("phase").asText())).count()).isEqualTo(1);
    }

    @Test void requestAbove65536Utf8BytesIsRejectedBeforeReservationCredentialsOrHttp() throws Exception {
        freezeSyntheticFixture();
        RuleAgentThreeQuestionHostTest.reserved = new BigDecimal(PER_CALL_RESERVE);
        // 字符数未超上限但UTF-8字节已超；不能用String.length替代真实外发大小。
        String oversized = "学".repeat(21846);
        assertThat(oversized.length()).isLessThan(65536);
        assertThat(oversized.getBytes(StandardCharsets.UTF_8).length).isEqualTo(65538);
        var provider = provider();
        assertThatThrownBy(() -> provider.decide(view(oversized), CALL_TIMEOUT))
                .isInstanceOf(RuleAgentProvider.Failure.class).hasMessage("BUDGET_EXCEEDED");
        assertThat(wireCalls).hasValue(0); assertThat(credentialReads).hasValue(0);
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(0);
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo(PER_CALL_RESERVE);
        assertThat(batch.resolve("provider-traces.jsonl")).doesNotExist();
        assertThat(batch.resolve("provider-exchanges.jsonl")).doesNotExist();
    }

    @ParameterizedTest(name = "single failScope有scope={0}时可幂等关闭且不NPE")
    @ValueSource(booleans = {true, false})
    void singleScopeFailureIsNullSafeAndIdempotent(boolean present) {
        var scope = RuleAgentThreeQuestionHostTest.e02Scope;
        if (!present) RuleAgentThreeQuestionHostTest.e02Scope = null;
        assertThat(RuleAgentThreeQuestionHostTest.scope).isNull();
        assertThatCode(() -> {
            RuleAgentThreeQuestionHostTest.failScope();
            RuleAgentThreeQuestionHostTest.failScope();
        }).doesNotThrowAnyException();
        if (present) assertThat(scope.closed()).isTrue();
        assertThat(wireCalls).hasValue(0); assertThat(credentialReads).hasValue(0);
    }

    @ParameterizedTest(name = "已Prepare包遇到 {0} 必须在发前拒绝")
    @ValueSource(strings = {"EXPIRED_TARIFF", "TAMPERED_PACKAGE"})
    void preparedPackageIsRecheckedBeforeCredentialsAndHttp(String change) throws Exception {
        freezeSyntheticFixture();
        RuleAgentThreeQuestionHostTest.reserved = new BigDecimal(PER_CALL_RESERVE);
        if (change.equals("EXPIRED_TARIFF")) {
            ((ObjectNode) plan.path("money")).put("tariffCheckedDate", "2000-01-01").put("tariffValidThrough", "2000-01-01");
            writePlan();
        } else {
            // 只加合法JSON空白也会改变授权字节SHA，不能靠“语义相同”复用原审包结果。
            Files.writeString(batch.resolve("request-package.json"), "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
        deniedWithoutAnotherSend(provider(), 0, 0, ReasonCode.AUDIT_UNAVAILABLE);
        assertThat(RuleAgentThreeQuestionHostTest.calls).hasValue(0);
        assertThat(RuleAgentThreeQuestionHostTest.reserved).isEqualByComparingTo(PER_CALL_RESERVE);
        assertThat(batch.resolve("provider-traces.jsonl")).doesNotExist();
        assertThat(batch.resolve("provider-exchanges.jsonl")).doesNotExist();
    }
}
