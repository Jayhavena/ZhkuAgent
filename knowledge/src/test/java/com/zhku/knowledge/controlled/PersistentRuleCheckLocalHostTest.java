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
    @Test void configuredOnlineEntryIsExplicitAndNeverPutsKeyInArguments() {
        var env=new java.util.HashMap<>(ENV);
        env.putAll(Map.of("ZHKU_STUDENT_EXECUTION_MODE","ONLINE","ZHKU_RULE_AGENT_MODE","ONLINE",
            "DASHSCOPE_API_KEY","synthetic-key-never-sent","ZHKU_RULE_AGENT_AUTHORIZATION_ID","TEST-CONFIGURATION-ONLY",
            "ZHKU_RULE_AGENT_CALL_ALLOWANCE","2"));
        var args=List.of(PersistentRuleCheckLocalHost.options(new String[0],env::get));
        assertThat(args).contains("--zhku.rule-agent-launch.executor=ONLINE","--zhku.rule-agent-http.online-enabled=true");
        assertThat(args).noneMatch(x->x.contains("synthetic-key")||x.contains("DASHSCOPE_API_KEY"));
        env.put("ZHKU_RULE_AGENT_CALL_ALLOWANCE","25");
        assertThatThrownBy(()->PersistentRuleCheckLocalHost.options(new String[0],env::get)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void stopDrainsTasksBeforeClosingApplicationResources() throws Exception {
        var app=new org.springframework.context.support.GenericApplicationContext();
        var tasks=org.mockito.Mockito.mock(com.zhku.knowledge.ruleagent.RuleAgentTaskStore.class);
        var executor=org.mockito.Mockito.mock(java.util.concurrent.ExecutorService.class);
        org.mockito.Mockito.when(executor.awaitTermination(25,java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        app.getBeanFactory().registerSingleton("tasks",tasks);
        app.getBeanFactory().registerSingleton("ruleAgentHttpExecutor",executor);app.refresh();
        PersistentRuleCheckLocalHost.run(new String[0],ENV::get,new StringReader("STOP\n"),args->app);
        var order=org.mockito.Mockito.inOrder(tasks,executor);
        order.verify(tasks).close();order.verify(executor).shutdownNow();order.verify(executor).awaitTermination(25,java.util.concurrent.TimeUnit.SECONDS);
        assertThat(app.isActive()).isFalse();
    }
    @Test void persistentStudentEntryEnablesTasksWithSimulatedExecutionOnlyByDefault() {
        assertThat(List.of(PersistentRuleCheckLocalHost.options(new String[0], ENV::get)))
            .contains("--zhku.persistent-student.enabled=true","--zhku.rule-agent-http.enabled=true",
                "--zhku.rule-agent-launch.executor=SIMULATED","--zhku.rule-agent-http.online-enabled=false",
                "--zhku.rule-agent-http.reviewed-tools-enabled=true");
    }
    @Test void onlineModeRequiresExplicitProviderConfigurationBeforeStarting() {
        var env=new java.util.HashMap<>(ENV);env.put("ZHKU_STUDENT_EXECUTION_MODE","ONLINE");
        assertThatThrownBy(()->PersistentRuleCheckLocalHost.options(new String[0],env::get))
            .isInstanceOf(IllegalArgumentException.class);
        env.put("ZHKU_STUDENT_EXECUTION_MODE","unexpected");
        assertThatThrownBy(()->PersistentRuleCheckLocalHost.options(new String[0],env::get))
            .isInstanceOf(IllegalArgumentException.class);
    }
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
