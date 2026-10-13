package com.zhku.knowledge.rulecheck;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static com.zhku.knowledge.rulecheck.RuleCheckRuleTemplate.*;
import static org.assertj.core.api.Assertions.*;

class RuleCheckDecisionServiceTest {
    // 全部为合成工程夹具，不是2025政策或真实资料放行记录。
    private static final UUID SOURCE=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHUNK=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String TEXT="合成甲：上限4学分。";
    private static final String SECOND="合成乙：另有限制。";
    private static final RuleCheckEvidenceSnapshot EVIDENCE=snapshot(CHUNK,TEXT,"v1",true);
    private static final RuleCheckEvidenceSnapshot EXCEPTION=snapshot(OTHER,SECOND,"v1",true);
    private static final Condition COURSE=new Condition("courseType","请明确课程类别",List.of("PUBLIC","PROFESSIONAL"));

    @Test void completeAnswerUsesReviewedTextAndVerifiedOriginalWithRequestAlias() {
        var result=decide(template(List.of(fact("limit",false))),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly("合成上限为4学分。");
        assertThat(result.confirmedFacts().getFirst().evidence().getFirst().text()).isEqualTo(TEXT);
        assertThat(result.confirmedFacts().getFirst().evidence().getFirst().citationId()).isEqualTo("request-"+CHUNK);
        assertThat(result.referenceRevision()).isEqualTo("v1");
        assertThat(result.unknownItems()).isEmpty();
    }

    @Test void missingDecisiveConditionAsksWithoutChoosingAnUnmatchedBranch() {
        var result=decide(withConditions(List.of(branch("public","PUBLIC")),List.of(COURSE)),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.clarificationQuestions()).extracting(RuleCheckDecision.Clarification::key).containsExactly("courseType");
        assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void branchSpecificMissingMaterialDoesNotPreemptCategoryClarification() {
        var result=decide(professionalDeferredGapRule(),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.clarificationQuestions()).extracting(RuleCheckDecision.Clarification::key).containsExactly("courseType");
        assertThat(result.confirmedFacts()).isEmpty();
        assertThat(result.unknownItems()).isEmpty();
    }

    @Test void professionalDeferredMaterialGapCannotPollutePublicBranch() {
        var result=decide(professionalDeferredGapRule(),Map.of("courseType","PUBLIC"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("public");
        assertThat(result.unknownItems()).isEmpty();
    }

    @Test void professionalBranchKeepsIndependentRetakeFactAndItsOwnDeferredGap() {
        var result=decide(professionalDeferredGapRule(),Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("professional");
        assertThat(result.unknownItems()).containsExactly("合成专业分支缺少另一项依据");
    }

    @Test void invalidGapConditionCannotBeUsedToAnswerIndependentFact() {
        var base=professionalDeferredGapRule();
        var onlyGeneral=withConditionalGaps(List.of(fact("general",true)),base.conditions(),base.conditionalEvidenceGaps());
        var result=decide(onlyGeneral,Map.of("courseType","OTHER"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void excludedGapCannotAskItsIrrelevantYear() {
        var year=new Condition("year","请补充年级",List.of("Y1","Y2"));
        var rule=withConditionalGaps(List.of(branch("public","PUBLIC")),List.of(COURSE,year),
                List.of(new ConditionalEvidenceGap("合成专业Y1缺证",Map.of("courseType","PROFESSIONAL","year","Y1"))));
        var result=decide(rule,Map.of("courseType","PUBLIC"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.clarificationQuestions()).isEmpty();
    }

    @Test void missingSecondGapConditionIsClarifiedBeforeGapIsActivated() {
        var year=new Condition("year","请补充年级",List.of("Y1","Y2"));
        var rule=withConditionalGaps(List.of(new Fact("professional","合成专业规则",true,
                        Map.of("courseType","PROFESSIONAL"),List.of(binding(EVIDENCE)))),List.of(COURSE,year),
                List.of(new ConditionalEvidenceGap("合成专业Y1缺证",Map.of("courseType","PROFESSIONAL","year","Y1"))));
        var result=decide(rule,Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.clarificationQuestions()).extracting(RuleCheckDecision.Clarification::key).containsExactly("year");
        assertThat(result.unknownItems()).isEmpty();
    }

    @Test void retakeOnlyScopeDoesNotInheritDeferredExamMaterialGap() {
        var base=professionalDeferredGapRule();
        var rule=withConditionalGaps(base.facts(),base.conditions(),List.of());
        var result=decide(rule,Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("professional");
    }

    @Test void gapCannotUseUndeclaredConditionsOrInventedValues() {
        assertThatThrownBy(()->withConditionalGaps(List.of(fact("general",true)),List.of(COURSE),
                List.of(new ConditionalEvidenceGap("合成缺证",Map.of("year","Y1")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->withConditionalGaps(List.of(fact("general",true)),List.of(COURSE),
                List.of(new ConditionalEvidenceGap("合成缺证",Map.of("courseType","OTHER")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ConditionalEvidenceGap("合成缺证",Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void gapOnlyBranchStillClarifiesItsMissingCondition() {
        var year=new Condition("year","请补充年级",List.of("Y1","Y2"));
        var rule=withConditionalGaps(List.of(branch("public","PUBLIC")),List.of(COURSE,year),
                List.of(new ConditionalEvidenceGap("合成专业Y1没有缓考依据",Map.of("courseType","PROFESSIONAL","year","Y1"))));
        var result=decide(rule,Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.clarificationQuestions()).extracting(RuleCheckDecision.Clarification::key).containsExactly("year");
        assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void fullyKnownGapOnlyBranchKeepsSpecificMissingMaterialReason() {
        var rule=withConditionalGaps(List.of(branch("public","PUBLIC")),List.of(COURSE),
                List.of(new ConditionalEvidenceGap("合成专业没有缓考依据",Map.of("courseType","PROFESSIONAL"))));
        var result=decide(rule,Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.unknownItems()).containsExactly("合成专业没有缓考依据");
        assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void independentInformationMayAccompanyClarification() {
        var result=decide(withConditions(List.of(fact("general",true),branch("public","PUBLIC")),List.of(COURSE)),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("general");
    }

    @Test void providedConditionIsNotAskedAgainAndOtherBranchIsNotShown() {
        var result=decide(withConditions(List.of(branch("public","PUBLIC"),branch("professional","PROFESSIONAL")),List.of(COURSE)),
                Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("professional");
        assertThat(result.clarificationQuestions()).isEmpty();
    }

    @Test void unknownValueDoesNotCoerceUserIntoAnAllowedCategory() {
        var result=decide(withConditions(List.of(branch("public","PUBLIC")),List.of(COURSE)),Map.of("courseType","UNKNOWN_CATEGORY"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.confirmedFacts()).isEmpty();assertThat(result.clarificationQuestions()).isEmpty();
    }

    @Test void materialGapCannotBeDisguisedAsAnotherUserQuestion() {
        var rule=rule(ReviewState.APPROVED,List.of(fact("general",true)),List.of(COURSE),List.of("缺当期通知"),List.of());
        var result=decide(rule,Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.unknownItems()).contains("缺当期通知");assertThat(result.clarificationQuestions()).isEmpty();
    }

    @Test void dependentFactCannotBecomePartialAnswerWhenMaterialIsMissing() {
        var result=decide(rule(ReviewState.APPROVED,List.of(fact("dependent",false)),List.of(),List.of("缺关联例外"),List.of()),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void emptyRuleIsInsufficientRatherThanVacuouslyAnswered() {
        var result=decide(template(List.of()),Map.of(),List.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.unknownItems()).isNotEmpty();
    }

    @Test void draftOrRetiredTemplateNeverReadsEvidenceOrEmitsItsFacts() {
        for(var state:List.of(ReviewState.DRAFT,ReviewState.RETIRED)) {
            var calls=new AtomicInteger();
            var result=service(calls).decide(rule(state,List.of(fact("limit",true)),List.of(),List.of(),List.of()),Map.of(),List.of(EVIDENCE));
            assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();assertThat(calls).hasValue(0);
        }
    }

    @Test void bindingWithFortyCannotUseValidFourCitationToReleaseFact() {
        var bad=new EvidenceBinding(SOURCE,CHUNK,"v1",hash(TEXT),0,TEXT.length(),"合成甲：上限40学分。");
        var result=decide(template(List.of(new Fact("bad","不能泄漏的结论40",true,Map.of(),List.of(bad)))),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
        assertThat(result.toString()).doesNotContain("不能泄漏","40");
    }

    @Test void factNeedsEveryBoundClauseIncludingException() {
        var combined=new Fact("combined","必须同时满足甲乙",true,Map.of(),List.of(binding(EVIDENCE),binding(EXCEPTION)));
        var result=decide(template(List.of(combined)),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void partialAnswerKeepsOnlyIndependentFactsWhenAnotherBindingFails() {
        var failed=new Fact("exception","未核验例外结论",false,Map.of(),List.of(binding(EXCEPTION)));
        var result=decide(template(List.of(fact("general",true),failed)),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("general");
        assertThat(result.unknownItems()).isNotEmpty();
    }

    @Test void withdrawalCannotReuseCapturedEligibleEvidence() {
        var current=snapshot(CHUNK,TEXT,"v1",false);
        var service=new RuleCheckDecisionService(new RuleCheckEvidenceVerifier((s,c)->Optional.of(current)));
        var result=service.decide(template(List.of(fact("limit",true))),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void equalTextCannotBypassCurrentRevisionChange() {
        var current=snapshot(CHUNK,TEXT,"v2",true);
        var result=new RuleCheckDecisionService(new RuleCheckEvidenceVerifier((s,c)->Optional.of(current)))
                .decide(template(List.of(fact("limit",true))),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void templateRevisionMustMatchBindingEvenIfSourceItselfIsValid() {
        var oldBinding=new EvidenceBinding(SOURCE,CHUNK,"v0",hash(TEXT),0,TEXT.length(),TEXT);
        var result=decide(template(List.of(new Fact("old","旧模板结论",true,Map.of(),List.of(oldBinding)))),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void ambiguousPersistentBindingCannotChooseFirstMatchingAlias() {
        var second=new RuleCheckEvidenceSnapshot("another-alias",SOURCE,CHUNK,"v1",hash(TEXT),TEXT,true);
        var result=decide(template(List.of(fact("limit",true))),Map.of(),List.of(EVIDENCE,second));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void fullySupportedConflictHasPriorityOverMissingCondition() {
        var other=new Fact("opposite","合成另一规则结论",false,Map.of(),List.of(binding(EXCEPTION)));
        var conflict=new Conflict("合成规则冲突",List.of(fact("limit",false),other));
        var result=decide(rule(ReviewState.APPROVED,List.of(),List.of(COURSE),List.of(),List.of(conflict)),Map.of(),List.of(EVIDENCE,EXCEPTION));
        assertThat(result.status()).isEqualTo(CONFLICTING_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
        assertThat(result.conflicts().getFirst().sides()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("limit","opposite");
        assertThat(result.clarificationQuestions()).isEmpty();
    }

    @Test void missingConflictSideCannotSilentlyChooseRemainingConclusion() {
        var other=new Fact("opposite","合成另一规则结论",false,Map.of(),List.of(binding(EXCEPTION)));
        var conflict=new Conflict("合成规则冲突",List.of(fact("limit",false),other));
        var result=decide(rule(ReviewState.APPROVED,List.of(fact("general",true)),List.of(),List.of(),List.of(conflict)),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
        assertThat(result.conflicts()).isEmpty();assertThat(result.unknownItems()).isNotEmpty();
    }

    @Test void readerFailureDoesNotExposeInternalErrorOrFacts() {
        var result=new RuleCheckDecisionService(new RuleCheckEvidenceVerifier((s,c)->{throw new IllegalStateException("internal-sensitive-detail");}))
                .decide(template(List.of(fact("limit",true))),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
        assertThat(result.toString()).doesNotContain("internal-sensitive-detail");
    }

    @Test void nonMatchingBranchDoesNotReadOrReleaseItsEvidence() {
        var calls=new AtomicInteger();
        var result=service(calls).decide(withConditions(List.of(branch("public","PUBLIC")),List.of(COURSE)),Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();assertThat(calls).hasValue(0);
    }

    @Test void nullConditionInputFailsClosedWithoutClaim() {
        var result=decide(template(List.of(fact("limit",true))),null,List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void approvedTemplateRequiresActualReviewMetadata() {
        assertThatThrownBy(()->new RuleCheckRuleTemplate("rule","v1",ReviewState.APPROVED,null,null,List.of(fact("limit",false)),List.of(),List.of(),List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void factsWithoutEvidenceCannotBeApprovedIntoOutput() {
        assertThatThrownBy(()->new Fact("unbound","没有依据的结论",true,Map.of(),List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateConditionsCannotCreateContradictoryPromptDefinitions() {
        assertThatThrownBy(()->withConditions(List.of(fact("limit",false)),List.of(COURSE,COURSE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void factConditionMustBelongToDeclaredAllowedValues() {
        assertThatThrownBy(()->withConditions(List.of(branch("bad","UNDECLARED")),List.of(COURSE))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void duplicateFactIdsCannotHideAnotherConclusion() {
        assertThatThrownBy(()->template(List.of(fact("duplicate",false),fact("duplicate",true))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void templateDefensivelyCopiesFactsConditionsAndRequirements() {
        var requirements=new HashMap<String,String>();requirements.put("courseType","PUBLIC");
        var bindings=new ArrayList<>(List.of(binding(EVIDENCE)));
        var fact=new Fact("public","合成上限为4学分。",false,requirements,bindings);
        var facts=new ArrayList<>(List.of(fact));var conditions=new ArrayList<>(List.of(COURSE));
        var rule=withConditions(facts,conditions);
        requirements.put("courseType","PROFESSIONAL");bindings.clear();facts.clear();conditions.clear();
        var result=decide(rule,Map.of("courseType","PUBLIC"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);assertThat(result.confirmedFacts()).hasSize(1);
        assertThatThrownBy(()->rule.facts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->result.confirmedFacts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void publicBranchDoesNotAskYearUsedOnlyByExcludedProfessionalBranch() {
        var result=decide(courseAndYearRule(),Map.of("courseType","PUBLIC"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("public");
        assertThat(result.clarificationQuestions()).isEmpty();
    }

    @Test void irrelevantUnknownYearCannotRejectOtherwiseCoveredPublicBranch() {
        var result=decide(courseAndYearRule(),Map.of("courseType","PUBLIC","year","Y3"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).containsExactly("public");
    }

    @Test void missingCategoryIsAskedBeforePotentiallyIrrelevantBranchYear() {
        var result=decide(courseAndYearRule(),Map.of(),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.clarificationQuestions()).extracting(RuleCheckDecision.Clarification::key).containsExactly("courseType");
        assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void professionalBranchStillAsksItsMissingYear() {
        var result=decide(courseAndYearRule(),Map.of("courseType","PROFESSIONAL"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(NEED_CLARIFICATION);
        assertThat(result.clarificationQuestions()).extracting(RuleCheckDecision.Clarification::key).containsExactly("year");
        assertThat(result.confirmedFacts()).isEmpty();
    }

    @Test void relevantUnknownYearCannotBeDiscardedToAnswerAnIndependentGeneralFact() {
        var base=courseAndYearRule();
        var facts=new ArrayList<>(base.facts());facts.add(fact("general",true));
        var rule=withConditions(facts,base.conditions());
        var result=decide(rule,Map.of("courseType","PROFESSIONAL","year","Y3"),List.of(EVIDENCE));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.confirmedFacts()).isEmpty();assertThat(result.clarificationQuestions()).isEmpty();
    }

    private static RuleCheckRuleTemplate courseAndYearRule() {
        var year=new Condition("year","请补充年级",List.of("Y1","Y2"));
        return withConditions(List.of(branch("public","PUBLIC"),
                new Fact("professional-y1","合成Y1规则",false,Map.of("courseType","PROFESSIONAL","year","Y1"),List.of(binding(EVIDENCE))),
                new Fact("professional-y2","合成Y2规则",false,Map.of("courseType","PROFESSIONAL","year","Y2"),List.of(binding(EVIDENCE)))),List.of(COURSE,year));
    }

    private static RuleCheckRuleTemplate professionalDeferredGapRule() {
        var facts=List.of(new Fact("public","合成公共规则",true,Map.of("courseType","PUBLIC"),List.of(binding(EVIDENCE))),
                new Fact("professional","合成专业规则",true,Map.of("courseType","PROFESSIONAL"),List.of(binding(EVIDENCE))));
        return new RuleCheckRuleTemplate("synthetic-rule","v1",ReviewState.APPROVED,"合成审核夹具",LocalDate.of(2026,10,9),
                facts,List.of(COURSE),List.of(),List.of(),
                List.of(new ConditionalEvidenceGap("合成专业分支缺少另一项依据",Map.of("courseType","PROFESSIONAL"))));
    }

    private static RuleCheckRuleTemplate withConditionalGaps(List<Fact> facts,List<Condition> conditions,List<ConditionalEvidenceGap> gaps) {
        return new RuleCheckRuleTemplate("synthetic-rule","v1",ReviewState.APPROVED,"合成审核夹具",LocalDate.of(2026,10,9),
                facts,conditions,List.of(),List.of(),gaps);
    }

    private static RuleCheckDecision decide(RuleCheckRuleTemplate rule,Map<String,String> conditions,List<RuleCheckEvidenceSnapshot> evidence) {
        return service(new AtomicInteger()).decide(rule,conditions,evidence);
    }
    private static RuleCheckDecisionService service(AtomicInteger calls) {
        return new RuleCheckDecisionService(new RuleCheckEvidenceVerifier((s,c)->{
            calls.incrementAndGet();return c.equals(CHUNK)?Optional.of(EVIDENCE):c.equals(OTHER)?Optional.of(EXCEPTION):Optional.empty();
        }));
    }
    private static Fact fact(String id,boolean independent) {return new Fact(id,"合成上限为4学分。",independent,Map.of(),List.of(binding(EVIDENCE)));}
    private static Fact branch(String id,String category) {return new Fact(id,"合成上限为4学分。",false,Map.of("courseType",category),List.of(binding(EVIDENCE)));}
    private static RuleCheckRuleTemplate template(List<Fact> facts) {return rule(ReviewState.APPROVED,facts,List.of(),List.of(),List.of());}
    private static RuleCheckRuleTemplate withConditions(List<Fact> facts,List<Condition> conditions) {return rule(ReviewState.APPROVED,facts,conditions,List.of(),List.of());}
    private static RuleCheckRuleTemplate rule(ReviewState state,List<Fact> facts,List<Condition> conditions,List<String> gaps,List<Conflict> conflicts) {
        return new RuleCheckRuleTemplate("synthetic-rule","v1",state,state==ReviewState.APPROVED?"合成审核夹具":null,
                state==ReviewState.APPROVED?LocalDate.of(2026,10,8):null,facts,conditions,gaps,conflicts);
    }
    private static EvidenceBinding binding(RuleCheckEvidenceSnapshot e) {return new EvidenceBinding(e.sourceId(),e.chunkId(),e.revision(),e.checksum(),0,e.text().length(),e.text());}
    private static RuleCheckEvidenceSnapshot snapshot(UUID chunk,String text,String revision,boolean eligible) {
        return new RuleCheckEvidenceSnapshot("request-"+chunk,SOURCE,chunk,revision,hash(text),text,eligible);
    }
    private static String hash(String text) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e) {throw new AssertionError(e);}
    }
}
