package com.zhku.knowledge.ruleagent;

import java.util.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;

/** 只核对已声明需求/能力的一致性与覆盖；不代替fresh引用、适用性或自然语言真值检查。 */
public final class RulePublicationGate {
    private final RuleFactCapabilityCatalogue catalogue;
    public RulePublicationGate(RuleFactCapabilityCatalogue catalogue) {
        this.catalogue=Objects.requireNonNull(catalogue);
    }

    record ReviewedFinish(RuleAgentContract.Finish proposal, PublicationAssessment assessment,
                          RelevanceDiagnostic exclusion, List<String> completedFactIds) {
        ReviewedFinish { completedFactIds = List.copyOf(completedFactIds); }
    }

    ReviewedFinish reviewFinish(RuleAgentRunner.Input input, RuleAgentContract.Finish proposal,
                               SemanticAssessment review, List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        // 只在问题明确问及缓考/延期考试时保留DEFERRED_EXAM需求；挂科/重修问题不能把同条缓考规则扩成额外需求。
        var effectiveReview = normalizeBindings(normalizeReview(input, review), facts);
        var completed = completeEmptyItems(input, proposal, effectiveReview, facts);
        var finish = completed.finish();
        try {
            return new ReviewedFinish(finish, assess(input, finish, effectiveReview, facts), null, completed.factIds());
        } catch (Rejected failure) {
            if (failure.code != Code.FACT_NOT_RELEVANT || input.questions().size() != 1 || finish.items().size() != 1) throw failure;
            var item = finish.items().getFirst();
            var detail = failure.relevance;
            if (detail == null || item.operation() != null) throw failure;
            // 伪造/未读ID不能被“未绑定即移除”吞掉；先要求所有提议ID都来自本轮已读事实。
            if (!item.factIds().stream().allMatch(id -> facts.stream().anyMatch(f -> f.id().equals(id))))
                throw new Rejected(Code.FACT_NOT_READ);
            // 只保留独立核验明确绑定到本题需求的事实；所有未绑定额外事实一次移除，避免逐个拒绝把整题拖成失败。
            var bound = effectiveReview.bindings().stream()
                    .filter(b -> effectiveReview.requirements().stream().anyMatch(r -> r.id().equals(b.requirementId())
                            && r.span().subquestionId().equals(item.subquestionId())))
                    .map(SemanticBinding::factId).collect(java.util.stream.Collectors.toSet());
            var retained = item.factIds().stream().filter(bound::contains).toList();
            if (retained.isEmpty() || retained.size() == item.factIds().size()) throw failure;
            var filtered = new RuleAgentContract.Finish(List.of(new RuleAgentContract.Proposal(item.subquestionId(),retained,item.issues(),null)));
            var assessment = assess(input, filtered, effectiveReview, facts);
            var coverage = assessment.questions().get(item.subquestionId());
            if (coverage == null || !coverage.uncoveredRequirementIds().isEmpty() || assessment.verdict() == Verdict.UNRESOLVED) throw failure;
            return new ReviewedFinish(filtered, assessment, detail, completed.factIds());
        }
    }

    /**
     * 缓考是独立于挂科重修的办理事项；问题没有明确问及缓考/延期考试时，
     * 核验器多识别出的DEFERRED_EXAM需求不能反过来让答案发布无关缓考事实。
     * 只移除该维度需求及其绑定，其他需求/未解决项保持原样。
     */
    static SemanticAssessment normalizeReview(RuleAgentRunner.Input input, SemanticAssessment review) {
        var questions = new HashMap<String, String>();
        input.questions().forEach(q -> questions.put(q.id(), q.text()));
        var dropped = new HashSet<String>();
        review.requirements().stream()
                .filter(r -> r.dimension() == Dimension.DEFERRED_EXAM)
                .filter(r -> {
                    String question = questions.getOrDefault(r.span().subquestionId(), "");
                    return !question.contains("缓考") && !question.contains("延期考试");
                })
                .map(Requirement::id).forEach(dropped::add);
        // 用户已声明课程类别后，另一类别的分支不再是本轮适用需求；保留它会让正确答案被误判为漏答。
        var declared = input.userConditions().get("courseType");
        Subject inapplicable = switch (declared == null ? "" : declared) {
            case "PUBLIC" -> Subject.PROFESSIONAL_ELECTIVE;
            case "PROFESSIONAL" -> Subject.PUBLIC_ELECTIVE;
            default -> null;
        };
        if (inapplicable != null) review.requirements().stream()
                .filter(r -> r.subject() == inapplicable)
                .map(Requirement::id).forEach(dropped::add);
        if (dropped.isEmpty()) return review;
        var requirements = review.requirements().stream().filter(r -> !dropped.contains(r.id())).toList();
        var bindings = review.bindings().stream().filter(b -> !dropped.contains(b.requirementId())).toList();
        // 若删光后合同会为空，保留原审核，交由既有Gate拒绝，避免宿主伪造一个空审核。
        if (requirements.isEmpty() && review.unresolvedDemands().isEmpty()) return review;
        return new SemanticAssessment(review.protocolRevision(), review.inputFingerprint(), requirements, bindings, review.unresolvedDemands());
    }

    /**
     * 绑定必须同时匹配需求维度/对象与已登记能力。核验模型偶尔会把其他维度的事实绑到主需求；
     * 这类绑定不能拖垮整题：先移除，保留其余可验证绑定，再由覆盖检查决定是否仍为 PARTIAL。
     */
    private SemanticAssessment normalizeBindings(SemanticAssessment review, List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        var requirements = new HashMap<String, Requirement>();
        review.requirements().forEach(r -> requirements.put(r.id(), r));
        var read = new HashMap<String, RuleAgentEvidenceTools.VerifiedFact>();
        facts.forEach(f -> read.put(f.id(), f));
        var kept = new ArrayList<SemanticBinding>();
        for (var binding : review.bindings()) {
            var requirement = requirements.get(binding.requirementId());
            if (requirement == null || requirement.recognition() != Recognition.IDENTIFIED)
                throw new Rejected(Code.REVIEW_BINDING_INVALID);
            var fact = read.get(binding.factId());
            if (fact == null) throw new Rejected(Code.FACT_NOT_READ);
            // 能力未登记/原文指纹漂移是硬拒绝，不能借“维度不匹配”静默移除。
            FactCapability capability = capability(fact);
            boolean dimension = binding.role() == BindingRole.CORE
                    ? capability.dimension() == requirement.dimension()
                    : capability.auxiliaryDimensions().contains(requirement.dimension());
            if (dimension && capability.subject() == requirement.subject() && capability.allowedRoles().contains(binding.role())) kept.add(binding);
        }
        if (kept.size() == review.bindings().size()) return review;
        return new SemanticAssessment(review.protocolRevision(), review.inputFingerprint(), review.requirements(), kept, review.unresolvedDemands());
    }

    /**
     * 模型对某题只报缺口、不发布任何事实时，宿主把独立核验已确认且可发布的CORE绑定补齐。
     * 完整性本应由宿主聚合；补齐只发生在空事实项，且仍逐项经过发布能力的原文指纹与when校验。
     */
    private Completed completeEmptyItems(RuleAgentRunner.Input input, RuleAgentContract.Finish finish,
                                         SemanticAssessment review, List<RuleAgentEvidenceTools.VerifiedFact> facts) {
        var publishable = new HashSet<>(catalogue.publicationFactIds(facts));
        var items = new ArrayList<RuleAgentContract.Proposal>();
        var added = new ArrayList<String>();
        for (var item : finish.items()) {
            if (!item.factIds().isEmpty() || item.issues().stream().anyMatch(i -> i.kind() == RuleAgentContract.IssueKind.PROCESSING_FAILURE)) {
                items.add(item);
                continue;
            }
            var ids = review.bindings().stream()
                    .filter(b -> b.role() == BindingRole.CORE)
                    .filter(b -> review.requirements().stream().anyMatch(r -> r.id().equals(b.requirementId())
                            && r.recognition() == Recognition.IDENTIFIED
                            && r.span().subquestionId().equals(item.subquestionId())))
                    .map(SemanticBinding::factId)
                    .filter(publishable::contains)
                    .filter(id -> facts.stream().filter(f -> f.id().equals(id)).findFirst()
                            .map(f -> f.definition().when().entrySet().stream()
                                    .allMatch(e -> e.getValue().equals(input.userConditions().get(e.getKey()))))
                            .orElse(false))
                    .distinct().toList();
            if (ids.isEmpty()) {
                items.add(item);
                continue;
            }
            added.addAll(ids);
            items.add(new RuleAgentContract.Proposal(item.subquestionId(), ids, item.issues(), item.operation()));
        }
        return new Completed(added.isEmpty() ? finish : new RuleAgentContract.Finish(items), added);
    }
    private record Completed(RuleAgentContract.Finish finish, List<String> factIds) {}

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
                var factCapability = capability(fact);
                if (matching.stream().noneMatch(b -> b.factId().equals(id))) {
                    // 已绑定主事实声明该维度为辅助维度时，同对象的补充事实是必要说明，不是无关多选。
                    var complementary = matchingCompanion(id, matching, read);
                    if (complementary == null) {
                        var diagnostic = relevanceDiagnostic(item, factCapability, read, review);
                        throw new Rejected(Code.FACT_NOT_RELEVANT, diagnostic);
                    }
                    accepted.add(complementary);
                }
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
            // 需求已识别但审核资料/能力不覆盖（实时截止时间、代办等）属资料边界，不能与“尚未理解需求”共用同一失败语义。
            boolean unsupported=unsupportedDemand(review,item.subquestionId());
            boolean issues=!item.issues().isEmpty();
            complete&=uncovered.isEmpty()&&!unresolved&&!issues;
            hasCovered|=!covered.isEmpty();
            assessments.put(item.subquestionId(),new QuestionAssessment(covered,uncovered,unresolved,unsupported,issues));
        }
        // 辅助例外不完成主需求；漏选已读事实也不能借核验器看过它而自称完整。
        return new PublicationAssessment(complete?Verdict.COMPLETE:hasCovered?Verdict.PARTIAL:Verdict.UNRESOLVED,accepted,assessments);
    }

    /** 主事实的辅助维度若声明了该事实维度，则它属于同一条款的必要补充说明，可直接允许发布。 */
    private SemanticBinding matchingCompanion(String id, List<SemanticBinding> matching,
                                               Map<String,RuleAgentEvidenceTools.VerifiedFact> read) {
        var fact = read.get(id);
        if (fact == null) return null;
        var capability = capability(fact);
        for (var binding : matching) {
            var bound = read.get(binding.factId());
            if (bound == null) continue;
            var boundCapability = capability(bound);
            if (boundCapability.subject() == capability.subject()
                    && boundCapability.auxiliaryDimensions().contains(capability.dimension()))
                return new SemanticBinding(binding.requirementId(), id, BindingRole.CORE);
        }
        return null;
    }

    private RelevanceDiagnostic relevanceDiagnostic(RuleAgentContract.Proposal item, FactCapability rejected,
            Map<String,RuleAgentEvidenceTools.VerifiedFact> read, SemanticAssessment review) {        var rows = new ArrayList<RequirementDiagnostic>();
        for (int i = 0; i < review.requirements().size(); i++) {
            var requirement = review.requirements().get(i);
            if (!requirement.span().subquestionId().equals(item.subquestionId())) continue;
            var bindings = review.bindings().stream().filter(b -> b.requirementId().equals(requirement.id()))
                    .map(b -> new BindingDiagnostic(b.factId(), b.role())).toList();
            rows.add(new RequirementDiagnostic(i + 1, requirement.dimension(), requirement.subject(), requirement.recognition(), bindings));
        }
        // 后续提议ID尚未逐项校验，只留已读目录中确实存在的值。
        var proposed = item.factIds().stream().filter(read::containsKey).toList();
        return new RelevanceDiagnostic(item.subquestionId(), proposed, rejected, rows);
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
            assessments.put(q,new QuestionAssessment(covered,uncovered,false,false,false));
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
    /**
     * 资料缺口只适用于“已被理解的需求”。若只看是否存在 UNSUPPORTED_DIMENSION，
     * 同一子问题里未识别的需求会被并存的资料缺口掩盖，最终发布成正常的资料不足；
     * 因此每个未识别需求都必须由自己的 UNSUPPORTED_DIMENSION 需求区间覆盖，其余需求则必须已识别。
     */
    public static boolean unsupportedDemand(SemanticAssessment review,String subquestionId) {
        var demands=review.unresolvedDemands().stream().filter(d->d.span().subquestionId().equals(subquestionId)).toList();
        if(demands.stream().noneMatch(d->d.reason()==UnresolvedReason.UNSUPPORTED_DIMENSION))return false;
        var gaps=demands.stream().filter(d->d.reason()==UnresolvedReason.UNSUPPORTED_DIMENSION).map(UnresolvedDemand::span).toList();
        return review.requirements().stream().filter(r->r.span().subquestionId().equals(subquestionId))
            .allMatch(r->r.recognition()==Recognition.IDENTIFIED||gaps.stream().anyMatch(gap->overlaps(gap,r.span())));
    }
    private static boolean overlaps(DemandSpan left,DemandSpan right) {
        return left.startInclusive()<right.endExclusive()&&right.startInclusive()<left.endExclusive();
    }
    private static void span(DemandSpan span,Map<String,RuleAgentRunner.Question> questions) {
        var question=questions.get(span.subquestionId());
        require(question!=null&&span.endExclusive()<=question.text().length());
    }
}
