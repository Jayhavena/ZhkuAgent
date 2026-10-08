package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalIndexingRepeatTest {
    @Test
    void repeatRunCountsOnlyNewIndexEntries() {
        UUID sourceId = UUID.randomUUID();
        IndexableChunk chunk = new IndexableChunk(UUID.randomUUID(), "学分规则", "a".repeat(64), "v1",
                new SourceEligibilitySnapshot(sourceId, "PUBLISHED", "STUDENT_VISIBLE", true,
                        false, "PUBLIC_ELECTIVE_CREDIT_LIMIT", null, null));
        RetrievalIndexRepository repository = new RetrievalIndexRepository() {
            boolean existing;

            @Override
            public void registerProfile(EmbeddingProfile profile) {
            }

            @Override
            public Optional<EmbeddingProfile> findProfile(String id) {
                return Optional.of(new EmbeddingProfile(id, "stub", "stub", 2, ProfileStatus.BUILDING));
            }

            @Override
            public UUID startRun(String id, String version) {
                return UUID.randomUUID();
            }

            @Override
            public boolean appendEntry(UUID run, String id, IndexableChunk value,
                    String version, float[] embedding) {
                if (existing)
                    return false;
                existing = true;
                return true;
            }

            @Override
            public void completeRun(UUID id, int indexed) {
            }

            @Override
            public void failRun(UUID id) {
            }

            @Override
            public List<IndexableChunk> listChunksWithSourceState() {
                return List.of(chunk);
            }
        };
        RetrievalIndexingService service = new RetrievalIndexingService(repository, new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return new float[] { 1, 2 };
            }

            @Override
            public int dimensions() {
                return 2;
            }
        }, new RetrievalEligibilityFilter(Clock.systemUTC()));
        assertThat(service.indexProfile("test", "v1", "PUBLIC_ELECTIVE_CREDIT_LIMIT").indexedEntries()).isEqualTo(1);
        assertThat(service.indexProfile("test", "v1", "PUBLIC_ELECTIVE_CREDIT_LIMIT").indexedEntries()).isZero();
    }
}
