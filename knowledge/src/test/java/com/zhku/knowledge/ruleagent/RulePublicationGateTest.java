package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
import static org.assertj.core.api.Assertions.*;

/** 需求语义是测试核验器的已知输出；只证明宿主对应/覆盖检查，不证明模型理解。 */
class RulePublicationGateTest {
    final RuleAgentEvidenceTools.VerifiedFact professional=fact("T02/ARTICLE4","professional-elective-study-arrangement",Map.of());
    final RuleAgentEvidenceTools.VerifiedFact publicCourse=fact("T02/ARTICLE4","public-elective-self-selection",Map.of());
    final List<RuleAgentEvidenceTools.VerifiedFact> arrangement=List.of(professional,publicCourse);

    RuleAgentEvidenceTools.VerifiedFact fact(String rule,String name,Map<String,String> when) {
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(UUID.randomUUID(),UUID.randomUUID(),"2025.9.2","0".repeat(64),0,4,"合成原文");
        var definition=new RuleCheckRuleTemplate.Fact(name,"合成已审解释",true,when,List.of(binding));
        return new RuleAgentEvidenceTools.VerifiedFact(rule+":"+name,rule,definition);
    }

    RuleFactCapabilityCatalogue catalogue(List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        var entries=facts.stream().map(f->{
            var conditions=f.definition().when().isEmpty()?List.<RuleCheckRuleTemplate.Condition>of():
                List.of(new RuleCheckRuleTemplate.Condition("courseType","合成条件",List.of("PUBLIC","PROFESSIONAL")));
            var rule=new RuleCheckRuleTemplate(f.ruleId(),"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,
                "SYNTHETIC_TEST",LocalDate.of(2026,10,13),List.of(f.definition()),conditions,List.of(),List.of());
            return new RuleCheckApplicationService.CatalogEntry(rule,"合成审核范围",
                new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),
                RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));
        }).toList();
        return RuleFactCapabilityCatalogue.fromReviewed(entries);
    }

    RuleAgentRunner.Input input(String question) {
        return RuleAgentRunner.Input.single(new Owner(UUID.randomUUID(),UUID.randomUUID()),UUID.randomUUID(),question);
    }

    Requirement requirement(String id,String question,Dimension dimension,Subject subject,Recognition recognition) {
        return new Requirement(id,new DemandSpan("q1",0,question.length()),dimension,subject,recognition,List.of());
    }

    SemanticAssessment comparison(SemanticInput input) {
        String question=input.questions().getFirst().text();
        return new SemanticAssessment(REVISION,input.inputFingerprint(),List.of(
            requirement("r1",question,Dimension.STUDY_ARRANGEMENT,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED),
            requirement("r2",question,Dimension.STUDY_ARRANGEMENT,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED)),
            List.of(new SemanticBinding("r1",professional.id(),BindingRole.CORE),
                new SemanticBinding("r2",publicCourse.id(),BindingRole.CORE)),List.of());
    }

    Finish proposal(String... ids) {
        return new Finish(List.of(new Proposal("q1",List.of(ids),List.of(),null)));
    }

    @Test void missingUserConditionCannotBeRecastAsMissingReadCapability(){
        var conditional=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var facts=List.of(conditional);var caps=catalogue(facts);
        var original=input("这门选修课没过，能重修吗？");
        var safe=RulePublicationContract.input(original,facts,caps);
        var requirement=new Requirement("r1",new DemandSpan("q1",0,original.question().length()),Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType"));
        var semantic=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(requirement),List.of(),List.of());
        var finish=new Finish(List.of(new Proposal("q1",List.of(),List.of(new ProposedIssue(IssueKind.DATA_GAP,ReasonCode.REVIEWED_FACT_COVERAGE_GAP)),null)));
        var result=new RulePublicationGate(caps).assess(original,finish,semantic,facts);
        assertThat(original.userConditions()).isEmpty();
        assertThat(result.questions().get("q1").uncoveredRequirementIds()).containsExactly("r1");
        assertThat(result.questions().get("q1").factCapabilityGapRequirementIds()).isEmpty();
        assertThat(result.questions().get("q1").coveredRequirementIds()).isEmpty();
    }

    @Test void bothSidesCompleteForDifferentQuestionExpressions() {
        var caps=catalogue(arrangement);
        for(String question:List.of("专业选修课和公共选修课在修读安排上有什么不同？","专选和公选在选课修读上哪里不一样？")) {
            var original=input(question);
            var reviewInput=RulePublicationContract.input(original,arrangement,caps);
            var result=new RulePublicationGate(caps).assess(original,proposal(professional.id(),publicCourse.id()),comparison(reviewInput),arrangement);
            assertThat(result.verdict()).isEqualTo(Verdict.COMPLETE);
            assertThat(result.questions().get("q1").coveredRequirementIds()).containsExactlyInAnyOrder("r1","r2");
        }
    }

    @Test void omittedSideRemainsUncoveredEvenWhenReviewerSawBothFacts() {
        var caps=catalogue(arrangement);
        var original=input("比较两类课程修读安排");
        var reviewInput=RulePublicationContract.input(original,arrangement,caps);
        var result=new RulePublicationGate(caps).assess(original,proposal(professional.id()),comparison(reviewInput),arrangement);
        assertThat(result.verdict()).isEqualTo(Verdict.PARTIAL);
        assertThat(result.questions().get("q1").uncoveredRequirementIds()).containsExactly("r2");
        assertThat(result.acceptedBindings()).hasSize(1);
    }

    @Test void twoRulesCoverDifferentRequirementsAndExceptionDoesNotCountAsCore() {
        var hours=fact("S03/GENERAL","hours-principle",Map.of());
        var limit=fact("S03/GENERAL","hours-upper-limit",Map.of());
        var credits=fact("S07/GENERAL","public-semester-four-credits",Map.of());
        var facts=List.of(hours,limit,credits);
        var caps=catalogue(facts);
        var original=input("公共选修课通常多少学时，每学期多少学分？");
        var reviewInput=RulePublicationContract.input(original,facts,caps);
        var assessment=new SemanticAssessment(REVISION,reviewInput.inputFingerprint(),List.of(
            requirement("r1",original.question(),Dimension.HOURS_NORMAL,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED),
            requirement("r2",original.question(),Dimension.SEMESTER_CREDIT_LIMIT,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED)),List.of(
            new SemanticBinding("r1",hours.id(),BindingRole.CORE),
            new SemanticBinding("r1",limit.id(),BindingRole.CONDITION_EXCEPTION),
            new SemanticBinding("r2",credits.id(),BindingRole.CORE)),List.of());
        var gate=new RulePublicationGate(caps);
        assertThat(gate.assess(original,proposal(hours.id(),limit.id(),credits.id()),assessment,facts).verdict()).isEqualTo(Verdict.COMPLETE);
        var withoutCore=gate.assess(original,proposal(limit.id(),credits.id()),assessment,facts);
        assertThat(withoutCore.verdict()).isEqualTo(Verdict.PARTIAL);
        assertThat(withoutCore.questions().get("q1").uncoveredRequirementIds()).containsExactly("r1");
    }

    @Test void incorrectSemanticBindingCannotRelabelRetakeAsStudyArrangement() {
        var retake=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var facts=List.of(retake);
        var caps=catalogue(facts);
        var original=input("解释公共选修的修读安排");
        var reviewInput=RulePublicationContract.input(original,facts,caps);
        var bad=new SemanticAssessment(REVISION,reviewInput.inputFingerprint(),List.of(
            requirement("r1",original.question(),Dimension.STUDY_ARRANGEMENT,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED)),
            List.of(new SemanticBinding("r1",retake.id(),BindingRole.CORE)),List.of());
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,proposal(retake.id()),bad,facts))
            .isInstanceOf(Rejected.class).hasMessage(Code.REVIEW_BINDING_INVALID.name());
    }

    @Test void unreadFactsAndChangedReviewFingerprintAreRejected() {
        var caps=catalogue(arrangement);
        var original=input("比较两类课程修读安排");
        var reviewInput=RulePublicationContract.input(original,arrangement,caps);
        var review=comparison(reviewInput);
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,proposal("unread:fact"),review,arrangement))
            .hasMessage(Code.FACT_NOT_READ.name());
        var changed=new SemanticAssessment(REVISION,"f".repeat(64),review.requirements(),review.bindings(),List.of());
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,proposal(professional.id()),changed,arrangement))
            .hasMessage(Code.REVIEW_SCHEMA_INVALID.name());
    }

    @Test void unresolvedDemandKeepsKnownCoveragePartial() {
        var caps=catalogue(arrangement);
        var original=input("专业选修怎么修，另外那个安排是指什么？");
        var reviewInput=RulePublicationContract.input(original,arrangement,caps);
        var review=new SemanticAssessment(REVISION,reviewInput.inputFingerprint(),List.of(
            requirement("r1",original.question(),Dimension.STUDY_ARRANGEMENT,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED),
            requirement("r2",original.question(),Dimension.UNKNOWN,Subject.UNSPECIFIED,Recognition.UNCLASSIFIED)),
            List.of(new SemanticBinding("r1",professional.id(),BindingRole.CORE)),List.of());
        var result=new RulePublicationGate(caps).assess(original,proposal(professional.id()),review,arrangement);
        assertThat(result.verdict()).isEqualTo(Verdict.PARTIAL);
        assertThat(result.questions().get("q1").demandUnresolved()).isTrue();
    }

    /**
     * 未识别需求不能靠同一子问题里另一个资料缺口蒙混过关；否则“尚未理解”会被发布成正常资料不足。
     * 缺口只覆盖已识别需求时，未被识别的需求必须保留未解决语义。
     */
    @Test void unclassifiedRequirementIsNotMaskedByAnotherDemandGap() {
        var caps=catalogue(arrangement);
        var original=input("专业选修怎么修，另外那个安排是指什么？");
        var reviewInput=RulePublicationContract.input(original,arrangement,caps);
        var review=new SemanticAssessment(REVISION,reviewInput.inputFingerprint(),List.of(
            new Requirement("r1",new DemandSpan("q1",0,4),Dimension.STUDY_ARRANGEMENT,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED,List.of()),
            new Requirement("r2",new DemandSpan("q1",5,9),Dimension.UNKNOWN,Subject.UNSPECIFIED,Recognition.UNCLASSIFIED,List.of())),
            List.of(new SemanticBinding("r1",professional.id(),BindingRole.CORE)),
            List.of(new UnresolvedDemand(new DemandSpan("q1",0,4),UnresolvedReason.UNSUPPORTED_DIMENSION)));
        var result=new RulePublicationGate(caps).assess(original,proposal(professional.id()),review,arrangement);
        assertThat(result.questions().get("q1").demandUnresolved()).isTrue();
        assertThat(result.questions().get("q1").unsupportedDemand()).isFalse();
    }

    /** 契约允许的路径：当期通知/代办类需求本就无法由历史原文识别，须由自己的资料缺口声明覆盖才能结束为资料不足。 */
    @Test void gapDemandCoveringItsOwnUnclassifiedRequirementStaysDataGap() {
        var caps=catalogue(arrangement);
        var original=input("专业选修怎么修，公选补选今晚几点关？");
        var reviewInput=RulePublicationContract.input(original,arrangement,caps);
        int split=original.question().indexOf("公选");
        var review=new SemanticAssessment(REVISION,reviewInput.inputFingerprint(),List.of(
            new Requirement("r1",new DemandSpan("q1",0,split),Dimension.STUDY_ARRANGEMENT,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED,List.of()),
            new Requirement("r2",new DemandSpan("q1",split,original.question().length()),Dimension.UNKNOWN,Subject.UNSPECIFIED,Recognition.UNCLASSIFIED,List.of())),
            List.of(new SemanticBinding("r1",professional.id(),BindingRole.CORE)),
            List.of(new UnresolvedDemand(new DemandSpan("q1",split,original.question().length()),UnresolvedReason.UNSUPPORTED_DIMENSION)));
        var result=new RulePublicationGate(caps).assess(original,proposal(professional.id()),review,arrangement);
        assertThat(result.questions().get("q1").unsupportedDemand()).isTrue();
    }

    @Test void partialOverlapDoesNotCoverUnclassifiedRequirement() {
        var unknown = new Requirement("r1",new DemandSpan("q1",2,10),
            Dimension.UNKNOWN,Subject.UNSPECIFIED,Recognition.UNCLASSIFIED,List.of());
        var partial = new SemanticAssessment(REVISION,"0".repeat(64),List.of(unknown),List.of(),
            List.of(new UnresolvedDemand(new DemandSpan("q1",0,5),UnresolvedReason.UNSUPPORTED_DIMENSION)));
        var complete = new SemanticAssessment(REVISION,"0".repeat(64),List.of(unknown),List.of(),
            List.of(new UnresolvedDemand(new DemandSpan("q1",0,10),UnresolvedReason.UNSUPPORTED_DIMENSION)));
        assertThat(RulePublicationGate.unsupportedDemand(partial,"q1")).isFalse();
        assertThat(RulePublicationGate.unsupportedDemand(complete,"q1")).isTrue();
    }

    @Test void mixedUnresolvedReasonsCannotBeClassifiedAsOnlyDataGap() {
        var unknown = new Requirement("r1",new DemandSpan("q1",0,10),
            Dimension.UNKNOWN,Subject.UNSPECIFIED,Recognition.UNCLASSIFIED,List.of());
        var mixed = new SemanticAssessment(REVISION,"0".repeat(64),List.of(unknown),List.of(),List.of(
            new UnresolvedDemand(new DemandSpan("q1",0,10),UnresolvedReason.UNSUPPORTED_DIMENSION),
            new UnresolvedDemand(new DemandSpan("q1",3,9),UnresolvedReason.AMBIGUOUS_SCOPE)));
        assertThat(RulePublicationGate.unsupportedDemand(mixed,"q1")).isFalse();
    }

    @Test void ambiguousRequirementIsNotAnExplicitlyUnsupportedUnknown() {
        var ambiguous = new Requirement("r1",new DemandSpan("q1",0,10),
            Dimension.UNKNOWN,Subject.UNSPECIFIED,Recognition.AMBIGUOUS,List.of());
        var review = new SemanticAssessment(REVISION,"0".repeat(64),List.of(ambiguous),List.of(),
            List.of(new UnresolvedDemand(new DemandSpan("q1",0,10),UnresolvedReason.UNSUPPORTED_DIMENSION)));
        assertThat(RulePublicationGate.unsupportedDemand(review,"q1")).isFalse();
    }

    @Test void reviewInputExcludesOwnerProposalAndOriginalTokens()throws Exception {
        var caps=catalogue(arrangement);
        var original=input("比较修读安排");
        var safe=RulePublicationContract.input(original,arrangement,caps);
        String json=new ObjectMapper().writeValueAsString(safe);
        assertThat(json).doesNotContain("actorId","sessionNonce","taskId","originalToken","proposal","acceptedBindings");
        assertThat(safe.facts()).hasSize(2);
        assertThat(MAX_DECISION_CALLS+RESERVED_REVIEW_CALLS).isEqualTo(DEFAULT_BUDGET.maxModelAttemptsPerRun());
    }

    @Test void duplicateBindingsAndOutOfRangeSpansAreRejected() {
        var caps=catalogue(arrangement);
        var original=input("比较修读安排");
        var safe=RulePublicationContract.input(original,arrangement,caps);
        var review=comparison(safe);
        assertThatThrownBy(()->new SemanticAssessment(REVISION,safe.inputFingerprint(),review.requirements(),
            List.of(review.bindings().getFirst(),review.bindings().getFirst()),List.of())).hasMessage(Code.REVIEW_SCHEMA_INVALID.name());
        var outside=new Requirement("r1",new DemandSpan("q1",0,original.question().length()+1),Dimension.STUDY_ARRANGEMENT,
            Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED,List.of());
        var bad=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(outside),List.of(),List.of());
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,proposal(professional.id()),bad,arrangement))
            .hasMessage(Code.REVIEW_SCHEMA_INVALID.name());
    }

    @Test void selectedUnrelatedFactIsRejectedEvenWhenOtherFactsCoverAllNeeds() {
        var retake=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var facts=List.of(professional,publicCourse,retake);
        var caps=catalogue(facts);
        var original=input("比较两类课程的修读安排");
        var safe=RulePublicationContract.input(original,facts,caps);
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,
            proposal(professional.id(),publicCourse.id(),retake.id()),comparison(safe),facts))
            .isInstanceOfSatisfying(Rejected.class, error -> {
                assertThat(error.code).isEqualTo(Code.FACT_NOT_RELEVANT);
                var detail = error.relevance;
                assertThat(detail.rejectedFact().factId()).isEqualTo(retake.id());
                assertThat(detail.proposedFactIds()).containsExactly(professional.id(),publicCourse.id(),retake.id());
                assertThat(detail.requirements()).extracting(RequirementDiagnostic::ordinal).containsExactly(1,2);
                assertThat(detail.requirements().stream().flatMap(row->row.bindings().stream()))
                        .extracting(BindingDiagnostic::factId).containsExactly(professional.id(),publicCourse.id());
            });
    }

    @Test void missingUserConditionCannotBeMadeTrueBySemanticBinding() {
        var retake=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var facts=List.of(retake);
        var caps=catalogue(facts);
        var original=input("这门选修课没过可以重修吗？");
        var safe=RulePublicationContract.input(original,facts,caps);
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(
            requirement("r1",original.question(),Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED)),
            List.of(new SemanticBinding("r1",retake.id(),BindingRole.CORE)),List.of());
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,proposal(retake.id()),review,facts))
            .hasMessage(Code.WHEN_NOT_SATISFIED.name());
    }

    @Test void reviewedFinishDropsOnlyTheUnaskedDeferredExamFactFromThePublicRetakeProposal() {
        var retake=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var deferred=fact("S10/DEFERRED_EXAM_ONLY","public-no-deferred-exam",Map.of("courseType","PUBLIC"));
        var facts=List.of(deferred,retake);
        var gate=new RulePublicationGate(catalogue(facts));
        var initial=input("如果挂科的是公共选修课，手册怎么说？");
        var original=new RuleAgentRunner.Input(initial.owner(),initial.taskId(),initial.version(),initial.question(),initial.questions(),Map.of("courseType","PUBLIC"));
        var safe=RulePublicationContract.input(original,facts,catalogue(facts));
        var demand=requirement("r1",original.question(),Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED);
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(demand),
            List.of(new SemanticBinding("r1",retake.id(),BindingRole.CORE)),List.of());
        var selected=gate.reviewFinish(original,proposal(deferred.id(),retake.id()),review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(retake.id());
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.exclusion().rejectedFact().factId()).isEqualTo(deferred.id());

        var missingBinding=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(demand),List.of(),List.of());
        assertThatThrownBy(()->gate.reviewFinish(original,proposal(deferred.id(),retake.id()),missingBinding,facts))
            .hasMessage(Code.FACT_NOT_RELEVANT.name());
        assertThatThrownBy(()->gate.reviewFinish(original,proposal(deferred.id(),retake.id(),"not-read"),review,facts))
            .hasMessage(Code.FACT_NOT_READ.name());
    }

    @Test void changedDefinitionCannotReuseCapabilityAndReviewCannotCrossTask() {
        var caps=catalogue(arrangement);
        var altered=new RuleCheckRuleTemplate.Fact(professional.definition().id(),"不同的合成解释",true,
            Map.of(),professional.definition().evidenceBindings());
        var changed=new RuleAgentEvidenceTools.VerifiedFact(professional.id(),professional.ruleId(),altered);
        var facts=List.of(changed,publicCourse);
        var original=input("比较修读安排");
        var safe=RulePublicationContract.input(original,facts,caps);
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,proposal(changed.id()),comparison(safe),facts))
            .hasMessage(Code.CAPABILITY_UNAVAILABLE.name());
        var first=input("比较修读安排");
        var review=comparison(RulePublicationContract.input(first,arrangement,caps));
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(input("比较修读安排"),
            proposal(professional.id(),publicCourse.id()),review,arrangement))
            .hasMessage(Code.REVIEW_SCHEMA_INVALID.name());
    }

    @Test void thirdNonterminalDecisionStopsWithoutSpendingReservedReviewCall()throws Exception {
        var fixture=new RuleAgentRunnerTest();
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var reviews=new java.util.concurrent.atomic.AtomicInteger();
        RuleAgentRunner.DecisionPort decision=(view,timeout)->{
            try{return new RuleAgentRunner.Reply(new ObjectMapper().writeValueAsString(new Search("q1","类别"+calls.incrementAndGet())),false);}
            catch(Exception invalid){throw new IllegalStateException("模拟动作编码失败");}
        };
        RuleSemanticReviewPort reviewer=(safe,timeout)->{reviews.incrementAndGet();throw new AssertionError("预算停止后不应核验");};
        var original=RuleAgentRunner.Input.single(fixture.owner,fixture.task,"选修课程类别有哪些？");
        var context=com.zhku.agent.agent.runtime.AgentRunContext.create(original.question());
        var outcome=new RuleAgentRunner(decision,new RuleActionBroker(fixture.tools(true,false,false)),java.time.Clock.systemUTC(),reviewer).run(context,original);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(reviews.get()).isZero();
        assertThat(outcome.snapshot().taskStatus()).isEqualTo(TaskStatus.STOPPED);
        assertThat(outcome.snapshot().execution().modelAttemptCount()).isEqualTo(3);
        assertThat(outcome.snapshot().result().items().getFirst().issues().getFirst().reasonCode()).isEqualTo(ReasonCode.BUDGET_EXCEEDED);
    }

    @Test void cancelDuringIndependentReviewDoesNotPublishAndClosesReviewPort()throws Exception {
        var fixture=new RulePublicationEligibilityTest();
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        var original=fixture.input;
        var context=com.zhku.agent.agent.runtime.AgentRunContext.create(original.question());
        RuleSemanticReviewPort reviewer=new RuleSemanticReviewPort(){
            public SemanticAssessment assess(SemanticInput safe,java.time.Duration timeout){
                context.requestStop(com.zhku.agent.agent.runtime.AgentStopReason.USER_REQUEST);
                return null;
            }
            public void close(){closed.set(true);}
        };
        var outcome=new RuleAgentRunner(fixture.decision(new Ask("q1","courseType","controlled",List.of(fixture.pub,fixture.pro))),new RuleActionBroker(fixture.tools()),java.time.Clock.systemUTC(),reviewer).run(context,original);
        assertThat(closed.get()).isTrue();
        assertThat(outcome.snapshot().taskStatus()).isEqualTo(TaskStatus.STOPPED);
        assertThat(outcome.snapshot().execution().modelAttemptCount()).isEqualTo(4);
        assertThat(outcome.snapshot().result().items().getFirst().claims()).isEmpty();
        assertThat(outcome.snapshot().result().evidence()).isEmpty();
        assertThat(context.snapshot().terminated()).isTrue();
    }

    @Test void askingMissingCourseTypeRequiresIndependentNeedAndConditionalFact() {
        var retake=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var other=fact("S10/RETAKE_ONLY","professional-failed-retake-application",Map.of("courseType","PROFESSIONAL"));
        var facts=List.of(retake,other);
        var caps=catalogue(facts);
        var original=input("这门选修没过，能再修吗？");
        var safe=RulePublicationContract.input(original,facts,caps);
        var needed=new Requirement("r1",new DemandSpan("q1",0,original.question().length()),Dimension.RETAKE,
            Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType"));
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(needed,new Requirement("r2",needed.span(),Dimension.RETAKE,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED,List.of("courseType"))),
            List.of(new SemanticBinding("r1",retake.id(),BindingRole.CORE),new SemanticBinding("r2",other.id(),BindingRole.CORE)),List.of());
        var ask=new Ask("q1","courseType","提议理由不能充当必要性证据",List.of(retake.id(),other.id()));
        var result=new RulePublicationGate(caps).assess(original,ask,review,facts);
        assertThat(result.questions().get("q1").uncoveredRequirementIds()).containsExactlyInAnyOrder("r1","r2");
        assertThat(result.questions().get("q1").coveredRequirementIds()).isEmpty();
        var unnecessary=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(
            requirement("r1",original.question(),Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED),
            requirement("r2",original.question(),Dimension.RETAKE,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED)),review.bindings(),List.of());
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,ask,unnecessary,facts))
            .hasMessage("CONDITION_NOT_NECESSARY");
        var known=new RuleAgentRunner.Input(original.owner(),original.taskId(),2,original.question(),original.questions(),Map.of("courseType","PUBLIC"));
        var knownReview=new SemanticAssessment(REVISION,RulePublicationContract.input(known,facts,caps).inputFingerprint(),review.requirements(),review.bindings(),List.of());
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(known,ask,knownReview,facts))
            .hasMessage("CONDITION_ALREADY_PROVIDED");
    }

    /**
     * 个人记录题可能同时带一般规则事实和额外依据事实；额外依据即使与主需求同对象，
     * 只要维度不同且未被核验绑定，就应只移除该事实，保留主事实和真实缺口，而不是让整题失败。
     */
    @Test void extraDifferentDimensionFactIsDroppedEvenWhenTheItemKeepsAGapIssue() {
        var plan=fact("S05/GENERAL","plan-required-credits",Map.of());
        var basis=fact("S05/GENERAL","plan-selection-basis",Map.of());
        var facts=List.of(plan,basis);
        var caps=catalogue(facts);
        var original=input("按这份手册，我还差多少学分才能毕业？");
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,original.question().length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),
            List.of(new Requirement("r1",span,Dimension.GRADUATION_CREDITS,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",plan.id(),BindingRole.CORE)),
            List.of(new UnresolvedDemand(span,UnresolvedReason.UNSUPPORTED_DIMENSION)));
        var proposal=new Finish(List.of(new Proposal("q1",List.of(basis.id(),plan.id()),
            List.of(new ProposedIssue(IssueKind.DATA_GAP,ReasonCode.UNSUPPORTED_DEMAND)),null)));
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal,review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(plan.id());
        assertThat(selected.proposal().items().getFirst().issues()).hasSize(1);
        assertThat(selected.assessment().questions().get("q1").uncoveredRequirementIds()).isEmpty();
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.PARTIAL);
        assertThat(selected.exclusion().rejectedFact().factId()).isEqualTo(basis.id());
    }

    /**
     * 缓考是独立事项：问题没有问缓考时，核验器多出来的DEFERRED_EXAM需求及其绑定应被移除，
     * 只发布问题实际问到的重修事实。
     */
    @Test void deferredExamRequirementIsDroppedWhenTheQuestionOnlyAsksAboutRetake() {
        var retake=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var deferred=fact("S10/DEFERRED_EXAM_ONLY","public-no-deferred-exam",Map.of("courseType","PUBLIC"));
        var facts=List.of(retake,deferred);
        var caps=catalogue(facts);
        var original=new RuleAgentRunner.Input(owner(),UUID.randomUUID(),1,"如果挂科的是公共选修课，手册怎么说？",
            List.of(new RuleAgentRunner.Question("q1","如果挂科的是公共选修课，手册怎么说？")),Map.of("courseType","PUBLIC"));
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,original.question().length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(
            new Requirement("r1",span,Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of()),
            new Requirement("r2",span,Dimension.DEFERRED_EXAM,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",retake.id(),BindingRole.CORE),
                new SemanticBinding("r2",deferred.id(),BindingRole.CORE)),List.of());
        var proposal=proposal(deferred.id(),retake.id());
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal,review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(retake.id());
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.exclusion().rejectedFact().factId()).isEqualTo(deferred.id());
    }

    /** 问题明确问缓考时不能误删缓考需求。 */
    @Test void deferredExamRequirementIsKeptWhenTheQuestionExplicitlyAsksAboutIt() {
        var deferred=fact("S10/DEFERRED_EXAM_ONLY","public-no-deferred-exam",Map.of("courseType","PUBLIC"));
        var facts=List.of(deferred);
        var caps=catalogue(facts);
        var question="公共选修课可以缓考吗？";
        var original=new RuleAgentRunner.Input(owner(),UUID.randomUUID(),1,question,
            List.of(new RuleAgentRunner.Question("q1",question)),Map.of("courseType","PUBLIC"));
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,question.length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),
            List.of(new Requirement("r1",span,Dimension.DEFERRED_EXAM,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",deferred.id(),BindingRole.CORE)),List.of());
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal(deferred.id()),review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(deferred.id());
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.exclusion()).isNull();
    }

    /** 已声明PUBLIC时，核验器保留的专业选修分支需求不应把正确的公共分支答案判成漏答。 */
    @Test void declaredCourseTypeDropsTheOtherBranchRequirement() {
        var publicFact=fact("S10/RETAKE_ONLY","public-failed-no-retake",Map.of("courseType","PUBLIC"));
        var professionalFact=fact("S10/RETAKE_ONLY","professional-failed-retake-application",Map.of("courseType","PROFESSIONAL"));
        var facts=List.of(publicFact,professionalFact);
        var caps=catalogue(facts);
        var question="公共选修课挂科能重修吗？";
        var original=new RuleAgentRunner.Input(owner(),UUID.randomUUID(),1,question,
            List.of(new RuleAgentRunner.Question("q1",question)),Map.of("courseType","PUBLIC"));
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,question.length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(
            new Requirement("r1",span,Dimension.RETAKE,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of()),
            new Requirement("r2",span,Dimension.RETAKE,Subject.PROFESSIONAL_ELECTIVE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",publicFact.id(),BindingRole.CORE),
                new SemanticBinding("r2",professionalFact.id(),BindingRole.CORE)),List.of());
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal(publicFact.id()),review,facts);
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.assessment().questions().get("q1").uncoveredRequirementIds()).isEmpty();
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(publicFact.id());
    }

    /** 多需求题中也可能混入完全未绑定的第三类事实；保留项覆盖全部需求时应只移除该额外事实。 */
    @Test void unboundExtraFactIsDroppedWhenRemainingFactsCoverAllRequirements() {
        var basis=fact("S05/PERSONAL_CREDIT_DEFICIT","plan-selection-basis",Map.of());
        var plan=fact("S05/PERSONAL_CREDIT_DEFICIT","plan-required-credits",Map.of());
        var online=fact("S05/PERSONAL_CREDIT_DEFICIT","online-general-half",Map.of());
        var extra=fact("T02/ARTICLE4","professional-elective-study-arrangement",Map.of());
        var facts=List.of(basis,plan,online,extra);
        var caps=catalogue(facts);
        var question="按这份手册，我还差多少学分才能毕业？";
        var original=input(question);
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,question.length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(
            new Requirement("r1",span,Dimension.STUDY_ARRANGEMENT,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED,List.of()),
            new Requirement("r2",span,Dimension.GRADUATION_CREDITS,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",basis.id(),BindingRole.CORE),
                new SemanticBinding("r2",plan.id(),BindingRole.CORE)),List.of());
        var proposal=proposal(basis.id(),plan.id(),online.id(),extra.id());
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal,review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(basis.id(),plan.id());
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.exclusion()).isNotNull();
    }

    /** 核验器把不同维度事实绑到同一需求时应移除该绑定，而不是让整题在 REVIEW_BINDING_INVALID 处失败。 */
    @Test void invalidBindingIsDroppedBeforeCoverageIsJudged() {
        var plan=fact("S05/PERSONAL_CREDIT_DEFICIT","plan-required-credits",Map.of());
        var online=fact("S05/PERSONAL_CREDIT_DEFICIT","online-general-half",Map.of());
        var facts=List.of(plan,online);
        var caps=catalogue(facts);
        var question="按这份手册，我还差多少学分才能毕业？";
        var original=input(question);
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,question.length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),
            List.of(new Requirement("r1",span,Dimension.GRADUATION_CREDITS,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",plan.id(),BindingRole.CORE),
                new SemanticBinding("r1",online.id(),BindingRole.CORE)),List.of());
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal(plan.id(),online.id()),review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(plan.id());
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.exclusion()).isNotNull();
    }

    /** 主事实声明HOURS_NORMAL为辅助维度时，原则值事实不能被“未绑定”过滤器误删。 */
    @Test void complementaryPrincipleFactIsAllowedWhenOnlyTheLimitRequirementIsBound() {
        var principle=fact("S03/GENERAL","hours-principle",Map.of());
        var limit=fact("S03/GENERAL","hours-upper-limit",Map.of());
        var facts=List.of(principle,limit);
        var caps=catalogue(facts);
        var question="有门公选课安排24学时，这能直接判定违规吗？";
        var original=input(question);
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,question.length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),
            List.of(new Requirement("r1",span,Dimension.HOURS_LIMIT,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",limit.id(),BindingRole.CORE)),List.of());
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal(principle.id(),limit.id()),review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(principle.id(),limit.id());
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.exclusion()).isNull();
    }

    /** 人数规则事实必须有发布能力，否则核验无法绑定，正确候选会被判无关。 */
    @Test void enrollmentFactsArePublishableAndCoverTheEnrollmentRequirement() {
        var range=fact("S04/GENERAL","campus-enrollment-general-range",Map.of());
        var below=fact("S04/GENERAL","campus-below-thirty",Map.of());
        var facts=List.of(range,below);
        var caps=catalogue(facts);
        var question="校内公选课只有29人选，按这份手册怎么规定？";
        var original=input(question);
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,question.length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),
            List.of(new Requirement("r1",span,Dimension.ENROLLMENT,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",range.id(),BindingRole.CORE),
                new SemanticBinding("r1",below.id(),BindingRole.CORE)),List.of());
        var selected=new RulePublicationGate(caps).reviewFinish(original,proposal(range.id(),below.id()),review,facts);
        assertThat(selected.assessment().verdict()).isEqualTo(Verdict.COMPLETE);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(range.id(),below.id());
    }

    private Owner owner(){return new Owner(UUID.randomUUID(),UUID.randomUUID());}

    @Test void askCannotUseArrangementFactOrUnresolvedDemandToInventCondition() {
        var caps=catalogue(arrangement);
        var original=input("比较两类选修的修读安排");
        var review=comparison(RulePublicationContract.input(original,arrangement,caps));
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,
            new Ask("q1","courseType","先问类别",List.of(professional.id())),review,arrangement))
            .hasMessage("CONDITION_NOT_NECESSARY");
    }

    /**
     * 模型只报缺口时，宿主用独立核验已确认的可发布CORE绑定补齐空事实项。
     * 该补齐只针对空项；有事实的提议不能被宿主改写。
     */
    @Test void emptyFactItemIsCompletedFromPublishableReviewBindings() {
        var plan=fact("S05/GENERAL","plan-required-credits",Map.of());
        var facts=List.of(plan);
        var caps=catalogue(facts);
        var original=input("按这份手册，我还差多少学分才能毕业？");
        var safe=RulePublicationContract.input(original,facts,caps);
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),
            List.of(requirement("r1",original.question(),Dimension.GRADUATION_CREDITS,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED)),
            List.of(new SemanticBinding("r1",plan.id(),BindingRole.CORE)),List.of());
        var empty=new Finish(List.of(new Proposal("q1",List.of(),List.of(new ProposedIssue(IssueKind.DATA_GAP,ReasonCode.UNSUPPORTED_DEMAND)),null)));
        var selected=new RulePublicationGate(caps).reviewFinish(original,empty,review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).containsExactly(plan.id());
        assertThat(selected.completedFactIds()).containsExactly(plan.id());
        assertThat(selected.assessment().questions().get("q1").coveredRequirementIds()).containsExactly("r1");

        var withFact=new Finish(List.of(new Proposal("q1",List.of(plan.id()),List.of(),null)));
        var untouched=new RulePublicationGate(caps).reviewFinish(original,withFact,review,facts);
        assertThat(untouched.completedFactIds()).isEmpty();
        assertThat(untouched.proposal()).isEqualTo(withFact);
    }

    /** 未绑定的需求不能被宿主补事实；否则会把不适用或未识别的需求伪装成已回答。 */
    @Test void unresolvedDemandIsNotCompletedFromAnUnboundFact() {
        var plan=fact("S05/GENERAL","plan-required-credits",Map.of());
        var facts=List.of(plan);
        var caps=catalogue(facts);
        var original=input("按这份手册，我还差多少学分才能毕业？");
        var safe=RulePublicationContract.input(original,facts,caps);
        var span=new DemandSpan("q1",0,original.question().length());
        var review=new SemanticAssessment(REVISION,safe.inputFingerprint(),
            List.of(new Requirement("r1",span,Dimension.UNKNOWN,Subject.UNSPECIFIED,Recognition.UNCLASSIFIED,List.of())),
            List.of(),List.of(new UnresolvedDemand(span,UnresolvedReason.UNSUPPORTED_DIMENSION)));
        var empty=new Finish(List.of(new Proposal("q1",List.of(),List.of(new ProposedIssue(IssueKind.DATA_GAP,ReasonCode.UNSUPPORTED_DEMAND)),null)));
        var selected=new RulePublicationGate(caps).reviewFinish(original,empty,review,facts);
        assertThat(selected.proposal().items().getFirst().factIds()).isEmpty();
        assertThat(selected.completedFactIds()).isEmpty();
        assertThat(selected.assessment().questions().get("q1").unsupportedDemand()).isTrue();
    }
}
