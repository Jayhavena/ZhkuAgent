package com.zhku.knowledge.ruleagent;

import java.util.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 只核对已声明需求/能力的一致性与覆盖；不代替fresh引用、适用性或自然语言真值检查。 */
public final class RulePublicationGate {
    private final RuleFactCapabilityCatalogue catalogue;
    public RulePublicationGate(RuleFactCapabilityCatalogue catalogue) {
        this.catalogue=Objects.requireNonNull(catalogue);
    }

    public PublicationAssessment assess(RuleAgentRunner.Input input,RuleAgentContract.NextAction proposal,
        SemanticAssessment review,List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        if(!(proposal instanceof RuleAgentContract.Finish finish))throw new Rejected(Code.ACTION_UNSUPPORTED);
        require(review!=null&&review.inputFingerprint().equals(RulePublicationContract.input(input,facts,catalogue).inputFingerprint()));
        var questions=new LinkedHashMap<String,RuleAgentRunner.Question>();
        input.questions().forEach(q->questions.put(q.id(),q));
        require(new HashSet<>(finish.items().stream().map(RuleAgentContract.Proposal::subquestionId).toList()).equals(questions.keySet()));
        var read=new LinkedHashMap<String,RuleAgentEvidenceTools.VerifiedFact>();
        for(var fact:facts) {
            require(fact.id().equals(fact.ruleId()+":"+fact.definition().id()));
            require(read.putIfAbsent(fact.id(),fact)==null);
        }
        var requirements=new LinkedHashMap<String,Requirement>();
        for(var requirement:review.requirements()) {
            span(requirement.span(),questions);
            requirements.put(requirement.id(),requirement);
        }
        for(var unresolved:review.unresolvedDemands())span(unresolved.span(),questions);
        for(String q:questions.keySet())require(review.requirements().stream().anyMatch(r->r.span().subquestionId().equals(q))
            ||review.unresolvedDemands().stream().anyMatch(r->r.span().subquestionId().equals(q)));

        for(var binding:review.bindings()) {
            var requirement=requirements.get(binding.requirementId());
            if(requirement==null||requirement.recognition()!=Recognition.IDENTIFIED)throw new Rejected(Code.REVIEW_BINDING_INVALID);
            var fact=read.get(binding.factId());
            if(fact==null)throw new Rejected(Code.FACT_NOT_READ);
            var capability=capability(fact);
            boolean dimension=binding.role()==BindingRole.CORE?capability.dimension()==requirement.dimension():
                capability.auxiliaryDimensions().contains(requirement.dimension());
            if(!dimension||capability.subject()!=requirement.subject()||!capability.allowedRoles().contains(binding.role()))
                throw new Rejected(Code.REVIEW_BINDING_INVALID);
        }
        var accepted=new ArrayList<SemanticBinding>();
        var assessments=new LinkedHashMap<String,QuestionAssessment>();
        boolean complete=true;
        boolean hasCovered=false;
        for(var item:finish.items()) {
            var matching=review.bindings().stream().filter(b->requirements.get(b.requirementId()).span().subquestionId().equals(item.subquestionId())).toList();
            for(String id:item.factIds()) {
                var fact=read.get(id);
                if(fact==null)throw new Rejected(Code.FACT_NOT_READ);
                capability(fact);
                if(matching.stream().noneMatch(b->b.factId().equals(id)))throw new Rejected(Code.FACT_NOT_RELEVANT);
                if(!fact.definition().when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey()))))
                    throw new Rejected(Code.WHEN_NOT_SATISFIED);
            }
            var selected=matching.stream().filter(b->item.factIds().contains(b.factId())).toList();
            accepted.addAll(selected);
            var covered=new LinkedHashSet<String>();
            selected.stream().filter(b->b.role()==BindingRole.CORE).forEach(b->covered.add(b.requirementId()));
            var uncovered=new LinkedHashSet<String>();
            review.requirements().stream().filter(r->r.span().subquestionId().equals(item.subquestionId())).map(Requirement::id).forEach(uncovered::add);
            uncovered.removeAll(covered);
            boolean unresolved=review.requirements().stream().anyMatch(r->r.span().subquestionId().equals(item.subquestionId())&&r.recognition()!=Recognition.IDENTIFIED)
                ||review.unresolvedDemands().stream().anyMatch(r->r.span().subquestionId().equals(item.subquestionId()));
            boolean issues=!item.issues().isEmpty();
            complete&=uncovered.isEmpty()&&!unresolved&&!issues;
            hasCovered|=!covered.isEmpty();
            assessments.put(item.subquestionId(),new QuestionAssessment(covered,uncovered,unresolved,issues));
        }
        // 辅助例外不完成主需求；漏选已读事实也不能借核验器看过它而自称完整。
        return new PublicationAssessment(complete?Verdict.COMPLETE:hasCovered?Verdict.PARTIAL:Verdict.UNRESOLVED,accepted,assessments);
    }

    private FactCapability capability(RuleAgentEvidenceTools.VerifiedFact fact) {
        var capability=catalogue.find(fact.id()).orElseThrow(()->new Rejected(Code.CAPABILITY_UNAVAILABLE));
        if(!capability.sourceFingerprint().equals(fingerprint(fact.definition())))throw new Rejected(Code.CAPABILITY_UNAVAILABLE);
        return capability;
    }
    private static void span(DemandSpan span,Map<String,RuleAgentRunner.Question> questions) {
        var question=questions.get(span.subquestionId());
        require(question!=null&&span.endExclusive()<=question.text().length());
    }
}
