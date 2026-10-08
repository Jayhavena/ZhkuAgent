package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.rag.CitationAuditRecord;
import com.zhku.knowledge.rag.CitationIdFactory;
import com.zhku.knowledge.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RuleCheckEvidenceCaptureTest {
    private final UUID source = UUID.randomUUID();
    private final Map<UUID, RuleCheckEvidenceSnapshot> current = new HashMap<>();
    private final List<CitationAuditRecord> audit = new ArrayList<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger ids = new AtomicInteger();
    private final Instant now = Instant.parse("2026-10-08T08:00:00Z");

    private RuleCheckEvidenceCapture capture(CitationIdFactory factory) {
        return new RuleCheckEvidenceCapture(new RuleCheckEvidenceVerifier((s,c)-> {
            reads.incrementAndGet();
            return Optional.ofNullable(current.get(c));
        }), factory, audit::add, Clock.fixed(now, ZoneOffset.UTC));
    }
    private RuleCheckEvidenceCapture capture() { return capture(()->"alias-"+ids.incrementAndGet()); }
    private RetrievedChunk chunk(String text) {
        var c = new RetrievedChunk(UUID.randomUUID(), text, "合成来源", "合成定位", 1, "test-v1", hash(text), source);
        current.put(c.chunkId(), new RuleCheckEvidenceSnapshot("latest-unrelated-alias", source, c.chunkId(),
            c.sourceVersion(), c.contentChecksum(), c.content(), true));
        return c;
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void rejected(RuleCheckEvidenceCapture.CaptureResult result, RuleCheckEvidenceCapture.RejectionReason reason) {
        assertThat(result.accepted()).isFalse();
        assertThat(result.snapshots()).isEmpty();
        assertThat(result.rejectionReason()).isEqualTo(reason);
    }

    @Test void capturesPersistentIdentityWithServerAliasesAndAudit() {
        var a = chunk("合成规则：上限4。"); var b = chunk("合成例外：另查计划。");
        var result = capture().capture(List.of(a,b));
        assertThat(result.accepted()).isTrue();
        assertThat(result.snapshots()).extracting(RuleCheckEvidenceSnapshot::citationId).containsExactly("alias-1","alias-2");
        assertThat(result.snapshots()).extracting(RuleCheckEvidenceSnapshot::sourceId).containsOnly(source);
        assertThat(result.snapshots()).extracting(RuleCheckEvidenceSnapshot::text).containsExactly(a.content(),b.content());
        assertThat(reads).hasValue(2);
        assertThat(audit).extracting(CitationAuditRecord::chunkId).containsExactly(a.chunkId(),b.chunkId());
        assertThat(audit).allSatisfy(row->{
            assertThat(row.route()).isEqualTo("RULE_CHECK_CAPTURE"); assertThat(row.issuedAt()).isEqualTo(now);
            assertThat(row.sourceId()).isEqualTo(source); assertThat(row.sourceRevision()).isEqualTo("test-v1");
            var original = current.get(row.chunkId());
            assertThat(row.contentChecksum()).isEqualTo(original.checksum()); assertThat(row.content()).isEqualTo(original.text());
        });
        assertThatThrownBy(()->result.snapshots().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void rejectsLegacyChunkWithoutInventingSourceIdentity() {
        var c=chunk("合成原文");
        var legacy=new RetrievedChunk(c.chunkId(),c.content(),c.sourceTitle(),c.sourceLocator(),1,c.sourceVersion(),c.contentChecksum());
        rejected(capture().capture(List.of(legacy)), RuleCheckEvidenceCapture.RejectionReason.INVALID_CANDIDATES);
        assertThat(reads).hasValue(0); assertThat(audit).isEmpty();
    }
    @Test void duplicateChunkAliasesAreNotASecondEvidenceIdentity() {
        var c=chunk("合成原文");
        rejected(capture().capture(List.of(c,c)), RuleCheckEvidenceCapture.RejectionReason.INVALID_CANDIDATES);
        assertThat(reads).hasValue(0); assertThat(audit).isEmpty();
    }
    @Test void duplicateServerAliasRejectsBatchBeforeAudit() {
        rejected(capture(()->"same-alias").capture(List.of(chunk("甲"),chunk("乙"))),
            RuleCheckEvidenceCapture.RejectionReason.CITATION_ALLOCATION_FAILED);
        assertThat(audit).isEmpty();
    }
    @Test void factoryFailureCannotExposeFirstPreparedSnapshot() {
        rejected(capture(()->{if(ids.incrementAndGet()==2) throw new IllegalStateException("opaque failure"); return "alias-1";})
            .capture(List.of(chunk("甲"),chunk("乙"))), RuleCheckEvidenceCapture.RejectionReason.CITATION_ALLOCATION_FAILED);
        assertThat(audit).isEmpty();
    }
    @Test void blankAliasFailsClosed() {
        rejected(capture(()->" ").capture(List.of(chunk("甲"))), RuleCheckEvidenceCapture.RejectionReason.CITATION_ALLOCATION_FAILED);
        assertThat(audit).isEmpty();
    }
    @Test void validIdWithTamperedFortyDoesNotBecomeEvidence() {
        var c=chunk("合成上限4。");
        var changed=new RetrievedChunk(c.chunkId(),"合成上限40。",c.sourceTitle(),c.sourceLocator(),1,c.sourceVersion(),c.contentChecksum(),source);
        rejected(capture().capture(List.of(changed)), RuleCheckEvidenceCapture.RejectionReason.UNVERIFIED_EVIDENCE);
        assertThat(audit).isEmpty();
    }
    @Test void withdrawnSecondChunkRejectsWholeBatch() {
        var a=chunk("甲"); var b=chunk("乙"); current.remove(b.chunkId());
        rejected(capture().capture(List.of(a,b)), RuleCheckEvidenceCapture.RejectionReason.UNVERIFIED_EVIDENCE);
        assertThat(audit).isEmpty();
    }
    @Test void sameTextDifferentCurrentRevisionStillRejects() {
        var c=chunk("甲"); current.put(c.chunkId(),new RuleCheckEvidenceSnapshot("other",source,c.chunkId(),"test-v2",c.contentChecksum(),c.content(),true));
        rejected(capture().capture(List.of(c)), RuleCheckEvidenceCapture.RejectionReason.UNVERIFIED_EVIDENCE);
        assertThat(audit).isEmpty();
    }
    @Test void unavailableReaderDoesNotReuseRetrievedText() {
        var service=new RuleCheckEvidenceCapture(new RuleCheckEvidenceVerifier((s,c)->{throw new IllegalStateException("unavailable");}),
            ()->"alias",audit::add,Clock.fixed(now,ZoneOffset.UTC));
        rejected(service.capture(List.of(chunk("甲"))),RuleCheckEvidenceCapture.RejectionReason.UNVERIFIED_EVIDENCE);
        assertThat(audit).isEmpty();
    }
    @Test void partialAuditFailureDoesNotReturnConsumableEvidence() {
        var count=new AtomicInteger();
        var service=new RuleCheckEvidenceCapture(new RuleCheckEvidenceVerifier((s,c)->Optional.ofNullable(current.get(c))),
            ()->"alias-"+ids.incrementAndGet(), row->{if(count.incrementAndGet()==2) throw new IllegalStateException("audit unavailable");audit.add(row);},
            Clock.fixed(now,ZoneOffset.UTC));
        rejected(service.capture(List.of(chunk("甲"),chunk("乙"))),RuleCheckEvidenceCapture.RejectionReason.AUDIT_UNAVAILABLE);
        assertThat(audit).hasSize(1);
    }
    @Test void emptyAndNullInputsDoNotReadOrAudit() {
        var service=capture();
        rejected(service.capture(List.of()),RuleCheckEvidenceCapture.RejectionReason.INVALID_CANDIDATES);
        rejected(service.capture(null),RuleCheckEvidenceCapture.RejectionReason.INVALID_CANDIDATES);
        var containingNull=new ArrayList<RetrievedChunk>(); containingNull.add(null);
        rejected(service.capture(containingNull),RuleCheckEvidenceCapture.RejectionReason.INVALID_CANDIDATES);
        assertThat(reads).hasValue(0); assertThat(audit).isEmpty();
    }
}
