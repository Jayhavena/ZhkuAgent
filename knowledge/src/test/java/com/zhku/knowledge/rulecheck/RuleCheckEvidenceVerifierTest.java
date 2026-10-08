package com.zhku.knowledge.rulecheck;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.zhku.knowledge.rulecheck.RuleCheckEvidenceVerifier.RejectionReason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleCheckEvidenceVerifierTest {
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHUNK = UUID.fromString("00000000-0000-0000-0000-000000000002");
    // 合成合同数据，无审核或学生发布资格；不能作为真实政策参考。
    private static final String TEXT = "合成条款：上限4学分。特殊情形另行审核。";
    private static final String QUOTE = "上限4学分。";
    private static final int START = TEXT.indexOf(QUOTE);
    private static final RuleCheckEvidenceSnapshot ORIGINAL = snapshot(TEXT);
    private static final RuleCheckEvidenceSpan PROPOSAL = new RuleCheckEvidenceSpan("ev-1", START, START + QUOTE.length(), QUOTE);

    @Test void validExcerptUsesServerIdentityAndReadsCurrentEvidenceExactlyOnce() {
        var calls = new AtomicInteger();
        var result = new RuleCheckEvidenceVerifier((source, chunk) -> {
            calls.incrementAndGet();
            assertThat(source).isEqualTo(SOURCE); assertThat(chunk).isEqualTo(CHUNK);
            return Optional.of(ORIGINAL);
        }).verify(PROPOSAL, List.of(ORIGINAL));
        assertThat(result.accepted()).isTrue();
        assertThat(result.rejectionReason()).isNull();
        assertThat(result.excerpt()).isEqualTo(new RuleCheckVerifiedExcerpt("ev-1", SOURCE, CHUNK,
                "v1", ORIGINAL.checksum(), START, START + QUOTE.length(), QUOTE));
        assertThat(calls).hasValue(1);
    }

    @Test void validCitationAndRangeCannotChangeFourIntoForty() {
        rejectBeforeRead(new RuleCheckEvidenceSpan("ev-1", START, START + QUOTE.length(), "上限40学分。"), List.of(ORIGINAL), QUOTE_MISMATCH);
    }

    @Test void unknownCitationCannotCauseCurrentRead() {
        rejectBeforeRead(new RuleCheckEvidenceSpan("forged", START, START + QUOTE.length(), QUOTE), List.of(ORIGINAL), UNKNOWN_CITATION);
    }

    @Test void nullOrBlankProposalIsControlledRejection() {
        rejectBeforeRead(null, List.of(ORIGINAL), INVALID_PROPOSAL);
        for (var proposal : List.of(new RuleCheckEvidenceSpan(null, 0, 1, "x"),
                new RuleCheckEvidenceSpan(" ", 0, 1, "x"),new RuleCheckEvidenceSpan("ev-1", 0, 1, null),
                new RuleCheckEvidenceSpan("ev-1", 0, 1, " \n"))) {
            rejectBeforeRead(proposal, List.of(ORIGINAL), INVALID_PROPOSAL);
        }
    }

    @Test void duplicateIdentifiersRejectEvenIfOneWouldMatch() {
        var other = new RuleCheckEvidenceSnapshot("ev-1", SOURCE, UUID.randomUUID(), "v1", sha(TEXT), TEXT, true);
        rejectBeforeRead(PROPOSAL, List.of(ORIGINAL, other), DUPLICATE_CITATION);
        var unrelated = new RuleCheckEvidenceSnapshot("ev-2", SOURCE, CHUNK, "v1", sha(TEXT), TEXT, true);
        rejectBeforeRead(PROPOSAL, List.of(ORIGINAL, unrelated, unrelated), DUPLICATE_CITATION);
    }

    @Test void malformedApprovedListIsRejectedBeforeRead() {
        rejectBeforeRead(PROPOSAL, null, INVALID_EVIDENCE);
        var rows = new ArrayList<RuleCheckEvidenceSnapshot>(); rows.add(null);
        rejectBeforeRead(PROPOSAL, rows, INVALID_EVIDENCE);
        rejectBeforeRead(PROPOSAL, List.of(copy(" ", SOURCE, CHUNK, "v1", sha(TEXT), TEXT, true)), INVALID_EVIDENCE);
        rejectBeforeRead(PROPOSAL, List.of(copy("ev-1", SOURCE, CHUNK, " ", sha(TEXT), TEXT, true)), INVALID_EVIDENCE);
    }

    @Test void capturedIneligibleEvidenceCannotCauseRead() {
        rejectBeforeRead(PROPOSAL, List.of(copy("ev-1", SOURCE, CHUNK, "v1", sha(TEXT), TEXT, false)), INELIGIBLE_EVIDENCE);
    }

    @Test void capturedChecksumMustBeRealUtf8Sha256() {
        for (var checksum : List.of("", "not-sha", "0".repeat(64), sha(TEXT).substring(1))) {
            rejectBeforeRead(PROPOSAL, List.of(copy("ev-1", SOURCE, CHUNK, "v1", checksum, TEXT, true)), INVALID_CHECKSUM);
        }
    }

    @Test void checksumHexCaseDoesNotChangeItsValue() {
        var upper = copy("ev-1", SOURCE, CHUNK, "v1", sha(TEXT).toUpperCase(), TEXT, true);
        assertThat(new RuleCheckEvidenceVerifier((s,c)->Optional.of(upper)).verify(PROPOSAL,List.of(ORIGINAL)).accepted()).isTrue();
        assertThat(new RuleCheckEvidenceVerifier((s,c)->Optional.of(ORIGINAL)).verify(PROPOSAL,List.of(upper)).accepted()).isTrue();
    }

    @Test void negativeEmptyReversedAndOverflowRangesAreControlled() {
        for (int[] range : List.of(new int[]{-1,3},new int[]{0,0},new int[]{4,2},
                new int[]{0,TEXT.length()+1},new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE},
                new int[]{Integer.MIN_VALUE,1})) {
            rejectBeforeRead(new RuleCheckEvidenceSpan("ev-1",range[0],range[1],QUOTE),List.of(ORIGINAL),INVALID_SPAN);
        }
    }

    @Test void utf16SpanCannotSplitSurrogatePair() {
        var source=snapshot("合成😀条款4。");
        rejectBeforeRead(new RuleCheckEvidenceSpan("ev-1",2,3,source.text().substring(2,3)),List.of(source),INVALID_SPAN);
        rejectBeforeRead(new RuleCheckEvidenceSpan("ev-1",3,4,source.text().substring(3,4)),List.of(source),INVALID_SPAN);
        var valid=new RuleCheckEvidenceSpan("ev-1",2,4,"😀");
        assertThat(new RuleCheckEvidenceVerifier((s,c)->Optional.of(source)).verify(valid,List.of(source)).excerpt().text()).isEqualTo("😀");
    }

    @Test void malformedUnicodeSourceCannotBecomeAnExcerpt() {
        var source=snapshot("条款\uD83D4。");
        rejectBeforeRead(new RuleCheckEvidenceSpan("ev-1",0,2,"条款"),List.of(source),INVALID_EVIDENCE);
    }

    @Test void repeatedTextIsVerifiedAtSpecifiedPosition() {
        var source=snapshot("4学分；4学分。");
        var proposal=new RuleCheckEvidenceSpan("ev-1",4,7,"4学分");
        var result=new RuleCheckEvidenceVerifier((s,c)->Optional.of(source)).verify(proposal,List.of(source));
        assertThat(result.accepted()).isTrue();assertThat(result.excerpt().startInclusive()).isEqualTo(4);
        rejectBeforeRead(new RuleCheckEvidenceSpan("ev-1",3,6,"4学分"),List.of(source),QUOTE_MISMATCH);
    }

    @Test void currentMissingOrReaderFailureCannotLeakProposalText() {
        var calls=new AtomicInteger();
        var missing=new RuleCheckEvidenceVerifier((s,c)->{calls.incrementAndGet();return Optional.empty();}).verify(PROPOSAL,List.of(ORIGINAL));
        assertRejected(missing,CURRENT_EVIDENCE_UNAVAILABLE);assertThat(calls).hasValue(1);
        assertRejected(new RuleCheckEvidenceVerifier((s,c)->{throw new IllegalStateException("内部错误");}).verify(PROPOSAL,List.of(ORIGINAL)),CURRENT_EVIDENCE_UNAVAILABLE);
        assertRejected(new RuleCheckEvidenceVerifier((s,c)->null).verify(PROPOSAL,List.of(ORIGINAL)),CURRENT_EVIDENCE_UNAVAILABLE);
    }

    @Test void currentWithdrawalIsRejected() {
        rejectCurrent(copy("ev-1",SOURCE,CHUNK,"v1",sha(TEXT),TEXT,false));
    }

    @Test void identicalTextDoesNotBypassRevisionChange() {
        rejectCurrent(copy("ev-1",SOURCE,CHUNK,"v2",sha(TEXT),TEXT,true));
    }

    @Test void currentIdentityCannotSubstituteAnotherSourceOrChunk() {
        rejectCurrent(copy("ev-1",UUID.randomUUID(),CHUNK,"v1",sha(TEXT),TEXT,true));
        rejectCurrent(copy("ev-1",SOURCE,UUID.randomUUID(),"v1",sha(TEXT),TEXT,true));
    }

    @Test void currentTextOrChecksumChangeIsRejectedEvenWithSameQuote() {
        var changed=TEXT+"追加例外。";
        rejectCurrent(copy("ev-1",SOURCE,CHUNK,"v1",sha(changed),changed,true));
        rejectCurrent(copy("ev-1",SOURCE,CHUNK,"v1",sha(TEXT),changed,true));
        rejectCurrent(copy("ev-1",SOURCE,CHUNK,"v1","0".repeat(64),TEXT,true));
    }

    @Test void resultCannotRepresentSuccessAndFailureTogether() {
        assertThatThrownBy(()->new RuleCheckEvidenceVerifier.VerificationResult(null,null)).isInstanceOf(IllegalArgumentException.class);
        var excerpt=new RuleCheckVerifiedExcerpt("ev-1",SOURCE,CHUNK,"v1",sha(TEXT),START,START+QUOTE.length(),QUOTE);
        assertThatThrownBy(()->new RuleCheckEvidenceVerifier.VerificationResult(excerpt,INVALID_PROPOSAL)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void rejectCurrent(RuleCheckEvidenceSnapshot current) {
        var calls=new AtomicInteger();
        var result=new RuleCheckEvidenceVerifier((s,c)->{calls.incrementAndGet();return Optional.of(current);}).verify(PROPOSAL,List.of(ORIGINAL));
        assertRejected(result,CURRENT_EVIDENCE_CHANGED);assertThat(calls).hasValue(1);
    }

    private static void rejectBeforeRead(RuleCheckEvidenceSpan proposal,List<RuleCheckEvidenceSnapshot> approved,
                                         RuleCheckEvidenceVerifier.RejectionReason expected) {
        var calls=new AtomicInteger();
        var result=new RuleCheckEvidenceVerifier((s,c)->{calls.incrementAndGet();return Optional.of(ORIGINAL);}).verify(proposal,approved);
        assertRejected(result,expected);assertThat(calls).hasValue(0);
    }

    private static void assertRejected(RuleCheckEvidenceVerifier.VerificationResult result, RuleCheckEvidenceVerifier.RejectionReason expected) {
        assertThat(result.accepted()).isFalse();assertThat(result.excerpt()).isNull();assertThat(result.rejectionReason()).isEqualTo(expected);
    }

    private static RuleCheckEvidenceSnapshot snapshot(String text) {return copy("ev-1",SOURCE,CHUNK,"v1",sha(text),text,true);}
    private static RuleCheckEvidenceSnapshot copy(String id,UUID source,UUID chunk,String version,String hash,String text,boolean eligible) {
        return new RuleCheckEvidenceSnapshot(id,source,chunk,version,hash,text,eligible);
    }
    private static String sha(String text) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch (NoSuchAlgorithmException error) {throw new AssertionError(error);}
    }
}
