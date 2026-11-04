package com.zhku.knowledge.retrieval;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;
import static org.assertj.core.api.Assertions.*;

/** 真实HTTP注册账号；应用、迁移和连接前的专用临时库守卫均复用既有夹具。 */
@Timeout(90)
class RuleCheckRegisteredAccountHttpTest {
    private static String name() { return "reg-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }
    private static String secret() { return "Synthetic-" + UUID.randomUUID(); }

    @Test void registrationRequiresCsrfRejectsInvalidConfirmationAndDuplicateThenLoginWorks() throws Exception {
        String name = name(), password = secret();
        withApp(app -> {
            try (var user = new User(app.port())) {
                try {
                    user.csrf();
                    assertThat(register(user, name, password, password, false).statusCode()).isEqualTo(403);
                    assertThat(register(user, "!invalid", password, password, true).statusCode()).isEqualTo(400);
                    assertThat(register(user, name, "short", "short", true).statusCode()).isEqualTo(400);
                    assertThat(register(user, name, password, secret(), true).statusCode()).isEqualTo(400);
                    assertThat(register(user, name, password, password, true).statusCode()).isEqualTo(201);
                    assertThat(register(user, name, password, password, true).statusCode()).isEqualTo(409);
                    // 注册不等于登录，不能凭注册响应获得会话权限。
                    assertThat(user.get("/auth/me").statusCode()).isEqualTo(401);
                    assertThat(login(user, name, secret(), false).statusCode()).isEqualTo(401);
                    assertThat(login(user, name, password, false).statusCode()).isEqualTo(200);
                    var response = user.get("/auth/me");
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
                    var me = JSON.readTree(response.body());
                    UUID.fromString(me.path("userId").asText());
                    assertThat(me.path("username").asText()).isEqualTo(name);
                    assertThat(me.path("accountStatus").asText()).isNotBlank();
                    Instant.parse(me.path("createdAt").asText());
                    assertThat(response.body().contains(password)).isFalse();
                    assertStoredUser(app, name, password, me.path("userId").asText());
                    user.csrf();
                    assertThat(user.post("/auth/logout", "", false).statusCode()).isEqualTo(403);
                    assertThat(user.post("/auth/logout", "", true).statusCode()).isEqualTo(204);
                    assertThat(user.get("/auth/me").statusCode()).isEqualTo(401);
                } finally { cleanup(app, name); }
            }
        });
    }

    @Test void registeredUsersHaveSeparateSessionsAndCannotUseEachOthersContinuation() throws Exception {
        String first = name(), second = name(), firstPassword = secret(), secondPassword = secret();
        withApp(true, app -> {
            try (var a = new User(app.port()); var b = new User(app.port())) {
                try {
                    a.csrf(); b.csrf();
                    assertThat(register(a, first, firstPassword, firstPassword, true).statusCode()).isEqualTo(201);
                    assertThat(register(b, second, secondPassword, secondPassword, true).statusCode()).isEqualTo(201);
                    assertThat(login(a, first, firstPassword, false).statusCode()).isEqualTo(200);
                    assertThat(login(b, second, secondPassword, false).statusCode()).isEqualTo(200);
                    var firstMe = JSON.readTree(a.get("/auth/me").body());
                    var secondMe = JSON.readTree(b.get("/auth/me").body());
                    assertThat(firstMe.path("username").asText()).isEqualTo(first);
                    assertThat(secondMe.path("username").asText()).isEqualTo(second);
                    assertThat(firstMe.path("userId").asText()).isNotEqualTo(secondMe.path("userId").asText());
                    a.csrf(); b.csrf();
                    String continuation = clarificationToken(a, "q");
                    assertThat(b.json(continuationBody("q", continuation), true, false).statusCode()).isEqualTo(409);
                    assertThat(a.json(continuationBody("q", continuation), true, false).statusCode()).isEqualTo(200);
                    assertThat(a.post("/auth/logout", "", true).statusCode()).isEqualTo(204);
                    assertThat(a.get("/auth/me").statusCode()).isEqualTo(401);
                    assertThat(b.get("/auth/me").statusCode()).isEqualTo(200);
                } finally { cleanup(app, first, second); }
            }
        });
    }

    @Test void rememberMeRestoresSessionAndLogoutRevokesPersistentCookie() throws Exception {
        String name = name(), password = secret();
        withApp(app -> {
            try (var user = new User(app.port())) {
                try {
                    user.csrf();
                    assertThat(register(user, name, password, password, true).statusCode()).isEqualTo(201);
                    assertThat(login(user, name, password, true).statusCode()).isEqualTo(200);
                    assertThat(user.cookies.getCookieStore().getCookies().stream()
                            .anyMatch(c -> c.getName().equals("ZHKU_RULE_CHECK_REMEMBER") && c.getMaxAge() > 0)).isTrue();
                    // 模拟浏览器会话消失，不删除长期Cookie；恢复必须走真实HTTP过滤器。
                    user.cookies.getCookieStore().getCookies().stream()
                            .filter(c -> c.getName().equals("ZHKU_RULE_CHECK_SESSION"))
                            .findFirst().ifPresent(c -> user.cookies.getCookieStore().remove(null, c));
                    var restored = user.get("/auth/me");
                    assertThat(restored.statusCode()).isEqualTo(200);
                    assertThat(JSON.readTree(restored.body()).path("username").asText()).isEqualTo(name);
                    user.csrf();
                    assertThat(user.post("/auth/logout", "", true).statusCode()).isEqualTo(204);
                    assertThat(user.get("/auth/me").statusCode()).isEqualTo(401);
                } finally { cleanup(app, name); }
            }
        });
    }

    private static HttpResponse<String> register(User user, String name, String password, String confirmation, boolean csrf) throws Exception {
        String json = JSON.writeValueAsString(Map.of("username", name, "password", password, "confirmPassword", confirmation));
        return user.encoded(PREFIX + "/auth/register", "application/json", json, csrf);
    }

    private static HttpResponse<String> login(User user, String name, String password, boolean remember) throws Exception {
        String form = "username=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&remember-me=" + remember;
        return user.post("/auth/login", form, true);
    }

    private static void assertStoredUser(App app, String name, String password, String id) throws Exception {
        try (var c = app.source().getConnection(); var p = c.prepareStatement(
                "SELECT user_id,password_hash,role,enabled FROM preview_identity WHERE login_name=?")) {
            p.setString(1, name);
            try (var row = p.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject(1, UUID.class).toString()).isEqualTo(id);
                String hash = row.getString(2);
                // 不把明文或哈希作为断言的显示值，避免失败日志泄露凭据材料。
                assertThat(hash != null && !hash.equals(password) && new BCryptPasswordEncoder().matches(password, hash)).isTrue();
                assertThat(row.getString(3)).isEqualTo("USER");
                assertThat(row.getBoolean(4)).isTrue();
                assertThat(row.next()).isFalse();
            }
        }
    }

    private static void cleanup(App app, String... names) throws Exception {
        // 仅清除此用例生成的随机身份，不清表；连接只能来自已通过现有目标守卫的夹具。
        try (var c = app.source().getConnection(); var p = c.prepareStatement("DELETE FROM preview_identity WHERE login_name=?")) {
            for (String name : names) { p.setString(1, name); p.executeUpdate(); }
        }
    }
}
