package com.zhku.knowledge.retrieval;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 实际供应商产物重用的精确JDBC排名对照；不是在线模型或独立性能测试。 */
class RuleCheck2025VectorCalibrationTest {
    private static final String ARTIFACT="docs/evidence/rule-check-refactor/2026-10-09/c04-vector-01/provider-capture/manifest.json";
    private static final String ARTIFACT_SHA="738b736b240b069adf7d9dc59cf7be07fc852c9b7974ebfe8a7151ebb9cb77d0";
    private Path root;private Reviewed2025Trial trial;private javax.sql.DataSource ds;
    private RuleCheckDevelopmentInputs.Loaded inputs;private RuleCheckEmbeddingArtifacts artifacts;
    private final String profile="c04-vector-"+UUID.randomUUID();private final UUID run=UUID.randomUUID();
    static void requireHealth(String mode,RetrievalOutcome outcome) {
        assertThat(outcome.vectorAvailable()).isEqualTo(!mode.equals("LEXICAL"));
        assertThat(outcome.lexicalAvailable()).isEqualTo(!mode.equals("VECTOR"));
    }
    @BeforeEach void setUp() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        var texts=new LinkedHashMap<String,String>();for(var chunk:trial.bundle.path("chunks"))texts.put("A%02d".formatted(chunk.path("article").asInt()),chunk.path("content").asText());
        for(var row:inputs.reference().path("records"))texts.put(row.path("reviewId").asText(),row.path("question").asText());
        // 完整冻结/原件及真实供应商指纹先核对，缺失或伪造产物不能先碰数据库。
        artifacts=RuleCheckEmbeddingArtifacts.load(root.resolve(ARTIFACT),ARTIFACT_SHA,texts);
        String url=System.getProperty("zhku.db.url");RuleCheckDatabaseTestTarget.verify(url);String user=System.getProperty("zhku.db.user","zhkuagent"),password=ZhkuDatabaseTestConfig.databasePassword();
        ds=new DriverManagerDataSource(url,user,password);ZhkuFlywayTestSupport.migrate(url,user,password);trial.importAndBind(ds);
        try(var c=ds.getConnection()){
            c.setAutoCommit(false);
            try {
                Reviewed2025Trial.execute(c,"INSERT INTO embedding_profile(profile_id,provider,model,dimensions,status) VALUES(?,'DASHSCOPE','text-embedding-v4',1024,'BUILDING')",profile);
                Reviewed2025Trial.execute(c,"INSERT INTO retrieval_index_run(id,profile_id,chunking_version,status) VALUES(?,?,?,'RUNNING')",run,profile,Reviewed2025Trial.CUT);
                for(var b:trial.bindings.values()){
                    float[] vector=artifacts.vector("A%02d".formatted(b.article()),b.text());var literal=new StringJoiner(",","[","]");for(float v:vector)literal.add(Float.toString(v));
                    Reviewed2025Trial.execute(c,"INSERT INTO retrieval_index_entry(id,run_id,profile_id,chunk_id,source_version,chunking_version,content_checksum,embedding) VALUES(?,?,?,?,?,?,?,CAST(? AS vector))",UUID.randomUUID(),run,profile,b.chunkId(),Reviewed2025Trial.REVISION,Reviewed2025Trial.CUT,b.checksum(),literal.toString());
                }
                Reviewed2025Trial.execute(c,"UPDATE retrieval_index_run SET status='COMPLETED',finished_at=CURRENT_TIMESTAMP,indexed_entries=10 WHERE id=?",run);
                Reviewed2025Trial.execute(c,"UPDATE embedding_profile SET status='RETIRED' WHERE profile_id=?",trial.profile);
                Reviewed2025Trial.execute(c,"UPDATE embedding_profile SET status='ACTIVE',activated_at=CURRENT_TIMESTAMP WHERE profile_id=?",profile);c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
    }
    @AfterEach void cleanUp() throws Exception {
        if(ds!=null)try(var c=ds.getConnection()){
            Reviewed2025Trial.execute(c,"DELETE FROM retrieval_index_entry WHERE profile_id=?",profile);Reviewed2025Trial.execute(c,"DELETE FROM retrieval_index_run WHERE profile_id=?",profile);Reviewed2025Trial.execute(c,"DELETE FROM embedding_profile WHERE profile_id=?",profile);
        }
        if(trial!=null)trial.close();
    }
    private JdbcLexicalCandidateRetriever lexical(){return JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);}
    private JdbcVectorCandidateRetriever vector(){
        return new JdbcVectorCandidateRetriever(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT,new EmbeddingClient(){
            public int dimensions(){return 1024;}
            public float[] embed(String query){for(var row:inputs.reference().path("records"))if(row.path("question").asText().equals(query))return artifacts.vector(row.path("reviewId").asText(),query);throw new IllegalArgumentException("非本批原问句");}
        });
    }
    @Test void actualVectorAndHybridRankingsAreComparedOnOriginalQuestions() throws Exception {
        var configurations=new ArrayList<Map<String,Object>>();var fusion=new HybridRetrievalService(new HybridCandidateFuser());
        for(String mode:List.of("LEXICAL","VECTOR","HYBRID"))for(int budget:List.of(10,20,40)){
            var rows=new ArrayList<Map<String,Object>>();int required=0,found=0;
            for(var reference:inputs.reference().path("records")){
                String question=reference.path("question").asText();var lexCandidates=new ArrayList<RetrievedChunk>();var vecCandidates=new ArrayList<RetrievedChunk>();long started=System.nanoTime();
                var outcome=fusion.retrieve(question,budget,5,(q,l)->{if(mode.equals("VECTOR"))throw new RetrieverUnavailableException("配置禁用词法");var actual=lexical().retrieve(q,l);lexCandidates.addAll(actual);return actual;},
                    (q,l)->{if(mode.equals("LEXICAL"))throw new RetrieverUnavailableException("配置禁用向量");var actual=vector().retrieve(q,l);vecCandidates.addAll(actual);return actual;});
                long nanos=System.nanoTime()-started;
                // 人工依据仅在实际检索完成后计分，不能参与候选排名或模型输入。
                var needed=new LinkedHashSet<Integer>();reference.path("humanReview").path("acceptedEvidence").forEach(n->needed.add(n.path("article").asInt()));
                var selected=new HashSet<UUID>();outcome.chunks().forEach(c->selected.add(c.chunkId()));var missing=needed.stream().filter(a->!selected.contains(trial.bindings.get(a).chunkId())).toList();required+=needed.size();found+=needed.size()-missing.size();
                var row=new LinkedHashMap<String,Object>();row.put("caseId",reference.path("reviewId").asText());row.put("question",question);row.put("outcome",outcome);row.put("actualLexicalCandidates",List.copyOf(lexCandidates));row.put("actualVectorCandidates",List.copyOf(vecCandidates));row.put("requiredArticles",needed);row.put("missingAt5",missing);row.put("retrievalNanosExcludingProvider",nanos);rows.add(row);
            }
            configurations.add(Map.of("mode",mode,"candidateBudget",budget,"resultBudget",5,"requiredOccurrences",required,"foundOccurrences",found,"recallAt5",(double)found/required,"cases",rows));
        }
        trial.save("vector-hybrid-calibration.json",Map.of("configurations",configurations,"bindings",trial.bindings.values(),"profileId",profile,"indexRunId",run,"artifactPath",ARTIFACT,"artifactSha256",ARTIFACT_SHA,"providerCallsDuringDatabaseTest",0,"queryVectorMode","CONTROLLED_REUSE_OF_ACTUAL_PROVIDER_ARTIFACT","defaultSwitched",false,"reportCasesConsumed",0));
        for(var configuration:configurations)for(var row:(List<Map<String,Object>>)configuration.get("cases")){
            requireHealth((String)configuration.get("mode"),(RetrievalOutcome)row.get("outcome"));
            var lex=(List<RetrievedChunk>)row.get("actualLexicalCandidates");var vec=(List<RetrievedChunk>)row.get("actualVectorCandidates");assertThat(lex.size()).isLessThanOrEqualTo((int)configuration.get("candidateBudget"));assertThat(vec.size()).isLessThanOrEqualTo((int)configuration.get("candidateBudget"));
            var all=new ArrayList<>(lex);all.addAll(vec);assertThat(all).containsAll(((RetrievalOutcome)row.get("outcome")).chunks());
        }
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);assertThat(Reviewed2025Trial.sha(Files.readAllBytes(root.resolve(ARTIFACT)))).isEqualTo(ARTIFACT_SHA);
    }
    @Test void vectorEligibilityCannotBeBypassedBySimilarity() throws Exception {
        String query=inputs.reference().path("records").get(0).path("question").asText();var retriever=vector();assertThat(retriever.retrieve(query,20)).isNotEmpty();var rows=new ArrayList<Map<String,Object>>();
        for(String mutation:List.of("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY' WHERE id=?","UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=FALSE WHERE id=?","UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',publication_status='DRAFT' WHERE id=?","UPDATE knowledge_source SET knowledge_scope='OTHER' WHERE id=?")){
            trial.update(mutation,trial.source);rows.add(Map.of("mutation",mutation,"actualCandidates",retriever.retrieve(query,20)));trial.update("UPDATE knowledge_source SET visibility_scope='STUDENT_VISIBLE',sharing_rights_confirmed=TRUE,publication_status='PUBLISHED',knowledge_scope=? WHERE id=?",Reviewed2025Trial.SCOPE,trial.source);
        }
        trial.update("UPDATE retrieval_index_entry SET chunking_version='other' WHERE profile_id=?",profile);rows.add(Map.of("mutation","WRONG_CHUNKING","actualCandidates",retriever.retrieve(query,20)));
        trial.update("UPDATE retrieval_index_entry SET chunking_version=?,content_checksum='bad' WHERE profile_id=?",Reviewed2025Trial.CUT,profile);rows.add(Map.of("mutation","INDEX_HASH_MISMATCH","actualCandidates",retriever.retrieve(query,20)));
        trial.save("vector-eligibility-controls.json",rows);assertThat(rows).allSatisfy(r->assertThat((List<?>)r.get("actualCandidates")).isEmpty());
    }
}
