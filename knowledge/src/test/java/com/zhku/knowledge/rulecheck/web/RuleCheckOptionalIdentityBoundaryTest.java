package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.security.PreviewIdentityRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅验证进程属性和装配边界，不连接数据库；真库持久性需要独立隔离验收。 */
class RuleCheckOptionalIdentityBoundaryTest {
    private static final String PREFIX = "zhku.rule-check-preview.persistent-identity.";
    private static final String IDENTITY_URL = "jdbc:postgresql://127.0.0.1:55463/zhkuagent_rulecheck_identity";

    @Test void absentOrDisabledIdentityKeepsOriginalDataSource() throws Exception {
        for (boolean explicitlyDisabled : new boolean[] {false, true}) {
            MockEnvironment env = RuleCheckPreviewConfigurationTest.valid();
            if (explicitlyDisabled) env.setProperty(PREFIX + "enabled", "false");
            assertThatCode(() -> RuleCheckPreviewConfiguration.validateEnvironment(env)).doesNotThrowAnyException();
            DataSource ruleSource = mock(DataSource.class);
            when(ruleSource.getConnection()).thenThrow(new SQLException("offline"));
            try (var repository = new RuleCheckPreviewConfiguration().ruleCheckIdentityRepository(ruleSource, null, env)) {
                assertThatThrownBy(() -> repository.authenticate("user-test", "test-password-123".toCharArray()))
                        .isInstanceOf(PreviewIdentityRepository.Failure.class);
                verify(ruleSource, timeout(3000)).getConnection();
            }
        }
    }

    @Test void explicitIdentityValidationRejectsUnsafeTargetsWithoutConnecting() throws Exception {
        // 子进程中注入合成凭据，使合法目标正向通过而无须更改当前进程或使用真实密码。
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classpath, ValidationProbe.class.getName());
        builder.redirectErrorStream(true);
        builder.environment().put("ZHKU_RULECHECK_IDENTITY_DB_USER", "identity_test_user");
        builder.environment().put("ZHKU_RULECHECK_IDENTITY_DB_PASSWORD", "synthetic-probe-password");
        Process process = builder.start();
        boolean exited = process.waitFor(30, TimeUnit.SECONDS);
        if (!exited) process.destroyForcibly();
        assertThat(exited).as("validation subprocess exited").isTrue();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("synthetic-probe-password");
        assertThat(process.exitValue()).as("validation probe: %s", output).isZero();
    }

    public static final class ValidationProbe {
        public static void main(String[] args) {
            MockEnvironment valid = environment(IDENTITY_URL);
            RuleCheckPreviewConfiguration.validateEnvironment(valid);
            for (String url : List.of(
                    "", "jdbc:postgresql://localhost:55463/zhkuagent_rulecheck_identity",
                    "jdbc:postgresql://127.0.0.1:54329/zhkuagent_rulecheck_identity",
                    "jdbc:postgresql://127.0.0.1:55462/zhkuagent_rulecheck_identity",
                    "jdbc:postgresql://127.0.0.1:1023/zhkuagent_rulecheck_identity",
                    "jdbc:postgresql://127.0.0.1:65536/zhkuagent_rulecheck_identity",
                    "jdbc:postgresql://127.0.0.1:55463/zhkuagent_rulecheck_e2e",
                    IDENTITY_URL + "?password=do-not-echo",
                    "jdbc:postgresql://do-not-echo@127.0.0.1:55463/zhkuagent_rulecheck_identity")) {
                reject(environment(url));
            }
            MockEnvironment invalidSwitch = environment(IDENTITY_URL);
            invalidSwitch.setProperty(PREFIX + "enabled", "TRUE");
            reject(invalidSwitch);
        }

        private static MockEnvironment environment(String url) {
            return RuleCheckPreviewConfigurationTest.valid().withProperty(PREFIX + "enabled", "true")
                    .withProperty(PREFIX + "url", url);
        }

        private static void reject(MockEnvironment env) {
            try {
                RuleCheckPreviewConfiguration.validateEnvironment(env);
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage() != null && expected.getMessage().contains("do-not-echo"))
                    throw new AssertionError("target leaked in diagnostic");
                return;
            }
            throw new AssertionError("unsafe target or switch accepted");
        }
    }
}
