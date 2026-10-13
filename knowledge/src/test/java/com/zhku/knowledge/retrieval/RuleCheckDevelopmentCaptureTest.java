package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 合成采集合同，不是实际学生问题成绩。 */
class RuleCheckDevelopmentCaptureTest {
    @TempDir Path temporary;
    private final RuleCheckDevelopmentCapture collector=new RuleCheckDevelopmentCapture();
    private RuleCheckScopedResult insufficient(){return new RuleCheckScopedResult(
        new RuleCheckDecision(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE,"2025.9.2",List.of(),List.of(),List.of("缺证"),List.of()),null,null,null);}
    private RuleCheckDevelopmentCapture.Input input(){return new RuleCheckDevelopmentCapture.Input("Q01","原问句，不替换成条款号？","S01/GENERAL",Map.of());}

    @Test void originalQuestionAndActualCandidateOutcomeAreRetained() {
        var seen=new ArrayList<String>();
        var result=collector.collect(input(),(q,l)->{seen.add(q);return List.of();},(q,l)->List.of(),
            (lex,vector)->(q,c)->insufficient());
        assertThat(seen).containsExactly(input().question());
        assertThat(result.question()).isEqualTo(input().question());assertThat(result.status()).isEqualTo("COLLECTED");
        assertThat(result.retrieval().chunks()).isEmpty();assertThat(result.output().decision().status()).isEqualTo(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE);
        assertThat(result.selectionQuality()).isEqualTo("NOT_EVALUATED");
    }
    @Test void unexpectedLexicalFailureIsNotScoredAsPolicyMissing() {
        var result=collector.collect(input(),(q,l)->{throw new IllegalStateException("SECRET-MUST-NOT-BE-SAVED");},
            (q,l)->List.of(),(lex,vector)->(q,c)->{throw new AssertionError("不可继续");});
        assertThat(result.status()).isEqualTo("COLLECTION_ERROR");assertThat(result.output()).isNull();
        assertThat(result.errorType()).isEqualTo("IllegalStateException");assertThat(result.toString()).doesNotContain("SECRET-MUST-NOT-BE-SAVED");
    }
    @Test void configuredUnavailableVectorIsSeparateFromHealthyEmptyLexical() {
        var result=collector.collect(input(),(q,l)->List.of(),(q,l)->{throw new RetrieverUnavailableException("配置关闭");},
            (lex,vector)->(q,c)->insufficient());
        assertThat(result.status()).isEqualTo("COLLECTED");assertThat(result.retrieval().lexicalAvailable()).isTrue();
        assertThat(result.retrieval().vectorAvailable()).isFalse();assertThat(result.vector().status()).isEqualTo("UNAVAILABLE");
    }
    @Test void applicationFailureRetainsRetrievedCandidatesAndErrorType() {
        var result=collector.collect(input(),(q,l)->List.of(),(q,l)->List.of(),
            (lex,vector)->(q,c)->{throw new IllegalArgumentException("SECRET-MUST-NOT-BE-SAVED");});
        assertThat(result.status()).isEqualTo("COLLECTION_ERROR");assertThat(result.retrieval()).isNotNull();
        assertThat(result.errorType()).isEqualTo("IllegalArgumentException");assertThat(result.toString()).doesNotContain("SECRET-MUST-NOT-BE-SAVED");
    }
    @Test void perAttemptReplayDoesNotInvokeDatabaseAgainOrAcceptAnotherQuery() {
        var calls=new int[1];
        var result=collector.collect(input(),(q,l)->{calls[0]++;return List.of();},(q,l)->List.of(),(lex,vector)->(q,c)->{
            assertThat(lex.retrieve(q,20)).isEmpty();
            assertThatThrownBy(()->lex.retrieve("偷偷改写问句",20)).isInstanceOf(IllegalArgumentException.class);
            return insufficient();});
        assertThat(result.status()).isEqualTo("COLLECTED");assertThat(calls[0]).isEqualTo(1);
        assertThat(result.totalNanos()).isGreaterThanOrEqualTo(result.applicationNanos());
    }
    @Test void independentAttemptsHaveIndependentIdsAndActualCalls() {
        var calls=new int[1];ChunkCandidateRetriever lexical=(q,l)->{calls[0]++;return List.of();};
        var a=collector.collect(input(),lexical,(q,l)->List.of(),(l,v)->(q,c)->insufficient());
        var b=collector.collect(input(),lexical,(q,l)->List.of(),(l,v)->(q,c)->insufficient());
        assertThat(a.attemptId()).isNotEqualTo(b.attemptId());assertThat(calls[0]).isEqualTo(2);
    }
    @Test void originalLocationReaderFailureRemainsVisibleDespiteServiceFallback(){
        var quote=new RuleCheckVerifiedExcerpt("test",UUID.randomUUID(),UUID.randomUUID(),"2025.9.2","a".repeat(64),0,2,"原文");
        var observed=RuleCheckDevelopmentCapture.locate(quote,(s,c)->{throw new IllegalStateException("SECRET-MUST-NOT-BE-SAVED");});
        assertThat(observed.result().code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.UNAVAILABLE);
        assertThat(observed.errorType()).isEqualTo("IllegalStateException");assertThat(observed.nanos()).isGreaterThan(0);
        assertThat(observed.toString()).doesNotContain("SECRET-MUST-NOT-BE-SAVED");
    }
    @Test void failedControlCannotEraseAlreadyCollectedRecords() throws Exception {
        Path output=temporary.resolve("records.jsonl");
        assertThatThrownBy(()->RuleCheckDevelopmentCapture.persistBeforeValidation(output,List.of("actual-record"),()->{throw new IllegalStateException("control failed");})).isInstanceOf(IllegalStateException.class);
        assertThat(Files.readAllLines(output)).containsExactly("actual-record");
    }
}
