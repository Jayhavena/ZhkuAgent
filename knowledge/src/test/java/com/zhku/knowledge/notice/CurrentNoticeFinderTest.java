package com.zhku.knowledge.notice;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentNoticeFinderTest {

    private static final AcademicTerm CURRENT_TERM =
            new AcademicTerm("2026-2027-1");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-10T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void shouldFindOnlyPublishedStudentVisibleNoticeForRequestedTerm() {
        PublishedNotice current = notice(
                CURRENT_TERM,
                NoticeLifecycleStatus.PUBLISHED,
                true
        );
        CurrentNoticeFinder finder = new CurrentNoticeFinder(
                () -> List.of(
                        notice(CURRENT_TERM, NoticeLifecycleStatus.SUPERSEDED, true),
                        notice(new AcademicTerm("2025-2026-2"), NoticeLifecycleStatus.PUBLISHED, true),
                        notice(CURRENT_TERM, NoticeLifecycleStatus.PUBLISHED, false),
                        current
                ),
                CLOCK
        );

        Optional<PublishedNotice> found = finder.find(
                CURRENT_TERM,
                NoticeType.COURSE_SELECTION
        );

        assertThat(found).containsSame(current);
    }

    @Test
    void shouldReturnEmptyWhenNoEffectiveNoticeExists() {
        CurrentNoticeFinder finder = new CurrentNoticeFinder(
                () -> List.of(notice(
                        CURRENT_TERM,
                        NoticeLifecycleStatus.EXPIRED,
                        true
                )),
                CLOCK
        );

        assertThat(finder.find(CURRENT_TERM, NoticeType.COURSE_SELECTION))
                .isEmpty();
    }

    private static PublishedNotice notice(
            AcademicTerm term,
            NoticeLifecycleStatus status,
            boolean studentVisible
    ) {
        return new PublishedNotice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                term,
                NoticeType.COURSE_SELECTION,
                status,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-15T00:00:00Z"),
                new OfficialWebSnapshot(
                        "https://jwc.zhku.edu.cn/notices/selection",
                        UUID.randomUUID() + ".html",
                        "A".repeat(64),
                        Instant.parse("2026-08-30T00:00:00Z"),
                        "text/html"
                ),
                studentVisible,
                studentVisible,
                List.of(new NoticeFact(
                        NoticeFactType.COURSE_SELECTION,
                        Instant.parse("2026-09-02T00:00:00Z"),
                        Instant.parse("2026-09-05T00:00:00Z"),
                        "https://jwc.zhku.edu.cn/selection",
                        true,
                        true
                ))
        );
    }
}
