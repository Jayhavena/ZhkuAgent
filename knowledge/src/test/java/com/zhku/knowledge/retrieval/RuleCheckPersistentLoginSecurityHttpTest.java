package com.zhku.knowledge.retrieval;

import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;
import static org.assertj.core.api.Assertions.*;

/** 真实 HTTP 与 PostgreSQL：凭据恢复和撤销的外部安全合同。 */
@Timeout(90)
class RuleCheckPersistentLoginSecurityHttpTest {
    private static final String REMEMBER = "ZHKU_RULE_CHECK_REMEMBER";

    @Test void concurrentRecoveryDoesNotLoseEveryValidLogin() throws Exception {
        withApp(app -> {
            var account = app.accounts().get(0);
            try (var initial = new User(app.port()); var first = new User(app.port()); var second = new User(app.port())) {
                initial.csrf();
                assertThat(login(initial, account, true).statusCode()).isEqualTo(200);
                String original = remember(initial);
                putRemember(first, original);
                putRemember(second, original);
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var workers = Executors.newFixedThreadPool(2);
                try {
                    var a = workers.submit(() -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("并发起跑超时"); return first.get("/auth/me"); });
                    var b = workers.submit(() -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("并发起跑超时"); return second.get("/auth/me"); });
                    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                    start.countDown();
                    var results = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
                    assertThat(results).extracting(HttpResponse::statusCode).contains(200).allMatch(code -> code == 200 || code == 401);
                    for (var result : results) if (result.statusCode() == 200)
                        assertThat(JSON.readTree(result.body()).path("userId").asText()).isEqualTo(account.id().toString());
                    // 允许实现选择单次消费或复用；但成功恢复的浏览器必须拥有可用会话。
                    User winner = results.get(0).statusCode() == 200 ? first : second;
                    assertThat(winner.get("/auth/me").statusCode()).isEqualTo(200);
                } finally {
                    start.countDown();
                    workers.shutdownNow();
                    assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                }
            }
        });
    }

    @Test void logoutRevokesCopiedPersistentCredentialNotJustBrowserCookie() throws Exception {
        withApp(app -> {
            var account = app.accounts().get(0);
            try (var user = new User(app.port()); var restored = new User(app.port()); var replay = new User(app.port()); var stale = new User(app.port())) {
                user.csrf();
                assertThat(login(user, account, true).statusCode()).isEqualTo(200);
                String original = remember(user);
                putRemember(restored, original);
                assertThat(restored.get("/auth/me").statusCode()).isEqualTo(200);
                String current = remember(restored);
                restored.csrf();
                assertThat(restored.post("/auth/logout", "", true).statusCode()).isEqualTo(204);
                assertThat(restored.get("/auth/me").statusCode()).isEqualTo(401);
                // 分别在全新客户端重放恢复前后凭据，避免仅验证浏览器删除 Cookie。
                putRemember(replay, current);
                putRemember(stale, original);
                assertThat(replay.get("/auth/me").statusCode()).isEqualTo(401);
                assertThat(stale.get("/auth/me").statusCode()).isEqualTo(401);
            }
        });
    }

    @Test void logoutRevokesSessionCredentialWhenRememberCookieIsMissing() throws Exception {
        assertLogoutRevokesSessionCredential(false);
    }

    @Test void logoutRevokesSessionCredentialWhenRememberCookieIsReplaced() throws Exception {
        assertLogoutRevokesSessionCredential(true);
    }

    private static void assertLogoutRevokesSessionCredential(boolean replaceCookie) throws Exception {
        withApp(app -> {
            var account = app.accounts().get(0);
            try (var user = new User(app.port()); var replay = new User(app.port())) {
                user.csrf();
                assertThat(login(user, account, true).statusCode()).isEqualTo(200);
                String original = remember(user);
                user.csrf();
                var store = user.cookies.getCookieStore();
                var uri = URI.create("http://127.0.0.1:" + user.port + PREFIX);
                var cookie = store.get(uri).stream().filter(c -> REMEMBER.equals(c.getName())).findFirst().orElseThrow();
                assertThat(store.remove(uri, cookie)).isTrue();
                if (replaceCookie) putRemember(user, "A".repeat(43));
                // 保留登录会话，但不让注销请求携带原长期凭据，验证服务端仍撤销会话绑定的凭据。
                assertThat(user.get("/auth/me").statusCode()).isEqualTo(200);
                assertThat(user.post("/auth/logout", "", true).statusCode()).isEqualTo(204);
                putRemember(replay, original);
                assertThat(replay.get("/auth/me").statusCode()).isEqualTo(401);
            }
        });
    }

    @Test void passwordChangeRevokesOtherSessionAndPersistentCredential() throws Exception {
        withApp(app -> {
            var account = app.accounts().get(0);
            String replacement = "Synthetic-" + UUID.randomUUID();
            try (var changer = new User(app.port()); var other = new User(app.port()); var replay = new User(app.port()); var fresh = new User(app.port())) {
                changer.csrf(); other.csrf();
                assertThat(login(changer, account, false).statusCode()).isEqualTo(200);
                assertThat(login(other, account, true).statusCode()).isEqualTo(200);
                String copied = remember(other);
                assertThat(other.get("/auth/me").statusCode()).isEqualTo(200);
                changer.csrf();
                String body = JSON.writeValueAsString(Map.of("currentPassword", account.password(), "newPassword", replacement, "confirmPassword", replacement));
                assertThat(changePassword(changer, body).statusCode()).isEqualTo(204);
                // 同时检查已建立的会话和全新浏览器中的长期凭据，覆盖两条撤销路径。
                assertThat(other.get("/auth/me").statusCode()).isEqualTo(401);
                putRemember(replay, copied);
                assertThat(replay.get("/auth/me").statusCode()).isEqualTo(401);
                fresh.csrf();
                assertThat(login(fresh, account.login(), account.password(), false).statusCode()).isEqualTo(401);
                fresh.csrf();
                assertThat(login(fresh, account.login(), replacement, false).statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(fresh.get("/auth/me").body()).path("userId").asText()).isEqualTo(account.id().toString());
            }
        });
    }

    private static HttpResponse<String> login(User user, Account account, boolean remember) throws Exception {
        return login(user, account.login(), account.password(), remember);
    }

    private static HttpResponse<String> login(User user, String name, String password, boolean remember) throws Exception {
        String form = "username=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8) + "&remember-me=" + remember;
        return user.post("/auth/login", form, true);
    }

    private static String remember(User user) {
        var cookie = user.cookies.getCookieStore().getCookies().stream().filter(c -> REMEMBER.equals(c.getName())).findFirst().orElseThrow();
        assertThat(cookie.getValue().isBlank()).isFalse();
        return cookie.getValue();
    }

    private static void putRemember(User user, String value) {
        var cookie = new HttpCookie(REMEMBER, value);
        cookie.setPath(PREFIX);
        user.cookies.getCookieStore().add(URI.create("http://127.0.0.1:" + user.port + PREFIX), cookie);
    }

    private static HttpResponse<String> changePassword(User user, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + user.port + PREFIX + "/auth/password"))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", user.token).PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        return user.client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
