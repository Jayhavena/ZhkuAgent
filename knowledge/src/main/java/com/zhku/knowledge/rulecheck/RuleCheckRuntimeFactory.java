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
