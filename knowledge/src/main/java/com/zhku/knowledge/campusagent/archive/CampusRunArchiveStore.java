package com.zhku.knowledge.campusagent.archive;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;

/** 单一领域存储边界，不负责模型、工具调度、Session 或运行恢复。 */
public interface CampusRunArchiveStore extends AutoCloseable {
    boolean isAccepting();
    Admission admit(UUID runId);
    Finalization finalizeRun(Admission admission, FinalFacts facts, PayloadCandidate candidate);
    Optional<Read> find(UUID runId);
    Page list(int limit, Cursor cursor);
    // 旧替身可继续实现匿名接口；默认owned必须安全失败，不能委派匿名方法形成旁路。
    default Admission admitOwned(UUID runId, com.zhku.knowledge.security.TrustedActor actor) {
        throw new Failure(Code.ARCHIVE_NOT_READY);
    }
    default Optional<Read> findOwned(com.zhku.knowledge.security.TrustedActor actor, UUID runId) {
        throw new Failure(Code.ARCHIVE_READ_UNAVAILABLE);
    }
    default Page listOwned(com.zhku.knowledge.security.TrustedActor actor, int limit, Cursor cursor) {
        throw new Failure(Code.ARCHIVE_READ_UNAVAILABLE);
    }
    default com.zhku.knowledge.campusagent.submission.CampusRunSubmission.Claim admitSubmissionOwned(
            com.zhku.knowledge.security.TrustedActor actor,
            com.zhku.knowledge.campusagent.submission.CampusRunSubmission.Command command) {
        throw new Failure(Code.ARCHIVE_NOT_READY);
    }
    default Optional<com.zhku.knowledge.campusagent.submission.CampusRunSubmission.Record> findSubmissionOwned(
            com.zhku.knowledge.security.TrustedActor actor, UUID submissionKey) {
        throw new Failure(Code.ARCHIVE_READ_UNAVAILABLE);
    }
    void stopAdmission();
    void sealWrites();
    void close(Duration remainingBudget);
    @Override default void close() { close(Duration.ZERO); }
}
