package com.zhku.knowledge.campusagent.archive;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentRunContext;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError;
import com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category;
import com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 归档事实与执行终态分离；持久记录不证明旧进程已发布结果或发出终止事件。 */
public record CampusRunArchive(UUID runId, int schemaVersion, String writerVersion, ArchiveStatus archiveStatus,
        OwnerToken owner, long revision, Instant admittedAt, Instant executionFinishedAt, Instant closedAt,
        Instant retainedUntil, FinalFacts execution, PayloadDisposition payloadDisposition,
        Reason payloadReason, String interruptionReason) {

    public static final int SCHEMA_VERSION = 1;
    public static final String WRITER_VERSION = "milestone-3a-preview";
    public static final String INTERRUPTION_REASON = "PREVIOUS_OWNER_RESULT_UNCONFIRMED";

    public static FinalFacts capture(AgentRunContext context, CampusCreditAgentService.Result result,
            boolean rejected, Instant observedAt) {
        var snapshot = context.snapshot();
        var error = snapshot.error();
        String failure = rejected ? "EXECUTOR_REJECTED"
                : context.hasCleanupFailure()
                        ? "CLEANUP_FAILURE"
                        : Objects.requireNonNull(result).failureCategory();
        return new FinalFacts(snapshot.runId(), snapshot.state(), snapshot.stopReason(), snapshot.endReason(),
                snapshot.currentStep(), error == null ? null : error.category(), error == null ? null : error.code(),
                result == null ? null : result.status().name(), failure,
                context.hasCleanupFailure() ? CleanupOutcome.FAILED : CleanupOutcome.SUCCESS, observedAt);
    }

    public CampusRunArchive {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(admittedAt);
        Objects.requireNonNull(archiveStatus);
        if (schemaVersion < 1 || revision < 0 || writerVersion == null
                || !writerVersion.matches("[A-Za-z0-9_.-]{1,64}"))
            throw new IllegalArgumentException("归档元数据非法");
        boolean closed = closedAt != null && retainedUntil != null && retainedUntil.isAfter(closedAt);
        boolean emptyFacts = executionFinishedAt == null && execution == null && payloadDisposition == null
                && payloadReason == null;
        boolean valid = switch (archiveStatus) {
            case ADMITTED -> emptyFacts && closedAt == null && retainedUntil == null && interruptionReason == null;
            case INTERRUPTED -> emptyFacts && closed && INTERRUPTION_REASON.equals(interruptionReason);
            case FINALIZED -> closed && executionFinishedAt != null && interruptionReason == null
                    && payloadDisposition != null && payloadReason != null
                    && (schemaVersion != SCHEMA_VERSION || execution != null && runId.equals(execution.runId()));
        };
        if (!valid)
            throw new IllegalArgumentException("归档状态与事实不一致");
    }

    public enum ArchiveStatus {
        // INTERRUPTED 表示旧 owner 的结果未确认，不能把它冒充用户取消或已完成清理。
        ADMITTED, FINALIZED, INTERRUPTED
    }

    public enum CleanupOutcome {
        SUCCESS, FAILED
    }

    public enum PayloadDisposition {
        STORED, NOT_APPLICABLE, SUPPRESSED
    }

    public enum VisibilityStatus {
        VISIBLE, HIDDEN, NOT_STORED
    }

    public enum Reason {
        NONE, NO_RESULT_FOR_OUTCOME, SOURCE_REFERENCE_MISSING, SOURCE_UNAVAILABLE,
        SOURCE_VERSION_CHANGED, SOURCE_NOT_VISIBLE, PAYLOAD_TOO_LARGE, UNSUPPORTED_SCHEMA
    }

    public enum Finalization {
        CONFIRMED, UNCONFIRMED, OWNER_LOST
    }

    public enum Code {
        ARCHIVE_UNAVAILABLE, ARCHIVE_ADMISSION_UNCONFIRMED, ARCHIVE_BUSY,
        ARCHIVE_NOT_READY, ARCHIVE_CAPACITY_EXCEEDED, ARCHIVE_FINALIZATION_UNCONFIRMED,
        ARCHIVE_OWNER_LOST, ARCHIVE_READ_UNAVAILABLE, ARCHIVE_NOT_FOUND, INVALID_REQUEST
    }

    public record OwnerToken(UUID instanceId, long epoch) {
        public OwnerToken {
            Objects.requireNonNull(instanceId);
            if (epoch < 0)
                throw new IllegalArgumentException("epoch 非法");
        }
    }

    public record Admission(UUID runId, OwnerToken owner, long revision) {
        public Admission {
            Objects.requireNonNull(runId);
            Objects.requireNonNull(owner);
            if (revision < 0)
                throw new IllegalArgumentException("revision 非法");
        }
    }

    public record Cursor(Instant admittedAt, UUID runId) {
        public Cursor {
            Objects.requireNonNull(admittedAt);
            Objects.requireNonNull(runId);
        }
    }

    public record Visibility(VisibilityStatus status, Reason reason) {
        public Visibility {
            Objects.requireNonNull(status);
            Objects.requireNonNull(reason);
        }
    }

    public record PayloadCandidate(String answer, ConversationAnswer source) {
    }

    public record Encoded(PayloadDisposition disposition, Reason reason, byte[] bytes) {
        public Encoded {
            Objects.requireNonNull(disposition);
            Objects.requireNonNull(reason);
            if (disposition == PayloadDisposition.STORED
                    && (bytes == null || bytes.length > 16384 || reason != Reason.NONE)
                    || disposition != PayloadDisposition.STORED && bytes != null)
                throw new IllegalArgumentException("载荷保存状态非法");
            bytes = bytes == null ? null : bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }

    public record FinalFacts(UUID runId, AgentRunState state, AgentStopReason stopReason, EndReason endReason,
            int currentStep, Category errorCategory, String errorCode, String businessStatus,
            String failureCategory, CleanupOutcome cleanupOutcome, Instant observedAt) {
        public FinalFacts {
            Objects.requireNonNull(state);
            Objects.requireNonNull(cleanupOutcome);
            Objects.requireNonNull(observedAt);
            if (!state.isTerminal() || (errorCategory == null) != (errorCode == null))
                throw new IllegalArgumentException("只归档完整终态事实");
            var error = errorCategory == null ? null : AgentRuntimeError.of(errorCategory, errorCode);
            // 共享契约负责终态一致性，knowledge 不复制另一套状态规则。
            new AgentRunSnapshot(runId, state, stopReason, currentStep, false, endReason, error);
            if (failureCategory == null || !failureCategory.matches("[A-Z][A-Z0-9_]{0,63}")
                    || businessStatus != null && !List.of("ANSWERED", "NEED_CLARIFICATION", "HANDOFF", "CANCELLED")
                            .contains(businessStatus)
                    || cleanupOutcome == CleanupOutcome.FAILED && errorCategory != Category.CLEANUP_FAILURE)
                throw new IllegalArgumentException("业务或清理分类非法");
        }
    }

    public record Limits(Duration operation, Duration startup, Duration drain, Duration retention, int maxRecords) {
        public Limits {
            budget(operation, Duration.ofSeconds(2));
            budget(startup, Duration.ofSeconds(5));
            budget(drain, Duration.ofSeconds(5));
            if (retention == null || retention.compareTo(Duration.ofDays(1)) < 0
                    || retention.compareTo(Duration.ofDays(30)) > 0 || maxRecords < 1 || maxRecords > 10000)
                throw new IllegalArgumentException("保留与容量必须在预览边界内");
        }

        private static void budget(Duration value, Duration maximum) {
            if (value == null || value.isNegative() || value.isZero() || value.compareTo(maximum) > 0)
                throw new IllegalArgumentException("存储等待预算非法");
        }

        public static Limits defaults() {
            return new Limits(Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5),
                    Duration.ofDays(7), 10000);
        }
    }

    public record Read(CampusRunArchive archive, Visibility visibility, PayloadCandidate result) {
        public Read {
            Objects.requireNonNull(archive);
            Objects.requireNonNull(visibility);
            if ((visibility.status() == VisibilityStatus.VISIBLE) != (result != null))
                throw new IllegalArgumentException("不可见正文不能从存储边界外泄");
        }
    }

    public record Page(List<CampusRunArchive> items, Cursor nextCursor) {
        public Page {
            items = List.copyOf(items);
        }
    }

    public static final class Failure extends RuntimeException {
        private final Code code;

        public Failure(Code code) {
            super(message(Objects.requireNonNull(code)));
            this.code = code;
        }

        public Code code() {
            return code;
        }

        public String safeMessage() {
            return getMessage();
        }

        private static String message(Code code) {
            return switch (code) {
                case ARCHIVE_UNAVAILABLE -> "运行归档暂不可用";
                case ARCHIVE_ADMISSION_UNCONFIRMED -> "运行准入尚未确认，请勿自动重试";
                case ARCHIVE_BUSY -> "运行归档繁忙";
                case ARCHIVE_NOT_READY -> "运行归档未开放准入";
                case ARCHIVE_CAPACITY_EXCEEDED -> "运行归档容量已满";
                case ARCHIVE_FINALIZATION_UNCONFIRMED -> "最终归档尚未确认";
                case ARCHIVE_OWNER_LOST -> "运行归档所有权已失效";
                case ARCHIVE_READ_UNAVAILABLE -> "运行历史暂不可读取";
                case ARCHIVE_NOT_FOUND -> "运行历史不存在或已到期";
                case INVALID_REQUEST -> "运行历史请求参数无效";
            };
        }
    }
}
