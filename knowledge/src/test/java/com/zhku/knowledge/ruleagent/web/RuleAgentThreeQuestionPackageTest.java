package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 纯本地审包/金额回归，不创建数据库、HTTP客户端或Provider。 */
class RuleAgentThreeQuestionPackageTest {
    @TempDir Path temp;
    private JsonNode plan() throws Exception {
        try (var stream = getClass().getResourceAsStream("/rule-agent-three-question-plan.json")) {
            return RuleAgentThreeQuestionPackage.JSON.readTree(stream);
        }
    }
    private Clock at(String time) { return Clock.fixed(Instant.parse(time), ZoneOffset.UTC); }
    @Test void reservesWholeContextAndTenExtraOutputTokensWithoutCacheAssumptions() throws Exception {
        JsonNode p = plan();
        assertThat(RuleAgentThreeQuestionPackage.perCallReserve(p)).isEqualByComparingTo("0.8027918");
        assertThat(RuleAgentThreeQuestionPackage.perCallReserve(p).multiply(BigDecimal.valueOf(20))).isEqualByComparingTo("16.055836");
        assertThatCode(() -> RuleAgentThreeQuestionPackage.tariff(p, at("2026-10-26T08:00:00Z"))).doesNotThrowAnyException();
    }
    @Test void rejectsExpiredTariffAndClockBeforeCheckedDate() throws Exception {
        JsonNode p = plan();
        assertThatThrownBy(() -> RuleAgentThreeQuestionPackage.tariff(p, at("2026-10-28T00:00:00Z"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> RuleAgentThreeQuestionPackage.tariff(p, at("2026-10-25T00:00:00Z"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void insufficientMoneyOrUnmatchedReserveNeverPasses() throws Exception {
        JsonNode p = plan(); ((ObjectNode)p.path("money")).put("hardBudget", "1.11");
        assertThatThrownBy(() -> RuleAgentThreeQuestionPackage.tariff(p, at("2026-10-26T08:00:00Z"))).isInstanceOf(IllegalStateException.class);
        JsonNode changed = plan(); ((ObjectNode)changed.path("money")).put("worstCaseReserved", "0");
        assertThatThrownBy(() -> RuleAgentThreeQuestionPackage.tariff(changed, at("2026-10-26T08:00:00Z"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void byteShaTamperRejectedBeforeAnyAssetsOrDatabaseAccess() throws Exception {
        Files.writeString(temp.resolve("request-package.json"), "{}");
        assertThatThrownBy(() -> RuleAgentThreeQuestionPackage.verify(temp.resolve("missing-code"), temp.resolve("missing-assets"), temp, "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("审包SHA不匹配");
    }
    @Test void duplicatePrepareDirectoryCannotOverwriteEarlierBatch() throws Exception {
        assertThatThrownBy(() -> RuleAgentThreeQuestionPackage.prepare(temp.resolve("missing-code"), temp.resolve("missing-assets"), temp))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("拒绝覆盖或重用批次");
    }
    @Test void globalClaimIsNotRefundedByCopyingBatchOrChangingAuthorizationId() throws Exception {
        String sha = "1".repeat(64);
        RuleAgentThreeQuestionPackage.claim(temp, temp, sha, "THREE_TEST_AUTH_1");
        assertThatThrownBy(() -> RuleAgentThreeQuestionPackage.claim(temp, temp, sha, "THREE_TEST_AUTH_2"))
                .isInstanceOf(FileAlreadyExistsException.class);
    }
    @Test void redactSessionAndContinuationAndOriginalTokensButKeepTaskAndRunIds() throws Exception {
        JsonNode raw = RuleAgentThreeQuestionPackage.JSON.readTree("{\"taskId\":\"task\",\"runId\":\"run\",\"actorId\":\"private\",\"result\":{\"originalToken\":\"secret\",\"continuationToken\":\"secret\",\"quote\":\"历史条款\"}}");
        JsonNode safe = RuleAgentThreeQuestionHostTest.redact(raw);
        assertThat(safe.toString()).doesNotContain("secret", "private");
        assertThat(safe.path("taskId").asText()).isEqualTo("task");
        assertThat(safe.path("result").path("quote").asText()).isEqualTo("历史条款");
    }
}
