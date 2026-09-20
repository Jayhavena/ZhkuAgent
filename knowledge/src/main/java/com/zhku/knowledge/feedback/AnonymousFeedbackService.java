package com.zhku.knowledge.feedback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;

/** 匿名枚举反馈的写入应用服务。 */
public final class AnonymousFeedbackService {

    static final Duration RETENTION = Duration.ofDays(90);

    private final FeedbackRepository repository;
    private final Clock clock;

    public AnonymousFeedbackService(FeedbackRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "反馈仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public AnonymousFeedbackReceipt submit(AnonymousFeedbackSubmission submission) {
        Objects.requireNonNull(submission, "匿名反馈不能为空");
        Instant createdAt = clock.instant();
        LocalDate submittedOn = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
        AnonymousFeedbackEvent event = new AnonymousFeedbackEvent(
                submission.guideIdentifier(),
                submission.guideVersion(),
                submission.reason(),
                submission.anonymousSessionHash(),
                dedupeKey(submission, submittedOn),
                createdAt,
                createdAt.plus(RETENTION)
        );
        boolean saved = repository.saveIfAbsent(event);
        return new AnonymousFeedbackReceipt(true, !saved);
    }

    private static String dedupeKey(
            AnonymousFeedbackSubmission submission,
            LocalDate submittedOn
    ) {
        String value = String.join("\\u0000",
                submission.anonymousSessionHash(),
                submission.guideIdentifier(),
                submission.guideVersion(),
                submission.reason().name(),
                submittedOn.toString()
        );
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境未提供 SHA-256", exception);
        }
    }
}
