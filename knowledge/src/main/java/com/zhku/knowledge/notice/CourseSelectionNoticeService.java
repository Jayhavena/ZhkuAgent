package com.zhku.knowledge.notice;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 当前选课通知的受限问答服务。
 *
 * <p>它只解释审核过的办理时间，不根据口述推断个人资格、剩余名额或是否能够办结。</p>
 */
public final class CourseSelectionNoticeService {

    private final CurrentNoticeFinder noticeFinder;
    private final AcademicTerm currentTerm;
    private final String officialQueryUrl;

    public CourseSelectionNoticeService(
            CurrentNoticeFinder noticeFinder,
            AcademicTerm currentTerm,
            String officialQueryUrl
    ) {
        this.noticeFinder = Objects.requireNonNull(noticeFinder, "当前通知查询器不能为空");
        this.currentTerm = Objects.requireNonNull(currentTerm, "当前学期不能为空");
        this.officialQueryUrl = OfficialWebSnapshot.requireHttpsUrl(
                officialQueryUrl,
                "官方查询链接必须是 HTTPS 地址"
        );
    }

    public CourseSelectionNoticeService(
            NoticeRepository repository,
            AcademicTerm currentTerm,
            String officialQueryUrl
    ) {
        this(new CurrentNoticeFinder(
                Objects.requireNonNull(repository, "通知仓储不能为空"),
                Clock.systemUTC()
        ), currentTerm, officialQueryUrl);
    }

    public ElectiveRetakeReply handle(String message) {
        String normalized = normalize(message);
        NoticeFactType requestedFact = requestedFact(normalized);
        if (requestedFact == null) {
            return new ElectiveRetakeReply(
                    ElectiveRetakeReplyType.OUT_OF_SCOPE,
                    "当前问题不属于已支持的当期选课通知咨询范围。",
                    null
            );
        }
        return noticeFinder.find(currentTerm, NoticeType.COURSE_SELECTION)
                .flatMap(notice -> answerFor(notice, requestedFact))
                .orElseGet(this::evidenceUnavailable);
    }

    private java.util.Optional<ElectiveRetakeReply> answerFor(
            PublishedNotice notice,
            NoticeFactType requestedFact
    ) {
        return notice.facts().stream()
                .filter(fact -> fact.type() == requestedFact)
                .findFirst()
                .map(fact -> new ElectiveRetakeReply(
                        ElectiveRetakeReplyType.ANSWER,
                        conclusionFor(requestedFact, fact),
                        new ElectiveRetakeAnswer(
                                conclusionFor(requestedFact, fact),
                                "本回答仅说明当前有效通知中的办理时间和官方入口；"
                                        + "个人资格、课程容量和最终办理结果仍以教务部门为准。",
                                notice.sourceId(),
                                notice.sourceTitle(),
                                notice.sourceVersion(),
                                snapshotCitation(notice)
                                        + "；快照 SHA-256："
                                        + notice.officialSnapshot().sha256(),
                                notice.applicableScope()
                        )
                ));
    }

    private ElectiveRetakeReply evidenceUnavailable() {
        return new ElectiveRetakeReply(
                ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE,
                "当前没有可确认的有效选课通知，无法给出具体日期或资格结论。"
                        + "请以教务部门当前公告为准：" + officialQueryUrl,
                null
        );
    }

    private static String snapshotCitation(PublishedNotice notice) {
        NoticeEvidenceOrigin origin = notice.officialSnapshot().origin();
        return origin.channel() == NoticeEvidenceChannel.PUBLIC_WEB
                ? "官方网页快照：" + origin.studentCitationLabel()
                : "已审核校内正式材料快照：" + origin.studentCitationLabel();
    }

    private static String conclusionFor(NoticeFactType type, NoticeFact fact) {
        return actionName(type) + "办理时间为 "
                + render(fact.startsAt()) + " 至 " + render(fact.endsAt())
                + "；官方入口：" + fact.officialEntryUrl();
    }

    private static String render(Instant value) {
        return value.toString();
    }

    private static String actionName(NoticeFactType type) {
        return switch (type) {
            case COURSE_SELECTION -> "选课";
            case COURSE_WITHDRAWAL -> "退改选";
            case COURSE_SUPPLEMENT -> "补选";
            case COURSE_CHANGE -> "改选";
        };
    }

    private static NoticeFactType requestedFact(String message) {
        boolean courseSelectionTopic = message.contains("选课")
                || message.contains("补选")
                || message.contains("改选")
                || message.contains("退选")
                || message.contains("退改选");
        if (!courseSelectionTopic) {
            return null;
        }
        if (message.contains("退") || message.contains("撤")) {
            return NoticeFactType.COURSE_WITHDRAWAL;
        }
        if (message.contains("补选")) {
            return NoticeFactType.COURSE_SUPPLEMENT;
        }
        if (message.contains("改选")) {
            return NoticeFactType.COURSE_CHANGE;
        }
        return NoticeFactType.COURSE_SELECTION;
    }

    private static String normalize(String message) {
        String normalized = Objects.requireNonNull(message, "消息不能为空").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("消息不能超过 500 个字符");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
