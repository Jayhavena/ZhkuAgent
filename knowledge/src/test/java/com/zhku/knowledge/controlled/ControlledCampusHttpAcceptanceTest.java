package com.zhku.knowledge.controlled;

import com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** 实际产品Application、回环监听、独立Cookie与临时真库；模型仍为测试替身。 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ControlledCampusHttpAcceptanceTest {
    private static Data data;
    @BeforeAll static void seed() throws Exception { data = ControlledCampusTestFixtures.seed(); }
    @org.junit.jupiter.api.AfterAll static void cleanup() throws Exception { ControlledCampusTestFixtures.cleanup(data); }

    @Test void originalApplicationCanListenWithSyntheticModelAndAuthenticate() throws Exception {
        var model = new Model();
        // 明确将未装配的生产入口记录为行为失败，不用另一个测试Application替代。
        assertThatCode(() -> {
            try (var app = start(data, model); var anonymous = app.user(); var a = app.user()) {
                assertThat(anonymous.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(401);
                assertThat(anonymous.send("POST", PREFIX + "/runs", "{}", false).statusCode()).isEqualTo(403);
                var login = a.login(data.a());
                assertThat(login.statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(login.body()).path("userId").asText()).isEqualTo(data.a().id().toString());
                assertThat(a.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(200);
                assertThat(model.calls).hasValue(0);
            }
        }).doesNotThrowAnyException();
    }

    @Test void usersCannotReadCancelOrListEachOthersRunsAndMaintainerHasNoUserRole() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user(); var b = app.user(); var m = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            assertThat(b.login(data.b()).statusCode()).isEqualTo(200);
            assertThat(m.login(data.maintainer()).statusCode()).isEqualTo(200);
            var runA = a.submit(QUESTION, COURSES);
            var runB = b.submit(QUESTION, COURSES);
            for (var result : new com.fasterxml.jackson.databind.JsonNode[] {a.awaitTerminal(runA), b.awaitTerminal(runB)}) {
                assertThat(result.path("run").path("state").asText()).isEqualTo("SUCCEEDED");
                assertThat(result.path("resultVisibility").asText()).isEqualTo("VISIBLE");
                assertThat(result.path("run").path("answer").asText()).contains("3.5");
                assertThat(result.path("run").path("source").path("sourceId").asText()).isEqualTo(data.sourceId().toString());
                result.path("run").path("steps").forEach(step -> assertThat(step.path("detail").isNull()).isTrue());
            }
            int completedCalls = model.calls.get();
            assertThat(completedCalls).isEqualTo(4);
            for (String path : new String[]{"/runs/" + runB, "/history/" + runB, "/runs/" + UUID.randomUUID()}) {
                var response = a.send("GET", PREFIX + path, null, false);
                assertThat(response.statusCode()).isEqualTo(404);
                assertThat(response.body()).doesNotContain("3.5", data.b().id().toString(), "合成学分规则");
            }
            assertThat(a.send("POST", PREFIX + "/runs/" + runB + "/cancel", "{}", true).statusCode()).isEqualTo(404);
            var listA = JSON.readTree(a.send("GET", PREFIX + "/history?limit=1", null, false).body());
            assertThat(listA.path("items").size()).isEqualTo(1);
            assertThat(listA.path("items").get(0).path("runId").asText()).isEqualTo(runA.toString());
            for (String path : new String[]{"/runs/" + runA, "/history", "/history/" + runA})
                assertThat(m.send("GET", PREFIX + path, null, false).statusCode()).isEqualTo(403);
            assertThat(m.send("POST", PREFIX + "/runs", "{\"question\":\"合成\"}", true).statusCode()).isEqualTo(403);
            assertThat(model.calls).hasValue(completedCalls);
        }
    }

    @Test void oldRoutesUnknownIdentityFieldsAndForgedHeadersNeverStartWork() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user(); var anonymous = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            long admittedBefore = ownedCount(data.a().id());
            for (String field : new String[]{"userId", "role", "authRevision", "sessionId"}) {
                var response = a.sendWithKey("POST", PREFIX + "/runs",
                        "{\"question\":\"合成\",\"" + field + "\":\"forged\"}", true,UUID.randomUUID());
                assertThat(response.statusCode()).isEqualTo(400);
            }
            for (String path : new String[]{"/api/zhku/answers", "/api/zhku/conversations",
                    "/api/zhku/campus-agent/runs", "/api/zhku/campus-agent/history", "/api/zhku/feedback",
                    "/api/zhku/admin", "/api/login", "/api/logout", "/api/index.html", "/api/error",
                    "/api/zhku/local-preview/answers", "/api/zhku/local-preview/conversations",
                    "/api/zhku/local-preview/campus-agent/v1/runs", "/api/zhku/local-preview/campus-agent/v1/history",
                    "/api/zhku/local-preview", "/api/zhku/local-admin/submissions",
                    PREFIX + "/conversations", PREFIX + "/conversations/stream"}) {
                int code = a.send("GET", path, null, false).statusCode();
                assertThat(code == 401 || code == 403).as("未开放路径必须拒绝: %s", path).isTrue();
            }
            var forged = anonymous.client.send(java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://127.0.0.1:" + app.port + PREFIX + "/history"))
                    .timeout(java.time.Duration.ofSeconds(5)).header("userId", data.a().id().toString())
                    .header("role", "USER").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(forged.statusCode()).isEqualTo(401);
            for (String path : new String[]{"/api/zhku/local-preview/conversations", "/api/zhku/local-preview/answers",
                    "/api/zhku/local-preview/campus-agent/v1/runs", "/api/zhku/feedback",
                    "/api/zhku/local-admin/submissions", PREFIX + "/conversations"}) {
                assertThat(a.send("POST", path, "{}", true).statusCode()).isEqualTo(403);
            }
            for (String accept : new String[]{"application/json", "text/event-stream"}) {
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + app.port
                        + "/api/zhku/local-preview/conversations/" + UUID.randomUUID() + "/messages"))
                        .timeout(java.time.Duration.ofSeconds(5)).header("Accept", accept)
                        .header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"));
                assertThat(a.client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode())
                        .isEqualTo(403);
            }
            // 旧路径不带受控Cookie；其匿名CSRF会话可能回写同名Cookie，先确认此组仍是已登录拒绝。
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            assertThat(a.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(200);
            for (String method : new String[]{"GET", "PUT", "PATCH", "DELETE"})
                assertThat(a.send(method, PREFIX + "/runs", "{}", true).statusCode()).isEqualTo(403);
            assertThat(model.calls).hasValue(0);
            assertThat(ownedCount(data.a().id())).isEqualTo(admittedBefore);
        }
    }

    @Test void ownershipDoesNotBypassWithdrawnSourceOrHistoricalVisibility() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            UUID run = a.submit(QUESTION, COURSES);
            assertThat(a.awaitTerminal(run).path("resultVisibility").asText()).isEqualTo("VISIBLE");
            try {
                sourceVisible(false);
                var online = a.send("GET", PREFIX + "/runs/" + run, null, false);
                assertThat(online.statusCode()).isEqualTo(200);
                var projected = JSON.readTree(online.body());
                assertThat(projected.path("run").path("state").asText()).isEqualTo("SUCCEEDED");
                assertThat(projected.path("resultVisibility").asText()).isEqualTo("HIDDEN");
                assertThat(projected.path("run").path("answer").isNull()).isTrue();
                assertThat(projected.path("run").path("source").isNull()).isTrue();
                assertThat(online.body()).doesNotContain("3.5", "合成学分规则");
                var history = a.send("GET", PREFIX + "/history/" + run, null, false);
                assertThat(history.statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(history.body()).path("result").isNull()).isTrue();
                // 时间戳33.590…也包含3.5；检查业务字段与答案文本，不能将元数据误判为正文泄露。
                assertThat(JSON.readTree(history.body()).findValues("totalCredits")).isEmpty();
                assertThat(history.body()).doesNotContain("3.5 学分", "合成学分规则", "totalCredits");
                UUID hiddenRun = a.submit(QUESTION, COURSES);
                var failed = a.awaitTerminal(hiddenRun).path("run");
                assertThat(failed.path("state").asText()).isEqualTo("INCOMPLETE");
                assertThat(failed.path("status").asText()).isEqualTo("HANDOFF");
                assertThat(failed.path("failureCategory").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
                assertThat(model.calls).hasValue(3);
            } finally { sourceVisible(true); }
        }
    }

    @Test void revisionRevokesOldLoginAndLogoutDoesNotGrantAnotherRole() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            try {
                identityEnabled(false);
                assertThat(a.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(401);
                identityEnabled(true);
                assertThat(a.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(401);
                assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
                assertThat(a.send("POST", PREFIX + "/auth/logout", null, false).statusCode()).isEqualTo(403);
                assertThat(a.send("POST", PREFIX + "/auth/logout", null, true).statusCode()).isEqualTo(204);
                assertThat(a.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(401);
                assertThat(model.calls).hasValue(0);
            } finally { identityEnabled(true); }
        }
    }

    @Test void failedAndCancelledOutcomesKeepTheirExecutionFactsAndReleaseModelWork() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user(); var b = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            assertThat(b.login(data.b()).statusCode()).isEqualTo(200);
            model.fail = true;
            var failure = a.awaitTerminal(a.submit(QUESTION, COURSES));
            assertThat(failure.path("run").path("state").asText()).isEqualTo("FAILED");
            assertThat(failure.path("run").path("failureCategory").asText()).isEqualTo("MODEL_FAILURE");
            assertThat(failure.path("resultVisibility").asText()).isEqualTo("NOT_APPLICABLE");
            model.fail = false; model.block = true;
            UUID blocked = a.submit(QUESTION, COURSES);
            assertThat(model.entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(b.send("POST", PREFIX + "/runs/" + blocked + "/cancel", "{}", true).statusCode()).isEqualTo(404);
            assertThat(model.active).hasValue(1);
            assertThat(a.send("POST", PREFIX + "/runs/" + blocked + "/cancel", "{}", true).statusCode()).isEqualTo(200);
            var cancelled = a.awaitTerminal(blocked);
            assertThat(cancelled.path("run").path("state").asText()).isEqualTo("CANCELLED");
            assertThat(cancelled.path("run").path("stopReason").asText()).isEqualTo("USER_REQUEST");
            assertThat(model.active).hasValue(0);
        } finally { model.release.countDown(); }
    }

    @Test void identityOrArchiveUnavailableNeverFallsBackToCachedAnswer() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            var run = a.submit(QUESTION, COURSES);
            assertThat(a.awaitTerminal(run).path("resultVisibility").asText()).isEqualTo("VISIBLE");
            for (String table : new String[]{"preview_identity", "campus_agent_run_archive"}) {
                try (var connection = data.source().getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        // 真库锁屏障保证读取确实受阻；不用关容器或sleep猜故障窗口。
                        try (var lock = connection.createStatement()) {
                            lock.setQueryTimeout(1); lock.execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE");
                        }
                        var denied = a.send("GET", PREFIX + "/runs/" + run, null, false);
                        assertThat(denied.statusCode()).isEqualTo(503);
                        assertThat(denied.body()).doesNotContain("3.5", "合成学分规则", "password", "SELECT", "Exception");
                        assertThat(model.calls).hasValue(2);
                    } finally { connection.rollback(); }
                }
                assertThat(a.send("GET", PREFIX + "/runs/" + run, null, false).statusCode()).isEqualTo(200);
            }
        }
    }

    @Test void cookieRotationAndFlagsProtectLoginAndGlobalAttemptsAreBounded() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user(); var oldSession = app.user()) {
            a.refreshCsrf(); a.copyCookiesTo(oldSession);
            var before = a.cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("ZHKU_CONTROLLED_SESSION")).findFirst().orElseThrow().getValue();
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            var cookie = a.cookies.getCookieStore().getCookies().stream()
                    .filter(value -> value.getName().equals("ZHKU_CONTROLLED_SESSION")).findFirst().orElseThrow();
            // 失败报告只打印布尔值，不能把Cookie值作为AssertJ的actual写入XML。
            assertThat(cookie.getValue().equals(before)).isFalse();
            assertThat(cookie.isHttpOnly()).isTrue(); assertThat(cookie.getSecure()).isFalse();
            assertThat(cookie.getPath()).isEqualTo(PREFIX);
            assertThat(oldSession.send("GET", PREFIX + "/auth/me", null, false).statusCode()).isEqualTo(401);
            var wrong = new Account(data.a().id(), "unknown-user", "Synthetic-incorrect-password");
            var wrongExisting = new Account(data.a().id(), "user-a", wrong.password());
            var missing = a.login(wrong);
            var existing = a.login(wrongExisting);
            assertThat(missing.statusCode()).isEqualTo(401); assertThat(existing.statusCode()).isEqualTo(401);
            assertThat(JSON.readTree(missing.body()).path("code").asText())
                    .isEqualTo(JSON.readTree(existing.body()).path("code").asText());
            for (int attempt = 3; attempt < 20; attempt++) assertThat(a.login(wrong).statusCode()).isEqualTo(401);
            assertThat(a.login(data.a()).statusCode()).isEqualTo(429);
            assertThat(model.calls).hasValue(0);
        }
    }

    @Test void clarificationAndUnlistedToolRemainDomainDecisions() throws Exception {
        var model = new Model();
        try (var app = start(data, model); var a = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            model.clarify = true;
            var clarification = a.awaitTerminal(a.submit(QUESTION, "[]"));
            assertThat(clarification.path("run").path("state").asText()).isEqualTo("INCOMPLETE");
            assertThat(clarification.path("run").path("status").asText()).isEqualTo("NEED_CLARIFICATION");
            assertThat(clarification.path("run").path("stopReason").asText()).isEqualTo("NONE");
            assertThat(clarification.path("resultVisibility").asText()).isEqualTo("NOT_APPLICABLE");
            model.clarify = false; model.disallowedTool = true;
            var refused = a.awaitTerminal(a.submit(QUESTION, COURSES));
            assertThat(refused.path("run").path("state").asText()).isEqualTo("FAILED");
            assertThat(refused.path("run").path("failureCategory").asText()).isEqualTo("UNKNOWN_TOOL");
            assertThat(refused.path("run").path("steps").size()).isZero();
            assertThat(model.calls).hasValue(2);
        }
    }

    @Test @Timeout(value = 35, unit = TimeUnit.SECONDS)
    void realThirtySecondHostTimeoutIsDistinctFromUserCancellation() throws Exception {
        var model = new Model(); model.block = true;
        try (var app = start(data, model); var a = app.user()) {
            assertThat(a.login(data.a()).statusCode()).isEqualTo(200);
            UUID run = a.submit(QUESTION, COURSES);
            assertThat(model.entered.await(5, TimeUnit.SECONDS)).isTrue();
            var timedOut = a.awaitTerminal(run, java.time.Duration.ofSeconds(35));
            assertThat(timedOut.path("run").path("state").asText()).isEqualTo("CANCELLED");
            assertThat(timedOut.path("run").path("stopReason").asText()).isEqualTo("RUN_TIMEOUT");
            assertThat(timedOut.path("resultVisibility").asText()).isEqualTo("NOT_APPLICABLE");
            assertThat(model.active).hasValue(0);
        } finally { model.release.countDown(); }
    }

    private static void sourceVisible(boolean visible) throws Exception {
        try (var connection = data.source().getConnection(); var statement = connection.prepareStatement(
                "UPDATE knowledge_source SET visibility_scope=? WHERE id=?")) {
            statement.setString(1, visible ? "STUDENT_VISIBLE" : "INTERNAL_ONLY");
            statement.setObject(2, data.sourceId()); statement.setQueryTimeout(1); statement.executeUpdate();
        }
    }
    private static long ownedCount(UUID actor) throws Exception {
        try (var connection = data.source().getConnection(); var query = connection.prepareStatement(
                "SELECT count(*) FROM campus_agent_run_archive WHERE owner_user_id=?")) {
            query.setObject(1, actor); query.setQueryTimeout(1);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }
    private static void identityEnabled(boolean enabled) throws Exception {
        try (var connection = data.source().getConnection(); var statement = connection.prepareStatement(
                "UPDATE preview_identity SET enabled=?,auth_revision=auth_revision+1 WHERE user_id=?")) {
            statement.setBoolean(1, enabled); statement.setObject(2, data.a().id());
            statement.setQueryTimeout(1); statement.executeUpdate();
        }
    }
}
