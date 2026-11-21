package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.rag.CitationIdFactory;
import com.zhku.knowledge.retrieval.*;
import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

/** 内部装配入口；构造不启动外部调用，也不授权任何真实资料。 */
public final class RuleCheckRuntimeFactory {
    private RuleCheckRuntimeFactory() {}
    static ChunkCandidateRetriever disabledVector() {
        // 未启用不是检索成功但无命中；否则词法故障会被误报为证据不足。
        return (question,limit)->{throw new RetrieverUnavailableException("向量检索未启用");};
    }
    /** 新路径只使用本轮fresh取证目录；规划器的注入不自动授权模型网络调用。 */
    public static RuleCheckApplicationService createEvidenceFirst2025(DataSource source,RuleCheckRuntimeSettings settings,
            Supplier<List<RuleCheckApplicationService.CatalogEntry>> catalogue,
            RuleCheckTaskPlan.Planner planner,CitationIdFactory ids,Clock clock) {
        var verifier=new RuleCheckEvidenceVerifier(new JdbcRuleCheckEvidenceReader(source,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()));
        var capture=new RuleCheckEvidenceCapture(verifier,ids,new JdbcRuleCheckCitationAuditRepository(source,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock);
        return RuleCheckApplicationService.evidenceFirst(settings,catalogue,planner,new HybridRetrievalService(new HybridCandidateFuser()),
            JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(source,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
            disabledVector(),capture,new RuleCheckDecisionService(verifier));
    }
    /** 单主题受控组合；不能沿用一般词法或在此处隐式打开向量外发。 */
    public static RuleCheckApplicationService createControlled2025(DataSource source,RuleCheckRuntimeSettings settings,
            Supplier<List<RuleCheckApplicationService.CatalogEntry>> catalogue,
            RuleCheckApplicationService.RuleSelector selector,CitationIdFactory ids,Clock clock) {
        if(!selector.requiresScopedCandidates())throw new IllegalArgumentException("受控选题必须先收窄候选");
        var verifier=new RuleCheckEvidenceVerifier(new JdbcRuleCheckEvidenceReader(source,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()));
        var capture=new RuleCheckEvidenceCapture(verifier,ids,new JdbcRuleCheckCitationAuditRepository(source,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock);
        return new RuleCheckApplicationService(settings,catalogue,selector,new HybridRetrievalService(new HybridCandidateFuser()),
            JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(source,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
            disabledVector(),capture,new RuleCheckDecisionService(verifier));
    }
    public static RuleCheckApplicationService create(DataSource dataSource, RuleCheckRuntimeSettings settings,
            Supplier<List<RuleCheckApplicationService.CatalogEntry>> catalogue,
            RuleCheckApplicationService.RuleSelector selector, EmbeddingClient embedding,
            CitationIdFactory citationIds, Clock clock) {
        var filter=new RetrievalEligibilityFilter(clock);
        var verifier=new RuleCheckEvidenceVerifier(new JdbcRuleCheckEvidenceReader(dataSource,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()));
        var capture=new RuleCheckEvidenceCapture(verifier,citationIds,
                new JdbcRuleCheckCitationAuditRepository(dataSource,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock);
        var vectorRetriever=new JdbcVectorCandidateRetriever(dataSource,filter,settings.profileId(),settings.requiredScope(),settings.chunkingVersion(),embedding);
        ChunkCandidateRetriever vector=(query,limit)->{
            try {return vectorRetriever.retrieve(query,limit);}
            catch (RetrieverUnavailableException unavailable) {throw unavailable;}
            catch (RuntimeException unavailable) {
                // 供应商适配器异常类型不统一；在新应用的向量边界转换，才能保留健康词法证据。
                // 原因仍保留供诊断，不外泄正文，也不改变旧检索器/索引任务的错误语义。
                throw new RetrieverUnavailableException("向量检索暂不可用",unavailable);
            }
        };
        return new RuleCheckApplicationService(settings,catalogue,selector,new HybridRetrievalService(new HybridCandidateFuser()),
                new JdbcLexicalCandidateRetriever(dataSource,filter,settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
                vector,
                capture,new RuleCheckDecisionService(verifier));
    }
}
