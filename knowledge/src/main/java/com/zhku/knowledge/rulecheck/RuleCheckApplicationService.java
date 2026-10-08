package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Stream;
import java.util.Objects;
import java.util.function.Supplier;

/** 统一查证编排；HTTP只投影结果，审核目录与续答规则绑定必须由服务端维护。 */
public final class RuleCheckApplicationService {
    private final RuleCheckRuntimeSettings settings;
    private final Supplier<List<CatalogEntry>> catalogue;
    private final RuleSelector selector;
    private final HybridRetrievalService retrieval;
    private final ChunkCandidateRetriever lexical, vector;
    private final RuleCheckEvidenceCapture capture;
    private final RuleCheckDecisionService decisions;
    public RuleCheckApplicationService(RuleCheckRuntimeSettings settings, Supplier<List<CatalogEntry>> catalogue,
            RuleSelector selector, HybridRetrievalService retrieval, ChunkCandidateRetriever lexical,
            ChunkCandidateRetriever vector, RuleCheckEvidenceCapture capture, RuleCheckDecisionService decisions) {
        this.settings=Objects.requireNonNull(settings);
        this.catalogue=Objects.requireNonNull(catalogue);
        this.selector=Objects.requireNonNull(selector);
        this.retrieval=Objects.requireNonNull(retrieval);
        this.lexical=Objects.requireNonNull(lexical);this.vector=Objects.requireNonNull(vector);
        this.capture=Objects.requireNonNull(capture);this.decisions=Objects.requireNonNull(decisions);
    }
    public RuleCheckDecision check(String question, Map<String,String> confirmedConditions) {
        return checkWithContext(question,confirmedConditions).decision();
    }
    public RuleCheckScopedResult checkWithContext(String question,Map<String,String> confirmedConditions) {
        return execute(question,validatedConditions(question,confirmedConditions),null);
    }
    public boolean isCurrent(RuleCheckScopedResult result) {
        if(result==null||!settings.referenceRevision().equals(result.decision().referenceRevision()))return false;
        if(result.decision().status()==RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE)
            return result.scope()==null&&result.continuation()==null&&result.releaseBinding()==null;
        try {
            var binding=result.releaseBinding();
            if(binding==null||!settings.equals(binding.settings())||binding.references().isEmpty()
                    ||binding.references().size()>settings.resultLimit())return false;
            validatedConditions(binding.question(),binding.confirmedConditions());
            var entry=binding.entry();String id=entry.template().ruleId();
            if(!entry.equals(approvedCatalogue().get(id)))return false;
            var aliases=new HashSet<String>();var identities=new HashSet<List<java.util.UUID>>();
            // 公开record可被内部调用者构造；先验证有界元数据和唯一身份，再做任何fresh读。
            for(var ref:binding.references())
                if(ref==null||ref.citationId()==null||ref.citationId().isBlank()||ref.citationId().length()>128
                        ||ref.sourceId()==null||ref.chunkId()==null||!settings.referenceRevision().equals(ref.revision())
                        ||ref.checksum()==null||!ref.checksum().matches("[0-9a-fA-F]{64}")
                        ||!aliases.add(ref.citationId())||!identities.add(List.of(ref.sourceId(),ref.chunkId())))return false;
            var snapshots=new ArrayList<RuleCheckEvidenceSnapshot>();
            for(var ref:binding.references()) {
                var latest=decisions.currentSnapshot(ref.sourceId(),ref.chunkId()).orElse(null);
                if(latest==null||!latest.eligible()||!ref.sourceId().equals(latest.sourceId())||!ref.chunkId().equals(latest.chunkId())
                        ||!ref.revision().equals(latest.revision())||!ref.checksum().equalsIgnoreCase(latest.checksum())
                        // decide可能排除这个片段；全取证集合的正文完整性必须在重放前逐个检查。
                        ||!RuleCheckEvidenceVerifier.contentIsIntact(latest))return false;
                snapshots.add(new RuleCheckEvidenceSnapshot(ref.citationId(),latest.sourceId(),latest.chunkId(),latest.revision(),latest.checksum(),latest.text(),true));
            }
            // 不扩大原检索集合；重跑确定性决策也涵盖尚无输出摘录的必要澄清。
            // decide内部verifier继续fresh读与全文hash/跨度核验，绝不把currentSnapshot直接当事实。
            var replay=scoped(decisions.decide(entry.template(),binding.confirmedConditions(),snapshots),entry,
                binding.question(),binding.confirmedConditions(),snapshots);
            // 后置目录复查阻断重读期间变化；这仍是顺序检查，HTTP身份/Session及网络窗口另有边界。
            return entry.equals(approvedCatalogue().get(id))&&result.decision().equals(replay.decision())
                &&Objects.equals(result.scope(),replay.scope())&&Objects.equals(result.continuation(),replay.continuation());
        }catch(RuntimeException unavailable){return false;}
    }
    public boolean isReferenceApproved(RuleCheckVerifiedExcerpt reference) {
        if(reference==null||reference.sourceId()==null||reference.chunkId()==null||!settings.referenceRevision().equals(reference.revision())
                ||reference.checksum()==null||!reference.checksum().matches("[0-9a-fA-F]{64}")||reference.text()==null
                ||reference.text().isBlank()||reference.startInclusive()<0||reference.endExclusive()<=reference.startInclusive()
                ||(long)reference.endExclusive()-reference.startInclusive()!=reference.text().length())return false;
        try {
            // 只证明当前目录仍包含这条精确绑定；原文HTTP还必须fresh locate资料资格和会话身份。
            return approvedCatalogue().values().stream().flatMap(entry->Stream.concat(entry.template().facts().stream(),
                    entry.template().conflicts().stream().flatMap(conflict->conflict.sides().stream())))
                .flatMap(fact->fact.evidenceBindings().stream()).anyMatch(b->reference.sourceId().equals(b.sourceId())
                    &&reference.chunkId().equals(b.chunkId())&&reference.revision().equals(b.revision())
                    &&reference.checksum().equalsIgnoreCase(b.checksum())&&reference.startInclusive()==b.startInclusive()
                    &&reference.endExclusive()==b.endExclusive()&&reference.text().equals(b.quote()));
        }catch(RuntimeException unavailable){return false;}
    }
    public RuleCheckScopedResult resume(RuleCheckScopedResult.Continuation context,Map<String,String> delta) {
        if (context==null) throw new IllegalArgumentException("澄清上下文缺失");
        if (!settings.equals(context.settings()) || !context.entry().template().conditions().contains(context.nextCondition()))
            return rejected("澄清运行边界已变化，请重新查证");
        String key=context.nextCondition().key();
        if (delta==null || delta.size()!=1 || !delta.containsKey(key)
                || !context.nextCondition().allowedValues().contains(delta.get(key)))
            throw new IllegalArgumentException("只允许补充本轮待问条件的声明值");
        String previous=context.confirmedConditions().get(key);
        if (previous!=null && !previous.isBlank()) throw new IllegalArgumentException("更改已声明条件须重新查证");
        var values=new LinkedHashMap<>(context.confirmedConditions());values.put(key,delta.get(key));
        // 续答不重新选题，也不能沿用旧证据；配置/目录复核后仍走完整捕获与当前核验。
        return execute(context.question(),validatedConditions(context.question(),values),context);
    }
    private RuleCheckScopedResult execute(String question, Map<String,String> values, RuleCheckScopedResult.Continuation expected) {
        try {
            var approved=approvedCatalogue();
            if (approved.isEmpty()) return rejected("当前引用版本没有可用的已审核规则");
            var options=approved.values().stream().map(e->new RuleOption(e.template().ruleId(),e.questionScope())).toList();
            // 选择器只能选目录ID，不能回传规则正文、引用资格或猜测学生个人条件。
            String id;
            if (expected==null) id=selector.select(question,options);
            else {
                id=expected.entry().template().ruleId();
                if (!expected.entry().equals(approved.get(id))) return rejected("澄清所用规则已变化或退役，请重新查证");
            }
            var entry=approved.get(id);
            if (entry==null) return rejected("尚不能确定该问题对应的已审核规则");
            var declared=entry.template().conditions().stream().map(RuleCheckRuleTemplate.Condition::key).toList();
            if (!declared.containsAll(values.keySet())) return rejected("输入包含本规则未声明的条件");
            var outcome=retrieval.retrieve(question,settings.candidateLimit(),settings.resultLimit(),lexical,vector);
            if (outcome.fullyDegraded()) return rejected("检索暂不可用，尚不能取得可靠依据");
            if (outcome.chunks().isEmpty()) return rejected("当前检索没有取得可核验依据");
            var captured=capture.capture(outcome.chunks());
            if (!captured.accepted()) return rejected("本轮证据捕获或审计未通过");
            // 捕获可能耗时；审核目录变化时不能用旧模板回答。资料资格另由决策reader再读。
            // 这仍是顺序复核，不代表目录、数据库与最终HTTP外发的原子提交。
            if (!entry.equals(approvedCatalogue().get(id))) return rejected("规则在处理过程中发生变化，请重新查证");
            return scoped(decisions.decide(entry.template(),values,captured.snapshots()),entry,question,values,captured.snapshots());
        } catch (RuntimeException unavailable) {
            // 供应商或目录异常文本可能含敏感内容，不能作为学生回答正文返回。
            return rejected("查证链路暂不可用，尚不能确认结论");
        }
    }
    private RuleCheckScopedResult scoped(RuleCheckDecision decision,CatalogEntry entry,String question,Map<String,String> values,List<RuleCheckEvidenceSnapshot> snapshots) {
        if (decision.status()==RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE)
            return new RuleCheckScopedResult(decision,null,null);
        var rule=entry.template();var when=new LinkedHashMap<String,Map<String,String>>();
        rule.facts().forEach(fact->when.put(fact.id(),fact.when()));
        var scope=new RuleCheckScopedResult.Scope(rule.ruleId(),entry.questionScope(),rule.conditions(),when);
        RuleCheckScopedResult.Continuation continuation=null;
        if (decision.status()==RuleCheckDecision.Status.NEED_CLARIFICATION) {
            var questionItem=decision.clarificationQuestions().getFirst();
            var condition=rule.conditions().stream().filter(c->c.key().equals(questionItem.key())).findFirst().orElseThrow();
            // 接口有输入上限；不能发出用户按合同永远无法回答的条件选项。
            if (condition.key().length()>64 || condition.allowedValues().stream().anyMatch(v->v.length()>128)
                    // 已有空白key只补值；新增第17项会让合法续答永久违反输入合同。
                    || (!values.containsKey(condition.key()) && values.size()>=16))
                return rejected("本规则的澄清字段超出支持范围");
            continuation=new RuleCheckScopedResult.Continuation(settings,entry,question,values,condition);
        }
        var refs=snapshots.stream().map(snapshot->new RuleCheckScopedResult.CapturedReference(snapshot.citationId(),snapshot.sourceId(),
            snapshot.chunkId(),snapshot.revision(),snapshot.checksum())).toList();
        return new RuleCheckScopedResult(decision,scope,continuation,new RuleCheckScopedResult.ReleaseBinding(settings,entry,question,values,refs));
    }
    private RuleCheckScopedResult rejected(String reason) {return new RuleCheckScopedResult(refused(reason),null,null);}
    private Map<String,CatalogEntry> approvedCatalogue() {
        var entries=List.copyOf(catalogue.get());
        var approved=new LinkedHashMap<String,CatalogEntry>();
        for (var entry : entries) {
            var rule=entry.template();
            if (rule.reviewState()!=RuleCheckRuleTemplate.ReviewState.APPROVED
                    || !settings.referenceRevision().equals(rule.referenceRevision())) continue;
            if (rule.ruleId().length()>128 || approved.putIfAbsent(rule.ruleId(),entry)!=null || approved.size()>64)
                throw new IllegalStateException("审核目录不满足受控选择约束");
        }
        return approved;
    }
    private static Map<String,String> validatedConditions(String question,Map<String,String> values) {
        if (question==null || question.isBlank() || question.length()>2048 || values==null || values.size()>16)
            throw new IllegalArgumentException("问题或条件超出受控输入范围");
        for (var entry:values.entrySet())
            if (entry.getKey()==null || entry.getKey().isBlank() || entry.getKey().length()>64
                    || entry.getValue()==null || entry.getValue().length()>128)
                throw new IllegalArgumentException("条件字段无效或过长");
        return Map.copyOf(values);
    }
    private RuleCheckDecision refused(String reason) {
        return new RuleCheckDecision(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE,settings.referenceRevision(),
                List.of(),List.of(),List.of(reason),List.of());
    }
    /** 问题范围须与模板一起审核；目录ID在范围内并不自动证明问题语义匹配。 */
    public record CatalogEntry(RuleCheckRuleTemplate template, String questionScope) {
        public CatalogEntry {
            Objects.requireNonNull(template);
            if (questionScope==null || questionScope.isBlank() || questionScope.length()>2048)
                throw new IllegalArgumentException("规则选择范围必须明确且有界");
        }
    }
    public record RuleOption(String ruleId, String questionScope) {}
    @FunctionalInterface public interface RuleSelector {
        String select(String question, List<RuleOption> approvedOptions);
    }
}
