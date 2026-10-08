package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索资格过滤的边界测试。
 *
 * <p>草稿、内部资料、权利未确认、已替代、过期、范围不符的资料
 * 一律不能进入候选集，更不能进入模型上下文——过滤必须在索引与召回两侧同时成立。</p>
 */
class RetrievalEligibilityFilterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);
    private static final String SCOPE = "PUBLIC_ELECTIVE";

    private final RetrievalEligibilityFilter filter = new RetrievalEligibilityFilter(CLOCK);

    @Test
    void shouldAcceptPublishedStudentVisibleConfirmedCurrentSource() {
        EligibilityDecision decision = filter.evaluate(eligibleSnapshot().build(), SCOPE);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    @Test
    void shouldRejectDraftSource() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot().publicationStatus("DRAFT").build(), SCOPE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("未发布");
    }

    @Test
    void shouldRejectReviewingSource() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot().publicationStatus("REVIEWING").build(), SCOPE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("未发布");
    }

    @Test
    void shouldRejectInternalOnlySource() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot().visibilityScope("INTERNAL_ONLY").build(), SCOPE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("内部");
    }

    @Test
    void shouldRejectSourceWithoutConfirmedSharingRights() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot().sharingRightsConfirmed(false).build(), SCOPE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("权利");
    }

    @Test
    void shouldRejectSupersededSource() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot().superseded(true).build(), SCOPE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("替代");
    }

    @Test
    void shouldRejectExpiredSource() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot()
                        .validFrom(Instant.parse("2026-09-01T00:00:00Z"))
                        .validUntil(Instant.parse("2026-09-17T00:00:00Z"))
                        .build(),
                SCOPE);

        // validUntil 为排他上界：到期时刻起即不可召回，避免过期通知在截止当天仍被引用。
        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("过期");
    }

    @Test
    void shouldRejectNotYetEffectiveSource() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot()
                        .validFrom(Instant.parse("2026-09-18T00:00:00Z"))
                        .build(),
                SCOPE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("生效");
    }

    @Test
    void shouldRejectScopeMismatch() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot().knowledgeScope("SCHOLARSHIP").build(), SCOPE);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.reason()).contains("范围");
    }

    @Test
    void shouldAcceptSourceAtValidFromBoundary() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot()
                        .validFrom(Instant.parse("2026-09-17T00:00:00Z"))
                        .validUntil(Instant.parse("2026-09-20T00:00:00Z"))
                        .build(),
                SCOPE);

        // validFrom 为包含下界：生效时刻起即可召回。
        assertThat(decision.eligible()).isTrue();
    }

    @Test
    void shouldAcceptWhenNoScopeRequired() {
        EligibilityDecision decision = filter.evaluate(
                eligibleSnapshot().knowledgeScope("SCHOLARSHIP").build(), null);

        assertThat(decision.eligible()).isTrue();
    }

    private static SnapshotBuilder eligibleSnapshot() {
        return new SnapshotBuilder()
                .sourceId(UUID.randomUUID())
                .publicationStatus("PUBLISHED")
                .visibilityScope("STUDENT_VISIBLE")
                .sharingRightsConfirmed(true)
                .superseded(false)
                .knowledgeScope(SCOPE);
    }

    private static final class SnapshotBuilder {
        private UUID sourceId;
        private String publicationStatus;
        private String visibilityScope;
        private boolean sharingRightsConfirmed;
        private boolean superseded;
        private String knowledgeScope;
        private Instant validFrom;
        private Instant validUntil;

        SnapshotBuilder sourceId(UUID value) {
            this.sourceId = value;
            return this;
        }

        SnapshotBuilder publicationStatus(String value) {
            this.publicationStatus = value;
            return this;
        }

        SnapshotBuilder visibilityScope(String value) {
            this.visibilityScope = value;
            return this;
        }

        SnapshotBuilder sharingRightsConfirmed(boolean value) {
            this.sharingRightsConfirmed = value;
            return this;
        }

        SnapshotBuilder superseded(boolean value) {
            this.superseded = value;
            return this;
        }

        SnapshotBuilder knowledgeScope(String value) {
            this.knowledgeScope = value;
            return this;
        }

        SnapshotBuilder validFrom(Instant value) {
            this.validFrom = value;
            return this;
        }

        SnapshotBuilder validUntil(Instant value) {
            this.validUntil = value;
            return this;
        }

        SourceEligibilitySnapshot build() {
            return new SourceEligibilitySnapshot(
                    sourceId, publicationStatus, visibilityScope, sharingRightsConfirmed,
                    superseded, knowledgeScope, validFrom, validUntil);
        }
    }
}
