package com.zhku.knowledge.notice;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishedNoticeTest {

    private static final Instant VALID_FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant VALID_TO = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    void shouldTreatExactValidityEndAsExpired() {
        PublishedNotice notice = publishedNotice(
                NoticeLifecycleStatus.PUBLISHED,
                VALID_FROM,
                VALID_TO
        );

        assertThat(notice.isCurrent(Clock.fixed(VALID_TO, ZoneOffset.UTC)))
                .isFalse();
    }

    @Test
    void shouldRejectPublishedNoticeWhoseFactHasNoConfirmedWindow() {
        assertThatThrownBy(() -> new NoticeFact(
                NoticeFactType.COURSE_SELECTION,
                VALID_FROM,
                null,
                "https://jwc.zhku.edu.cn/selection",
                true,
                true
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("开始和结束时间");
    }

    @Test
    void shouldRejectStudentVisibleNoticeWithoutAnImmutableOfficialSnapshot() {
        assertThatThrownBy(() -> new PublishedNotice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new AcademicTerm("2026-2027-1"),
                NoticeType.COURSE_SELECTION,
                NoticeLifecycleStatus.PUBLISHED,
                VALID_FROM,
                VALID_TO,
                null,
                true,
                true,
                List.of(validFact())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("网页快照");
    }

    @Test
    void shouldNotTreatSupersededNoticeAsCurrent() {
        PublishedNotice notice = publishedNotice(
                NoticeLifecycleStatus.SUPERSEDED,
                VALID_FROM,
                VALID_TO
        );

        assertThat(notice.isCurrent(Clock.fixed(
                Instant.parse("2026-09-10T00:00:00Z"),
                ZoneOffset.UTC
        ))).isFalse();
    }

    private static PublishedNotice publishedNotice(
            NoticeLifecycleStatus status,
            Instant validFrom,
            Instant validTo
    ) {
        return new PublishedNotice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new AcademicTerm("2026-2027-1"),
                NoticeType.COURSE_SELECTION,
                status,
                validFrom,
                validTo,
                validSnapshot(),
                true,
                true,
                List.of(validFact())
        );
    }

    private static NoticeFact validFact() {
        return new NoticeFact(
                NoticeFactType.COURSE_SELECTION,
                Instant.parse("2026-09-02T01:00:00Z"),
                Instant.parse("2026-09-05T10:00:00Z"),
                "https://jwc.zhku.edu.cn/selection",
                true,
                true
        );
    }

    private static OfficialWebSnapshot validSnapshot() {
        return new OfficialWebSnapshot(
                "https://jwc.zhku.edu.cn/notices/2026-selection",
                "notice-2026-2027-1.html",
                "A".repeat(64),
                Instant.parse("2026-08-31T00:00:00Z"),
                "text/html"
        );
    }
}
