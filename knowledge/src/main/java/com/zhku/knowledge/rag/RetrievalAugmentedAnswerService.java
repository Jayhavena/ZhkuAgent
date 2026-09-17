package com.zhku.knowledge.rag;

import com.zhku.knowledge.retrieval.ChunkCandidateRetriever;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.RetrievalOutcome;
import com.zhku.knowledge.retrieval.RetrievedChunk;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 检索增强级的作答服务：混合检索 → 服务端分配引用 → 受控生成 → 引用审计。
 *
 * <p>边界刻意收紧：只有"有候选且生成通过守卫"才返回作答；
 * 无候选、生成降级、模型伪造引用一律返回空，由编排层第五级统一拒答并转人工。
 * 这样"检索级"永远不会给出没有出处的结论。</p>
 */
public final class RetrievalAugmentedAnswerService {

    private final HybridRetrievalService hybridRetrieval;
    private final ChunkCandidateRetriever lexicalRetriever;
    private final ChunkCandidateRetriever vectorRetriever;
    private final GroundedAnswerComposer composer;
    private final CitationIdFactory citationIdFactory;
    private final CitationAuditRepository auditRepository;
    private final Clock clock;
    private final int topK;

    public RetrievalAugmentedAnswerService(
            HybridRetrievalService hybridRetrieval,
            ChunkCandidateRetriever lexicalRetriever,
            ChunkCandidateRetriever vectorRetriever,
            ModelChatClient chatClient,
            AnswerEvidenceGuard guard,
            CitationIdFactory citationIdFactory,
            CitationAuditRepository auditRepository,
            Clock clock,
            int topK
    ) {
        this.hybridRetrieval = Objects.requireNonNull(hybridRetrieval, "混合检索服务不能为空");
        this.lexicalRetriever = Objects.requireNonNull(lexicalRetriever, "词法检索器不能为空");
        this.vectorRetriever = Objects.requireNonNull(vectorRetriever, "向量检索器不能为空");
        this.composer = new GroundedAnswerComposer(chatClient, guard);
        this.citationIdFactory = Objects.requireNonNull(citationIdFactory, "引用标识工厂不能为空");
        this.auditRepository = Objects.requireNonNull(auditRepository, "引用审计仓储不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        if (topK <= 0) {
            throw new IllegalArgumentException("送模型的候选上限必须为正数");
        }
        this.topK = topK;
    }

    /**
     * 尝试以检索增强路径作答。
     *
     * @return 通过守卫的作答；无候选或降级时为空
     */
    public Optional<ComposedAnswer> answer(String question) {
        return answerWithCitations(question).map(RetrievalAnswer::composed);
    }

    /**
     * 尝试以检索增强路径作答，并返回引用明细（标识 + 来源标题/定位/页码）。
     *
     * <p>明细与白名单标识在同一次分配中配对产生：标识给模型、明细给服务端渲染引用卡，
     * 明细不经过模型，模型无法影响它。</p>
     */
    public Optional<RetrievalAnswer> answerWithCitations(String question) {
        Objects.requireNonNull(question, "问题不能为空");

        RetrievalOutcome outcome = hybridRetrieval.retrieve(
                question, topK, lexicalRetriever, vectorRetriever);
        if (outcome.chunks().isEmpty()) {
            return Optional.empty();
        }

        // 引用标识在把证据交给模型之前分配并留痕：这样"模型输出了审计中不存在的 ID"
        // 本身就是可判定的伪造，而不是靠比对文本猜测。
        List<ApprovedEvidence> approved = new ArrayList<>();
        List<ComposedAnswerCitation> citations = new ArrayList<>();
        for (RetrievedChunk chunk : outcome.chunks()) {
            String citationId = citationIdFactory.next();
            approved.add(new ApprovedEvidence(
                    citationId,
                    chunk.content(),
                    chunk.sourceTitle() + " · " + chunk.sourceLocator()
            ));
            citations.add(new ComposedAnswerCitation(
                    citationId,
                    chunk.chunkId(),
                    chunk.sourceTitle(),
                    chunk.sourceLocator(),
                    chunk.pageNumber()
            ));
            auditRepository.record(new CitationAuditRecord(
                    citationId, chunk.chunkId(), "RETRIEVAL_AUGMENTED", clock.instant()));
        }

        ComposedAnswer composed = composer.compose(question, approved);
        return composed.status() == ComposedAnswerStatus.ANSWERED
                ? Optional.of(new RetrievalAnswer(composed, citations))
                : Optional.empty();
    }
}
