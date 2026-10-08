package com.zhku.knowledge.campusagent.archive;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampusRunHistoryServiceTest {
    private final CampusRunArchiveStore store = mock(CampusRunArchiveStore.class);
    private final CampusRunHistoryService service = new CampusRunHistoryService(store);

    @Test
    void historyIsProjectedWithoutOwnerRevisionOrTerminated() {
        var archive = admitted(UUID.randomUUID());
        when(store.find(archive.runId())).thenReturn(Optional.of(
                new Read(archive, new Visibility(VisibilityStatus.NOT_STORED, Reason.NO_RESULT_FOR_OUTCOME), null)));
        var detail = service.find(archive.runId());
        assertThat(detail).isNotNull();
        assertThat(detail.execution()).isNull();
        assertThat(detail.result()).isNull();
        assertThat(detail.provenance()).isEqualTo("REFERENCE_ONLY");
        assertThat(Arrays.stream(CampusRunHistoryService.Detail.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("terminated", "owner", "revision", "steps", "question");
    }

    @Test
    void missingArchiveIsFixedNotFound() {
        when(store.find(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.find(UUID.randomUUID())).isInstanceOf(Failure.class)
                .extracting(e -> ((Failure) e).code()).isEqualTo(Code.ARCHIVE_NOT_FOUND);
    }

    @Test
    void strictCursorRoundTripsAndPassesOnlyDecodedKeysetToStore() {
        var row = admitted(UUID.randomUUID());
        var cursor = new Cursor(Instant.parse("2026-10-03T01:02:03Z"), row.runId());
        when(store.list(20, null)).thenReturn(new Page(List.of(row), cursor));
        var first = service.list(20, null);
        assertThat(first).isNotNull();
        assertThat(first.nextCursor()).isNotBlank().doesNotContain("=");
        when(store.list(20, cursor)).thenReturn(new Page(List.of(), null));
        assertThat(service.list(20, first.nextCursor()).items()).isEmpty();
        verify(store).list(20, cursor);
        assertThat(Arrays.stream(CampusRunHistoryService.Summary.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .doesNotContain("answer", "source", "result", "owner", "revision");
    }

    @Test
    void invalidBoundsAndCursorNeverReachStorage() {
        for (var limit : new int[] { 0, 101 })
            assertThatThrownBy(() -> service.list(limit, null)).isInstanceOf(Failure.class);
        for (var cursor : List.of("", "!", "a".repeat(257), "YWJj",
                encode("0000-01-01T00:00:00Z\n" + UUID.randomUUID()),
                encode("2026-10-03T00:00:00Z\n1-1-1-1-1"),
                encode("2026-10-03T00:00:00Z\n" + UUID.randomUUID() + "\nextra")))
            assertThatThrownBy(() -> service.list(20, cursor)).as(cursor).isInstanceOf(Failure.class)
                    .extracting(e -> ((Failure) e).code()).isEqualTo(Code.INVALID_REQUEST);
        verifyNoInteractions(store);
    }

    private static String encode(String input) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static CampusRunArchive admitted(UUID id) {
        return new CampusRunArchive(id, 1, WRITER_VERSION, ArchiveStatus.ADMITTED,
                new OwnerToken(UUID.randomUUID(), 1), 0, Instant.EPOCH, null, null, null, null, null, null, null);
    }
}
