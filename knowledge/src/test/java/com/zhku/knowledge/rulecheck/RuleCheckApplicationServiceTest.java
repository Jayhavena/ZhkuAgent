package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.rag.*;
import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.*;
import static org.assertj.core.api.Assertions.*;

class RuleCheckApplicationServiceTest {
    // 合成资料与审核身份仅用于工程合同；不装载真实2025规则、不访问模型或数据库。
    private final UUID source=UUID.randomUUID(), chunk=UUID.randomUUID();
    private final String text="合成条款：上限4。";
    private final String hash=sha(text);
    private final RuleCheckRuntimeSettings settings=new RuleCheckRuntimeSettings("fixture","cut-v1","SCOPE","v1",20,5);
    private final AtomicInteger selected=new AtomicInteger(), retrieved=new AtomicInteger(), audited=new AtomicInteger(), reads=new AtomicInteger();
    private final AtomicReference<List<RuleCheckApplicationService.CatalogEntry>> catalogue=new AtomicReference<>();
    private final AtomicBoolean eligible=new AtomicBoolean(true);
    private Runnable duringAudit=()->{};
    private RuleCheckApplicationService.RuleSelector selector=(q,options)->{
        selected.incrementAndGet(); assertThat(q).isEqualTo("合成条款");
        assertThat(options).containsExactly(new RuleCheckApplicationService.RuleOption("test-only","合成规则查询范围"));
        return "test-only";
    };
    private ChunkCandidateRetriever lexical=(q,limit)->{
        retrieved.incrementAndGet(); assertThat(limit).isEqualTo(20);
        return List.of(new RetrievedChunk(chunk,text,"合成", "合成页",1,"v1",hash,source));
    };
    private ChunkCandidateRetriever vector=(q,limit)->List.of();
    private EvidenceBinding binding(){return new EvidenceBinding(source,chunk,"v1",hash,0,text.length(),text);}
    private Fact fact(String id,boolean independent,Map<String,String> when){
        return new Fact(id,"审核的合成事实"+id,independent,when,List.of(binding()));
    }
    private RuleCheckRuleTemplate rule(ReviewState state,List<Fact> facts,List<Condition> conditions,List<String> gaps,List<Conflict> conflicts){
        return new RuleCheckRuleTemplate("test-only","v1",state,state==ReviewState.APPROVED?"合成责任":null,
                state==ReviewState.APPROVED?LocalDate.of(2026,10,8):null,facts,conditions,gaps,conflicts);
    }
    private RuleCheckRuleTemplate ordinary(){return rule(ReviewState.APPROVED,List.of(fact("limit",true,Map.of())),List.of(),List.of(),List.of());}
    private RuleCheckApplicationService app(RuleCheckRuleTemplate rule){
        catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(rule,"合成规则查询范围")));
        var verifier=new RuleCheckEvidenceVerifier((s,c)->{
            reads.incrementAndGet();
            return Optional.of(new RuleCheckEvidenceSnapshot("current",source,chunk,"v1",hash,text,eligible.get()));
        });
        var capture=new RuleCheckEvidenceCapture(verifier,()->"citation-"+UUID.randomUUID(),row->{
            audited.incrementAndGet();duringAudit.run();
        },Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        return new RuleCheckApplicationService(settings,catalogue::get,selector,
                new HybridRetrievalService(new HybridCandidateFuser()),lexical,vector,capture,new RuleCheckDecisionService(verifier));
    }
    @Test void answersOnlyWithRetrievedAuditedAndRevalidatedEvidence(){
        var result=app(ordinary()).check("合成条款",Map.of());
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts().getFirst().evidence().getFirst().text()).isEqualTo(text);
        assertThat(selected.get()).isEqualTo(1);assertThat(retrieved.get()).isEqualTo(1);assertThat(audited.get()).isEqualTo(1);
        assertThat(reads.get()).isEqualTo(2);
    }
    @Test void independentFactsRemainPartialWhenReviewedGapExists(){
        var rule=rule(ReviewState.APPROVED,List.of(fact("limit",true,Map.of())),List.of(),List.of("缺少公告"),List.of());
        var result=app(rule).check("合成条款",Map.of());
        assertThat(result.status()).isEqualTo(PARTIALLY_ANSWERED);assertThat(result.unknownItems()).contains("缺少公告");
    }
    @Test void asksForDecisiveConditionInsteadOfGuessingIt(){
        var rule=rule(ReviewState.APPROVED,List.of(fact("limit",false,Map.of("type","ordinary"))),
                List.of(new Condition("type","请选择类型",List.of("ordinary","other"))),List.of(),List.of());
        var service=app(rule);
        var missing=service.check("合成条款",Map.of());
        assertThat(missing.status()).isEqualTo(NEED_CLARIFICATION);assertThat(missing.confirmedFacts()).isEmpty();
        assertThat(service.check("合成条款",Map.of("type","ordinary")).status()).isEqualTo(ANSWERED);
    }
    @Test void verifiedConflictingSidesRemainConflict(){
        var rule=rule(ReviewState.APPROVED,List.of(),List.of(),List.of(),
                List.of(new Conflict("未解决冲突",List.of(fact("a",true,Map.of()),fact("b",true,Map.of())))));
        var result=app(rule).check("合成条款",Map.of());
        assertThat(result.status()).isEqualTo(CONFLICTING_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }
    @Test void draftCatalogueDoesNotSelectRetrieveOrAudit(){
        var service=app(rule(ReviewState.DRAFT,List.of(fact("limit",true,Map.of())),List.of(),List.of(),List.of()));
        assertThat(service.check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(selected.get()+retrieved.get()+audited.get()+reads.get()).isZero();
    }
    @Test void emptyCatalogueAndOtherRevisionNeverReachSelector(){
        var service=app(ordinary());catalogue.set(List.of());
        assertThat(service.check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        var r=ordinary();var other=new RuleCheckRuleTemplate(r.ruleId(),"v2",r.reviewState(),r.reviewer(),r.reviewedAt(),r.facts(),r.conditions(),r.evidenceGaps(),r.conflicts());
        catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(other,"other")));
        assertThat(service.check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(selected.get()+retrieved.get()).isZero();
    }
    @Test void selectorCannotInjectUnknownRuleOrExposeExceptionBody(){
        selector=(q,o)->"model invented";var service=app(ordinary());
        assertThat(service.check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(retrieved.get()).isZero();
        selector=(q,o)->{throw new IllegalStateException("secret model body");};service=app(ordinary());
        assertThat(service.check("合成条款",Map.of()).unknownItems()).doesNotContain("secret model body");
        assertThat(retrieved.get()).isZero();
    }
    @Test void duplicateApprovedRuleIdsAreNotResolvedByListOrder(){
        var service=app(ordinary());var entry=catalogue.get().getFirst();catalogue.set(List.of(entry,entry));
        assertThat(service.check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(selected.get()+retrieved.get()).isZero();
    }
    @Test void unknownConditionKeysAreRefusedBeforeRetrieval(){
        var service=app(ordinary());var result=service.check("合成条款",Map.of("studentVisible","true"));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(retrieved.get()).isZero();
    }
    @Test void retiredRuleDuringAuditCannotProduceFacts(){
        var service=app(ordinary());duringAudit=()->catalogue.set(List.of());
        var result=service.check("合成条款",Map.of());
        assertThat(audited.get()).isEqualTo(1);assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.confirmedFacts()).isEmpty();assertThat(reads.get()).isEqualTo(1);
    }
    @Test void changedApprovedTemplateDuringAuditIsAlsoRefused(){
        var service=app(ordinary());duringAudit=()->catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(
                rule(ReviewState.APPROVED,List.of(fact("changed",true,Map.of())),List.of(),List.of(),List.of()),"合成规则查询范围")));
        assertThat(service.check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(audited.get()).isEqualTo(1);
    }
    @Test void sourceWithdrawalAfterAuditIsRecheckedByDecision(){
        var service=app(ordinary());duringAudit=()->eligible.set(false);
        assertThat(service.check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(reads.get()).isEqualTo(2);assertThat(audited.get()).isEqualTo(1);
    }
    @Test void bothRetrievalPathsUnavailableCannotAnswer(){
        lexical=(q,l)->{throw new RetrieverUnavailableException("internal",new IllegalStateException());};vector=lexical;
        assertThat(app(ordinary()).check("合成条款",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(audited.get()).isZero();
    }
    @Test void healthyLexicalPathCanAnswerWhenVectorUnavailable(){
        vector=(q,l)->{throw new RetrieverUnavailableException("internal",new IllegalStateException());};
        assertThat(app(ordinary()).check("合成条款",Map.of()).status()).isEqualTo(ANSWERED);
        assertThat(audited.get()).isEqualTo(1);
    }
    @Test void currentReaderMismatchCannotReachAudit(){
        eligible.set(false);var result=app(ordinary()).check("合成条款",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(reads.get()).isEqualTo(1);assertThat(audited.get()).isZero();
    }
    @Test void invalidOrOversizedRequestIsRejectedBeforeDependencies(){
        var service=app(ordinary());
        for(String question:new String[]{"", " ", "a".repeat(2049)})
            assertThatThrownBy(()->service.check(question,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.check(null,Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.check("合成条款",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.check("合成条款",Map.of("key","a".repeat(129)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(selected.get()+retrieved.get()).isZero();
    }
    @Test void runtimeSettingsRejectMissingBoundariesAndUnboundedBudgets(){
        assertThatThrownBy(()->new RuleCheckRuntimeSettings("fixture"," ","SCOPE","v1",20,5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RuleCheckRuntimeSettings("fixture","cut","SCOPE","v1",4,5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RuleCheckRuntimeSettings("fixture","cut","SCOPE","v1",201,5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new RuleCheckRuntimeSettings("fixture","cut","SCOPE","v1",20,0)).isInstanceOf(IllegalArgumentException.class);
    }
    private RuleCheckRuleTemplate conditional() {
        return rule(ReviewState.APPROVED,List.of(fact("limit",false,Map.of("type","ordinary"))),
                List.of(new Condition("type","请选择类型",List.of("ordinary","other"))),List.of(),List.of());
    }
    private RuleCheckScopedResult.Continuation context(RuleCheckApplicationService service) {
        var result=service.checkWithContext("合成条款",Map.of());assertThat(result.continuation()).isNotNull();return result.continuation();
    }
    @Test void scopedClarificationKeepsOnlyServerApprovedRuleAndScope() {
        var result=app(conditional()).checkWithContext("合成条款",Map.of());
        assertThat(result.scope()).isNotNull();assertThat(result.scope().ruleId()).isEqualTo("test-only");
        assertThat(result.scope().questionScope()).isEqualTo("合成规则查询范围");
        assertThat(result.scope().factPreconditions().get("limit")).containsEntry("type","ordinary");
        assertThat(result.continuation()).isNotNull();assertThat(result.continuation().confirmedConditions()).isEmpty();
        assertThat(result.continuation().nextCondition().key()).isEqualTo("type");
    }
    @Test void continuationSkipsSelectorButRetrievesAuditsAndRevalidatesAgain() {
        var service=app(conditional());var saved=context(service);
        var result=service.resume(saved,Map.of("type","ordinary"));
        assertThat(result.decision().status()).isEqualTo(ANSWERED);assertThat(result.continuation()).isNull();
        assertThat(selected).hasValue(1);assertThat(retrieved).hasValue(2);assertThat(audited).hasValue(2);assertThat(reads).hasValue(4);
    }
    @Test void retiredOrChangedCatalogueCannotReuseContinuation() {
        var service=app(conditional());var saved=context(service);catalogue.set(List.of());
        assertThat(service.resume(saved,Map.of("type","ordinary")).decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(saved.entry().template(),"范围已变")));
        assertThat(service.resume(saved,Map.of("type","ordinary")).decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(retrieved).hasValue(1);assertThat(selected).hasValue(1);
    }
    @Test void continuationCannotCrossRuntimeSettings() {
        var service=app(conditional());var saved=context(service);
        var changed=new RuleCheckScopedResult.Continuation(new RuleCheckRuntimeSettings("fixture","other-cut","SCOPE","v1",20,5),saved.entry(),saved.question(),saved.confirmedConditions(),saved.nextCondition());
        assertThat(service.resume(changed,Map.of("type","ordinary")).decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(retrieved).hasValue(1);
    }
    @Test void continuationCannotSkipIndependentCurrentScopeCheck() {
        var service=app(conditional());var saved=context(service);
        var changedQuestion=new RuleCheckScopedResult.Continuation(saved.settings(),saved.entry(),"今天选课入口是否开放？",saved.confirmedConditions(),saved.nextCondition());
        var result=service.resume(changedQuestion,Map.of("type","ordinary"));
        assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.decision().confirmedFacts()).isEmpty();assertThat(retrieved).hasValue(1);assertThat(selected).hasValue(1);
    }
    @Test void firstPersonGeneralGradeRuleStillClarifiesCourseTypeAndAnswersKnownBranch() {
        selector=(q,options)->"test-only";
        var service=app(conditional());String question="我想了解选修课成绩不合格时的一般重修规则。";
        var result=service.checkWithContext(question,Map.of());
        assertThat(result.decision().status()).isEqualTo(NEED_CLARIFICATION);assertThat(result.continuation()).isNotNull();
        assertThat(service.resume(result.continuation(),Map.of("type","ordinary")).decision().status()).isEqualTo(ANSWERED);
        assertThat(service.check(question,Map.of("type","ordinary")).status()).isEqualTo(ANSWERED);
    }
    @Test void markedExamplePreservesClarificationAndOriginalQuestionOnResume() {
        selector=(q,options)->"test-only";
        var service=app(conditional());String question="“今天入口是否开放”只是例句；我想了解选修课成绩不合格时的一般重修规则。";
        var result=service.checkWithContext(question,Map.of());
        assertThat(result.decision().status()).isEqualTo(NEED_CLARIFICATION);assertThat(result.continuation().question()).isEqualTo(question);
        assertThat(service.isCurrent(result)).isTrue();
        var resumed=service.resume(result.continuation(),Map.of("type","ordinary"));
        assertThat(resumed.decision().status()).isEqualTo(ANSWERED);assertThat(resumed.releaseBinding().question()).isEqualTo(question);assertThat(service.isCurrent(resumed)).isTrue();
    }
    @Test void continuationOnlyAcceptsOnePendingAllowedCondition() {
        var service=app(conditional());var entry=catalogue.get().getFirst();
        var saved=new RuleCheckScopedResult.Continuation(settings,entry,"合成条款",Map.of(),entry.template().conditions().getFirst());
        for(var delta:List.of(Map.<String,String>of(),Map.of("other","ordinary"),Map.of("type","invalid"),Map.of("type","ordinary","other","v")))
            assertThatThrownBy(()->service.resume(saved,delta)).isInstanceOf(IllegalArgumentException.class);
        assertThat(selected).hasValue(0);assertThat(retrieved).hasValue(0);
    }
    @Test void withdrawalBetweenRoundsDoesNotReuseOldEvidence() {
        var service=app(conditional());var saved=context(service);eligible.set(false);
        var result=service.resume(saved,Map.of("type","ordinary"));assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.decision().confirmedFacts()).isEmpty();assertThat(result.continuation()).isNull();
        assertThat(retrieved).hasValue(2);assertThat(audited).hasValue(1);
    }
    @Test void branchContinuationDoesNotAskAlreadyExcludedOrConfirmedCondition() {
        var r=rule(ReviewState.APPROVED,List.of(fact("ordinary",false,Map.of("type","ordinary")),
            fact("retake",false,Map.of("type","retake","year","first"))),
            List.of(new Condition("type","类型",List.of("ordinary","retake")),new Condition("year","年级",List.of("first","other"))),List.of(),List.of());
        var service=app(r);var saved=context(service);
        assertThat(service.resume(saved,Map.of("type","ordinary")).decision().status()).isEqualTo(ANSWERED);
        var retake=service.resume(saved,Map.of("type","retake"));assertThat(retake.continuation()).isNotNull();
        assertThat(retake.continuation().nextCondition().key()).isEqualTo("year");
        assertThat(retake.continuation().confirmedConditions()).containsEntry("type","retake");
        assertThat(service.resume(retake.continuation(),Map.of("year","first")).decision().status()).isEqualTo(ANSWERED);
        assertThat(selected).hasValue(1);
    }
    @Test void unavailableEvidenceDoesNotCreateClarificationContext() {
        var service=app(conditional());eligible.set(false);
        var result=service.checkWithContext("合成条款",Map.of());
        assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.continuation()).isNull();
    }
    private RuleCheckRuleTemplate manyConditions(int count) {
        var conditions=new ArrayList<Condition>();var when=new LinkedHashMap<String,String>();
        for(int i=0;i<count;i++){String key="k"+i;conditions.add(new Condition(key,"合成条件"+i,List.of("yes","no")));when.put(key,"yes");}
        return rule(ReviewState.APPROVED,List.of(fact("limit",false,when)),conditions,List.of(),List.of());
    }
    @Test void cannotAskForSeventeenthConditionThatRequestContractCannotMerge() {
        var values=new LinkedHashMap<String,String>();for(int i=0;i<16;i++)values.put("k"+i,"yes");
        var result=app(manyConditions(17)).checkWithContext("合成条款",values);
        assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.continuation()).isNull();assertThat(result.decision().clarificationQuestions()).isEmpty();
    }
    @Test void mayClarifyExistingBlankKeyWithinSixteenConditionLimit() {
        var values=new LinkedHashMap<String,String>();for(int i=0;i<15;i++)values.put("k"+i,"yes");values.put("k15","");
        var service=app(manyConditions(16));var result=service.checkWithContext("合成条款",values);
        assertThat(result.decision().status()).isEqualTo(NEED_CLARIFICATION);assertThat(result.continuation()).isNotNull();
        assertThat(service.resume(result.continuation(),Map.of("k15","yes")).decision().status()).isEqualTo(ANSWERED);
    }
    private static String sha(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
}
