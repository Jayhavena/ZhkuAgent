package com.zhku.knowledge.campusagent.submission;

import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 只表达提交事实；重复记录没有派发凭据，也不保存可恢复的业务原文。 */
public final class CampusRunSubmission {
    private CampusRunSubmission() { }
    public record Fingerprint(int version, String sha256) {
        public Fingerprint {
            if (version != 1 || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("指纹无效");
        }
    }
    public record Command(UUID submissionKey, UUID proposedRunId, Fingerprint fingerprint) {
        public Command { Objects.requireNonNull(submissionKey); Objects.requireNonNull(proposedRunId); Objects.requireNonNull(fingerprint); }
    }
    public enum Decision { CREATED, REPLAYED }
    public record Record(UUID userId, UUID submissionKey, UUID runId, Fingerprint fingerprint, Instant acceptedAt) {
        public Record { Objects.requireNonNull(userId); Objects.requireNonNull(submissionKey); Objects.requireNonNull(runId); Objects.requireNonNull(fingerprint); Objects.requireNonNull(acceptedAt); }
    }
    public record Claim(Decision decision, Record record, CampusRunArchive.Admission admission) {
        public Claim {
            Objects.requireNonNull(decision); Objects.requireNonNull(record);
            if ((decision == Decision.CREATED) != (admission != null)
                    || admission != null && !admission.runId().equals(record.runId()))
                throw new IllegalArgumentException("准入许可与原运行不一致");
        }
    }
    public enum Code { SUBMISSION_CONTENT_CONFLICT, SUBMISSION_UNCONFIRMED, SUBMISSION_NOT_FOUND,
        USER_IN_FLIGHT_LIMIT, INSTANCE_CAPACITY_EXCEEDED, RESULT_READ_UNAVAILABLE }
    public static final class Failure extends RuntimeException {
        private final Code code;
        private final UUID submissionKey;
        private final UUID runId;
        public Failure(Code code, UUID submissionKey) { this(code, submissionKey, null); }
        public Failure(Code code, UUID submissionKey, UUID runId) {
            super(code.name(), null, false, false);
            this.code = Objects.requireNonNull(code); this.submissionKey = submissionKey; this.runId = runId;
        }
        public Code code() { return code; }
        public UUID submissionKey() { return submissionKey; }
        public UUID runId() { return runId; }
    }
}
