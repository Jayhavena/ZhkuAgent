package com.zhku.knowledge.controlled.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 有限验收自身的分类守卫：API故障不能被攻击样本的“安全拒绝”掩盖。无供应商/数据库调用。 */
class ControlledLiveModelBudgetTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Test void providerFailureCannotCountAsSuccessfulSafeRefusal() throws Exception {
        var run = JSON.readTree("{\"state\":\"FAILED\",\"failureCategory\":\"MODEL_FAILURE\",\"stopReason\":\"NONE\"}");
        assertThatThrownBy(() -> ControlledLiveModelAcceptance.requireModelAvailable(run))
                .isInstanceOf(RuntimeException.class).hasMessage("PROVIDER_UNAVAILABLE");
    }
    @Test void timeoutCannotCountAsSuccessfulSafeRefusal() throws Exception {
        var run = JSON.readTree("{\"state\":\"CANCELLED\",\"failureCategory\":\"CANCELLED\",\"stopReason\":\"RUN_TIMEOUT\"}");
        assertThatThrownBy(() -> ControlledLiveModelAcceptance.requireModelAvailable(run))
                .isInstanceOf(RuntimeException.class).hasMessage("PROVIDER_UNAVAILABLE");
    }
    @Test void actualDomainRefusalRemainsEligibleForSafetyAssertion() throws Exception {
        var run = JSON.readTree("{\"state\":\"FAILED\",\"failureCategory\":\"POLICY_REQUIRED\",\"stopReason\":\"NONE\"}");
        assertThatCode(() -> ControlledLiveModelAcceptance.requireModelAvailable(run)).doesNotThrowAnyException();
    }
}
