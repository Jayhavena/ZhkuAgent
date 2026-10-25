package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ChineseBigramLexicalRankerTest {
    @Test void duplicateChunksCannotOccupyEverySlotBeforeDifferentRelevantEvidence(){
        var repeated=row("公共选修课程的学分登记统计说明，不能据此判断个人选课资格。");
        var duplicate=new RetrievedChunk(UUID.randomUUID(),repeated.content(),repeated.sourceTitle(),repeated.sourceLocator(),null,
            repeated.sourceVersion(),repeated.contentChecksum(),repeated.sourceId());
        var different=row("每学期最高不得超过4个学分。");
        var result=ChineseBigramLexicalRanker.rank("公共选修 学分",List.of(repeated,duplicate,different),2);
        assertThat(result).contains(different);assertThat(result).hasSize(2);
    }
    @Test void recordIdsCanCollapseButPolicyNumbersNegationsAndProvenanceCannot(){
        UUID source=UUID.randomUUID();
        var a=new RetrievedChunk(UUID.randomUUID(),"记录1，每学期上限4学分。","合成","合成",null,"test","a",source);
        var b=new RetrievedChunk(UUID.randomUUID(),"记录2，每学期上限4学分。","合成","合成",null,"test","b",source);
        var changed=new RetrievedChunk(UUID.randomUUID(),"记录3，每学期上限5学分。","合成","合成",null,"test","c",source);
        var negated=new RetrievedChunk(UUID.randomUUID(),"记录4，每学期不上限4学分。","合成","合成",null,"test","d",source);
        var elsewhere=new RetrievedChunk(UUID.randomUUID(),a.content(),"别的来源","合成",null,"test","e",UUID.randomUUID());
        var result=ChineseBigramLexicalRanker.rank("每学期 学分",List.of(a,b,changed,negated,elsewhere),10);
        assertThat(result).hasSize(4).contains(changed,negated,elsewhere);
        assertThat(result.stream().filter(r->r.equals(a)||r.equals(b)).count()).isEqualTo(1);
    }
    private RetrievedChunk row(UUID id,String text){return new RetrievedChunk(id,text,"合成标题","合成第1条",null,"test","hash",UUID.randomUUID());}
    private RetrievedChunk row(String text){return row(UUID.randomUUID(),text);}
    @Test void chineseParaphraseCanMatchWithoutWholeSentenceInDocument(){
        var required=row("每学期选修公共选修课最高不得超过4个学分。");
        assertThat(ChineseBigramLexicalRanker.rank("公共选修课每学期最高可以拿几学分？",List.of(required),5)).containsExactly(required);
    }
    @Test void rareMatchesOutrankCommonOnlyMatchesInEligibleCorpus(){
        var common=row("选修课程选修课程");var rare=row("缓考课程");var other=row("选修课程学分");
        assertThat(ChineseBigramLexicalRanker.rank("选修 缓考",List.of(common,rare,other),1)).containsExactly(rare);
    }
    @Test void punctuationCannotInventChineseCrossBoundaryPhrase(){
        assertThat(ChineseBigramLexicalRanker.rank("缓，考",List.of(row("缓考")),5)).isEmpty();
    }
    @Test void supplementaryHanCharactersUseCodepointBigrams(){
        var required=row("𠀀学相关资料");
        assertThat(ChineseBigramLexicalRanker.rank("𠀀学",List.of(required),5)).containsExactly(required);
    }
    @Test void englishLiteralAndBudgetRemainDeterministic(){
        var first=row(new UUID(Long.MIN_VALUE,0),"ABC");var second=row(new UUID(1,0),"ABC");
        assertThat(ChineseBigramLexicalRanker.rank("ABC",List.of(second,first),1)).containsExactly(first);
        assertThat(ChineseBigramLexicalRanker.rank("ABC",List.of(first),0)).isEmpty();
    }
    @Test void repeatedQueryTermsDoNotMultiplyVotes(){
        var a=row("学分");var b=row("选修学分");
        assertThat(ChineseBigramLexicalRanker.rank("学分 学分 学分 选修",List.of(a,b),5)).containsExactly(b,a);
    }
    @Test void blankSingleHanAndMissingTermsReturnEmpty(){
        for(String query:Arrays.asList(null,"","课","不相关"))assertThat(ChineseBigramLexicalRanker.rank(query,List.of(row("学分")),5)).isEmpty();
    }
    @Test void unavailableLexicalCannotPassTheZeroRecallBaselineHealthGuard(){
        assertThatThrownBy(()->RuleCheck2025LexicalCalibrationTest.requireObservedPaths(new RetrievalOutcome(List.of(),false,false))).isInstanceOf(AssertionError.class);
        assertThatCode(()->RuleCheck2025LexicalCalibrationTest.requireObservedPaths(new RetrievalOutcome(List.of(),true,false))).doesNotThrowAnyException();
    }
    @Test void replacingChunkIdsCannotChangeTiedDifferentContentOrder(){
        var a=new RetrievedChunk(new UUID(Long.MIN_VALUE,0),"课程甲规则","合成标题","定位甲",1,"test","a".repeat(64),UUID.randomUUID());
        var b=new RetrievedChunk(new UUID(1,0),"课程乙规则","合成标题","定位乙",1,"test","b".repeat(64),UUID.randomUUID());
        var rebuiltA=new RetrievedChunk(b.chunkId(),a.content(),a.sourceTitle(),a.sourceLocator(),a.pageNumber(),a.sourceVersion(),a.contentChecksum(),a.sourceId());
        var rebuiltB=new RetrievedChunk(a.chunkId(),b.content(),b.sourceTitle(),b.sourceLocator(),b.pageNumber(),b.sourceVersion(),b.contentChecksum(),b.sourceId());
        var before=ChineseBigramLexicalRanker.rank("课程",List.of(a,b),1).stream().map(RetrievedChunk::content).toList();
        var after=ChineseBigramLexicalRanker.rank("课程",List.of(rebuiltA,rebuiltB),1).stream().map(RetrievedChunk::content).toList();
        assertThat(after).isEqualTo(before);
    }
}
