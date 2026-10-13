package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import java.util.*;
import java.util.function.Supplier;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;

/** 证据先行的新路径；原封存选题路径保持独立，不能拿旧响应冒充新规划结果。 */
final class RuleCheckEvidenceFirstFlow {
    private final RuleCheckRuntimeSettings settings;
    private final Supplier<List<RuleCheckApplicationService.CatalogEntry>> catalogue;
    private final RuleCheckTaskPlan.Planner planner;
    private final HybridRetrievalService retrieval;
    private final ChunkCandidateRetriever lexical,vector;
    private final RuleCheckEvidenceCapture capture;
    private final RuleCheckDecisionService decisions;
    private final RuleCheckApplicabilityGuard applicability=new RuleCheckApplicabilityGuard();
    RuleCheckEvidenceFirstFlow(RuleCheckRuntimeSettings settings,Supplier<List<RuleCheckApplicationService.CatalogEntry>> catalogue,
        RuleCheckTaskPlan.Planner planner,HybridRetrievalService retrieval,ChunkCandidateRetriever lexical,ChunkCandidateRetriever vector,
        RuleCheckEvidenceCapture capture,RuleCheckDecisionService decisions) {
        this.settings=settings;this.catalogue=catalogue;this.planner=planner;this.retrieval=retrieval;
        this.lexical=lexical;this.vector=vector;this.capture=capture;this.decisions=decisions;
    }
    RuleCheckScopedResult check(String question,Map<String,String> values,RuleCheckTaskPlan savedPlan) {
        try {
            var approved=approved();if(approved.isEmpty())return reject("当前引用版本没有可用的已审核规则");
            // 主题词未命中不是资料缺失。检索器先按权限/审核/索引身份过滤，捕获再fresh核验原文。
            var needs=RuleCheckQuestionCoverage.needs(question);
            var queries=RuleCheckQuestionCoverage.queries(question,needs,approved.values().stream().anyMatch(e->e.topicCoverage().enabled()));
            var retrieved=retrieveSubquestions(queries);
            if(retrieved.fullyDegraded())return reject("检索暂不可用，尚不能取得可靠依据");
            if(retrieved.chunks().isEmpty())return reject("检索没有取得可核验依据，不能推断资料一定没有规定");
            var captured=capture.capture(retrieved.chunks());if(!captured.accepted())return reject("本轮证据捕获或审计未通过");
            var available=approved.values().stream().filter(entry->supported(entry,captured.snapshots())).toList();
            if(available.isEmpty())return withTrace(reject("已取到的原文没有完整支持可执行的审核条款"),captured.snapshots(),retrieved.chunks(),null,"NOT_CALLED_NO_USABLE_EVIDENCE");
            var options=available.stream().map(RuleCheckClauseOperations::option).toList();
            var proposed=savedPlan==null?planner.plan(question,options):savedPlan;
            var plan=savedPlan==null?RuleCheckQuestionCoverage.complete(question,proposed,available):savedPlan;
            var result=plan==null?reject("未可靠理解问题与本轮证据的关系，尚不能确认结论"):render(question,values,plan,available,captured.snapshots());
            return withTrace(result,captured.snapshots(),retrieved.chunks(),proposed,proposed==null?"NO_VALID_MODEL_PLAN":"PLAN_RECORDED");
        }catch(RuntimeException unavailable){return reject("查证链路暂不可用，尚不能确认结论");}
    }
    private RuleCheckScopedResult withTrace(RuleCheckScopedResult result,List<RuleCheckEvidenceSnapshot> snapshots,List<RetrievedChunk> chunks,RuleCheckTaskPlan proposed,String plannerStatus) {
        var references=snapshots.stream().map(s->new RuleCheckScopedResult.CapturedReference(s.citationId(),s.sourceId(),s.chunkId(),s.revision(),s.checksum())).toList();
        return new RuleCheckScopedResult(result.decision(),result.scope(),result.continuation(),result.releaseBinding(),result.applicability(),result.evidencePlan(),
            new RuleCheckScopedResult.RetrievalTrace(references,chunks.stream().map(RetrievedChunk::sourceLocator).toList(),plannerStatus,proposed));
    }
    private RetrievalOutcome retrieveSubquestions(List<String> queries) {
        var outcomes=queries.stream().map(q->retrieval.retrieve(q,settings.candidateLimit(),settings.resultLimit(),lexical,vector)).toList();
        var merged=new LinkedHashMap<UUID,RetrievedChunk>();
        // 轮流保留各子问题前列证据，总捕获仍不超过resultLimit；不扩大到所有候选。
        for(int rank=0;rank<settings.resultLimit()&&merged.size()<settings.resultLimit();rank++)for(var outcome:outcomes) {
            if(rank<outcome.chunks().size())merged.putIfAbsent(outcome.chunks().get(rank).chunkId(),outcome.chunks().get(rank));
            if(merged.size()==settings.resultLimit())break;
        }
        return new RetrievalOutcome(List.copyOf(merged.values()),outcomes.stream().anyMatch(RetrievalOutcome::lexicalAvailable),outcomes.stream().anyMatch(RetrievalOutcome::vectorAvailable));
    }
    private LinkedHashMap<String,RuleCheckApplicationService.CatalogEntry> approved() {
        var result=new LinkedHashMap<String,RuleCheckApplicationService.CatalogEntry>();
        for(var entry:List.copyOf(catalogue.get()))if(entry.template().reviewState()==RuleCheckRuleTemplate.ReviewState.APPROVED
                &&settings.referenceRevision().equals(entry.template().referenceRevision())) {
            if(entry.template().ruleId().length()>128||result.putIfAbsent(entry.template().ruleId(),entry)!=null||result.size()>64)
                throw new IllegalArgumentException("审核目录不满足预算或唯一性约束");
        }
        return result;
    }
    private boolean supported(RuleCheckApplicationService.CatalogEntry entry,List<RuleCheckEvidenceSnapshot> snapshots) {
        if(entry.template().facts().isEmpty())return false;
        return entry.template().facts().stream().flatMap(f->f.evidenceBindings().stream()).allMatch(binding->snapshots.stream().anyMatch(s->
            binding.sourceId().equals(s.sourceId())&&binding.chunkId().equals(s.chunkId())&&binding.revision().equals(s.revision())
            &&binding.checksum().equalsIgnoreCase(s.checksum())&&binding.startInclusive()>=0&&binding.endExclusive()<=s.text().length()
            &&binding.endExclusive()>binding.startInclusive()&&s.text().substring(binding.startInclusive(),binding.endExclusive()).equals(binding.quote())));
    }
    private RuleCheckApplicabilityGuard.Assessment historicalAssessment(String question,RuleCheckApplicationService.CatalogEntry entry) {
        var raw=applicability.assess(question,entry.coverage(),settings.referenceRevision());
        var blocked=raw.blockingReasons().stream().filter(s->!s.equals("问题涉及其他年份，当前引用版本不能证明该年份的规则或实际安排")
            &&!s.equals("缺少当期安排或现行有效性的已审核依据，历史文本不能证明当前状态")).toList();
        var gaps=new LinkedHashSet<>(raw.evidenceGaps());
        if(raw.timeDemand()!=RuleCheckApplicabilityGuard.TimeDemand.SNAPSHOT_RULE)gaps.add("所引依据仅说明2025版历史规则，不能确认其他年份或当期安排及现行有效性");
        return new RuleCheckApplicabilityGuard.Assessment(raw.requestedAudiences(),raw.timeDemand(),raw.personalRecordsRequired(),raw.actualOfferingRequired(),raw.signals(),blocked,List.copyOf(gaps));
    }
    private RuleCheckScopedResult render(String question,Map<String,String> values,RuleCheckTaskPlan plan,
        List<RuleCheckApplicationService.CatalogEntry> available,List<RuleCheckEvidenceSnapshot> snapshots) {
        var selected=new LinkedHashMap<String,RuleCheckApplicationService.CatalogEntry>();
        var facts=new LinkedHashMap<String,RuleCheckDecision.SupportedFact>();var gaps=new LinkedHashSet<String>();
        var questions=new ArrayList<RuleCheckDecision.Clarification>();var conflicts=new ArrayList<RuleCheckDecision.ConflictEvidence>();
        if(!plan.rejectedTasks().isEmpty())gaps.add("部分请求未能可靠处理，以下仅展示已核验且可独立成立的内容");
        RuleCheckApplicationService.CatalogEntry pending=null,first=null;RuleCheckApplicabilityGuard.Assessment firstAssessment=null;
        for(var proposed:plan.tasks()) {
            var task=proposed;
            if(task.action()==RuleCheckTaskPlan.Action.UNSUPPORTED){gaps.add("该子问题没有对应的本轮已审核证据，不能作出结论");continue;}
            var entry=available.stream().filter(e->e.template().ruleId().equals(proposed.ruleId())).findFirst().orElse(null);
            if(entry==null||!RuleCheckClauseOperations.accepts(task,RuleCheckClauseOperations.option(entry))) {
                gaps.add("部分请求未能可靠处理，以下仅展示已核验且可独立成立的内容");continue;
            }
            // 未提供百分比时只能退回已审核条文解释，绝不使用模型补出的值生成比较或个人结论。
            // 有百分比表达却无法核对输入时仍拒绝，避免负数/科学计数法尾部被当成合法值。
            if(task.action()==RuleCheckTaskPlan.Action.COMPARE_THRESHOLD&&!RuleCheckClauseOperations.hasPercentageExpression(question))
                task=new RuleCheckTaskPlan.Task(task.ruleId(),RuleCheckTaskPlan.Action.EXPLAIN_RULE,"","");
            if(!RuleCheckClauseOperations.valueComesFromQuestion(task,question)) {
                gaps.add("所问数值尚未可靠确认，不能据此比较条件");continue;
            }
            if(task.action()==RuleCheckTaskPlan.Action.COMPARE_THRESHOLD) {
                var actual=RuleCheckClauseOperations.comparisonValue(question);
                if(actual.isEmpty()){gaps.add("无法独立区分所问比例与政策阈值，请明确需要比较的实际比例");continue;}
                task=new RuleCheckTaskPlan.Task(task.ruleId(),task.action(),task.operand(),actual.get());
            }
            selected.putIfAbsent(task.ruleId(),entry);
            var assessment=historicalAssessment(question,entry);if(first==null){first=entry;firstAssessment=assessment;}
            if(assessment.blocked()){gaps.addAll(assessment.blockingReasons());continue;}
            // 对象/个人记录缺口来自独立适用性核对；UNKNOWN_CURRENT不能绕过它们，不能拿现行性代替个人资格。
            gaps.addAll(assessment.evidenceGaps());
            if(task.action()==RuleCheckTaskPlan.Action.UNKNOWN_CURRENT){gaps.add("缺少当期安排或现行有效性的已审核依据，历史文本不能证明当前状态");continue;}
            var localValues=new LinkedHashMap<String,String>();entry.template().conditions().forEach(c->{if(values.containsKey(c.key()))localValues.put(c.key(),values.get(c.key()));});
            var courseCondition=entry.template().conditions().stream().filter(c->c.key().equals("courseType")&&c.allowedValues().containsAll(List.of("PUBLIC","PROFESSIONAL"))).findFirst();
            if(courseCondition.isPresent()) {
                String explicit=explicitCourseType(question),supplied=localValues.get("courseType");
                if(explicit!=null&&supplied!=null&&!supplied.isBlank()&&!explicit.equals(supplied)) {
                    gaps.add("问题文字与已提供的课程类别不一致，请先确认课程类别");continue;
                }
                if(explicit!=null)localValues.put("courseType",explicit);
            }
            var decision=decisions.decide(applicability.constrain(entry.template(),assessment),localValues,snapshots);
            decision.confirmedFacts().forEach(f->facts.putIfAbsent(entry.template().ruleId()+"/"+f.id(),f));
            gaps.addAll(decision.unknownItems());conflicts.addAll(decision.conflicts());
            if(questions.isEmpty()&&!decision.clarificationQuestions().isEmpty()){questions.add(decision.clarificationQuestions().getFirst());pending=entry;}
            if(task.action()==RuleCheckTaskPlan.Action.MISSING_DETAIL)gaps.add(RuleCheckClauseOperations.missingDetail(task.operand()));
            if(task.action()==RuleCheckTaskPlan.Action.EXCLUDE_CONDITION||task.action()==RuleCheckTaskPlan.Action.COMPARE_THRESHOLD) {
                if(decision.status()!=ANSWERED&&decision.status()!=PARTIALLY_ANSWERED)continue;
                var evidence=decision.confirmedFacts().stream().flatMap(f->f.evidence().stream()).distinct().toList();
                if(!evidence.isEmpty())facts.put(task.ruleId()+"/"+task.action()+"/"+task.value(),new RuleCheckDecision.SupportedFact(
                    task.ruleId()+"/"+task.action()+"/"+task.value(),RuleCheckClauseOperations.explanation(task),evidence));
            }
        }
        if(available.stream().anyMatch(e->e.topicCoverage().enabled()))gaps.addAll(RuleCheckQuestionCoverage.gaps(question,RuleCheckQuestionCoverage.needs(question),selected.values()));
        var allowedKeys=selected.values().stream().flatMap(e->e.template().conditions().stream()).map(RuleCheckRuleTemplate.Condition::key).toList();
        if(!allowedKeys.containsAll(values.keySet()))return reject("输入包含所选条款未声明的条件");
        if(selected.isEmpty()||facts.isEmpty()&&questions.isEmpty()&&conflicts.isEmpty())return reject(gaps.isEmpty()?"尚无可以确认的结论":String.join("；",gaps));
        var latest=approved();if(selected.entrySet().stream().anyMatch(e->!e.getValue().equals(latest.get(e.getKey()))))return reject("规则在处理过程中发生变化，请重新查证");
        var decision=!conflicts.isEmpty()?new RuleCheckDecision(CONFLICTING_EVIDENCE,settings.referenceRevision(),List.of(),List.of(),List.copyOf(gaps),conflicts):
            !questions.isEmpty()?new RuleCheckDecision(NEED_CLARIFICATION,settings.referenceRevision(),List.copyOf(facts.values()),questions,List.copyOf(gaps),List.of()):
            new RuleCheckDecision(gaps.isEmpty()?ANSWERED:PARTIALLY_ANSWERED,settings.referenceRevision(),List.copyOf(facts.values()),List.of(),List.copyOf(gaps),List.of());
        var conditions=selected.values().stream().flatMap(e->e.template().conditions().stream()).distinct().toList();
        var when=new LinkedHashMap<String,Map<String,String>>();selected.values().forEach(e->e.template().facts().forEach(f->when.put(f.id(),f.when())));
        var scope=new RuleCheckScopedResult.Scope(String.join(" + ",selected.keySet()),"仅解释2025版已核验条款；各子任务独立处理，个人资格、当期安排与现行性不自动确认",conditions,when);
        var references=snapshots.stream().map(s->new RuleCheckScopedResult.CapturedReference(s.citationId(),s.sourceId(),s.chunkId(),s.revision(),s.checksum())).toList();
        var release=new RuleCheckScopedResult.ReleaseBinding(settings,first,question,values,references);
        var planBinding=new RuleCheckTaskPlan.Binding(plan,List.copyOf(selected.values()));
        // 续答也保存全部目录与原捕获集合；不能仅凭pending条款未变就静默换用另一条新规则。
        var continuation=pending==null?null:new RuleCheckScopedResult.Continuation(settings,pending,question,values,pending.template().conditions().stream()
            .filter(c->c.key().equals(questions.getFirst().key())).findFirst().orElseThrow(),plan,planBinding,release);
        return new RuleCheckScopedResult(decision,scope,continuation,release,firstAssessment,planBinding);
    }
    /** 仅补齐已声明的课程类别，不从关键词推断身份或个人资格；否定、混合和不确定表达继续澄清。 */
    private static String explicitCourseType(String question) {
        boolean publicCourse=question.contains("公共选修")||question.contains("公选"),professional=question.contains("专业选修")||question.contains("专选");
        if(publicCourse==professional||question.matches("(?s).*(?:不确定|不清楚|不知道|不是|并非|而非|是否(?:是|属于)?(?:公共选修|公选|专业选修|专选)|是不是(?:公共选修|公选|专业选修|专选)).*"))return null;
        return publicCourse?"PUBLIC":"PROFESSIONAL";
    }
    boolean continuationIsCurrent(RuleCheckScopedResult.Continuation context) {
        try {
            var binding=context.originalCapture();var plan=context.originalPlanBinding();
            return binding!=null&&plan!=null&&plan.plan().equals(context.evidencePlan())&&plan.entries().contains(context.entry())
                &&binding.question().equals(context.question())&&binding.confirmedConditions().equals(context.confirmedConditions())
                &&currentSnapshots(binding,plan)!=null;
        }catch(RuntimeException invalid){return false;}
    }
    boolean isCurrent(RuleCheckScopedResult result) {
        try {
            var binding=result.releaseBinding();var plan=result.evidencePlan();
            var snapshots=currentSnapshots(binding,plan);if(snapshots==null)return false;
            var replay=render(binding.question(),binding.confirmedConditions(),plan.plan(),plan.entries(),snapshots);
            return result.decision().equals(replay.decision())&&Objects.equals(result.scope(),replay.scope())
                &&Objects.equals(result.continuation(),replay.continuation())&&Objects.equals(result.applicability(),replay.applicability())
                &&Objects.equals(result.evidencePlan(),replay.evidencePlan());
        }catch(RuntimeException invalid){return false;}
    }
    private List<RuleCheckEvidenceSnapshot> currentSnapshots(RuleCheckScopedResult.ReleaseBinding binding,RuleCheckTaskPlan.Binding plan) {
            if(binding==null||plan==null||!settings.equals(binding.settings())||binding.references().isEmpty()||binding.references().size()>settings.resultLimit()
                    ||!plan.entries().getFirst().equals(binding.entry()))return null;
            var current=approved();if(plan.entries().stream().anyMatch(e->!e.equals(current.get(e.template().ruleId()))))return null;
            var snapshots=new ArrayList<RuleCheckEvidenceSnapshot>();var aliases=new HashSet<String>();var identities=new HashSet<List<UUID>>();
            for(var ref:binding.references()) {
                if(ref==null||ref.citationId()==null||ref.citationId().isBlank()||ref.citationId().length()>128||ref.sourceId()==null||ref.chunkId()==null
                        ||!settings.referenceRevision().equals(ref.revision())||ref.checksum()==null||!ref.checksum().matches("[0-9a-fA-F]{64}")
                        ||!aliases.add(ref.citationId())||!identities.add(List.of(ref.sourceId(),ref.chunkId())))return null;
                var latest=decisions.currentSnapshot(ref.sourceId(),ref.chunkId()).orElse(null);
                if(latest==null||!latest.eligible()||!ref.sourceId().equals(latest.sourceId())||!ref.chunkId().equals(latest.chunkId())
                        ||!ref.revision().equals(latest.revision())||!ref.checksum().equalsIgnoreCase(latest.checksum())||!RuleCheckEvidenceVerifier.contentIsIntact(latest))return null;
                snapshots.add(new RuleCheckEvidenceSnapshot(ref.citationId(),latest.sourceId(),latest.chunkId(),latest.revision(),latest.checksum(),latest.text(),true));
            }
            if(plan.entries().stream().anyMatch(e->!supported(e,snapshots)))return null;
            return List.copyOf(snapshots);
    }
    private RuleCheckScopedResult reject(String reason) {return new RuleCheckScopedResult(new RuleCheckDecision(INSUFFICIENT_EVIDENCE,settings.referenceRevision(),List.of(),List.of(),List.of(reason),List.of()),null,null);}
}
