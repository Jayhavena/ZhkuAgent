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
        requireQuestionCoverage(finish);
        for (var item : finish.items()) {
            for (var issue : item.issues()) {
                // UNSUPPORTED_DEMAND须等独立核验确认当前需求不受支持。
                if (issue.reasonCode() != ReasonCode.UNSUPPORTED_DEMAND && !allowedIssue(item.subquestionId(), issue.reasonCode())) {
                    throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                }
            }
        }
        for(var item:finish.items())for(String id:item.factIds()){
            var fact=broker.fact(item.subquestionId(),id);
            var current=entry(fact);
            checkApplicability(item.subquestionId(),current,new LinkedHashMap<>());
            if(!fact.definition().when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey())))
                ||!current.template().conflicts().isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            requireCompanionFacts(item, fact, current);
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
        requireQuestionCoverage(finish);
        var items=new ArrayList<Subquestion>();
        for(var p:finish.items()){
            var claims=new ArrayList<Claim>();var issues=new LinkedHashMap<ReasonCode,Issue>();
            String question=input.questions().stream().filter(q->q.id().equals(p.subquestionId())).findFirst().orElseThrow().text();
            // 范围/受众/当期缺口由宿主按问题边界发布，不能把“研究生/2026级/今晚截止/奖学金”误报成资料不存在。
            // 无事实的纯缺口提议仍由核验的 UNSUPPORTED_DEMAND 语义处理，避免把“尚未理解”洗成正常资料不足。
            var scopeGap=(p.factIds().size()>0||outOfCorpus(question))?scopeGap(p.subquestionId(),question):null;
            if(scopeGap!=null)add(issues,scopeGap.reason(),scopeGap.message(),scopeGap.nextStep());
            if(review!=null){
                var coverage=review.questions().get(p.subquestionId());
                if(coverage==null)throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                // 需求已识别但资料/能力不覆盖（实时截止时间、代办）是正常的资料终态；只有真正没理解需求才算处理失败。
                // 个人记录缺口不能借用“当期通知/代办”文案；同一缺口在发布事实时也会由适用性守卫补入，按原因去重。
                if(coverage.demandUnresolved()){
                    if(scopeGap!=null&&coverage.unsupportedDemand()){
                        if(scopeGap.reason()!=ReasonCode.PERSONAL_RECORD_NOT_AVAILABLE&&personalRecordRequired(p.subquestionId()))
                            add(issues,ReasonCode.PERSONAL_RECORD_NOT_AVAILABLE);
                    }else add(issues,coverage.unsupportedDemand()
                        ?(personalRecordRequired(p.subquestionId())?ReasonCode.PERSONAL_RECORD_NOT_AVAILABLE:ReasonCode.UNSUPPORTED_DEMAND)
                        :ReasonCode.QUESTION_UNDERSTANDING_UNRESOLVED);
                }
                else if(!coverage.uncoveredRequirementIds().isEmpty())add(issues,ReasonCode.ANSWER_COVERAGE_INCOMPLETE);
            }
            var publishedTexts=p.factIds().stream().map(id->broker.fact(p.subquestionId(),id).definition().text()).toList();
            var hoursBoundary=RuleAnswerBoundaries.hoursComparison(question,publishedTexts);
            boolean hoursBoundaryApplied=false;
            for(String id:p.factIds()){
                if(review!=null&&!review.acceptedBindings().stream().anyMatch(b->b.factId().equals(id)
                    &&(review.questions().get(p.subquestionId()).coveredRequirementIds().contains(b.requirementId())
                    ||review.questions().get(p.subquestionId()).uncoveredRequirementIds().contains(b.requirementId()))))
                    throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                var f=broker.fact(p.subquestionId(),id);var entry=entry(f);
                // 研究生/其他对象问题只能保留正文明确限定“本科”的条文；无对象限定的规则不能借范围缺口继续发布。
                if(audienceOutOfScope(p.subquestionId(),entry)&&!f.definition().text().contains("本科"))
                    throw new Rejected(ReasonCode.MATERIAL_SCOPE_MISMATCH);
                checkScope(p.subquestionId(),entry,issues);
                if(!f.definition().when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey()))))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                requireCompanionFacts(p,f,entry);
                if(!entry.template().conflicts().isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
                String text=f.definition().text();if(p.operation()!=null)text=operation(p,f,entry);
                if(review!=null)text=historicalText(entry.template().referenceRevision(),text);
                // 数值比较只追加一次，并挂在原文上限事实后；不能把派生说明伪装成另一条规则事实。
                if(!hoursBoundaryApplied&&RuleAnswerBoundaries.isHoursLimitFact(f.definition().text())){
                    text=RuleAnswerBoundaries.append(text,hoursBoundary);hoursBoundaryApplied=true;
                }
                text=RuleAnswerBoundaries.append(text,RuleAnswerBoundaries.applicationBoundary(f.definition().text()));
                text=RuleAnswerBoundaries.append(text,RuleAnswerBoundaries.planRelativeBoundary(f.definition().text()));
                var claim=new Claim("c"+claims.size(),id,text,f.definition().when(),ConditionSource.USER_DECLARED);claims.add(claim);
                relate(p.subquestionId(),claim.id(),EvidenceRole.CLAIM,broker.proofs(p.subquestionId(),f,input));
                if(!entry.template().evidenceGaps().isEmpty()
                    &&!issues.containsKey(ReasonCode.PERSONAL_RECORD_NOT_AVAILABLE)
                    &&!issues.containsKey(ReasonCode.CURRENT_NOTICE_MISSING))
                    add(issues,ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT);
                for(var gap:entry.template().conditionalEvidenceGaps())if(gap.when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey()))))add(issues,ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT);
                var option=RuleCheckClauseOperations.option(entry);
                if(option.operands().stream().anyMatch(o->Set.of("GRADE_WEIGHTS","CURRENT_DEADLINE","PUBLICATION_DATE").contains(o)))add(issues,ReasonCode.DETAIL_NOT_IN_REVIEWED_TEXT);
            }
            for(var proposed:p.issues()){
                // 已由更具体的范围/个人缺口表达时，不再叠加通用“资料没有覆盖”文案。
                if(proposed.reasonCode()==ReasonCode.UNSUPPORTED_DEMAND
                    &&(scopeGap!=null||personalRecordRequired(p.subquestionId())))continue;
                if(!allowedIssue(p.subquestionId(),proposed.reasonCode()))throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);add(issues,proposed.reasonCode());
            }
            if(claims.isEmpty()&&issues.isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            items.add(new Subquestion(p.subquestionId(),claims.isEmpty()?Coverage.NONE:issues.isEmpty()?Coverage.COMPLETE:Coverage.PARTIAL,claims,List.copyOf(issues.values()),List.of()));
        }
        return build(items,items.stream().flatMap(q->q.issues().stream()).anyMatch(i->i.kind()==IssueKind.PROCESSING_FAILURE)?TaskStatus.FAILED:TaskStatus.RESOLVED,null);
    }
    private void requireQuestionCoverage(Finish finish) {
        var proposed = new HashSet<>(finish.items().stream().map(Proposal::subquestionId).toList());
        var expected = new HashSet<>(input.questions().stream().map(Question::id).toList());
        if (!proposed.equals(expected)) throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
    }
    private void requireCompanionFacts(Proposal proposal, RuleAgentEvidenceTools.VerifiedFact fact,
                                       RuleCheckApplicationService.CatalogEntry entry) {
        if (fact.definition().independentlyAnswerable()) return;
        for (var companion : entry.template().facts()) {
            var conditions = companion.when().entrySet();
            boolean compatible = conditions.stream().noneMatch(e -> input.userConditions().containsKey(e.getKey())
                    && !e.getValue().equals(input.userConditions().get(e.getKey())));
            if (!compatible) continue;
            boolean confirmed = conditions.stream().allMatch(e -> e.getValue().equals(input.userConditions().get(e.getKey())));
            if (!confirmed || !proposal.factIds().contains(entry.template().ruleId() + ":" + companion.id())) {
                throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            }
        }
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
        String question=needed.question();
        // 仅在独立核验已确认必要性且两分支均有已读依据时解释差异，不回显模型自由理由。
        if(review!=null&&needed.key().equals("courseType")&&supporting.stream().map(f->f.definition().when().get("courseType")).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of("PUBLIC","PROFESSIONAL")))
            question="公共选修课和专业选修课的处理规则不同，需要先确认课程类别。"+question;
        var clarification=new Clarification(needed.key(),question,needed.allowedValues());
        for(var f:supporting)relate(ask.subquestionId(),needed.key(),EvidenceRole.CLARIFICATION,broker.proofs(ask.subquestionId(),f,input));
        var items=new ArrayList<Subquestion>();
        for(var q:input.questions()){
            var claims=new ArrayList<Claim>();var issues=new LinkedHashMap<ReasonCode,Issue>();
            for(var f:broker.facts(q.id())){
                if(!f.definition().independentlyAnswerable()||!f.definition().when().entrySet().stream().allMatch(e->e.getValue().equals(input.userConditions().get(e.getKey()))))continue;
                if(review!=null&&!review.acceptedBindings().stream().anyMatch(b->b.factId().equals(f.id())
                    &&b.role()==RulePublicationContract.BindingRole.CORE&&review.questions().get(q.id()).coveredRequirementIds().contains(b.requirementId())))continue;
                var entry=entry(f);try{checkScope(q.id(),entry,issues);}catch(Rejected unrelated){continue;}
                String text=review==null?f.definition().text():historicalText(entry.template().referenceRevision(),f.definition().text());
                var claim=new Claim("c"+claims.size(),f.id(),text,f.definition().when(),ConditionSource.USER_DECLARED);claims.add(claim);relate(q.id(),claim.id(),EvidenceRole.CLAIM,broker.proofs(q.id(),f,input));
            }
            boolean waiting=q.id().equals(ask.subquestionId());if(waiting)add(issues,ReasonCode.MISSING_CONDITION);
            // 未处理的其他子问题不能虚构成资料缺失；先读取其证据，再提出本轮唯一必要追问。
            if(!waiting&&claims.isEmpty())throw new Rejected(ReasonCode.INVALID_MODEL_RESPONSE);
            items.add(new Subquestion(q.id(),claims.isEmpty()?Coverage.NONE:issues.isEmpty()?Coverage.COMPLETE:Coverage.PARTIAL,claims,List.copyOf(issues.values()),waiting?List.of(clarification):List.of()));
        }
        return build(items,TaskStatus.WAITING_USER,UUID.randomUUID());
    }
    static String historicalText(String revision,String text){return "依据"+revision+"历史文本："+text;}
    Published failed(ReasonCode reason){
        // 目录本身故障时也必须能收敛运行；这里不携带历史主张，不能借失败兜底发布过期事实。
        String version="unavailable";try{version=broker.tools.catalogueVersion();}catch(RuntimeException ignored){}
        return new Published(Set.of(ReasonCode.CANCELLED,ReasonCode.BUDGET_EXCEEDED,ReasonCode.NO_PROGRESS).contains(reason)?TaskStatus.STOPPED:TaskStatus.FAILED,
            new ResultEnvelope(input.version(),run,version,broker.tools.referenceRevision(),Coverage.NONE,input.questions().stream().map(q->new Subquestion(q.id(),Coverage.NONE,List.of(),List.of(issue(reason)),List.of())).toList(),List.of(),List.of()),null);
    }
    private RuleCheckApplicationService.CatalogEntry entry(RuleAgentEvidenceTools.VerifiedFact fact){var e=broker.tools.reviewedEntry(fact.ruleId()).orElseThrow(()->new Rejected(ReasonCode.EVIDENCE_CHANGED));if(!e.template().facts().contains(fact.definition()))throw new Rejected(ReasonCode.EVIDENCE_CHANGED);return e;}
    /** 仅按已读事实的审核范围判断是否属于个人记录缺口；不读取模型自由理由。 */
    private boolean personalRecordRequired(String q){
        String question=input.questions().stream().filter(x->x.id().equals(q)).findFirst().orElseThrow().text();
        for(var fact:broker.facts(q)){
            try{
                var entry=entry(fact);
                if(new RuleCheckApplicabilityGuard().assess(question,entry.coverage(),entry.template().referenceRevision()).personalRecordsRequired())return true;
            }catch(RuntimeException ignored){/* 失效事实由发布守卫另行拒绝，不能在此扩大缺口。 */}
        }
        return false;
    }
    private void checkScope(String q,RuleCheckApplicationService.CatalogEntry entry,Map<ReasonCode,Issue> issues){
        String text=input.questions().stream().filter(x->x.id().equals(q)).findFirst().orElseThrow().text();
        // 只有通过独立Gate的提议免旧主题否决；旧工程路径保持原边界。
        if(review==null){var topic=new RuleCheckTopicGuard().assess(text,entry.topicCoverage());if(!topic.evaluated()||topic.blocked())throw new Rejected(ReasonCode.MATERIAL_SCOPE_MISMATCH);}
        checkApplicability(q,entry,issues);
    }
    private void checkApplicability(String q,RuleCheckApplicationService.CatalogEntry entry,Map<ReasonCode,Issue> issues){
        String text=input.questions().stream().filter(x->x.id().equals(q)).findFirst().orElseThrow().text();
        var scope=new RuleCheckApplicabilityGuard().assess(text,entry.coverage(),entry.template().referenceRevision());
        // 范围/受众/年份不匹配时保留可解释的限定，而不是把整题打成处理失败。
        if(!entry.coverage().audiences().containsAll(scope.requestedAudiences()))add(issues,ReasonCode.MATERIAL_SCOPE_MISMATCH);
        if(scope.timeDemand()==RuleCheckApplicabilityGuard.TimeDemand.OTHER_PERIOD)add(issues,ReasonCode.CURRENT_APPLICABILITY_UNKNOWN);
        if(scope.timeDemand()==RuleCheckApplicabilityGuard.TimeDemand.CURRENT){
            if(scope.personalRecordsRequired()){
                if(!historicalBasis(text)||asksCurrentValidity(text))add(issues,ReasonCode.CURRENT_APPLICABILITY_UNKNOWN);
            }
            else if(noticeDemand(text))add(issues,ReasonCode.CURRENT_NOTICE_MISSING);
            else add(issues,ReasonCode.CURRENT_APPLICABILITY_UNKNOWN);
        }
        if(scope.personalRecordsRequired())add(issues,ReasonCode.PERSONAL_RECORD_NOT_AVAILABLE);
        if(scope.actualOfferingRequired())add(issues,ReasonCode.CURRENT_NOTICE_MISSING);
    }

    private boolean audienceOutOfScope(String q,RuleCheckApplicationService.CatalogEntry entry){
        String text=input.questions().stream().filter(x->x.id().equals(q)).findFirst().orElseThrow().text();
        var scope=new RuleCheckApplicabilityGuard().assess(text,entry.coverage(),entry.template().referenceRevision());
        return (scope.requestedAudiences().contains(RuleCheckApplicabilityGuard.Audience.POSTGRADUATE)
            ||scope.requestedAudiences().contains(RuleCheckApplicabilityGuard.Audience.OTHER))
            &&!entry.coverage().audiences().containsAll(scope.requestedAudiences());
    }

    private static boolean noticeDemand(String question){
        return question.contains("截止")||question.contains("补选")||question.contains("入口")
            ||question.contains("开放")||question.contains("几点")||question.contains("今晚")
            ||question.contains("报名")||question.contains("选课系统")||question.contains("什么时候");
    }

    private static boolean historicalBasis(String question){
        return question.contains("这份手册")||question.contains("本手册")
            ||question.contains("按手册")||question.contains("根据手册")||question.contains("手册里的");
    }

    private static boolean asksCurrentValidity(String question){
        return question.contains("有效")||question.contains("废止")||question.contains("现行")
            ||question.contains("最新")||question.contains("失效");
    }

    private ScopeGap scopeGap(String q,String question){
        if(outOfCorpus(question))return new ScopeGap(ReasonCode.MATERIAL_SCOPE_MISMATCH,
            "当前评测语料只覆盖2025.9.2选修课规则，不含奖学金、医保、住宿或餐饮等评定依据。",
            "请查找对应的正式管理办法及当年度通知；本系统不以现有语料推断这些结果。");
        var scope=scopeAssessment(q,question);
        if(scope.requestedAudiences().contains(RuleCheckApplicabilityGuard.Audience.POSTGRADUATE)
            ||scope.requestedAudiences().contains(RuleCheckApplicabilityGuard.Audience.OTHER))
            return new ScopeGap(ReasonCode.MATERIAL_SCOPE_MISMATCH,
                "本手册只核验全日制普通本科学生的选修规则；没有提供研究生或其他培养类别的规则，不能把本科条文直接推广。",
                "请核对研究生培养方案或对应规定，不要以本科条文代替研究生规则。");
        if(scope.timeDemand()==RuleCheckApplicabilityGuard.TimeDemand.OTHER_PERIOD)
            return new ScopeGap(ReasonCode.CURRENT_APPLICABILITY_UNKNOWN,
                "本次只核验2025.9.2历史文本；2026级或当期是否适用尚未由这份资料确认。",
                "请核对现行手册、当期通知或对应培养方案，不能把历史文本直接当作当前有效规则。");
        if(scope.actualOfferingRequired())
            return new ScopeGap(ReasonCode.CURRENT_NOTICE_MISSING,
                "条文只给一般人数要求，不能证明某门课实际一定开课；实际开班结果需查当期教务通知。",
                "请核对当期教务处开课计划或课程状态，不以历史条文替具体课程作事实认定。");
        if(scope.timeDemand()==RuleCheckApplicabilityGuard.TimeDemand.CURRENT&&!scope.personalRecordsRequired()){
            if(noticeDemand(question))return new ScopeGap(ReasonCode.CURRENT_NOTICE_MISSING,
                "所引2025.9.2历史文本没有给出本学期具体截止时间或入口状态；不能把历史规则当作当期通知，也不代办补选。",
                "请查看教务处当期通知或官方系统，必要时咨询辅导员；本系统不代办报名、补选、退选或缴费。");
            return new ScopeGap(ReasonCode.CURRENT_APPLICABILITY_UNKNOWN,
                "这里只能解释2025.9.2历史文本；当期安排或现行适用性尚未核实。",
                "请核对当期通知或现行手册；本系统不以历史文本断言当前仍然有效。");
        }
        if(personalResultQuestion(question))
            return new ScopeGap(ReasonCode.PERSONAL_RECORD_NOT_AVAILABLE,
                "本系统没有核验你的选课记录、考核记录和成绩状态，不能替你认定这次成绩是否有效。",
                "请以教务系统个人记录和任课教师/教务处确认为准；本系统只解释已审核规则原文。");
        return null;
    }

    private RuleCheckApplicabilityGuard.Assessment scopeAssessment(String q,String question){
        for(var fact:broker.facts(q)){
            try{
                var entry=entry(fact);
                return new RuleCheckApplicabilityGuard().assess(question,entry.coverage(),entry.template().referenceRevision());
            }catch(RuntimeException ignored){/* 失效事实由发布守卫另行拒绝；这里只做问题边界判断。 */}
        }
        return new RuleCheckApplicabilityGuard().assess(question,
            new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),"2025.9.2");
    }

    private static boolean outOfCorpus(String question){
        return question.contains("奖学金")||question.contains("医保")||question.contains("报销")
            ||question.contains("宿舍")||question.contains("住宿费")||question.contains("食堂")||question.contains("餐厅");
    }

    private static boolean personalResultQuestion(String question){
        return question.contains("成绩")&&(question.contains("我")||question.contains("本人"))
            &&(question.contains("有效")||question.contains("保证")||question.contains("合格"));
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
    private static void add(Map<ReasonCode,Issue> issues,ReasonCode r,String message,String nextStep){
        issues.putIfAbsent(r,new Issue("i-"+r.name(),issue(r).kind(),r,message,nextStep));
    }
    private record ScopeGap(ReasonCode reason,String message,String nextStep) {}
    static Issue issue(ReasonCode r){
        String message=switch(r){case RETRIEVAL_NO_MATCH->"本次检索未找到可用依据，不能据此断定资料没有规定。";case MISSING_CONDITION->"还缺影响结论的必要条件。";case DETAIL_NOT_IN_REVIEWED_TEXT,REVIEWED_FACT_COVERAGE_GAP->"已取得的审核资料不足以支持完整结论。";case UNSUPPORTED_DEMAND->"已审核资料没有覆盖该需求（例如当期通知、具体课程实际安排、个人记录或代办操作），本轮不作结论，也不代替你办理。";case PERSONAL_RECORD_NOT_AVAILABLE->"手册没有提供你的个人成绩、已修学分或适用培养方案，无法据此计算还差多少学分或判定毕业资格。";case CURRENT_APPLICABILITY_UNKNOWN,CURRENT_NOTICE_MISSING->"这里只能解释历史文本，当期安排或现行适用性尚未核实。";case QUESTION_UNDERSTANDING_UNRESOLVED->"已确认的部分保留；其余需求的理解或核验尚未完成。";case INVALID_MODEL_RESPONSE->"本轮模型响应未通过协议或语义核验，无法确认可信结论；不能将核验失败误当作资料不存在。";case ANSWER_COVERAGE_INCOMPLETE->"已确认的部分保留；本轮尚未回答全部需求，不能把漏答当作资料缺失。";case CANCELLED->"本轮已取消。";case BUDGET_EXCEEDED,NO_PROGRESS->"本轮达到执行边界，未完成的部分保留未知。";default->"本轮处理未完成，不能将处理失败当作资料缺失。";};
        // 资料不足与能力边界必须给出可执行下一步；代办类需求还要说明系统不代替学生操作，避免误以为已办成。
        String nextStep=switch(r){case MISSING_CONDITION->"请补充下方必要条件。";case UNSUPPORTED_DEMAND->"请以教务系统当期通知、页面显示或辅导员确认为准；本系统只解释已审核规则原文，不代办报名、补选、退选或缴费。";case PERSONAL_RECORD_NOT_AVAILABLE->"请核对个人成绩单、已修学分和对应培养方案，或咨询教务/辅导员；本系统只解释已审核规则原文。";default->"可核对原文或重新发起查证；本轮不作无依据结论。";};
        return new Issue("i-"+r.name(),r.kind,r,message,nextStep);
    }
}
