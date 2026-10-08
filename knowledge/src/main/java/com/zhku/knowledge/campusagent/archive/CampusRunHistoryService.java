package com.zhku.knowledge.campusagent.archive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;

/** 历史查询只表达已存事实；来源许可由 Store 当前核验，不重放模型或工具。 */
public final class CampusRunHistoryService {
    private final CampusRunArchiveStore store;

    public CampusRunHistoryService(CampusRunArchiveStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public Detail find(UUID id) {
        var read = store.find(Objects.requireNonNull(id)).orElseThrow(() -> new Failure(Code.ARCHIVE_NOT_FOUND));
        return detail(read);
    }

    private static Detail detail(Read read) {
        var a = read.archive();
        var f = a.execution();
        var execution = f == null ? null
                : new Execution(f.state(), f.stopReason(), f.endReason(), f.currentStep(), f.errorCategory(),
                        f.errorCode(), f.businessStatus(), f.failureCategory(), f.cleanupOutcome());
        // 当前来源可展示只证明引用许可；同ID/同版本可以改正文，不能声称不可变证据。
        return new Detail(a.runId(), a.schemaVersion(), a.archiveStatus(), a.admittedAt(), a.executionFinishedAt(),
                a.closedAt(),
                a.retainedUntil(), execution, read.result(), read.visibility(), "REFERENCE_ONLY");
    }

    public Listing list(int limit, String cursor) {
        if (limit < 1 || limit > 100)
            throw new Failure(Code.INVALID_REQUEST);
        var page = store.list(limit, cursor == null ? null : decode(cursor));
        return listing(page);
    }

    private static Listing listing(Page page) {
        return new Listing(
                page.items().stream()
                        .map(a -> new Summary(a.runId(), a.schemaVersion(), a.archiveStatus(), a.admittedAt(),
                                a.closedAt(), a.retainedUntil()))
                        .toList(),
                page.nextCursor() == null ? null : encode(page.nextCursor()));
    }

    public Detail findOwned(com.zhku.knowledge.security.TrustedActor actor, UUID id) {
        return detail(store.findOwned(Objects.requireNonNull(actor), Objects.requireNonNull(id))
                .orElseThrow(() -> new Failure(Code.ARCHIVE_NOT_FOUND)));
    }
    public Listing listOwned(com.zhku.knowledge.security.TrustedActor actor, int limit, String cursor) {
        if (limit < 1 || limit > 100) throw new Failure(Code.INVALID_REQUEST);
        return listing(store.listOwned(Objects.requireNonNull(actor), limit, cursor == null ? null : decode(cursor)));
    }

    private static String encode(Cursor cursor) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((cursor.admittedAt() + "\n" + cursor.runId()).getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String value) {
        try {
            // 只接受长度受限且可规范重编码的游标，避免宽松解析产生多个分页边界表示。
            if (value.length() > 256 || !value.matches("[A-Za-z0-9_-]+"))
                throw new IllegalArgumentException();
            var pieces = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\n", -1);
            if (pieces.length != 2)
                throw new IllegalArgumentException();
            var instant = Instant.parse(pieces[0]);
            int year = instant.atOffset(ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9999)
                throw new IllegalArgumentException();
            var cursor = new Cursor(instant, UUID.fromString(pieces[1]));
            if (!encode(cursor).equals(value))
                throw new IllegalArgumentException();
            return cursor;
        } catch (RuntimeException e) {
            throw new Failure(Code.INVALID_REQUEST);
        }
    }

    public record Detail(UUID runId, int schemaVersion, ArchiveStatus archiveStatus, Instant admittedAt,
            Instant executionFinishedAt,
            Instant closedAt, Instant retainedUntil, Execution execution, PayloadCandidate result,
            Visibility resultVisibility, String provenance) {
    }

    public record Execution(com.zhku.agent.agent.runtime.AgentRunState state,
            com.zhku.agent.agent.runtime.AgentStopReason stopReason,
            com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason endReason, int currentStep,
            com.zhku.agent.agent.runtime.contract.AgentRuntimeError.Category errorCategory, String errorCode,
            String businessStatus, String failureCategory, CleanupOutcome cleanupOutcome) {
    }

    public record Summary(UUID runId, int schemaVersion, ArchiveStatus archiveStatus, Instant admittedAt,
            Instant closedAt, Instant retainedUntil) {
    }

    public record Listing(List<Summary> items, String nextCursor) {
    }
}
