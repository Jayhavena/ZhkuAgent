package com.zhku.knowledge.notice;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseSelectionNoticeServiceTest {

    private static final AcademicTerm TERM = new AcademicTerm("2026-2027-1");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void shouldReturnTraceableAnswerFromCurrentSelectionNotice() {
        PublishedNotice notice = currentNotice();
        CourseSelectionNoticeService service = new CourseSelectionNoticeService(
                new CurrentNoticeFinder(() -> List.of(notice), CLOCK),
                TERM,
                "https://jwc.zhku.edu.cn"
        );

        ElectiveRetakeReply reply = service.handle("本学期什么时候选课？");

        assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
        assertThat(reply.answer()).isNotNull();
        assertThat(reply.answer().sourceId()).isEqualTo(notice.sourceId());
        assertThat(reply.answer().sourceTitle())
                .isEqualTo("2026-2027 学年第一学期本科生选课通知");
        assertThat(reply.answer().sourceVersion()).isEqualTo("2026 年第 1 号");
        assertThat(reply.answer().sourceLocator()).contains("网页快照");
    }

    @Test
    void shouldRefuseDateWhenNoCurrentNoticeExists() {
        CourseSelectionNoticeService service = new CourseSelectionNoticeService(
                List::<PublishedNotice>of,
                TERM,
                "https://jwc.zhku.edu.cn"
        );

        ElectiveRetakeReply reply = service.handle("什么时候可以补选？");

        assertThat(reply.type())
                .isEqualTo(ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE);
        assertThat(reply.message()).contains("https://jwc.zhku.edu.cn");
        assertThat(reply.answer()).isNull();
    }

    private static PublishedNotice currentNotice() {
        return new PublishedNotice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "2026-2027 学年第一学期本科生选课通知",
                "2026 年第 1 号",
                "2026-2027-1 学期本科生",
                TERM,
                NoticeType.COURSE_SELECTION,
                NoticeLifecycleStatus.PUBLISHED,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-15T00:00:00Z"),
                new OfficialWebSnapshot(
                        "https://jwc.zhku.edu.cn/notices/selection",
                        "2026-selection.html",
                        "A".repeat(64),
                        Instant.parse("2026-08-30T00:00:00Z"),
                        "text/html"
                ),
                true,
                true,
                List.of(new NoticeFact(
                        NoticeFactType.COURSE_SELECTION,
                        Instant.parse("2026-09-02T00:00:00Z"),
                        Instant.parse("2026-09-05T00:00:00Z"),
                        "https://jwc.zhku.edu.cn/course-selection",
                        true,
                        true
                ))
        );
    }
}
