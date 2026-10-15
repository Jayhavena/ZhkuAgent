package com.zhku.knowledge.ruleagent;

import com.zhku.knowledge.rulecheck.*;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 能力按审核事实登记，不存允许通过的问句；未登记事实不获得默认能力。 */
public final class RuleFactCapabilityCatalogue {
    public static final String VERSION="REVIEWED_FACT_CAPABILITY_01";
    private final Map<String,FactCapability> capabilities;
    private final List<RulePublicationContract.ReviewedCondition> conditions;
    private final Map<String,Map<String,String>> reviewedWhen;
    private RuleFactCapabilityCatalogue(Map<String,FactCapability> capabilities,List<RulePublicationContract.ReviewedCondition> conditions,Map<String,Map<String,String>> reviewedWhen) {
        this.reviewedWhen=Map.copyOf(reviewedWhen);
        this.conditions=List.copyOf(conditions);
        this.capabilities=Map.copyOf(capabilities);
    }
    public List<RulePublicationContract.ReviewedCondition> conditions(List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        var read=facts.stream().map(RuleAgentEvidenceTools.VerifiedFact::id).collect(java.util.stream.Collectors.toSet());
        return conditions.stream().map(c->new RulePublicationContract.ReviewedCondition(c.ruleId(),c.conditionId(),c.question(),c.allowedValues(),c.factIds().stream().filter(read::contains).toList())).filter(c->!c.factIds().isEmpty()).toList();
    }
    /** 缺少某值的事实不等于该值不可行；只用已读并进入核验身份的事实排除被其他用户已知条件明确否定的分支。 */
    public Set<String> feasibleValues(RulePublicationContract.ReviewedCondition condition,Dimension dimension,Map<String,String> known) {
        var result=new LinkedHashSet<String>();
        for(String value:condition.allowedValues()) {
            var candidates=reviewedWhen.entrySet().stream().filter(e->condition.factIds().contains(e.getKey())&&e.getKey().startsWith(condition.ruleId()+":")&&capabilities.get(e.getKey()).dimension()==dimension&&value.equals(e.getValue().get(condition.conditionId()))).toList();
            if(candidates.isEmpty()||candidates.stream().anyMatch(e->e.getValue().entrySet().stream().allMatch(w->w.getKey().equals(condition.conditionId())||!known.containsKey(w.getKey())||known.get(w.getKey()).equals(w.getValue()))))result.add(value);
        }
        return Set.copyOf(result);
    }
    public String version() {return VERSION;}
    public Optional<FactCapability> find(String id) {return Optional.ofNullable(capabilities.get(id));}

    /** 可读事实不等于可发布事实；提议、Schema与Gate共用同一登记和原始定义指纹。 */
    public List<String> publicationFactIds(List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        return facts.stream().filter(f->find(f.id()).filter(c->c.sourceFingerprint().equals(fingerprint(f.definition()))).isPresent())
            .map(RuleAgentEvidenceTools.VerifiedFact::id).distinct().sorted().toList();
    }
    FactCapability requireCapability(RuleAgentEvidenceTools.VerifiedFact fact) {
        var capability=find(fact.id()).orElseThrow(()->new Rejected(Code.CAPABILITY_UNAVAILABLE));
        if(!capability.sourceFingerprint().equals(fingerprint(fact.definition())))throw new Rejected(Code.CAPABILITY_UNAVAILABLE);
        return capability;
    }
    void requireProposal(RuleAgentContract.NextAction proposal,List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        var ids=proposal instanceof RuleAgentContract.Ask ask?ask.supportingFactIds():
            ((RuleAgentContract.Finish)proposal).items().stream().flatMap(i->i.factIds().stream()).toList();
        for(String id:ids)requireCapability(facts.stream().filter(f->f.id().equals(id)).findFirst().orElseThrow(()->new Rejected(Code.FACT_NOT_READ)));
    }

    public static RuleFactCapabilityCatalogue fromReviewed(List<RuleCheckApplicationService.CatalogEntry> entries) {
        var capabilities=new LinkedHashMap<String,FactCapability>();
        var conditions=new ArrayList<RulePublicationContract.ReviewedCondition>();
        var reviewedWhen=new LinkedHashMap<String,Map<String,String>>();
        for(var entry:entries) {
            var rule=entry.template();
            if(rule.reviewState()!=RuleCheckRuleTemplate.ReviewState.APPROVED||!rule.referenceRevision().equals("2025.9.2"))continue;
            for(var fact:rule.facts()) {
                String id=rule.ruleId()+":"+fact.id();
                var capability=capability(id,fact);
                if(capability==null)continue;
                reviewedWhen.put(id,fact.when());
                var previous=capabilities.putIfAbsent(id,capability);
                if(previous!=null&&!previous.equals(capability))throw new Rejected(Code.CAPABILITY_UNAVAILABLE);
            }
        }
        for(var entry:entries) {
            var rule=entry.template();
            if(rule.reviewState()!=RuleCheckRuleTemplate.ReviewState.APPROVED||!rule.referenceRevision().equals("2025.9.2"))continue;
            for(var c:rule.conditions()) {
                var ids=rule.facts().stream().filter(f->f.when().containsKey(c.key())&&capabilities.containsKey(rule.ruleId()+":"+f.id())).map(f->rule.ruleId()+":"+f.id()).toList();
                if(!ids.isEmpty())conditions.add(new RulePublicationContract.ReviewedCondition(rule.ruleId(),c.key(),c.question(),c.allowedValues(),ids));
            }
        }
        return new RuleFactCapabilityCatalogue(capabilities,conditions.stream().distinct().sorted(Comparator.comparing(RulePublicationContract.ReviewedCondition::ruleId).thenComparing(RulePublicationContract.ReviewedCondition::conditionId)).toList(),reviewedWhen);
    }

    private static FactCapability capability(String id,RuleCheckRuleTemplate.Fact fact) {
        Dimension dimension;
        Subject subject;
        switch(id) {
            case "T02/ARTICLE4:professional-elective-study-arrangement" -> {dimension=Dimension.STUDY_ARRANGEMENT;subject=Subject.PROFESSIONAL_ELECTIVE;}
            case "T02/ARTICLE4:public-elective-self-selection" -> {dimension=Dimension.STUDY_ARRANGEMENT;subject=Subject.PUBLIC_ELECTIVE;}
            case "S02/GENERAL:elective-category-distinction" -> {dimension=Dimension.CATEGORY;subject=Subject.GENERAL_UNDERGRADUATE;}
            case "S02/GENERAL:public-course-subcategories" -> {dimension=Dimension.CATEGORY;subject=Subject.PUBLIC_ELECTIVE;}
            case "S03/GENERAL:hours-principle" -> {dimension=Dimension.HOURS_NORMAL;subject=Subject.PUBLIC_ELECTIVE;}
            case "S03/GENERAL:hours-upper-limit" -> {dimension=Dimension.HOURS_LIMIT;subject=Subject.PUBLIC_ELECTIVE;}
            case "S07/GENERAL:public-semester-four-credits" -> {dimension=Dimension.SEMESTER_CREDIT_LIMIT;subject=Subject.PUBLIC_ELECTIVE;}
            case "S10/RETAKE_ONLY:public-failed-no-retake" -> {dimension=Dimension.RETAKE;subject=Subject.PUBLIC_ELECTIVE;}
            case "S10/RETAKE_ONLY:professional-failed-retake-application" -> {dimension=Dimension.RETAKE;subject=Subject.PROFESSIONAL_ELECTIVE;}
            case "S10/DEFERRED_EXAM_ONLY:public-no-deferred-exam" -> {dimension=Dimension.DEFERRED_EXAM;subject=Subject.PUBLIC_ELECTIVE;}
            default -> {return null;}
        }
        var auxiliary=dimension==Dimension.HOURS_LIMIT?Set.of(Dimension.HOURS_NORMAL):Set.<Dimension>of();
        var roles=auxiliary.isEmpty()?Set.of(BindingRole.CORE):Set.of(BindingRole.CORE,BindingRole.CONDITION_EXCEPTION);
        return new FactCapability(id,dimension,subject,roles,auxiliary,fingerprint(fact));
    }
}
