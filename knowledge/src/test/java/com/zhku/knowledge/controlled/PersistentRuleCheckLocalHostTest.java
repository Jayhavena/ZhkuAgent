package com.zhku.knowledge.controlled;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PersistentRuleCheckLocalHostTest {
    private static final Map<String, String> ENV = Map.of(
            "ZHKU_RULECHECK_ROOT", ".", "ZHKU_RULECHECK_PORT", "8124",
            "ZHKU_DB_URL", "jdbc:postgresql://127.0.0.1:15432/zhkuagent_rulecheck_e2e",
            "ZHKU_RULECHECK_IDENTITY_DB_URL", "jdbc:postgresql://127.0.0.1:15433/zhkuagent_rulecheck_identity");

    @Test void explicitlySelectsTwoDatabasesAndSealedReplayWithoutPassingCredentials() {
        List<String> options = List.of(PersistentRuleCheckLocalHost.options(new String[0], ENV::get));
        assertThat(options).contains("--zhku.rule-check-preview.persistent-identity.enabled=true",
                "--zhku.rule-check-preview.persistent-identity.url=" + ENV.get("ZHKU_RULECHECK_IDENTITY_DB_URL"),
                "--zhku.db.url=" + ENV.get("ZHKU_DB_URL"),
                "--zhku.rule-check-preview.selector-mode=SEALED_REPLAY");
        assertThat(options).noneMatch(option -> option.toLowerCase().contains("password"));
    }

    @Test void taskReplayRequiresAndForwardsBothPinnedDigests() {
        var environment = new java.util.HashMap<>(ENV);
        environment.put("ZHKU_RULECHECK_TASK_BATCH_DIRECTORY", "batch");
        environment.put("ZHKU_RULECHECK_TASK_PACKAGE_SHA256", "package-digest");
        environment.put("ZHKU_RULECHECK_TASK_SUMMARY_SHA256", "summary-digest");
        assertThat(List.of(PersistentRuleCheckLocalHost.options(new String[0], environment::get)))
                .contains("--zhku.rule-check-preview.selector-mode=TASK_PLAN_REPLAY",
                        "--zhku.rule-check-preview.task-batch-directory=batch",
                        "--zhku.rule-check-preview.task-package-sha256=package-digest",
                        "--zhku.rule-check-preview.task-summary-sha256=summary-digest");
    }

    @Test void rejectsArgumentsMissingConfigurationAndInvalidPortBeforeStarting() {
        assertThatThrownBy(() -> PersistentRuleCheckLocalHost.options(new String[]{"--server.address=0.0.0.0"}, ENV::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersistentRuleCheckLocalHost.options(new String[0], key -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersistentRuleCheckLocalHost.options(new String[0], key ->
                key.equals("ZHKU_RULECHECK_PORT") ? "80" : ENV.get(key))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PersistentRuleCheckLocalHost.options(new String[0], key ->
                key.equals("ZHKU_RULECHECK_TASK_BATCH_DIRECTORY") ? "batch" : ENV.get(key)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ZHKU_RULECHECK_TASK_PACKAGE_SHA256");
    }

    @Test void closesContextOnEofStopAndReadFailure() throws Exception {
        for (Reader input : List.of(new StringReader(""), new StringReader("ignored\nSTOP\n"), new Reader() {
            @Override public int read(char[] chars, int offset, int length) throws IOException { throw new IOException("read failed"); }
            @Override public void close() { }
        })) {
            var closed = new AtomicBoolean();
            try {
                PersistentRuleCheckLocalHost.run(new String[0], ENV::get, input, args -> () -> closed.set(true));
            } catch (IOException expected) {
                assertThat(expected).hasMessage("read failed");
            }
            assertThat(closed).isTrue();
        }
    }
}
