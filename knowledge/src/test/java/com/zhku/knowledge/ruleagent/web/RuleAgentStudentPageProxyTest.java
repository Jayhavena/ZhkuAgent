package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** 真实回环HTTP代理契约验收，不是浏览器验收；仅可连接已显式启动的零模型页面夹具。 */
@Timeout(90)
class RuleAgentStudentPageProxyTest {
    @Test void actualFrontendProxyAndSyntheticTaskLifecycle() throws Exception {
        Assumptions.assumeTrue(System.getProperty("zhku.page.proxyPort") != null);
        int port = Integer.parseInt(System.getProperty("zhku.page.proxyPort"));
        Path evidence = Path.of(System.getProperty("zhku.page.proxyEvidence"));
        // 先核对本次宿主的无供应商声明，不能把代理测试误指向Live入口。
        JsonNode ready = RuleAgentTaskHttpTest.JSON.readTree(evidence.resolve("browser-ready.json").toFile());
        assertThat(ready.path("executionMode").asText()).isEqualTo("TEST_SIMULATION_NO_PROVIDER");
        assertThat(ready.path("providerCalls").asInt(-1)).isZero();
        var anonymous = new RuleAgentTaskHttpTest.User(port);
        var a = new RuleAgentTaskHttpTest.User(port);
        var sameStudentOtherSession = new RuleAgentTaskHttpTest.User(port);
        var admin = new RuleAgentTaskHttpTest.User(port);
        RuleAgentTaskHttpTest.exchanges.clear();
        var page = anonymous.client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/rules")).timeout(java.time.Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body().contains("/assets/")).isTrue();
        assertThat(anonymous.get("/tasks/capabilities").statusCode()).isEqualTo(401);
        a.csrf = RuleAgentTaskHttpTest.JSON.readTree(a.get("/auth/csrf").body());
        var invalidLogin = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + RuleAgentTaskHttpTest.P + "/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(a.csrf.path("headerName").asText(), a.csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString("username=student-a&password=wrong-fixture-input")).build();
        assertThat(a.client.send(invalidLogin, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        a.login("student-a"); sameStudentOtherSession.login("student-a"); admin.login("maintainer");
        assertThat(admin.get("/tasks/capabilities").statusCode()).isEqualTo(403);
        assertThat(a.get("/tasks/capabilities").statusCode()).isEqualTo(200);
        String key = UUID.randomUUID().toString();
        var body = Map.of("question", "我那门选修挂了，下次直接再上一遍就好了吧？", "clientRequestId", key);
        assertThat(a.post("/tasks", body, false).statusCode()).isEqualTo(403);
        var accepted = a.post("/tasks", body, true);
        assertThat(accepted.statusCode()).isEqualTo(202);
        String task = RuleAgentTaskHttpTest.JSON.readTree(accepted.body()).path("taskId").asText();
        assertThat(RuleAgentTaskHttpTest.JSON.readTree(a.post("/tasks", body, true).body()).path("duplicate").asBoolean()).isTrue();
        assertThat(a.post("/tasks", Map.of("question", "different fixture question", "clientRequestId", key), true).statusCode()).isEqualTo(409);
        assertThat(sameStudentOtherSession.get("/tasks/" + task).statusCode()).isEqualTo(404);
        assertThat(sameStudentOtherSession.get("/tasks/requests/CREATE/" + key).statusCode()).isEqualTo(404);
        assertThat(RuleAgentTaskHttpTest.JSON.readTree(a.get("/tasks/" + task).body()).path("taskStatus").asText()).isEqualTo("ACTIVE");
        JsonNode waiting = await(a, task, "WAITING_USER");
        var message = new HashMap<String, Object>();
        message.put("message", "PUBLIC"); message.put("continuationToken", waiting.path("continuationToken").asText());
        message.put("expectedVersion", waiting.path("version").asLong() - 1); message.put("clientRequestId", UUID.randomUUID());
        assertThat(a.post("/tasks/" + task + "/messages", message, true).statusCode()).isEqualTo(409);
        message.put("expectedVersion", waiting.path("version").asLong());
        assertThat(a.post("/tasks/" + task + "/messages", message, true).statusCode()).isEqualTo(202);
        assertThat(a.post("/tasks/" + task + "/messages", message, true).statusCode()).isEqualTo(202);
        JsonNode resolved = await(a, task, "RESOLVED");
        String originalPath = "/tasks/" + task + "/originals/" + resolved.path("result").path("evidence").get(0).path("originalToken").asText();
        assertThat(a.get(originalPath).statusCode()).isEqualTo(200);
        assertThat(sameStudentOtherSession.get(originalPath).statusCode()).isEqualTo(404);
        for (var scenario : Map.of("这学期公选补选今晚几点关？现在还能帮我补上吗？", "RESOLVED", "故障", "FAILED", "取消", "STOPPED").entrySet()) {
            var response = a.post("/tasks", Map.of("question", scenario.getKey(), "clientRequestId", UUID.randomUUID()), true);
            assertThat(response.statusCode()).isEqualTo(202);
            JsonNode ticket = RuleAgentTaskHttpTest.JSON.readTree(response.body()); String id = ticket.path("taskId").asText();
            if (scenario.getValue().equals("STOPPED")) {
                var cancel = Map.of("runId", ticket.path("runId").asText(), "expectedVersion", ticket.path("version").asLong(), "clientRequestId", UUID.randomUUID());
                assertThat(a.post("/tasks/" + id + "/cancel", cancel, true).statusCode()).isEqualTo(202);
            }
            var done = await(a, id, scenario.getValue());
            if (scenario.getKey().startsWith("这学期")) {
                assertThat(done.path("result").path("items").get(0).path("claims").isEmpty()).isTrue();
                assertThat(done.path("result").path("items").get(0).path("issues").get(0).path("reasonCode").asText()).isEqualTo("CURRENT_NOTICE_MISSING");
            }
        }
        a.logout(); a.login("student-a"); assertThat(a.get("/tasks/" + task).statusCode()).isEqualTo(404);
        // 只保存方法、去标识路径和状态码；不记录认证正文、Cookie、CSRF或任务续答/原文token。
        var statuses = RuleAgentTaskHttpTest.exchanges.stream().map(x -> Map.of("method", x.get("method"),
                "route", x.get("path").toString().replaceAll("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", "{opaque}"), "status", x.get("status"))).toList();
        RuleAgentTaskHttpTest.JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("proxy-http-result.json").toFile(),
                Map.of("mode", "TEST_SIMULATION_NO_PROVIDER", "proxyPort", port, "httpPassed", true, "browserExecuted", false,
                        "providerCalls", 0, "databaseCalls", 0, "checks", List.of("built /rules HTTP 200", "anonymous 401", "wrong synthetic input 401", "maintainer 403", "CSRF 403", "idempotency 202/409", "same-student different-session 404", "ACTIVE -> WAITING_USER -> RESOLVED", "stale version 409", "original 200/404", "data gap without claim", "FAILED", "cancel STOPPED", "logout invalidates task"), "exchanges", statuses));
        RuleAgentTaskHttpTest.exchanges.clear();
    }
    private JsonNode await(RuleAgentTaskHttpTest.User user, String task, String expected) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < end) {
            var response = user.get("/tasks/" + task); assertThat(response.statusCode()).isEqualTo(200);
            var snapshot = RuleAgentTaskHttpTest.JSON.readTree(response.body());
            if (snapshot.path("terminated").asBoolean()) { assertThat(snapshot.path("taskStatus").asText()).isEqualTo(expected); return snapshot; }
            Thread.sleep(100);
        }
        throw new AssertionError("fixture did not reach expected terminal status");
    }
}
