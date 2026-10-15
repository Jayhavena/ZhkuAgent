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
            .hasMessage(Code.FACT_NOT_RELEVANT.name());
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

    @Test void askCannotUseArrangementFactOrUnresolvedDemandToInventCondition() {
        var caps=catalogue(arrangement);
        var original=input("比较两类选修的修读安排");
        var review=comparison(RulePublicationContract.input(original,arrangement,caps));
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(original,
            new Ask("q1","courseType","先问类别",List.of(professional.id())),review,arrangement))
            .hasMessage("CONDITION_NOT_NECESSARY");
    }
}
