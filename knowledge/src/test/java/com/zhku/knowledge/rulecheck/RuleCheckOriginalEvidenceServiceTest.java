package com.zhku.knowledge.rulecheck;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.rulecheck.RuleCheckOriginalEvidenceService.Code.*;
import static org.assertj.core.api.Assertions.*;

class RuleCheckOriginalEvidenceServiceTest {
    final UUID source=UUID.randomUUID(),chunk=UUID.randomUUID();
    static final String TEXT="合成上下文。上限4学分。例外另行核实。";
    RuleCheckEvidenceSnapshot snapshot(String revision,String hash,String text,boolean eligible){return new RuleCheckEvidenceSnapshot("current-alias",source,chunk,revision,hash,text,eligible);}
    RuleCheckLocatedEvidence located(RuleCheckEvidenceSnapshot snapshot,String url){return new RuleCheckLocatedEvidence(snapshot,"合成手册",url,"合成第1条",76,"合成章节");}
    RuleCheckVerifiedExcerpt quote(String text,int start,int end){return new RuleCheckVerifiedExcerpt("round-citation",source,chunk,"v1",sha(text),start,end,text.substring(start,end));}
    RuleCheckOriginalEvidenceService service(RuleCheckLocatedEvidence row){return new RuleCheckOriginalEvidenceService((s,c)->Optional.of(row),"v1",Set.of("official.example"));}
    @Test void returnsCurrentMetadataAndQuoteOffsetsWithoutInventingPageKind() {
        var result=service(located(snapshot("v1",sha(TEXT),TEXT,true),"https://official.example/manual.pdf")).locate(quote(TEXT,6,12));
        assertThat(result.code()).isEqualTo(FOUND);var view=result.original();assertThat(view.citationId()).isEqualTo("round-citation");
        assertThat(view.title()).isEqualTo("合成手册");assertThat(view.sourceLocator()).isEqualTo("合成第1条");assertThat(view.recordedPageNumber()).isEqualTo(76);
        assertThat(view.pageNumberKind()).isEqualTo("RECORDED_SOURCE_PAGE");assertThat(view.safeSourceUrl()).isEqualTo("https://official.example/manual.pdf");
        assertThat(view.contextText().substring(view.quoteStartInclusive()-view.contextStartInclusive(),view.quoteEndExclusive()-view.contextStartInclusive())).isEqualTo(TEXT.substring(6,12));
        assertThat(view.unknownItems()).contains("记录页码的物理页与印刷页映射尚未核实");
    }
    @Test void versionHashEligibilityAndPersistentIdentityChangesRejectOldReference() {
        var q=quote(TEXT,6,12);
        var other=new RuleCheckEvidenceSnapshot("current",UUID.randomUUID(),chunk,"v1",sha(TEXT),TEXT,true);
        for(var snapshot:List.of(snapshot("v2",sha(TEXT),TEXT,true),snapshot("v1",sha(TEXT+"变"),TEXT+"变",true),snapshot("v1",sha(TEXT),TEXT,false),other)) {
            var result=service(located(snapshot,null)).locate(q);assertThat(result.code()).isEqualTo(REFERENCE_CHANGED);assertThat(result.original()).isNull();
        }
    }
    @Test void corruptedCurrentTextWithUnchangedStoredChecksumIsRejected() {
        var result=service(located(snapshot("v1",sha(TEXT),TEXT.replace("4","40"),true),null)).locate(quote(TEXT,6,12));
        assertThat(result.code()).isEqualTo(REFERENCE_CHANGED);assertThat(result.original()).isNull();
    }
    @Test void currentReadFailureOrAbsentRowsDoesNotReturnBodyOrCause() {
        for(var reader:List.<RuleCheckOriginalEvidenceService.Reader>of((s,c)->Optional.empty(),(s,c)->{throw new IllegalStateException("private database cause");})) {
            var result=new RuleCheckOriginalEvidenceService(reader,"v1",Set.of()).locate(quote(TEXT,6,12));
            assertThat(result.code()).isEqualTo(UNAVAILABLE);assertThat(result.original()).isNull();assertThat(result.toString()).doesNotContain(TEXT,"private");
        }
    }
    @Test void invalidReferenceCannotInvokeReader() {
        var calls=new AtomicInteger();var service=new RuleCheckOriginalEvidenceService((s,c)->{calls.incrementAndGet();return Optional.empty();},"v1",Set.of());
        for(var q:Arrays.asList(null,new RuleCheckVerifiedExcerpt("",source,chunk,"v1",sha(TEXT),0,1,"合"),
                new RuleCheckVerifiedExcerpt("c",source,chunk,"v2",sha(TEXT),0,1,"合"),new RuleCheckVerifiedExcerpt("c",source,chunk,"v1",sha(TEXT),-1,1,"合"),
                new RuleCheckVerifiedExcerpt("c",source,chunk,"v1",sha(TEXT),0,2,"合")))
            assertThat(service.locate(q).code()).isEqualTo(INVALID_REFERENCE);
        assertThat(calls).hasValue(0);
    }
    @Test void surrogateSplitAndQuoteMismatchAreRejectedByCurrentVerification() {
        String text="😀合成原文";var service=service(located(snapshot("v1",sha(text),text,true),null));
        for(var q:List.of(new RuleCheckVerifiedExcerpt("c",source,chunk,"v1",sha(text),0,1,"\uD83D"),
                new RuleCheckVerifiedExcerpt("c",source,chunk,"v1",sha(text),2,4,"别的")))
            assertThat(service.locate(q).original()).isNull();
    }
    @Test void sourceUrlOnlyAllowsExplicitHttpsHostsWithoutUserInfoOrUnexpectedPort() {
        for(String url:List.of("javascript:alert(1)","file:///D:/secret","data:text/html,x","http://official.example/manual.pdf",
            "https://official.example.evil/manual.pdf","https://evil.example@official.example/manual.pdf","https://official.example:8443/manual.pdf","https://evil.example/manual.pdf")) {
            var result=service(located(snapshot("v1",sha(TEXT),TEXT,true),url)).locate(quote(TEXT,6,12));
            assertThat(result.code()).isEqualTo(FOUND);assertThat(result.original().safeSourceUrl()).isNull();
            assertThat(result.original().unknownItems()).contains("暂无已验证安全的原件地址");
        }
    }
    @Test void existingUrlFragmentIsNotReplacedWithUnverifiedPageAnchor() {
        String url="https://official.example/manual.pdf?download=1#existing";
        var result=service(located(snapshot("v1",sha(TEXT),TEXT,true),url)).locate(quote(TEXT,6,12));
        assertThat(result.code()).isEqualTo(FOUND);assertThat(result.original().safeSourceUrl()).isEqualTo(url).doesNotContain("#page=");
    }
    @Test void missingMetadataIsExplicitUnknownWithoutInventedValues() {
        var row=new RuleCheckLocatedEvidence(snapshot("v1",sha(TEXT),TEXT,true),null,null,"",null,null);
        var result=service(row).locate(quote(TEXT,6,12));assertThat(result.code()).isEqualTo(FOUND);
        assertThat(result.original().title()).isNull();assertThat(result.original().sourceLocator()).isNull();
        assertThat(result.original().recordedPageNumber()).isNull();assertThat(result.original().pageNumberKind()).isEqualTo("UNKNOWN");
        assertThat(result.original().unknownItems()).contains("来源标题尚未记录","条款定位尚未记录","来源页码尚未记录");
    }
    @Test void contextWindowIsBoundedAndNeverSplitsSurrogatePairs() {
        String text="前".repeat(743)+"😀"+"核验摘录"+"😀"+"后".repeat(800);int start=745,end=749;
        var result=service(located(snapshot("v1",sha(text),text,true),null)).locate(quote(text,start,end));assertThat(result.code()).isEqualTo(FOUND);
        var view=result.original();assertThat(view.contextText().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8192);
        assertThat(Character.isLowSurrogate(view.contextText().charAt(0))).isFalse();assertThat(Character.isHighSurrogate(view.contextText().charAt(view.contextText().length()-1))).isFalse();
        assertThat(view.contextText().substring(start-view.contextStartInclusive(),end-view.contextStartInclusive())).isEqualTo("核验摘录");
        assertThat(view.contextText().length()).isLessThanOrEqualTo(518);
    }
    @Test void cannotTruncateAnOversizedVerifiedExcerptToPretendOriginalShown() {
        String text="中".repeat(3000);var result=service(located(snapshot("v1",sha(text),text,true),null)).locate(quote(text,0,text.length()));
        assertThat(result.code()).isEqualTo(UNAVAILABLE);assertThat(result.original()).isNull();
    }
    @Test void surrogatePairsAcrossActualWindowEdgesShrinkInsideEachSideBudget() {
        String text="前".repeat(300)+"😀"+"前".repeat(255)+"核验摘录"+"后".repeat(255)+"😀"+"后".repeat(300);
        int start=557,end=561;
        var result=service(located(snapshot("v1",sha(text),text,true),null)).locate(quote(text,start,end));assertThat(result.code()).isEqualTo(FOUND);
        var view=result.original();assertThat(start-view.contextStartInclusive()).isLessThanOrEqualTo(256);
        assertThat(view.contextStartInclusive()+view.contextText().length()-end).isLessThanOrEqualTo(256);
        assertThat(Character.isLowSurrogate(view.contextText().charAt(0))).isFalse();
        assertThat(Character.isHighSurrogate(view.contextText().charAt(view.contextText().length()-1))).isFalse();
        assertThat(view.contextText().substring(start-view.contextStartInclusive(),end-view.contextStartInclusive())).isEqualTo("核验摘录");
    }
    @Test void sourceLocatorAndTitleStayPlainTextDataForFutureEscapedRendering() {
        var row=new RuleCheckLocatedEvidence(snapshot("v1",sha(TEXT),TEXT,true),"<img src=x onerror=alert(1)>",null,"<script>locator</script>",1,null);
        var result=service(row).locate(quote(TEXT,6,12));assertThat(result.code()).isEqualTo(FOUND);
        assertThat(result.original().title()).isEqualTo(row.title());assertThat(result.original().sourceLocator()).isEqualTo(row.sourceLocator());
    }
    static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
}
