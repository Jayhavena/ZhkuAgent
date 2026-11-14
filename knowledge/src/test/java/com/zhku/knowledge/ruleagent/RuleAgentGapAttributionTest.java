package com.zhku.knowledge.ruleagent;

import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.RetrievedChunk;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
import static org.assertj.core.api.Assertions.*;

/** 使用真实读取与Gate聚合区分原文回退、事实能力不足和模型漏选；不调用模型。 */
class RuleAgentGapAttributionTest {
    final RuleAgentRunnerTest fixture=new RuleAgentRunnerTest();
    final UUID second=UUID.randomUUID(),run=UUID.randomUUID();
    final RuleAgentRunner.Input input=RuleAgentRunner.Input.single(fixture.owner,fixture.task,"课程类别是什么？");
    RuleFactCapabilityCatalogue catalogue;
    RuleActionBroker broker;

    void read(boolean complete)throws Exception{read(complete,false);}
    void read(boolean complete,boolean includeSecondFact)throws Exception{
        var bindings=List.of(fixture.chunk,second).stream().map(chunk->new RuleCheckRuleTemplate.EvidenceBinding(
            fixture.source,chunk,"2025.9.2",hash(),0,fixture.text.length(),fixture.text)).toList();
        var fact=new RuleCheckRuleTemplate.Fact("elective-category-distinction","合成分类说明",true,Map.of(),bindings);
        var facts=includeSecondFact?List.of(fact,new RuleCheckRuleTemplate.Fact("public-course-subcategories","合成公选分类说明",true,Map.of(),bindings)):List.of(fact);
        var rule=new RuleCheckRuleTemplate("S02/GENERAL","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"SYNTHETIC",LocalDate.of(2026,11,14),facts,List.of(),List.of(),List.of());
        var entry=new RuleCheckApplicationService.CatalogEntry(rule,"合成范围",new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));
        catalogue=RuleFactCapabilityCatalogue.fromReviewed(List.of(entry));
        var tools=new RuleAgentEvidenceTools(fixture.owner,fixture.task,new RuleCheckRuntimeSettings("p","c","s","2025.9.2",20,5),()->List.of(entry),
            (q,n)->List.of(fixture.chunk,second).stream().map(chunk->new RetrievedChunk(chunk,fixture.text,"synthetic","synthetic",1,"2025.9.2",hash(),fixture.source)).toList(),
            (s,c)->Optional.of(new RuleCheckEvidenceSnapshot("current",s,c,"2025.9.2",hash(),fixture.text,true)),a->{},Clock.systemUTC());
        broker=new RuleActionBroker(tools);
        var search=broker.execute(new Search("q1","类别"),input,run,1);
        var raw=broker.execute(new Read("q1",List.of(search.candidates().getFirst().handle())),input,run,2);
        assertThat(raw.dataLevel()).isEqualTo(DataLevel.VERIFIED_EVIDENCE);
        if(complete){
            var full=broker.execute(new Read("q1",search.candidates().stream().map(Candidate::handle).toList()),input,run,3);
            assertThat(full.dataLevel()).isEqualTo(DataLevel.VERIFIED_FACT);
        }
    }
    String hash(){try{return fixture.hash();}catch(Exception failure){throw new IllegalStateException(failure);}}
    Finish finish(List<String> facts){return new Finish(List.of(new Proposal("q1",facts,List.of(new ProposedIssue(IssueKind.DATA_GAP,ReasonCode.REVIEWED_FACT_COVERAGE_GAP)),null)));}
    PublicationAssessment assess(Finish finish,boolean bind){
        var safe=RulePublicationContract.input(input,broker.facts(),catalogue);
        var requirement=new Requirement("r1",new DemandSpan("q1",0,input.question().length()),Dimension.CATEGORY,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED,List.of());
        var bindings=bind?List.of(new SemanticBinding("r1",broker.facts().getFirst().id(),BindingRole.CORE)):List.<SemanticBinding>of();
        return new RulePublicationGate(catalogue).assess(input,finish,new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(requirement),bindings,List.of()),broker.facts());
    }
    @Test void rawReadWithoutFactsRetainsHonestCoverageGap()throws Exception{
        read(false);var proposal=finish(List.of());var assessment=assess(proposal,false);
        assertThat(assessment.acceptedBindings()).isEmpty();
        var published=new RuleResultPublisher(broker,input,run,assessment).finish(proposal);
        assertThat(published.status()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(published.result().items().getFirst().claims()).isEmpty();
        assertThat(published.result().items().getFirst().issues()).extracting(Issue::reasonCode).contains(ReasonCode.REVIEWED_FACT_COVERAGE_GAP);
    }
    @Test void fullyCoveredClaimCannotBorrowEarlierRawReadToInventGap()throws Exception{
        read(true);var proposal=finish(broker.facts().stream().map(RuleAgentEvidenceTools.VerifiedFact::id).toList());var assessment=assess(proposal,true);
        assertThat(assessment.questions().get("q1").uncoveredRequirementIds()).isEmpty();
        assertThatThrownBy(()->new RuleResultPublisher(broker,input,run,assessment).finish(proposal)).isInstanceOf(RuleAgentRunner.Rejected.class);
    }
    @Test void omittingAvailableFactCannotBeRecastAsFactCapabilityGap()throws Exception{
        read(true);var proposal=finish(List.of());var assessment=assess(proposal,false);
        assertThat(assessment.questions().get("q1").uncoveredRequirementIds()).contains("r1");
        assertThatThrownBy(()->new RuleResultPublisher(broker,input,run,assessment).finish(proposal)).isInstanceOf(RuleAgentRunner.Rejected.class);
    }
    @Test void factCapabilityGapAndTextDetailHaveDifferentExplanations(){
        assertThat(RuleResultPublisher.issue(ReasonCode.REVIEWED_FACT_COVERAGE_GAP).message())
            .isNotEqualTo(RuleResultPublisher.issue(ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT).message());
    }
    @Test void omittedAvailableFactWithoutGapLabelRemainsExplicitProcessingFailure()throws Exception{
        read(true,true);
        var general=broker.facts().stream().filter(f->f.definition().id().equals("elective-category-distinction")).findFirst().orElseThrow();
        var publicCourse=broker.facts().stream().filter(f->f.definition().id().equals("public-course-subcategories")).findFirst().orElseThrow();
        var proposal=new Finish(List.of(new Proposal("q1",List.of(general.id()),List.of(),null)));
        var safe=RulePublicationContract.input(input,broker.facts(),catalogue);
        var span=new DemandSpan("q1",0,input.question().length());
        var semantic=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(
            new Requirement("r1",span,Dimension.CATEGORY,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED,List.of()),
            new Requirement("r2",span,Dimension.CATEGORY,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",general.id(),BindingRole.CORE),new SemanticBinding("r2",publicCourse.id(),BindingRole.CORE)),List.of());
        var assessment=new RulePublicationGate(catalogue).assess(input,proposal,semantic,broker.facts());
        assertThat(assessment.questions().get("q1").uncoveredRequirementIds()).containsExactly("r2");
        assertThat(assessment.questions().get("q1").factCapabilityGapRequirementIds()).isEmpty();
        var published=new RuleResultPublisher(broker,input,run,assessment).finish(proposal);
        assertThat(published.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(published.result().coverage()).isEqualTo(Coverage.PARTIAL);
        assertThat(published.result().items().getFirst().claims()).hasSize(1);
        assertThat(published.result().items().getFirst().issues()).extracting(Issue::reasonCode).containsExactly(ReasonCode.ANSWER_COVERAGE_INCOMPLETE);
    }
    @Test void hostAddsCapabilityGapBesideValidClaimWithoutModelGapLabel()throws Exception{
        read(true);
        var proposal=new Finish(List.of(new Proposal("q1",List.of(broker.facts().getFirst().id()),List.of(),null)));
        var safe=RulePublicationContract.input(input,broker.facts(),catalogue);
        var span=new DemandSpan("q1",0,input.question().length());
        var semantic=new SemanticAssessment(REVISION,safe.inputFingerprint(),List.of(
            new Requirement("r1",span,Dimension.CATEGORY,Subject.GENERAL_UNDERGRADUATE,Recognition.IDENTIFIED,List.of()),
            new Requirement("r2",span,Dimension.DEFERRED_EXAM,Subject.PUBLIC_ELECTIVE,Recognition.IDENTIFIED,List.of())),
            List.of(new SemanticBinding("r1",broker.facts().getFirst().id(),BindingRole.CORE)),List.of());
        var assessment=new RulePublicationGate(catalogue).assess(input,proposal,semantic,broker.facts());
        assertThat(assessment.questions().get("q1").factCapabilityGapRequirementIds()).containsExactly("r2");
        var published=new RuleResultPublisher(broker,input,run,assessment).finish(proposal);
        assertThat(published.status()).isEqualTo(TaskStatus.RESOLVED);
        assertThat(published.result().coverage()).isEqualTo(Coverage.PARTIAL);
        assertThat(published.result().items().getFirst().claims()).hasSize(1);
        assertThat(published.result().items().getFirst().issues()).extracting(Issue::reasonCode).containsExactly(ReasonCode.REVIEWED_FACT_COVERAGE_GAP);
    }
}
