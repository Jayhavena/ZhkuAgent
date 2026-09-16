package com.zhku.knowledge.notice;

import java.time.Clock;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** 只选择当前学期、当前类型且仍有效的学生可见通知。 */
public final class CurrentNoticeFinder {

    private final NoticeRepository repository;
    private final Clock clock;

    public CurrentNoticeFinder(NoticeRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "通知仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public Optional<PublishedNotice> find(
            AcademicTerm academicTerm,
            NoticeType type
    ) {
        Objects.requireNonNull(academicTerm, "查询学期不能为空");
        Objects.requireNonNull(type, "查询通知类型不能为空");
        return repository.listNotices().stream()
                .filter(notice -> academicTerm.equals(notice.academicTerm()))
                .filter(notice -> type == notice.type())
                .filter(notice -> notice.isCurrent(clock))
                // 同一范围发布两个“当前”通知是数据异常；先稳定选择，后续 JDBC 发布事务保证唯一性。
                .min(Comparator.comparing(PublishedNotice::noticeId));
    }
}
