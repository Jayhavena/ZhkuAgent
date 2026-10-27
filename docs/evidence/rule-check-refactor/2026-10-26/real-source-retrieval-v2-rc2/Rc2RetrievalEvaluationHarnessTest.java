package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.knowledge.retrieval.ChunkCandidateRetriever;
import com.zhku.knowledge.retrieval.EmbeddingClient;
import com.zhku.knowledge.retrieval.HybridCandidateFuser;
import com.zhku.knowledge.retrieval.HybridRetrievalService;
import com.zhku.knowledge.retrieval.JdbcLexicalCandidateRetriever;
import com.zhku.knowledge.retrieval.JdbcVectorCandidateRetriever;
import com.zhku.knowledge.retrieval.RankedCandidate;
import com.zhku.knowledge.retrieval.RetrievalEligibilityFilter;
import com.zhku.knowledge.retrieval.RetrievedChunk;
import com.zhku.knowledge.retrieval.ScoredCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class Rc2RetrievalEvaluationHarnessTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DATASET_VERSION = "v2.0.0-rc2";
    private static final String CORPUS_FINGERPRINT = "4fd00ba1c8154fa7f4389cde6b64fe7276b5ce36ae05a6c87224ee9d465980f0";
    private static final String LEXICAL_PROFILE = "real-source-lexical-only-v1-20261026";
    private static final String VECTOR_PROFILE = "dashscope-text-embedding-v4-1024";
    private static final int CANDIDATE_LIMIT = 8;
    private static final int RESULT_LIMIT = 5;
    private static final int RRF_K = 60;

    @Test
    void runFrozenRc2ThreeWayRetrievalComparison() throws Exception {
        Path root = Path.of(System.getProperty("zhku.rc2.root"));
        assertTrue(Files.isDirectory(root), "Candidate directory missing");
        Path casesPath = root.resolve("cases.json");
        Path snapshotPath = root.resolve("corpus-snapshot.json");
        Path embeddingsPath = root.resolve("query-embeddings.json");
        Path resultsPath = root.resolve("retrieval-results-v2-rc2.json");
        Path tracePath = root.resolve("retrieval-trace-v2-rc2.json");
        assertFalse(Files.exists(resultsPath), "Refusing to overwrite existing results");
        assertFalse(Files.exists(tracePath), "Refusing to overwrite existing trace");

        JsonNode dataset = MAPPER.readTree(Files.readAllBytes(casesPath));
        JsonNode snapshot = MAPPER.readTree(Files.readAllBytes(snapshotPath));
        JsonNode embeddingsDoc = MAPPER.readTree(Files.readAllBytes(embeddingsPath));
        assertEquals(DATASET_VERSION, dataset.path("datasetVersion").asText());
        assertEquals(DATASET_VERSION, embeddingsDoc.path("datasetVersion").asText());
        assertEquals(CORPUS_FINGERPRINT, snapshot.path("corpusFingerprint").path("value").asText());
        assertEquals(CORPUS_FINGERPRINT, embeddingsDoc.path("corpusFingerprint").asText());
        assertEquals(18, dataset.path("cases").size());
        assertEquals(18, embeddingsDoc.path("queryEmbeddings").size());

        Map<String, float[]> vectorsByQuestion = new HashMap<>();
        Map<String, Set<String>> caseIdsByQuestion = new HashMap<>();
        for (JsonNode item : embeddingsDoc.path("queryEmbeddings")) {
            String question = item.path("question").asText();
            assertFalse(question.isBlank(), "Blank query embedding input");
            assertEquals(sha256(question.getBytes(StandardCharsets.UTF_8)), item.path("questionSha256").asText());
            assertEquals(1024, item.path("dimensions").asInt());
            JsonNode vectorNode = item.path("vector");
            assertEquals(1024, vectorNode.size());
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vector.length; i++) {
                double value = vectorNode.get(i).asDouble();
                assertTrue(Double.isFinite(value), "Non-finite query embedding");
                vector[i] = (float) value;
                assertTrue(Float.isFinite(vector[i]), "Query embedding overflows float32");
            }
            assertNull(vectorsByQuestion.put(question, vector), "Duplicate question vector");
            Set<String> caseIds = new java.util.HashSet<>();
            item.path("caseIds").forEach(id -> caseIds.add(id.asText()));
            caseIdsByQuestion.put(question, Set.copyOf(caseIds));
        }

        Map<String, JsonNode> chunksById = new HashMap<>();
        for (JsonNode chunk : snapshot.path("chunks")) {
            chunksById.put(chunk.path("chunkId").asText(), chunk);
        }
        assertEquals(17, chunksById.size());
        Set<String> eligibleIds = new java.util.HashSet<>();
        for (JsonNode chunk : snapshot.path("chunks")) {
            if (chunk.path("eligibleForStudentRetrieval").asBoolean()) {
                eligibleIds.add(chunk.path("chunkId").asText());
            }
        }
        assertEquals(8, eligibleIds.size());

        String dbPassword = System.getenv("ZHKU_DB_PASSWORD");
        assertNotNull(dbPassword, "ZHKU_DB_PASSWORD must be set; secret values are never printed");
        String dbUrl = "jdbc:postgresql://127.0.0.1:55442/zhkuagent_eval_v2_rc2";
        DriverManagerDataSource base = new DriverManagerDataSource(dbUrl, "zhkuagent", dbPassword);
        DataSource readOnlyDataSource = new ReadOnlyTransactionDataSource(base);
        RetrievalEligibilityFilter eligibility = new RetrievalEligibilityFilter(
                Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC));
        EmbeddingClient cachedEmbeddingClient = new EmbeddingClient() {
            @Override public float[] embed(String text) {
                float[] vector = vectorsByQuestion.get(text);
                if (vector == null) throw new IllegalArgumentException("No cached query vector for exact evaluation question");
                return vector.clone();
            }
            @Override public int dimensions() { return 1024; }
        };
        HybridCandidateFuser fuser = new HybridCandidateFuser();
        HybridRetrievalService hybrid = new HybridRetrievalService(fuser);

        ObjectNode results = MAPPER.createObjectNode();
        results.put("schemaVersion", 1);
        results.put("datasetVersion", DATASET_VERSION);
        results.put("corpusFingerprint", CORPUS_FINGERPRINT);
        ObjectNode config = results.putObject("retrievalConfig");
        config.put("resultLimitK", RESULT_LIMIT);
        config.put("candidateLimitPerPath", CANDIDATE_LIMIT);
        config.put("rrfK", RRF_K);
        ObjectNode provenance = results.putObject("execution");
        provenance.put("database", "zhkuagent_eval_v2_rc2");
        provenance.put("databaseEndpoint", "127.0.0.1:55442");
        provenance.put("queryEmbeddingsFile", "query-embeddings.json");
        provenance.put("queryEmbeddingsSha256", sha256(Files.readAllBytes(embeddingsPath)));
        provenance.put("queryEmbeddingProvider", embeddingsDoc.path("provider").asText());
        provenance.put("queryEmbeddingModel", embeddingsDoc.path("model").asText());
        provenance.put("queryEmbeddingDimensions", 1024);
        provenance.put("queryEmbeddingApiCallsDuringRetrieval", 0);
        provenance.put("retrievalImplementation", "JdbcLexicalCandidateRetriever + JdbcVectorCandidateRetriever + HybridRetrievalService/HybridCandidateFuser");
        provenance.put("lexicalChunkingVersion", "real-source-lexical-baseline-v1");
        provenance.put("vectorChunkingVersion", "real-source-embedding-v1-20261026");
        provenance.put("readOnlyDatabaseTransactions", true);
        provenance.put("candidatePoolSize", eligibleIds.size());
        provenance.put("generatedAt", Instant.now().toString());

        ObjectNode trace = MAPPER.createObjectNode();
        trace.put("schemaVersion", 1);
        trace.put("datasetVersion", DATASET_VERSION);
        trace.put("corpusFingerprint", CORPUS_FINGERPRINT);
        trace.put("candidateLimitPerPath", CANDIDATE_LIMIT);
        trace.put("resultLimitK", RESULT_LIMIT);
        trace.put("rrfK", RRF_K);
        trace.put("queryEmbeddingsSha256", sha256(Files.readAllBytes(embeddingsPath)));
        trace.put("readOnlyDatabaseTransactions", true);
        ArrayNode traceRuns = trace.putArray("caseTraces");

        Map<String, ObjectNode> runNodes = new LinkedHashMap<>();
        ArrayNode runs = results.putArray("runs");
        String[] modes = {"ALL_ELIGIBLE_STUDENT_VISIBLE", "CONFIGURED_PUBLIC_ELECTIVE"};
        String[] algorithms = {"LEXICAL", "VECTOR", "RRF_HYBRID"};
        for (String mode : modes) {
            for (String algorithm : algorithms) {
                ObjectNode run = runs.addObject();
                run.put("algorithm", algorithm);
                run.put("scopeMode", mode);
                run.putArray("caseResults");
                runNodes.put(mode + "|" + algorithm, run);
            }
        }

        int primaryCaseCount = 0;
        int scopeCaseCount = 0;
        for (JsonNode testCase : dataset.path("cases")) {
            String caseClass = testCase.path("caseClass").asText();
            String mode;
            String scope;
            if ("POSITIVE_RETRIEVAL".equals(caseClass) || "AUTHORIZATION_EXCLUSION".equals(caseClass)) {
                mode = "ALL_ELIGIBLE_STUDENT_VISIBLE";
                scope = null;
                primaryCaseCount++;
            } else if ("SCOPE_CONTRACT".equals(caseClass)) {
                mode = "CONFIGURED_PUBLIC_ELECTIVE";
                scope = "PUBLIC_ELECTIVE";
                scopeCaseCount++;
            } else {
                throw new IllegalStateException("Unknown case class: " + caseClass);
            }
            String caseId = testCase.path("caseId").asText();
            String question = testCase.path("question").asText();
            assertTrue(vectorsByQuestion.containsKey(question), "Missing query vector for " + caseId);
            assertTrue(caseIdsByQuestion.get(question).contains(caseId), "Query vector case mapping mismatch for " + caseId);

            ChunkCandidateRetriever lexical = JdbcLexicalCandidateRetriever.forChineseBigramBaselineEvaluation(
                    readOnlyDataSource, eligibility, LEXICAL_PROFILE, scope, "real-source-lexical-baseline-v1");
            ChunkCandidateRetriever vector = JdbcVectorCandidateRetriever.forOfflineEvaluation(
                    readOnlyDataSource, eligibility, VECTOR_PROFILE, scope, cachedEmbeddingClient);

            List<RetrievedChunk> lexicalTop8 = lexical.retrieve(question, CANDIDATE_LIMIT);
            List<RetrievedChunk> vectorTop8 = vector.retrieve(question, CANDIDATE_LIMIT);
            List<RetrievedChunk> hybridTop5 = hybrid.retrieve(
                    question, CANDIDATE_LIMIT, RESULT_LIMIT, lexical, vector).chunks();
            List<RankedCandidate> lexicalRanks = ranked(lexicalTop8);
            List<RankedCandidate> vectorRanks = ranked(vectorTop8);
            List<ScoredCandidate> fused = fuser.fuse(lexicalRanks, vectorRanks);
            List<String> fusedTop5Ids = fused.stream().limit(RESULT_LIMIT).map(ScoredCandidate::candidateId).toList();
            List<String> hybridServiceIds = hybridTop5.stream().map(c -> c.chunkId().toString()).toList();
            assertEquals(fusedTop5Ids, hybridServiceIds, "Hybrid service/fuser disagreement for " + caseId);

            for (RetrievedChunk hit : lexicalTop8) assertTrue(eligibleIds.contains(hit.chunkId().toString()), "Lexical returned chunk outside frozen eligible pool");
            for (RetrievedChunk hit : vectorTop8) assertTrue(eligibleIds.contains(hit.chunkId().toString()), "Vector returned chunk outside frozen eligible pool");
            for (RetrievedChunk hit : hybridTop5) assertTrue(eligibleIds.contains(hit.chunkId().toString()), "RRF returned chunk outside frozen eligible pool");

            appendScoredCase(runNodes.get(mode + "|LEXICAL"), caseId, lexicalTop8, chunksById);
            appendScoredCase(runNodes.get(mode + "|VECTOR"), caseId, vectorTop8, chunksById);
            appendScoredCase(runNodes.get(mode + "|RRF_HYBRID"), caseId, hybridTop5, chunksById);
            appendTrace(traceRuns, mode, caseId, question, lexicalTop8, vectorTop8, fused, chunksById);
        }
        assertEquals(16, primaryCaseCount);
        assertEquals(2, scopeCaseCount);
        assertEquals(6, runs.size());
        for (JsonNode run : runs) {
            int expected = "ALL_ELIGIBLE_STUDENT_VISIBLE".equals(run.path("scopeMode").asText()) ? 16 : 2;
            assertEquals(expected, run.path("caseResults").size());
        }

        writeLf(resultsPath, results);
        writeLf(tracePath, trace);
        System.out.println("RC2_RETRIEVAL_RESULTS=" + resultsPath);
        System.out.println("RC2_RETRIEVAL_TRACE=" + tracePath);
        System.out.println("QUERY_EMBEDDINGS_SHA256=" + provenance.path("queryEmbeddingsSha256").asText());
        System.out.println("PRIMARY_CASES=" + primaryCaseCount + "; SCOPE_CASES=" + scopeCaseCount);
        System.out.println("ALGORITHMS=LEXICAL,VECTOR,RRF_HYBRID");
        System.out.println("CANDIDATE_LIMIT=8; RESULT_LIMIT=5; RRF_K=60");
        System.out.println("READ_ONLY_DB_TRANSACTIONS=true; RETRIEVAL_TIME_EMBEDDING_API_CALLS=0");
        System.out.println("RETRIEVAL_RESULTS_SHA256=" + sha256(Files.readAllBytes(resultsPath)));
        System.out.println("RETRIEVAL_TRACE_SHA256=" + sha256(Files.readAllBytes(tracePath)));
    }

    private static List<RankedCandidate> ranked(List<RetrievedChunk> chunks) {
        List<RankedCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ranked.add(new RankedCandidate(chunks.get(i).chunkId().toString(), i + 1));
        }
        return List.copyOf(ranked);
    }

    private static void appendScoredCase(ObjectNode run, String caseId, List<RetrievedChunk> hits,
                                         Map<String, JsonNode> chunksById) {
        ObjectNode caseResult = ((ArrayNode) run.path("caseResults")).addObject();
        caseResult.put("caseId", caseId);
        ArrayNode rankedChunks = caseResult.putArray("rankedChunks");
        for (RetrievedChunk hit : hits.stream().limit(RESULT_LIMIT).toList()) {
            rankedChunks.add(hitNode(hit, chunksById.get(hit.chunkId().toString()), null));
        }
    }

    private static void appendTrace(ArrayNode traceRuns, String mode, String caseId, String question,
                                   List<RetrievedChunk> lexical, List<RetrievedChunk> vector,
                                   List<ScoredCandidate> fused, Map<String, JsonNode> chunksById) {
        ObjectNode row = traceRuns.addObject();
        row.put("scopeMode", mode);
        row.put("caseId", caseId);
        row.put("question", question);
        ArrayNode lexicalArray = row.putArray("lexicalTop8");
        for (int i = 0; i < lexical.size(); i++) lexicalArray.add(hitNode(lexical.get(i), chunksById.get(lexical.get(i).chunkId().toString()), i + 1));
        ArrayNode vectorArray = row.putArray("vectorTop8");
        for (int i = 0; i < vector.size(); i++) vectorArray.add(hitNode(vector.get(i), chunksById.get(vector.get(i).chunkId().toString()), i + 1));
        ArrayNode hybridArray = row.putArray("rrfHybridTop5");
        int rank = 0;
        for (ScoredCandidate candidate : fused) {
            if (rank >= RESULT_LIMIT) break;
            rank++;
            JsonNode chunk = chunksById.get(candidate.candidateId());
            ObjectNode hit = hybridArray.addObject();
            hit.put("rank", rank);
            hit.put("chunkId", candidate.candidateId());
            hit.put("rrfScore", candidate.score());
            if (chunk != null) {
                hit.put("sourceId", chunk.path("sourceId").asText());
                hit.put("contentChecksum", chunk.path("contentChecksum").asText());
                hit.put("knowledgeScope", chunk.path("knowledgeScope").asText());
                hit.put("pageNumber", chunk.path("pageNumber").asInt());
                hit.put("sourceLocator", chunk.path("sourceLocator").asText());
            }
        }
    }

    private static ObjectNode hitNode(RetrievedChunk hit, JsonNode chunk, Integer rank) {
        if (chunk == null) throw new IllegalStateException("Returned chunk absent from frozen snapshot: " + hit.chunkId());
        ObjectNode node = MAPPER.createObjectNode();
        if (rank != null) node.put("rank", rank);
        node.put("chunkId", hit.chunkId().toString());
        node.put("sourceId", hit.sourceId().toString());
        node.put("contentChecksum", hit.contentChecksum());
        node.put("knowledgeScope", chunk.path("knowledgeScope").asText());
        if (hit.pageNumber() == null) node.putNull("pageNumber"); else node.put("pageNumber", hit.pageNumber());
        node.put("sourceLocator", hit.sourceLocator());
        return node;
    }

    private static void writeLf(Path path, JsonNode node) throws IOException {
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        json = json.replace("\r\n", "\n").replace("\r", "\n");
        Files.writeString(path, json + "\n", StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class ReadOnlyTransactionDataSource extends DelegatingDataSource {
        ReadOnlyTransactionDataSource(DataSource target) { super(target); }

        @Override public Connection getConnection() throws SQLException {
            return readOnly(super.getConnection());
        }

        @Override public Connection getConnection(String username, String password) throws SQLException {
            return readOnly(super.getConnection(username, password));
        }

        private Connection readOnly(Connection connection) throws SQLException {
            try {
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET TRANSACTION READ ONLY");
                }
                return connection;
            } catch (SQLException failure) {
                try { connection.close(); } catch (SQLException ignored) { }
                throw failure;
            }
        }
    }
}