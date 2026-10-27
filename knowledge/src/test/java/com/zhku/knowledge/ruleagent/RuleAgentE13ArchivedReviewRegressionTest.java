package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/**
 * Offline regression based on the actually received E13 PUBLICATION_REVIEW response.
 * Does not connect to a network, import secrets, start a database, or resend the paid batch.
 */
class RuleAgentE13ArchivedReviewRegressionTest {
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode fixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/three-question-regression/e13-review-20261027.json")) {
            assertThat(input).as("Archived provider review fixture must exist").isNotNull();
            return json.readTree(input);
        }
    }

    @Test
    void exactE13ModelReviewFailsClosedWithoutAutocorrectingUnsupportedDemand() throws Exception {
        JsonNode fixture = fixture();
        assertThat(fixture.path("caseId").asText()).isEqualTo("E13");
        assertThat(fixture.path("responseBodySha256").asText())
                .isEqualTo("f757792d2b5fdd9885424e8c116850aa124295945a5a95be04c3d6650e34d926");
        var safe = json.treeToValue(fixture.path("input"), SemanticInput.class);
        String actual = fixture.path("rawReviewContent").asText();
        assertThat(safe.questions()).hasSize(1);
        assertThat(safe.questions().getFirst().text()).isEqualTo("这学期公选补选今晚几点关？现在还能帮我补上吗？");

        JsonNode review = json.readTree(actual);
        assertThat(review.path("protocolRevision").asText()).isEqualTo(REVISION);
        assertThat(review.path("requirements").isArray()).isTrue();
        assertThat(review.path("requirements").findValuesAsText("dimension")).contains("UNKNOWN");
        assertThat(review.path("requirements").findValuesAsText("recognition")).contains("IDENTIFIED");

        assertThatThrownBy(() -> RulePublicationProtocol.decode(actual, safe))
                .isInstanceOfSatisfying(RuleAgentProtocol.Rejected.class, error -> {
                    assertThat(error.validation.stage()).isEqualTo(RuleAgentProtocol.Stage.REVIEW_JSON);
                    assertThat(error.validation.code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_JSON_INVALID);
                });
    }

    @Test
    void promptAndWireContractExplicitlyDisallowTheE13Contradiction() throws Exception {
        JsonNode contract = RulePublicationProtocol.description();
        String invariant = contract.path("recognitionDimensionInvariant").asText();
        assertThat(invariant).contains("IDENTIFIED", "UNKNOWN", "UNCLASSIFIED", "unresolvedDemands", "UNSUPPORTED_DIMENSION", "CORE");
        assertThat(RuleAgentProvider.REVIEW_SYSTEM).contains("IDENTIFIED", "UNKNOWN", "UNCLASSIFIED", "unresolvedDemands", "UNSUPPORTED_DIMENSION");
        var safe = json.treeToValue(fixture().path("input"), SemanticInput.class);
        // A legitimate response can represent an unanswerable, unsupported request without any fabricated fact binding.
        ObjectNode unresolvedOnly = json.createObjectNode();
        unresolvedOnly.put("protocolRevision", REVISION);
        unresolvedOnly.put("inputFingerprint", safe.inputFingerprint());
        unresolvedOnly.putArray("requirements");
        unresolvedOnly.putArray("bindings");
        var un = unresolvedOnly.putArray("unresolvedDemands").addObject();
        un.putObject("span").put("subquestionId", "q1").put("excerpt", safe.questions().getFirst().text());
        un.put("reason", "UNSUPPORTED_DIMENSION");
        var decoded = RulePublicationProtocol.decode(unresolvedOnly.toString(), safe);
        assertThat(decoded.requirements()).isEmpty();
        assertThat(decoded.bindings()).isEmpty();
        assertThat(decoded.unresolvedDemands()).hasSize(1);
    }

    @Test
    void archivedReviewFailsThroughRealProviderDecoderOnLoopbackWithNoExternalModel() throws Exception {
        JsonNode fixture = fixture();
        var safe = json.treeToValue(fixture.path("input"), SemanticInput.class);
        var stub = new RuleAgentProviderTest();
        try {
            // Fake the supplier's exact 200/stop message locally; never reach DashScope.
            stub.start(200, stub.response(fixture.path("rawReviewContent").asText(), "stop"), 0);
            assertThatThrownBy(() -> stub.port.assess(safe, java.time.Duration.ofSeconds(2)))
                    .isInstanceOf(RuleAgentProvider.Failure.class);
            assertThat(stub.calls).hasValue(1);
            var last = stub.traces.getLast();
            assertThat(last.validation().stage()).isEqualTo(RuleAgentProtocol.Stage.REVIEW_JSON);
            assertThat(last.validation().code()).isEqualTo(RuleAgentProtocol.Code.REVIEW_JSON_INVALID);
            assertThat(last.reason()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);
            assertThat(last.responseReceived()).isTrue();
        } finally {
            stub.close();
        }
    }
    @Test
    void invalidReviewCannotMasqueradeAsMissingSourceOrPublishedAnswer() {
        var issue = RuleResultPublisher.issue(ReasonCode.INVALID_MODEL_RESPONSE);
        assertThat(issue.kind()).isEqualTo(IssueKind.PROCESSING_FAILURE);
        assertThat(issue.reasonCode()).isEqualTo(ReasonCode.INVALID_MODEL_RESPONSE);
        assertThat(issue.message()).contains("核验失败", "不能", "资料不存在");
        assertThat(issue.kind()).isNotEqualTo(IssueKind.DATA_GAP);
    }
}