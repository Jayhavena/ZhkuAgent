package com.zhku.knowledge.rulecheck.web;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 外置审核资产完整正例单独运行；缺原件就失败，不能以跳过充当装配通过。 */
class RuleCheckPreviewAssetsTest {
    @Test void reviewedControlledAssetsPassCompleteEnvironmentValidation() {
        assertThatCode(() -> RuleCheckPreviewConfiguration.validateEnvironment(
                RuleCheckPreviewConfigurationTest.reviewedControlled())).doesNotThrowAnyException();
    }
}
