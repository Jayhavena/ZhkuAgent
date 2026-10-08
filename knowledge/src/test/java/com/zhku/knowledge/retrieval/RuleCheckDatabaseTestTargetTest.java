package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 目标校验不建连接；旧默认URL和伪装为临时库的URL均须失败。 */
class RuleCheckDatabaseTestTargetTest {
    @Test
    void missingTargetIsRejected() {
        reject(null); reject(""); reject(" ");
    }

    @Test
    void originalDemoDatabaseIsRejected() { reject("jdbc:postgresql://127.0.0.1:54329/zhkuagent"); }

    @Test
    void originalPortIsRejectedEvenWithTestDatabaseName() { reject(url("127.0.0.1", "54329", "zhkuagent_rulecheck_e2e")); }

    @Test
    void nonExactLoopbackHostsAreRejected() {
        for (String host : new String[]{"localhost", "remote.example", "[::1]", "127.0.0.1.example"}) {
            reject(url(host, "55439", "zhkuagent_rulecheck_e2e"));
        }
    }

    @Test
    void otherDatabaseNamesAreRejected() {
        reject(url("127.0.0.1", "55439", "zhkuagent"));
        reject(url("127.0.0.1", "55439", "zhkuagent_rulecheck_e2e_backup"));
    }

    @Test
    void QueryOptionsAndTrailingContentAreRejected() {
        String safe = url("127.0.0.1", "55439", "zhkuagent_rulecheck_e2e");
        for (String suffix : new String[]{"?options=x", "#fragment", ";other", "/", " "}) { reject(safe + suffix); }
    }

    @Test
    void invalidOrReservedPortsAreRejected() {
        for (String port : new String[]{"0", "1023", "65536", "-1", "123456"}) {
            reject(url("127.0.0.1", port, "zhkuagent_rulecheck_e2e"));
        }
    }

    @Test
    void dedicatedTemporaryTargetsAreAllowed() {
        for (String port : new String[]{"55439", "55440"}) {
            assertThatNoException().isThrownBy(() -> RuleCheckDatabaseTestTarget.verify(url("127.0.0.1", port, "zhkuagent_rulecheck_e2e")));
        }
    }

    @Test
    void rejectedSetupDoesNotConnectAgainDuringAfterEach() {
        for (String target : new String[]{null, "jdbc:postgresql://127.0.0.1:54329/zhkuagent", "jdbc:postgresql://remote.example:55439/zhkuagent_rulecheck_e2e"}) {
            var lifecycle = new JdbcCandidateRetrieverTest();
            assertThatThrownBy(() -> lifecycle.setUpAt(target)).isInstanceOf(IllegalArgumentException.class);
            assertThatNoException().isThrownBy(lifecycle::tearDown);
        }
    }

    private static void reject(String url) {
        assertThatThrownBy(() -> RuleCheckDatabaseTestTarget.verify(url)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String url(String host, String port, String database) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }
}
