package com.zhku.knowledge.retrieval;

import java.net.HttpCookie;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 通过隔离库与真实 HTTP 验证活动会话的长期凭据续期。 */
@Timeout(90)
class RuleCheckSlidingLoginHttpTest {
    private static final String REMEMBER = "ZHKU_RULE_CHECK_REMEMBER";

    @Test void rememberedActiveSessionSlidesExpiryAndRefreshesCookie() throws Exception {
        String name = randomName(), password = "Synthetic-" + UUID.randomUUID();
        withApp(app -> {
            try (var user = new User(app.port())) {
                try {
                    registerAndLogin(user, name, password, true);
                    Instant shortened = shortenExpiry(app, name);
                    // 保留原有会话及 Cookie，避免走无会话时的凭据恢复分支。
                    var response = user.get("/auth/me");
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(JSON.readTree(response.body()).path("username").asText()).isEqualTo(name);
                    Instant renewed = expiry(app, name);
                    assertThat(renewed).isAfter(shortened.plusSeconds(24 * 3600));
                    assertThat(renewed).isBetween(Instant.now().plusSeconds(29L * 86400 + 23 * 3600),
                            Instant.now().plusSeconds(30L * 86400 + 3600));
                    assertThat(hasPersistentCookie(response)).isTrue();
                } finally { cleanup(app, name); }
            }
        });
    }

    @Test void loginWithoutRememberDoesNotCreatePersistentCredential() throws Exception {
        String name = randomName(), password = "Synthetic-" + UUID.randomUUID();
        withApp(app -> {
            try (var user = new User(app.port())) {
                try {
                    registerAndLogin(user, name, password, false);
                    var response = user.get("/auth/me");
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(JSON.readTree(response.body()).path("username").asText()).isEqualTo(name);
                    assertThat(hasPersistentCookie(response)).isFalse();
                    try (var c = app.source().getConnection(); var p = c.prepareStatement(
                            "SELECT count(*) FROM preview_persistent_login p JOIN preview_identity i ON i.user_id=p.user_id WHERE i.login_name=?")) {
                        p.setString(1, name);
                        try (var rows = p.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getLong(1)).isZero();
                        }
                    }
                } finally { cleanup(app, name); }
            }
        });
    }

    private static String randomName() {
        return "slide-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private static void registerAndLogin(User user, String name, String password, boolean remember) throws Exception {
        user.csrf();
        String body = JSON.writeValueAsString(Map.of("username", name, "password", password, "confirmPassword", password));
        assertThat(user.encoded(PREFIX + "/auth/register", "application/json", body, true).statusCode()).isEqualTo(201);
        String form = "username=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8) + "&remember-me=" + remember;
        assertThat(user.post("/auth/login", form, true).statusCode()).isEqualTo(200);
    }

    private static Instant shortenExpiry(App app, String name) throws Exception {
        // 只按本用例的随机身份更新，不读取凭据摘要或明文。
        try (var c = app.source().getConnection(); var p = c.prepareStatement(
                "UPDATE preview_persistent_login p SET expires_at=clock_timestamp()+interval '28 days' FROM preview_identity i WHERE p.user_id=i.user_id AND i.login_name=? RETURNING p.expires_at")) {
            p.setString(1, name);
            try (var rows = p.executeQuery()) {
                assertThat(rows.next()).isTrue();
                Instant result = rows.getTimestamp(1).toInstant();
                assertThat(rows.next()).isFalse();
                return result;
            }
        }
    }

    private static Instant expiry(App app, String name) throws Exception {
        try (var c = app.source().getConnection(); var p = c.prepareStatement(
                "SELECT p.expires_at FROM preview_persistent_login p JOIN preview_identity i ON i.user_id=p.user_id WHERE i.login_name=?")) {
            p.setString(1, name);
            try (var rows = p.executeQuery()) {
                assertThat(rows.next()).isTrue();
                Instant result = rows.getTimestamp(1).toInstant();
                assertThat(rows.next()).isFalse();
                return result;
            }
        }
    }

    private static boolean hasPersistentCookie(HttpResponse<?> response) {
        // 只断言元数据及存在性，不把 Set-Cookie 的凭据值送入断言输出。
        return response.headers().allValues("Set-Cookie").stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .anyMatch(cookie -> REMEMBER.equals(cookie.getName()) && cookie.getMaxAge() >= 30L * 86400 - 60
                        && PREFIX.equals(cookie.getPath()) && cookie.isHttpOnly());
    }

    private static void cleanup(App app, String name) throws Exception {
        try (var c = app.source().getConnection(); var p = c.prepareStatement("DELETE FROM preview_identity WHERE login_name=?")) {
            p.setString(1, name);
            p.executeUpdate();
        }
    }
}
