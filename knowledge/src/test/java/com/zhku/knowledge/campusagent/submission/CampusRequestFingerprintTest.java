package com.zhku.knowledge.campusagent.submission;

import com.zhku.knowledge.campusagent.CampusCreditAgentService.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CampusRequestFingerprintTest {
    private Request request(String question, String credits) {
        return new Request(question, List.of(new CourseInput(" A ", " 公共选修课 ", new BigDecimal(credits)),
                new CourseInput("B", null, null)));
    }
    @Test void normalizesOnlyOuterWhitespaceAndEquivalentNumbers() {
        var a = CampusRequestFingerprint.normalize(request(" 问  题 ", "1.50"));
        var b = CampusRequestFingerprint.normalize(request("问  题", "1.5"));
        assertThat(a.question()).isEqualTo("问  题");
        assertThat(a.courses().getFirst().courseId()).isEqualTo("A");
        assertThat(a.courses().getFirst().category()).isEqualTo("公共选修课");
        assertThat(CampusRequestFingerprint.of(a)).isEqualTo(CampusRequestFingerprint.of(b));
        assertThat(CampusRequestFingerprint.of(a)).isNotEqualTo(CampusRequestFingerprint.of(request("问 题", "1.5")));
    }
    @Test void courseOrderAndNullRemainSignificant() {
        var a = CampusRequestFingerprint.normalize(request("问题", "1.50"));
        var b = new Request(a.question(), a.courses().reversed());
        assertThat(CampusRequestFingerprint.of(a)).isNotEqualTo(CampusRequestFingerprint.of(b));
        assertThat(a.courses().getLast().credits()).isNull();
        assertThat(a.courses().getLast().category()).isNull();
        assertThat(CampusRequestFingerprint.of(a).sha256()).matches("[0-9a-f]{64}");
    }
    @Test void rejectsLengthPrecisionAndExponentBeforeExpansion() {
        assertThatThrownBy(() -> CampusRequestFingerprint.normalize(new Request("问题",
                java.util.Collections.nCopies(21, new CourseInput("A", "B", null)))))
                .isInstanceOf(IllegalArgumentException.class);
        for (String value : List.of("1E+100000", "1E-100000", "12345678901234567", "0.0000001"))
            assertThatThrownBy(() -> CampusRequestFingerprint.normalize(request("问题", value)))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CampusRequestFingerprint.normalize(new Request("问题",
                List.of(new CourseInput("a".repeat(65), "B", null)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CampusRequestFingerprint.normalize(new Request("问题",
                List.of(new CourseInput("A", "b".repeat(33), null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsInclusiveLimitsAndZeroNegativeClarificationValues() {
        var a = CampusRequestFingerprint.normalize(new Request("问".repeat(500),
                java.util.Collections.nCopies(20, new CourseInput("a".repeat(64), "b".repeat(32), new BigDecimal("-1.00")))));
        assertThat(a.courses()).hasSize(20);
        assertThat(CampusRequestFingerprint.of(a).version()).isEqualTo(1);
        assertThat(CampusRequestFingerprint.normalize(request("问题", "0.000000"))) .isNotNull();
    }
}
