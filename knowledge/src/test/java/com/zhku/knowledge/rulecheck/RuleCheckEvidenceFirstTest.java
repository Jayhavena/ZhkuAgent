package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;

/** 合成条款验证新编排；替身计划不证明真实模型理解能力。 */
class RuleCheckEvidenceFirstTest {
    private final UUID source=UUID.randomUUID(), chunk=UUID.randomUUID();
    private final String text="合成条款：因特殊原因无法上课的，应提前履行请假手续。缺课累计超过该门课程学期总学时数的30%。";
    private final String hash=sha(text);
    private final AtomicBoolean eligible=new AtomicBoolean(true);
    private final List<String> calls=new ArrayList<>();
    private final AtomicReference<List<RuleCheckApplicationService.CatalogEntry>> entries=new AtomicReference<>();
    private final AtomicInteger plans=new AtomicInteger();
    private RuleCheckApplicationService.CatalogEntry entry(String id) {
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,"2025.9.2",hash,0,text.length(),text);
        var fact=new RuleCheckRuleTemplate.Fact("clause",text,true,Map.of(),List.of(binding));
        return new RuleCheckApplicationService.CatalogEntry(new RuleCheckRuleTemplate(id,"2025.9.2",
            RuleCheckRuleTemplate.ReviewState.APPROVED,"合成审核者",LocalDate.of(2026,10,11),List.of(fact),List.of(),List.of(),List.of()),
            "合成条款",new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)));
    }
    private RuleCheckApplicationService app(RuleCheckTaskPlan.Planner planner) {
        var settings=new RuleCheckRuntimeSettings("synthetic","cut","scope","2025.9.2",20,5);
        var verifier=new RuleCheckEvidenceVerifier((s,c)->Optional.of(new RuleCheckEvidenceSnapshot("fresh",source,chunk,"2025.9.2",hash,text,eligible.get())));
        var capture=new RuleCheckEvidenceCapture(verifier,()->UUID.randomUUID().toString(),row->calls.add("audit"),Clock.systemUTC());
        ChunkCandidateRetriever lexical=(q,n)->{calls.add("retrieve");return List.of(new RetrievedChunk(chunk,text,"合成","合成页",1,"2025.9.2",hash,source));};
        return RuleCheckApplicationService.evidenceFirst(settings,entries::get,(q,options)->{plans.incrementAndGet();calls.add("plan");return planner.plan(q,options);},
            new HybridRetrievalService(new HybridCandidateFuser()),lexical,(q,n)->List.of(),capture,new RuleCheckDecisionService(verifier));
    }
    private RuleCheckTaskPlan.Task task(RuleCheckTaskPlan.Action action,String operand,String value) {
        return new RuleCheckTaskPlan.Task("A15/GENERAL",action,operand,value);
    }
    @Test void paraphraseRetrievesAndAuditsBeforePlanningWithoutLegacyTopicMatch() {
        entries.set(List.of(entry("A15/GENERAL")));
        var service=app((q,options)->{assertThat(options).hasSize(1);return new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.EXPLAIN_RULE,"","")));});
        var result=service.checkWithContext("特殊原因不能上课，提前做什么？",Map.of());
        assertThat(result.decision().status()).isEqualTo(ANSWERED);
        assertThat(calls).containsExactly("retrieve","audit","plan");
        assertThat(service.isCurrent(result)).isTrue();assertThat(plans.get()).isEqualTo(1);
        eligible.set(false);assertThat(service.isCurrent(result)).isFalse();
    }
    @Test void uncapturedClauseNeverEntersPlannerOptions() {
        var absent=entry("other");var old=absent.template();var fact=old.facts().getFirst();var b=fact.evidenceBindings().getFirst();
        var missing=new RuleCheckRuleTemplate.EvidenceBinding(source,UUID.randomUUID(),b.revision(),b.checksum(),0,text.length(),text);
        var rule=new RuleCheckRuleTemplate("other",old.referenceRevision(),old.reviewState(),old.reviewer(),old.reviewedAt(),
            List.of(new RuleCheckRuleTemplate.Fact("other",text,true,Map.of(),List.of(missing))),List.of(),List.of(),List.of());
        entries.set(List.of(entry("A15/GENERAL"),new RuleCheckApplicationService.CatalogEntry(rule,"没有取到的条款")));
        var result=app((q,options)->{assertThat(options).extracting(RuleCheckTaskPlan.Option::ruleId).containsExactly("A15/GENERAL");
            return new RuleCheckTaskPlan(List.of(new RuleCheckTaskPlan.Task("other",RuleCheckTaskPlan.Action.EXPLAIN_RULE,"","")));}).check("问题",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }
    @Test void unknownCurrentTaskDoesNotSwallowIndependentHistoricalAnswer() {
        entries.set(List.of(entry("A15/GENERAL")));
        var service=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""),task(RuleCheckTaskPlan.Action.UNKNOWN_CURRENT,"",""))));
        var result=service.checkWithContext("解释历史条款；2026还有效吗？",Map.of());
        assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.decision().confirmedFacts()).hasSize(1);assertThat(result.decision().unknownItems()).anyMatch(s->s.contains("现行"));
        assertThat(service.isCurrent(result)).isTrue();assertThat(plans.get()).isEqualTo(1);
    }
    @Test void thresholdEqualityIsExplainedWithoutCertifyingPersonalEligibility() {
        entries.set(List.of(entry("A15/GENERAL")));
        var service=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.COMPARE_THRESHOLD,"ABSENCE_PERCENT","30"))));
        var result=service.check("正好30%是否满足这条条件？",Map.of());
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).anyMatch(f->f.text().contains("不包括正好30%"));
        assertThat(result.confirmedFacts()).noneMatch(f->f.text().contains("保证可以考试"));
    }
    @Test void unsupportedOperandCannotProduceAClaim() {
        entries.set(List.of(entry("A15/GENERAL")));
        var result=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.COMPARE_THRESHOLD,"PERSONAL_ELIGIBILITY","30")))).check("问题",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }
    @Test void modelCannotInventPercentageAbsentFromQuestion() {
        entries.set(List.of(entry("A15/GENERAL")));
        var result=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.COMPARE_THRESHOLD,"ABSENCE_PERCENT","31"))))
            .check("缺课正好30%是否满足条件？",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }
    @Test void catalogueChangeBeforeReleaseInvalidatesPlanWithoutModelRetry() {
        entries.set(List.of(entry("A15/GENERAL")));
        var service=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""))));
        var result=service.checkWithContext("问题",Map.of());entries.set(List.of());
        assertThat(service.isCurrent(result)).isFalse();assertThat(plans.get()).isEqualTo(1);
    }
    @Test void unsupportedSubtaskRemainsUnknownAlongsideSupportedTask() {
        entries.set(List.of(entry("A15/GENERAL")));
        var result=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""),
            new RuleCheckTaskPlan.Task("",RuleCheckTaskPlan.Action.UNSUPPORTED,"","")))).check("请解释条款并告诉我医保规则",Map.of());
        assertThat(result.status()).isEqualTo(PARTIALLY_ANSWERED);assertThat(result.unknownItems()).isNotEmpty();
    }
    @Test void continuationRejectsChangedNonPendingClauseWithoutReplanning() {
        var condition=new RuleCheckRuleTemplate.Condition("type","请选择类型",List.of("A","B"));
        var raw=entry("pending").template();var original=raw.facts().getFirst();
        var pending=new RuleCheckApplicationService.CatalogEntry(new RuleCheckRuleTemplate("pending",raw.referenceRevision(),raw.reviewState(),raw.reviewer(),raw.reviewedAt(),
            List.of(new RuleCheckRuleTemplate.Fact("conditional",text,false,Map.of("type","A"),original.evidenceBindings())),List.of(condition),List.of(),List.of()),"需澄清的合成条款");
        var independent=entry("A15/GENERAL");entries.set(List.of(pending,independent));
        var service=app((q,options)->new RuleCheckTaskPlan(List.of(new RuleCheckTaskPlan.Task("pending",RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""),task(RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""))));
        var first=service.checkWithContext("解释两个合成条款",Map.of());assertThat(first.decision().status()).isEqualTo(NEED_CLARIFICATION);
        var old=independent.template();var changed=new RuleCheckRuleTemplate(old.ruleId(),old.referenceRevision(),old.reviewState(),"变更审核者",old.reviewedAt(),old.facts(),old.conditions(),old.evidenceGaps(),old.conflicts());
        entries.set(List.of(pending,new RuleCheckApplicationService.CatalogEntry(changed,independent.questionScope(),independent.coverage())));
        var resumed=service.resume(first.continuation(),Map.of("type","A"));
        assertThat(resumed.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(plans.get()).isEqualTo(1);
    }
    @Test void unsupportedSignedOrExponentialPercentageCannotBecomePositiveInput() {
        entries.set(List.of(entry("A15/GENERAL")));
        for(var item:Map.of("缺课-30%是否满足？","30","缺课1e2%是否满足？","2").entrySet()) {
            var result=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.COMPARE_THRESHOLD,"ABSENCE_PERCENT",item.getValue())))).check(item.getKey(),Map.of());
            assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        }
    }
    @Test void askingForThresholdExplainsRuleInsteadOfComparingModelInventedInput() {
        entries.set(List.of(entry("A15/GENERAL")));
        var service=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.COMPARE_THRESHOLD,"ABSENCE_PERCENT","30"))));
        var result=service.checkWithContext("缺课比例达到什么条件，会影响考核？",Map.of());
        assertThat(result.decision().status()).isEqualTo(ANSWERED);
        assertThat(result.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly(text);
        assertThat(service.isCurrent(result)).isTrue();
    }
    @Test void oneRejectedTaskLeavesVerifiedIndependentFactsAndAVisibleGap() {
        entries.set(List.of(entry("A15/GENERAL")));
        var service=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""),
            new RuleCheckTaskPlan.Task("uncaptured",RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""))));
        var result=service.checkWithContext("解释条文和另一项要求",Map.of());
        assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly(text);
        assertThat(result.decision().unknownItems()).isNotEmpty();assertThat(service.isCurrent(result)).isTrue();
    }
    @Test void explicitPublicCourseAnswersBeforeReportingUnrelatedMissingEvidence() {
        entries.set(List.of(courseEntry()));
        var service=app((q,options)->new RuleCheckTaskPlan(List.of(new RuleCheckTaskPlan.Task("S10/RETAKE_ONLY",RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""),
            new RuleCheckTaskPlan.Task("",RuleCheckTaskPlan.Action.UNSUPPORTED,"",""))));
        var result=service.checkWithContext("公选课可以重修吗？宿舍收费是什么？",Map.of());
        assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);
        assertThat(result.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly("合成公选重修规定");
        assertThat(result.decision().clarificationQuestions()).isEmpty();assertThat(service.isCurrent(result)).isTrue();
    }
    @Test void explicitProfessionalCourseSelectsOnlyProfessionalBranch() {
        entries.set(List.of(courseEntry()));
        var result=courseApp().check("专业选修课可以重修吗？",Map.of());
        assertThat(result.status()).isEqualTo(ANSWERED);
        assertThat(result.confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::text).containsExactly("合成专业选修重修规定");
    }
    @Test void textCategoryContradictingStructuredInputCannotSilentlyChangeBranches() {
        entries.set(List.of(courseEntry()));
        var result=courseApp().check("公选课可以重修吗？",Map.of("courseType","PROFESSIONAL"));
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }
    @Test void absentAmbiguousOrNegatedCourseCategoryStillRequiresClarification() {
        entries.set(List.of(courseEntry()));
        for(String question:List.of("这门课可以重修吗？","公选还是专业选修，我不确定，可以重修吗？","不是公选课，可以重修吗？","不确定这是不是公选课，可以重修吗？")) {
            var result=courseApp().check(question,Map.of());
            assertThat(result.status()).as(question).isEqualTo(NEED_CLARIFICATION);
            assertThat(result.confirmedFacts()).isEmpty();
        }
    }
    @Test void explicitPercentageBoundaryComparisonsRetainLiteralExpectedRelations() {
        entries.set(List.of(entry("A15/GENERAL")));
        for(var item:Map.of("29","低于30%","30","正好30%","31","超过30%，满足").entrySet()) {
            var result=app((q,options)->new RuleCheckTaskPlan(List.of(task(RuleCheckTaskPlan.Action.COMPARE_THRESHOLD,"ABSENCE_PERCENT",item.getKey()))))
                .check("缺课"+item.getKey()+"%是否满足这项比例条件？",Map.of());
            assertThat(result.status()).isEqualTo(ANSWERED);
            assertThat(result.confirmedFacts()).anyMatch(f->f.text().contains(item.getValue()));
        }
    }
    private RuleCheckApplicationService courseApp() {
        return app((q,options)->new RuleCheckTaskPlan(List.of(new RuleCheckTaskPlan.Task("S10/RETAKE_ONLY",RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""))));
    }
    private RuleCheckApplicationService.CatalogEntry courseEntry() {
        var raw=entry("S10/RETAKE_ONLY");var t=raw.template();var bindings=t.facts().getFirst().evidenceBindings();
        var rule=new RuleCheckRuleTemplate(t.ruleId(),t.referenceRevision(),t.reviewState(),t.reviewer(),t.reviewedAt(),
            List.of(new RuleCheckRuleTemplate.Fact("public","合成公选重修规定",false,Map.of("courseType","PUBLIC"),bindings),
                new RuleCheckRuleTemplate.Fact("professional","合成专业选修重修规定",false,Map.of("courseType","PROFESSIONAL"),bindings)),
            List.of(new RuleCheckRuleTemplate.Condition("courseType","这门课属于公共选修还是专业选修？",List.of("PUBLIC","PROFESSIONAL"))),List.of(),List.of());
        return new RuleCheckApplicationService.CatalogEntry(rule,raw.questionScope(),raw.coverage());
    }
    private static String sha(String text) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
}
