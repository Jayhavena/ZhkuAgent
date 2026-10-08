package com.zhku.knowledge.controlled;

import com.zhku.knowledge.web.ZhkuLocalPreviewApplication;
import com.zhku.knowledge.governance.web.ZhkuLocalAdminApplication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import static org.assertj.core.api.Assertions.*;

/** 静态排除及原入口实际HTTP；此处没有受控身份或模型夹具。 */
class LegacyPreviewSecurityCompatibilityTest {
    @org.junit.jupiter.api.BeforeAll static void migrate() {
        com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase.migrate();
    }
    @ParameterizedTest
    @ValueSource(classes = { ZhkuLocalPreviewApplication.class, ZhkuLocalAdminApplication.class })
    void legacyEntryExplicitlyExcludesDefaultLoginAndGeneratedAccount(Class<?> entry) {
        assertThat(entry.getAnnotation(SpringBootApplication.class).exclude())
                .contains(SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class);
    }

    @org.junit.jupiter.api.Timeout(30)
    @ParameterizedTest
    @ValueSource(classes = { ZhkuLocalPreviewApplication.class, ZhkuLocalAdminApplication.class })
    void legacyEntryStillServesItsOriginalAnonymousHttpWithoutDefaultLogin(Class<?> entry) throws Exception {
        boolean admin = entry == ZhkuLocalAdminApplication.class;
        var application = new org.springframework.boot.SpringApplication(entry);
        application.setAdditionalProfiles(admin ? "zhku-local-admin" : "zhku-local-preview");
        try (var context = (org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) application.run(
                "--server.port=0", "--server.address=127.0.0.1", "--server.servlet.context-path=/api",
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--zhku.local-preview.enabled=" + !admin, "--zhku.local-admin.enabled=" + admin,
                "--zhku.controlled-preview.enabled=false", "--zhku.campus-agent.enabled=false",
                "--zhku.campus-agent.archive.enabled=false", "--zhku.rag.enabled=false", "--zhku.streaming.enabled=false");
             var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(1))
                     .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()) {
            int port = context.getWebServer().getPort();
            String suffix = admin ? "/api/zhku/local-admin/submissions" : "/api/zhku/local-preview/conversations";
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + suffix))
                    .timeout(java.time.Duration.ofSeconds(5));
            if (admin) request.GET(); else request.POST(java.net.http.HttpRequest.BodyPublishers.noBody());
            var response = client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("WWW-Authenticate").isPresent()).isFalse();
            assertThat(response.headers().firstValue("Location").isPresent()).isFalse();
            assertThat(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)).isEmpty();
            assertThat(context.getBeansOfType(com.zhku.knowledge.security.PreviewIdentityRepository.class)).isEmpty();
            assertThat(context.getBeansOfType(org.springframework.ai.chat.model.ChatModel.class)).isEmpty();
        }
    }
}
