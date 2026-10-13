package com.zhku.knowledge.retrieval;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import com.zhku.knowledge.rag.CitationAuditRecord;
import static org.assertj.core.api.Assertions.*;

class RuleCheckNoiseFixturesTest {
    @Test void noiseHasBoundedUniqueClearlySyntheticDocuments(){
        assertThat(RuleCheckNoiseFixtures.texts(40)).hasSize(40).doesNotHaveDuplicates().allSatisfy(t->assertThat(t).contains("合成压力样本","不是校园政策"));
        assertThat(RuleCheckNoiseFixtures.texts(100)).hasSize(100).doesNotHaveDuplicates();
    }
    @Test void unrelatedTopicFamiliesIncludeNearTermsWithoutReferenceAnswers(){
        assertThat(String.join("",RuleCheckNoiseFixtures.texts(40))).contains("奖学金","图书馆","志愿服务","医保","餐厅");
    }
    @Test void unsupportedOrUnboundedSizesAreRefused(){
        assertThat(RuleCheckNoiseFixtures.texts(0)).isEmpty();
        for(int count:List.of(-1,1,41,10000))assertThatThrownBy(()->RuleCheckNoiseFixtures.texts(count)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unavailablePathsDoNotCountAsHealthyZeroRecall(){
        assertThatThrownBy(()->RuleCheck2025RobustnessTest.requireLexicalHealth(new RetrievalOutcome(List.of(),false,false))).isInstanceOf(AssertionError.class);
    }
    private RuleCheckDevelopmentCapture.Attempt attempt(RetrievedChunk chunk){
        var outcome=new RetrievalOutcome(List.of(chunk),true,false);
        return new RuleCheckDevelopmentCapture.Attempt(UUID.randomUUID(),"SYNTHETIC","合成问题","SYNTHETIC/GENERAL","NOT_EVALUATED","COLLECTED",outcome,null,null,null,null,1,1);
    }
    @Test void safeRejectionAfterNonemptyRetrievalCannotPassAuditControl(){
        var chunk=new RetrievedChunk(UUID.randomUUID(),"合成正文","合成标题","合成定位",1,"test","a".repeat(64),UUID.randomUUID());
        assertThatThrownBy(()->RuleCheck2025RobustnessTest.requireAuditBindings(attempt(chunk),List.of())).isInstanceOf(AssertionError.class);
    }
    @Test void auditCountWithoutExactContentBindingCannotPass(){
        var chunk=new RetrievedChunk(UUID.randomUUID(),"合成正文","合成标题","合成定位",1,"test","a".repeat(64),UUID.randomUUID());
        var wrong=new CitationAuditRecord("synthetic-alias",chunk.chunkId(),"RULE_CHECK_CAPTURE",Instant.EPOCH,chunk.sourceId(),"test","b".repeat(64),chunk.content());
        assertThatThrownBy(()->RuleCheck2025RobustnessTest.requireAuditBindings(attempt(chunk),List.of(wrong))).isInstanceOf(AssertionError.class);
    }
    @Test void healthyExactAuditBindingPasses(){
        var chunk=new RetrievedChunk(UUID.randomUUID(),"合成正文","合成标题","合成定位",1,"test","a".repeat(64),UUID.randomUUID());
        var audit=new CitationAuditRecord("synthetic-alias",chunk.chunkId(),"RULE_CHECK_CAPTURE",Instant.EPOCH,chunk.sourceId(),"test",chunk.contentChecksum(),chunk.content());
        assertThatCode(()->RuleCheck2025RobustnessTest.requireAuditBindings(attempt(chunk),List.of(audit))).doesNotThrowAnyException();
    }
}
