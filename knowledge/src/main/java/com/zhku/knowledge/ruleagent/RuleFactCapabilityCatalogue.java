package com.zhku.knowledge.ruleagent;

import com.zhku.knowledge.rulecheck.*;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 能力按审核事实登记，不存允许通过的问句；未登记事实不获得默认能力。 */
public final class RuleFactCapabilityCatalogue {
    public static final String VERSION="REVIEWED_FACT_CAPABILITY_01";
    private final Map<String,FactCapability> capabilities;
    private RuleFactCapabilityCatalogue(Map<String,FactCapability> capabilities) {
        this.capabilities=Map.copyOf(capabilities);
    }
    public String version() {return VERSION;}
    public Optional<FactCapability> find(String id) {return Optional.ofNullable(capabilities.get(id));}

    public static RuleFactCapabilityCatalogue fromReviewed(List<RuleCheckApplicationService.CatalogEntry> entries) {
        var capabilities=new LinkedHashMap<String,FactCapability>();
        for(var entry:entries) {
            var rule=entry.template();
            if(rule.reviewState()!=RuleCheckRuleTemplate.ReviewState.APPROVED||!rule.referenceRevision().equals("2025.9.2"))continue;
            for(var fact:rule.facts()) {
                String id=rule.ruleId()+":"+fact.id();
                var capability=capability(id,fact);
                if(capability==null)continue;
                var previous=capabilities.putIfAbsent(id,capability);
                if(previous!=null&&!previous.equals(capability))throw new Rejected(Code.CAPABILITY_UNAVAILABLE);
            }
        }
        return new RuleFactCapabilityCatalogue(capabilities);
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
