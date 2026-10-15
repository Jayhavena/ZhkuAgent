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
        if(!(proposal instanceof RuleAgentContract.Finish)&&!(proposal instanceof RuleAgentContract.Ask))throw new Rejected(Code.ACTION_UNSUPPORTED);
        require(review!=null&&review.inputFingerprint().equals(RulePublicationContract.input(input,facts,catalogue).inputFingerprint()));
        var questions=new LinkedHashMap<String,RuleAgentRunner.Question>();
        input.questions().forEach(q->questions.put(q.id(),q));
        if(proposal instanceof RuleAgentContract.Finish finish)
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
        if(proposal instanceof RuleAgentContract.Ask ask)return assessAsk(input,ask,review,read,requirements,questions);
        var finish=(RuleAgentContract.Finish)proposal;
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

    private PublicationAssessment assessAsk(RuleAgentRunner.Input input,RuleAgentContract.Ask ask,
        SemanticAssessment review,Map<String,RuleAgentEvidenceTools.VerifiedFact> read,
        Map<String,Requirement> requirements,Map<String,RuleAgentRunner.Question> questions) {
        require(questions.containsKey(ask.subquestionId()));
        if(input.userConditions().containsKey(ask.conditionId()))throw new Rejected(Code.CONDITION_ALREADY_PROVIDED);
        if(ask.supportingFactIds().isEmpty())throw new Rejected(Code.CONDITION_NOT_NECESSARY);
        var accepted=new ArrayList<SemanticBinding>();
        var pending=new LinkedHashSet<String>();
        for(String id:ask.supportingFactIds()) {
            var fact=read.get(id);
            if(fact==null)throw new Rejected(Code.FACT_NOT_READ);
            capability(fact);
            var compatible=fact.definition().when().entrySet().stream().allMatch(e->
                e.getKey().equals(ask.conditionId())||e.getValue().equals(input.userConditions().get(e.getKey())));
            if(!compatible)throw new Rejected(Code.WHEN_NOT_SATISFIED);
            var matching=review.bindings().stream().filter(b->b.factId().equals(id)&&b.role()==BindingRole.CORE)
                .filter(b->requirements.get(b.requirementId()).span().subquestionId().equals(ask.subquestionId()))
                .filter(b->requirements.get(b.requirementId()).necessaryConditionIds().contains(ask.conditionId())).toList();
            if(!fact.definition().when().containsKey(ask.conditionId())||matching.isEmpty())throw new Rejected(Code.CONDITION_NOT_NECESSARY);
            accepted.addAll(matching);
            matching.forEach(b->pending.add(b.requirementId()));
        }
        requireCompleteBranches(input,ask,review,read,requirements,accepted);
        var assessments=new LinkedHashMap<String,QuestionAssessment>();
        boolean hasKnown=false;
        for(String q:questions.keySet()) {
            var covered=new LinkedHashSet<String>();
            // 只保留核验明确关联且条件满足的独立事实；读过的无关背景不能自动展示。
            for(var binding:review.bindings()) {
                var requirement=requirements.get(binding.requirementId());
                var fact=read.get(binding.factId());
                if(!requirement.span().subquestionId().equals(q)||binding.role()!=BindingRole.CORE)continue;
                if(fact.definition().independentlyAnswerable()&&fact.definition().when().entrySet().stream()
                    .allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey())))) {
                    covered.add(requirement.id());
                    if(!accepted.contains(binding))accepted.add(binding);
                }
            }
            var uncovered=new LinkedHashSet<String>();
            review.requirements().stream().filter(r->r.span().subquestionId().equals(q)).map(Requirement::id).forEach(uncovered::add);
            uncovered.removeAll(covered);
            boolean unresolved=review.requirements().stream().anyMatch(r->r.span().subquestionId().equals(q)&&r.recognition()!=Recognition.IDENTIFIED)
                ||review.unresolvedDemands().stream().anyMatch(r->r.span().subquestionId().equals(q));
            // 追问不能掩盖其余未理解/未完成需求；首期只接受可由这一审核条件推进的澄清。
            if(unresolved||uncovered.stream().anyMatch(id->!pending.contains(id)))throw new Rejected(Code.CONDITION_NOT_NECESSARY);
            hasKnown|=!covered.isEmpty();
            assessments.put(q,new QuestionAssessment(covered,uncovered,false,false));
        }
        if(pending.isEmpty()||pending.stream().allMatch(id->assessments.get(ask.subquestionId()).coveredRequirementIds().contains(id)))
            throw new Rejected(Code.CONDITION_NOT_NECESSARY);
        return new PublicationAssessment(hasKnown?Verdict.PARTIAL:Verdict.UNRESOLVED,accepted,assessments);
    }

    /** 同一原始需求的条件分支必须覆盖审核允许值；不能由模型漏报一侧缩小验收集合。 */
    private void requireCompleteBranches(RuleAgentRunner.Input input,RuleAgentContract.Ask ask,SemanticAssessment review,
        Map<String,RuleAgentEvidenceTools.VerifiedFact> read,Map<String,Requirement> requirements,List<SemanticBinding> accepted) {
        var declarations=catalogue.conditions(List.copyOf(read.values()));
        var groups=accepted.stream().map(b->requirements.get(b.requirementId())).collect(java.util.stream.Collectors.groupingBy(r->List.of(r.span(),r.dimension())));
        for(var group:groups.values()) {
            var first=group.getFirst();var expected=new LinkedHashSet<String>();var represented=new LinkedHashSet<String>();
            for(var declaration:declarations) {
                if(!declaration.conditionId().equals(ask.conditionId()))continue;
                boolean relevant=declaration.factIds().stream().map(read::get).filter(Objects::nonNull).anyMatch(f->catalogue.find(f.id()).orElseThrow().dimension()==first.dimension());
                if(relevant)expected.addAll(catalogue.feasibleValues(declaration,first.dimension(),input.userConditions()));
            }
            for(var binding:accepted) {
                var requirement=requirements.get(binding.requirementId());
                if(!requirement.span().equals(first.span())||requirement.dimension()!=first.dimension())continue;
                var fact=read.get(binding.factId());String value=fact.definition().when().get(ask.conditionId());
                if(value!=null)represented.add(value);
            }
            if(expected.isEmpty()||!expected.equals(represented))throw new Rejected(Code.CONDITION_NOT_NECESSARY);
        }
    }
    private FactCapability capability(RuleAgentEvidenceTools.VerifiedFact fact) {
        return catalogue.requireCapability(fact);
    }
    private static void span(DemandSpan span,Map<String,RuleAgentRunner.Question> questions) {
        var question=questions.get(span.subquestionId());
        require(question!=null&&span.endExclusive()<=question.text().length());
    }
}
