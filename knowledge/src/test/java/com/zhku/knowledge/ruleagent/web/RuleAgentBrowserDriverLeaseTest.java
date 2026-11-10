package com.zhku.knowledge.ruleagent.web;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 只验证本批次文件握手，不启动浏览器或任何外部服务。 */
class RuleAgentBrowserDriverLeaseTest {
    @TempDir Path batch;
    static final String HASH = "a".repeat(64);

    @Test void missingDriverLeaseIsRejectedBeforePreview() {
        assertThatThrownBy(() -> RuleAgentThreeQuestionHostTest.readDriverLease(batch, HASH))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void readyTicketEchoesValidatedNonceAndRetainsSchema() throws Exception {
        String nonce = "b".repeat(64);
        Files.writeString(batch.resolve("browser-driver-lease.json"),
                "{\"schemaVersion\":1,\"nonce\":\"" + nonce + "\",\"packageSha256\":\"" + HASH + "\"}");
        var oldPlan = RuleAgentThreeQuestionHostTest.plan;
        var oldHash = RuleAgentThreeQuestionHostTest.hash;
        try {
            var plan = RuleAgentThreeQuestionHostTest.JSON.createObjectNode();
            plan.putArray("cases").addObject().put("question", "synthetic");
            plan.putObject("page").put("driver", "synthetic");
            RuleAgentThreeQuestionHostTest.plan = plan;
            RuleAgentThreeQuestionHostTest.hash = HASH;
            var ready = RuleAgentThreeQuestionHostTest.readyTicket(1234, 5678,
                    RuleAgentThreeQuestionHostTest.readDriverLease(batch, HASH));
            RuleAgentThreeQuestionHostTest.writeReadyTicket(batch, ready);
            var written = RuleAgentThreeQuestionHostTest.JSON.readTree(Files.readAllBytes(batch.resolve("browser-ready.json")));
            assertThat(written).isEqualTo(ready);
            assertThatThrownBy(() -> RuleAgentThreeQuestionHostTest.writeReadyTicket(batch, ready))
                    .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            assertThat(ready.path("schemaVersion").asInt()).isEqualTo(4);
            assertThat(ready.path("driverLeaseSchemaVersion").asInt()).isEqualTo(1);
            assertThat(ready.path("driverLeaseNonce").asText()).isEqualTo(nonce);
            assertThat(ready.path("packageSha256").asText()).isEqualTo(HASH);
            assertThat(ready.path("javaPostAllowed").asBoolean(true)).isFalse();
        } finally {
            RuleAgentThreeQuestionHostTest.plan = oldPlan;
            RuleAgentThreeQuestionHostTest.hash = oldHash;
        }
    }

    @Test void existingRunMarkersRejectReusedBatch() throws Exception {
        String nonce = "b".repeat(64);
        Files.writeString(batch.resolve("browser-driver-lease.json"),
                "{\"schemaVersion\":1,\"nonce\":\"" + nonce + "\",\"packageSha256\":\"" + HASH + "\"}");
        for (String name : List.of("browser-ready.json", "STOP", "live-result.json")) {
            Path marker = batch.resolve(name);
            Files.createFile(marker);
            assertThatThrownBy(() -> RuleAgentThreeQuestionHostTest.readDriverLease(batch, HASH))
                    .as("拒绝既有 %s", name).isInstanceOf(IllegalStateException.class);
            Files.delete(marker);
        }
        assertThat(RuleAgentThreeQuestionHostTest.readDriverLease(batch, HASH)).isEqualTo(nonce);
    }

    @Test void invalidLeaseContentIsRejectedAndValidLeaseReturnsNonce() throws Exception {
        String nonce = "b".repeat(64);
        Path lease = batch.resolve("browser-driver-lease.json");
        String valid = "{\"schemaVersion\":1,\"nonce\":\"" + nonce + "\",\"packageSha256\":\"" + HASH + "\"}";
        for (String invalid : List.of(
                "{}", "not json", valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace(nonce, "B".repeat(64)), valid.replace(nonce, "b".repeat(63)),
                valid.replace(HASH, "c".repeat(64)),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"))) {
            Files.writeString(lease, invalid);
            assertThatThrownBy(() -> RuleAgentThreeQuestionHostTest.readDriverLease(batch, HASH))
                    .as("拒绝无效租约: %s", invalid).isInstanceOf(Exception.class);
        }
        Files.writeString(lease, valid);
        assertThat(RuleAgentThreeQuestionHostTest.readDriverLease(batch, HASH)).isEqualTo(nonce);
    }
}
