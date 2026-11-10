package com.zhku.knowledge.ruleagent;
import com.zhku.knowledge.rulecheck.*;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RuleAgentRunner.*;

/** 模型只提议ID；正文、澄清及依据关系由宿主按fresh审核范围生成。 */
public final class RuleResultPublisher {
    record Published(TaskStatus status,ResultEnvelope result,UUID continuation){}
    private final RuleActionBroker broker;private final Input input;private final UUID run;
    private final LinkedHashMap<String,Evidence> proofs=new LinkedHashMap<>();private final List<EvidenceRelation> relations=new ArrayList<>();
    private final RulePublicationContract.PublicationAssessment review;
    RuleResultPublisher(RuleActionBroker broker,Input input,UUID run){this(broker,input,run,null);}
    RuleResultPublisher(RuleActionBroker broker,Input input,UUID run,RulePublicationContract.PublicationAssessment review){
        this.broker=broker;
        this.input=input;
        this.run=run;
        this.review=review;
    }
    /** 核验前只检查确定性条件/权限/原文，不让旧关键词门禁提前否决未知表达。 */
    void precheckFinish(Finish finish){
        for(var item:finish.items())for(String id:item.factIds()){
            var fact=broker.fact(item.subquestionId(),id);
            var current=entry(fact);
            checkApplicability(item.subquestionId(),current,new LinkedHashMap<>());
            if(!fact.definition().when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey())))
                ||!current.template().conflicts().isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            broker.proofs(item.subquestionId(),fact,input);
        }
    }
    /** 追问的目标条件可以缺失，其余条件仍须满足；不能要求缺失条件先成立。 */
    void precheckAsk(Ask ask){
        if(input.userConditions().containsKey(ask.conditionId())||ask.supportingFactIds().isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
        for(String id:ask.supportingFactIds()){
            var fact=broker.fact(ask.subquestionId(),id);
            var current=entry(fact);
            checkApplicability(ask.subquestionId(),current,new LinkedHashMap<>());
            if(current.template().conditions().stream().noneMatch(c->c.key().equals(ask.conditionId()))
                ||!fact.definition().when().containsKey(ask.conditionId())
                ||!fact.definition().when().entrySet().stream().allMatch(e->e.getKey().equals(ask.conditionId())||e.getValue().equals(input.userConditions().get(e.getKey())))
                ||!current.template().conflicts().isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            broker.proofs(ask.subquestionId(),fact,input);
        }
    }
    Published finish(Finish finish){
        if(!new HashSet<>(finish.items().stream().map(Proposal::subquestionId).toList()).equals(new HashSet<>(input.questions().stream().map(Question::id).toList())))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
        var items=new ArrayList<Subquestion>();
        for(var p:finish.items()){
            var claims=new ArrayList<Claim>();var issues=new LinkedHashMap<ReasonCode,Issue>();
            if(review!=null){
                var coverage=review.questions().get(p.subquestionId());
                if(coverage==null)throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                // 需求已识别但资料/能力不覆盖（实时截止时间、代办）是正常的资料终态；只有真正没理解需求才算处理失败。
                if(coverage.demandUnresolved())add(issues,coverage.unsupportedDemand()?ReasonCode.UNSUPPORTED_DEMAND:ReasonCode.QUESTION_UNDERSTANDING_UNRESOLVED);
                else if(!coverage.uncoveredRequirementIds().isEmpty())add(issues,ReasonCode.ANSWER_COVERAGE_INCOMPLETE);
            }
            for(String id:p.factIds()){
                if(review!=null&&!review.acceptedBindings().stream().anyMatch(b->b.factId().equals(id)
                    &&(review.questions().get(p.subquestionId()).coveredRequirementIds().contains(b.requirementId())
                    ||review.questions().get(p.subquestionId()).uncoveredRequirementIds().contains(b.requirementId()))))
                    throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                var f=broker.fact(p.subquestionId(),id);var entry=entry(f);checkScope(p.subquestionId(),entry,issues);
                if(!f.definition().when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey()))))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                if(!f.definition().independentlyAnswerable()&&entry.template().facts().stream().filter(other->other.when().entrySet().stream().allMatch(e->!input.userConditions().containsKey(e.getKey())||e.getValue().equals(input.userConditions().get(e.getKey())))).anyMatch(other->!other.when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey())))||!p.factIds().contains(entry.template().ruleId()+":"+other.id())))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                if(!entry.template().conflicts().isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                String text=f.definition().text();if(p.operation()!=null)text=operation(p,f,entry);
                var claim=new Claim("c"+claims.size(),id,text,f.definition().when(),ConditionSource.USER_DECLARED);claims.add(claim);
                relate(p.subquestionId(),claim.id(),EvidenceRole.CLAIM,broker.proofs(p.subquestionId(),f,input));
                if(!entry.template().evidenceGaps().isEmpty())add(issues,ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT);
                for(var gap:entry.template().conditionalEvidenceGaps())if(gap.when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey()))))add(issues,ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT);
                var option=RuleCheckClauseOperations.option(entry);
                if(option.operands().stream().anyMatch(o->Set.of("GRADE_WEIGHTS","CURRENT_DEADLINE","PUBLICATION_DATE").contains(o)))add(issues,ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT);
            }
            for(var proposed:p.issues()){
                if(!allowedIssue(p.subquestionId(),proposed.reasonCode()))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);add(issues,proposed.reasonCode());
            }
            if(claims.isEmpty()&&issues.isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            items.add(new Subquestion(p.subquestionId(),claims.isEmpty()?Coverage.NONE:issues.isEmpty()?Coverage.COMPLETE:Coverage.PARTIAL,claims,List.copyOf(issues.values()),List.of()));
        }
        return build(items,items.stream().flatMap(q->q.issues().stream()).anyMatch(i->i.kind()==IssueKind.PROCESSING_FAILURE)?TaskStatus.FAILED:TaskStatus.RESOLVED,null);
    }
    Published ask(Ask ask){
        if(review!=null)precheckAsk(ask);
        var supporting=ask.supportingFactIds().stream().map(id->broker.fact(ask.subquestionId(),id)).toList();
        if(supporting.isEmpty()||input.userConditions().containsKey(ask.conditionId()))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
        RuleCheckRuleTemplate.Condition needed=null;
        for(var f:supporting){var entry=entry(f);checkScope(ask.subquestionId(),entry,new LinkedHashMap<>());
            boolean required=entry.template().facts().stream().anyMatch(candidate->candidate.when().containsKey(ask.conditionId())&&candidate.when().entrySet().stream().allMatch(e->!input.userConditions().containsKey(e.getKey())||e.getValue().equals(input.userConditions().get(e.getKey()))))
                ||entry.template().conditionalEvidenceGaps().stream().anyMatch(g->g.when().containsKey(ask.conditionId())&&g.when().entrySet().stream().allMatch(e->!input.userConditions().containsKey(e.getKey())||e.getValue().equals(input.userConditions().get(e.getKey()))));
            if(required)needed=entry.template().conditions().stream().filter(c->c.key().equals(ask.conditionId())).findFirst().orElse(null);
        }
        if(needed==null)throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
        var clarification=new Clarification(needed.key(),needed.question(),needed.allowedValues());
        for(var f:supporting)relate(ask.subquestionId(),needed.key(),EvidenceRole.CLARIFICATION,broker.proofs(ask.subquestionId(),f,input));
        var items=new ArrayList<Subquestion>();
        for(var q:input.questions()){
            var claims=new ArrayList<Claim>();var issues=new LinkedHashMap<ReasonCode,Issue>();
            for(var f:broker.facts(q.id())){
                if(!f.definition().independentlyAnswerable()||!f.definition().when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey()))))continue;
                if(review!=null&&!review.acceptedBindings().stream().anyMatch(b->b.factId().equals(f.id())
                    &&b.role()==RulePublicationContract.BindingRole.CORE&&review.questions().get(q.id()).coveredRequirementIds().contains(b.requirementId())))continue;
                var entry=entry(f);try{checkScope(q.id(),entry,issues);}catch(Rejected unrelated){continue;}
                var claim=new Claim("c"+claims.size(),f.id(),f.definition().text(),f.definition().when(),ConditionSource.USER_DECLARED);claims.add(claim);relate(q.id(),claim.id(),EvidenceRole.CLAIM,broker.proofs(q.id(),f,input));
            }
            boolean waiting=q.id().equals(ask.subquestionId());if(waiting)add(issues,ReasonCode.MISSING_CONDITION);
            // 未处理的其他子问题不能虚构成资料缺失；先读取其证据，再提出本轮唯一必要追问。
            if(!waiting&&claims.isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            items.add(new Subquestion(q.id(),claims.isEmpty()?Coverage.NONE:issues.isEmpty()?Coverage.COMPLETE:Coverage.PARTIAL,claims,List.copyOf(issues.values()),waiting?List.of(clarification):List.of()));
        }
        return build(items,TaskStatus.WAITING_USER,UUID.randomUUID());
    }
    Published failed(ReasonCode reason){
        // 目录本身故障时也必须能收敛运行；这里不携带历史主张，不能借失败兜底发布过期事实。
        String version="unavailable";try{version=broker.tools.catalogueVersion();}catch(RuntimeException ignored){}
        return new Published(Set.of(ReasonCode.CANCELLED,ReasonCode.BUDGET_EXCEEDED,ReasonCode.NO_PROGRESS).contains(reason)?TaskStatus.STOPPED:TaskStatus.FAILED,
            new ResultEnvelope(input.version(),run,version,broker.tools.referenceRevision(),Coverage.NONE,input.questions().stream().map(q->new Subquestion(q.id(),Coverage.NONE,List.of(),List.of(issue(reason)),List.of())).toList(),List.of(),List.of()),null);
    }
    private RuleCheckApplicationService.CatalogEntry entry(RuleAgentEvidenceTools.VerifiedFact fact){var e=broker.tools.reviewedEntry(fact.ruleId()).orElseThrow(()->new Rejected(ReasonCode.EVIDENCE_CHANGED));if(!e.template().facts().contains(fact.definition()))throw new Rejected(ReasonCode.EVIDENCE_CHANGED);return e;}
    private void checkScope(String q,RuleCheckApplicationService.CatalogEntry entry,Map<ReasonCode,Issue> issues){
        String text=input.questions().stream().filter(x->x.id().equals(q)).findFirst().orElseThrow().text();
        // 只有通过独立Gate的提议免旧主题否决；旧工程路径保持原边界。
        if(review==null){var topic=new RuleCheckTopicGuard().assess(text,entry.topicCoverage());if(!topic.evaluated()||topic.blocked())throw new Rejected(ReasonCode.MATERIAL_SCOPE_MISMATCH);}
        checkApplicability(q,entry,issues);
    }
    private void checkApplicability(String q,RuleCheckApplicationService.CatalogEntry entry,Map<ReasonCode,Issue> issues){
        String text=input.questions().stream().filter(x->x.id().equals(q)).findFirst().orElseThrow().text();
        var scope=new RuleCheckApplicabilityGuard().assess(text,entry.coverage(),entry.template().referenceRevision());
        if(!entry.coverage().audiences().containsAll(scope.requestedAudiences())||scope.timeDemand()==RuleCheckApplicabilityGuard.TimeDemand.OTHER_PERIOD)throw new Rejected(ReasonCode.MATERIAL_SCOPE_MISMATCH);
        if(scope.timeDemand()==RuleCheckApplicabilityGuard.TimeDemand.CURRENT)add(issues,ReasonCode.CURRENT_APPLICABILITY_UNKNOWN);
        if(scope.personalRecordsRequired())add(issues,ReasonCode.PERSONAL_RECORD_NOT_AVAILABLE);
        if(scope.actualOfferingRequired())add(issues,ReasonCode.CURRENT_NOTICE_MISSING);
    }
    private boolean allowedIssue(String q,ReasonCode reason){
        // 核验器已判定该子问题属“资料/能力不覆盖”时，允许模型如实申报该数据缺口，而不是被迫编造事实或改写为其它原因。
        if(reason==ReasonCode.UNSUPPORTED_DEMAND)return review!=null&&review.questions().containsKey(q)&&review.questions().get(q).unsupportedDemand();
        if(broker.observations(q).stream().anyMatch(o->o.reasonCode()==reason))return true;
        if(reason==ReasonCode.REVIEWED_FACT_COVERAGE_GAP)return broker.observations(q).stream().anyMatch(o->o.dataLevel()==DataLevel.VERIFIED_EVIDENCE);
        if(reason==ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT)return broker.facts(q).stream().map(this::entry).anyMatch(e->!e.template().evidenceGaps().isEmpty()||RuleCheckClauseOperations.option(e).operands().stream().anyMatch(o->Set.of("GRADE_WEIGHTS","CURRENT_DEADLINE","PUBLICATION_DATE").contains(o)));
        return false;
    }
    private String operation(Proposal p,RuleAgentEvidenceTools.VerifiedFact fact,RuleCheckApplicationService.CatalogEntry entry){
        var op=p.operation();String question=input.questions().stream().filter(q->q.id().equals(p.subquestionId())).findFirst().orElseThrow().text();
        var span=op.sourceSpan();var value=RuleCheckClauseOperations.comparisonValue(question);
        // 首期只支持已核验缺课条款的严格百分比阈值；其他合法格式仍不能自动授予运算权限。
        if(!op.factOperandId().equals(fact.id()+":ABSENCE_PERCENT")||op.comparison()!=Comparison.GT||!RuleCheckClauseOperations.option(entry).operands().contains("ABSENCE_PERCENT")||span.endExclusive()>input.question().length()||!input.question().substring(span.startInclusive(),span.endExclusive()).equals(op.declaredValue())||value.isEmpty()||new java.math.BigDecimal(value.get()).compareTo(new java.math.BigDecimal(op.declaredValue()))!=0)throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
        return RuleCheckClauseOperations.explanation(new RuleCheckTaskPlan.Task(entry.template().ruleId(),RuleCheckTaskPlan.Action.COMPARE_THRESHOLD,"ABSENCE_PERCENT",op.declaredValue()));
    }
    private void relate(String q,String target,EvidenceRole role,List<Evidence> quotes){for(var e:quotes){proofs.putIfAbsent(e.id(),e);var relation=new EvidenceRelation(e.id(),q,role,target);if(!relations.contains(relation))relations.add(relation);}}
    private Published build(List<Subquestion> items,TaskStatus status,UUID token){var coverage=items.stream().allMatch(q->q.coverage()==Coverage.COMPLETE)?Coverage.COMPLETE:items.stream().anyMatch(q->!q.claims().isEmpty())?Coverage.PARTIAL:Coverage.NONE;return new Published(status,new ResultEnvelope(input.version(),run,broker.tools.catalogueVersion(),broker.tools.referenceRevision(),coverage,items,List.copyOf(proofs.values()),relations),token);}
    private static void add(Map<ReasonCode,Issue> issues,ReasonCode r){issues.putIfAbsent(r,issue(r));}
    static Issue issue(ReasonCode r){
        String message=switch(r){case RETRIEVAL_NO_MATCH->"本次检索未找到可用依据，不能据此断定资料没有规定。";case MISSING_CONDITION->"还缺影响结论的必要条件。";case DETAIL_NOT_IN_REVIEWED_TEXT,REVIEWED_FACT_COVERAGE_GAP->"已取得的审核资料不足以支持完整结论。";case UNSUPPORTED_DEMAND->"已审核资料没有覆盖该需求（例如当期截止时间或代办操作），本轮不作结论，也不代替你办理。";case PERSONAL_RECORD_NOT_AVAILABLE->"可以解释一般规则，尚未核验个人记录与资格。";case CURRENT_APPLICABILITY_UNKNOWN,CURRENT_NOTICE_MISSING->"这里只能解释历史文本，当期安排或现行适用性尚未核实。";case QUESTION_UNDERSTANDING_UNRESOLVED->"已确认的部分保留；其余需求的理解或核验尚未完成。";case INVALID_MODEL_RESPONSE->"本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。";case ANSWER_COVERAGE_INCOMPLETE->"已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。";case CANCELLED->"本轮已取消。";case BUDGET_EXCEEDED,NO_PROGRESS->"本轮达到执行边界，未完成的部分保留未知。";default->"本轮处理未完成，不能将处理失败当作资料缺失。";};
        // 资料不足与能力边界必须给出可执行下一步；代办类需求还要说明系统不代替学生操作，避免误以为已办成。
        String nextStep=switch(r){case MISSING_CONDITION->"请补充下方必要条件。";case UNSUPPORTED_DEMAND->"请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。";default->"可核对原文或重新发起查证；本轮不作无依据结论。";};
        return new Issue("i-"+r.name(),r.kind,r,message,nextStep);
    }
}
