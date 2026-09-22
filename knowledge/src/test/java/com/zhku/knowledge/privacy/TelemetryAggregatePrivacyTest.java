package com.zhku.knowledge.privacy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 遥测聚合导出只允许一个已审核桶维度、计数和比例，避免管理端退化为明细导出。 */
class TelemetryAggregatePrivacyTest {

    @Test
    void shouldExportOnlyTheRequestedTelemetryBucketCountAndRatio() {
        Map<String, Object> exported = PrivacyBoundary.telemetryAggregateExport(
                "route", "HIGH_RISK_RULE", 2L, new BigDecimal("0.6667")
        );

        assertThat(exported).containsExactly(
                Map.entry("route", "HIGH_RISK_RULE"),
                Map.entry("count", 2L),
                Map.entry("ratio", new BigDecimal("0.6667"))
        );
        assertThat(exported.keySet()).doesNotContain(
                "message", "question", "answer", "studentId", "name", "contact", "apiKey",
                "anonymousSessionHash", "sessionHash", "deduplicationKey"
        );
    }

    @Test
    void shouldRejectAnyUnreviewedTelemetryDimension() {
        assertThatIllegalArgumentException().isThrownBy(() -> PrivacyBoundary.telemetryAggregateExport(
                "message", "不应导出的自由文本", 1L, new BigDecimal("1.0000")
        ));
    }
}
