package com.zhku.knowledge.rag;

import com.zhku.knowledge.retrieval.HybridCandidateFuser;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索增强级作答服务测试：检索 → 服务端分配引用 → 受控生成 → 引用审计。
 *
 * <p>职责边界：本服务只在"有候选且生成通过守卫"时给出作答；无候选或
 * 生成降级一律返回空，交由编排层第五级拒答并转人工——不在检索级硬造回答。</p>
 */
class RetrievalAugmentedAnswerServiceTest {

    private static final RetrievedChunk CHUNK_A = chunk("chunk-a", "第一条 每学期公共选修课最高修读 4 学分。");
    private static final RetrievedChunk CHUNK_B = chunk("chunk-b", "第二条 公共选修课不办理缓考。");

    private final List<CitationAuditRecord> audits = new ArrayList<>();

    @Test
    void shouldAnswerWithServerAssignedCitationIdsAndRecordAudit() {
        ModelChatClient client = request -> {
            // 模型只能使用服务端下发的引用标识，不得自造。
            assertThat(request.evidenceCitationIds()).hasSize(2);
            return new ModelResponse(
                    "每学期公共选修课最高修读 4 学分。", 0.95,
                    List.of(request.evidenceCitationIds().getFirst()), false);
        };

        Optional<ComposedAnswer> answer = service(client, CHUNK_A, CHUNK_B).answer("每学期最多修多少学分？");

        assertThat(answer).isPresent();
        assertThat(answer.get().status()).isEqualTo(ComposedAnswerStatus.ANSWERED);
        assertThat(answer.get().citationIds()).hasSize(1);
        // 审计必须能把下发的引用标识反查到具体片段。
        assertThat(audits).hasSize(2);
        assertThat(audits).extracting(CitationAuditRecord::chunkId)
                .containsExactly(CHUNK_A.chunkId(), CHUNK_B.chunkId());
        assertThat(answer.get().citationIds().getFirst())
                .isEqualTo(audits.getFirst().citationId());
    }

    @Test
    void shouldReturnEmptyAndSkipModelWhenNoCandidateRetrieved() {
        ModelChatClient client = request -> {
            throw new AssertionError("无候选时不得调用模型");
        };

        Optional<ComposedAnswer> answer = service(client).answer("无关问题");

        assertThat(answer).isEmpty();
        assertThat(audits).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenModelCitesForgedId() {
        ModelChatClient client = request -> new ModelResponse(
                "伪造引用支撑的回答XYZ", 0.9, List.of("cit-forged"), false);

        Optional<ComposedAnswer> answer = service(client, CHUNK_A).answer("每学期最多修多少学分？");

        // 伪造引用让整答降级：返回空，由编排层第五级拒答并转人工。
        assertThat(answer).isEmpty();
    }

    @Test
    void shouldOnlyExposeTopKChunksToModel() {
        ModelChatClient client = request -> {
            assertThat(request.evidenceTexts()).hasSize(1);
            return new ModelResponse(
                    "每学期公共选修课最高修读 4 学分。", 0.9,
                    List.of(request.evidenceCitationIds().getFirst()), false);
        };

        Optional<ComposedAnswer> answer = service(client, CHUNK_A, CHUNK_B)
                .withTopK(1)
                .answer("每学期最多修多少学分？");

        assertThat(answer).isPresent();
        assertThat(audits).hasSize(1);
    }

    private ServiceBuilder service(ModelChatClient client, RetrievedChunk... chunks) {
        return new ServiceBuilder(client, List.of(chunks));
    }

    private static RetrievedChunk chunk(String name, String content) {
        return new RetrievedChunk(
                UUID.nameUUIDFromBytes(name.getBytes()),
                content,
                "学生手册 2026 版",
                "第 1 页",
                1,
                "学生手册 2026 版",
                "a".repeat(64));
    }

    /** 组装被测服务：候选由固定检索器提供，审计写入内存列表。 */
    private final class ServiceBuilder {

        private final ModelChatClient client;
        private final List<RetrievedChunk> chunks;
        private int topK = 5;

        private ServiceBuilder(ModelChatClient client, List<RetrievedChunk> chunks) {
            this.client = client;
            this.chunks = chunks;
        }

        ServiceBuilder withTopK(int value) {
            this.topK = value;
            return this;
        }

        Optional<ComposedAnswer> answer(String question) {
            HybridRetrievalService hybrid = new HybridRetrievalService(new HybridCandidateFuser());
            RetrievalAugmentedAnswerService service = new RetrievalAugmentedAnswerService(
                    hybrid,
                    (query, limit) -> List.copyOf(chunks),
                    (query, limit) -> List.of(),
                    client,
                    new AnswerEvidenceGuard(),
                    new SequentialCitationIdFactory(),
                    audits::add,
                    Clock.fixed(Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC),
                    topK);
            return service.answer(question);
        }
    }

    /** 确定性引用标识工厂，便于断言"模型只能用服务端下发的 ID"。 */
    private static final class SequentialCitationIdFactory implements CitationIdFactory {

        private int counter = 0;

        @Override
        public String next() {
            counter++;
            return "cit-test-" + counter;
        }
    }
}
