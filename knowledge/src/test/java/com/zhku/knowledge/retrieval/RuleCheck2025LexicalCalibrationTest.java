package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** C04词法单路真实库对比；不执行报告集或按答案排序。 */
class RuleCheck2025LexicalCalibrationTest {
    static void requireObservedPaths(RetrievalOutcome outcome){
        // 零候选只有在词法路健康时才是质量观测；数据库不可用不能伪装成基线复现。
        assertThat(outcome.lexicalAvailable()).isTrue();assertThat(outcome.vectorAvailable()).isFalse();
    }
    private Reviewed2025Trial trial;private javax.sql.DataSource dataSource;private Path root;
    private RuleCheckDevelopmentInputs.Loaded inputs;
    @BeforeEach void setUp() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        String target=System.getProperty("zhku.db.url");RuleCheckDatabaseTestTarget.verify(target);
        String user=System.getProperty("zhku.db.user","zhkuagent"),password=ZhkuDatabaseTestConfig.databasePassword();
        dataSource=new DriverManagerDataSource(target,user,password);ZhkuFlywayTestSupport.migrate(target,user,password);trial.importAndBind(dataSource);
    }
    @AfterEach void cleanUp() throws Exception {if(trial!=null)trial.close();}
    private JdbcLexicalCandidateRetriever experimental(){return JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(dataSource,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);}
    @Test void originalQuestionsHaveActualRankingsAtThreeBudgets() throws Exception {
        var legacy=new JdbcLexicalCandidateRetriever(dataSource,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
        var configurations=new ArrayList<Map<String,Object>>();
        for(boolean chinese:List.of(false,true))for(int budget:List.of(10,20,40)){
            var retriever=chinese?experimental():legacy;var rows=new ArrayList<Map<String,Object>>();int required=0,found=0;
            for(var reference:inputs.reference().path("records")){
                String question=reference.path("question").asText();long start=System.nanoTime();
                var candidates=new ArrayList<RetrievedChunk>();
                var outcome=new HybridRetrievalService(new HybridCandidateFuser()).retrieve(question,budget,5,(q,l)->{var observed=retriever.retrieve(q,l);candidates.addAll(observed);return observed;},(q,l)->{throw new RetrieverUnavailableException("配置禁用向量");});
                long nanos=System.nanoTime()-start;var needed=new LinkedHashSet<Integer>();reference.path("humanReview").path("acceptedEvidence").forEach(n->needed.add(n.path("article").asInt()));
                var actual=new HashSet<UUID>();outcome.chunks().forEach(c->actual.add(c.chunkId()));
                var missing=needed.stream().filter(a->!actual.contains(trial.bindings.get(a).chunkId())).toList();required+=needed.size();found+=needed.size()-missing.size();
                rows.add(Map.of("caseId",reference.path("reviewId").asText(),"question",question,"outcome",outcome,"actualLexicalCandidates",List.copyOf(candidates),"requiredArticles",needed,"missingAt5",missing,"nanos",nanos));
            }
            configurations.add(Map.of("strategy",chinese?"HAN_BIGRAM_IDF":"LEGACY_WHOLE_PHRASE","candidateBudget",budget,"returnBudget",5,"requiredOccurrences",required,"foundOccurrences",found,"recallAt5",(double)found/required,"cases",rows));
        }
        trial.save("lexical-calibration.json",Map.of("configurations",configurations,"bindings",trial.bindings.values(),"profileId",trial.profile,"sourceId",trial.source,"controlsSha256",inputs.controlsSha256(),"experimentType","ACTUAL_JDBC_LEXICAL_ONLY","providerCalls",0,"reportCasesConsumed",0,"qualityGatePassed",false,"defaultSwitched",false));
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
        for(var configuration:configurations)for(var row:(List<Map<String,Object>>)configuration.get("cases")){
            var outcome=(RetrievalOutcome)row.get("outcome");requireObservedPaths(outcome);
            var actual=(List<RetrievedChunk>)row.get("actualLexicalCandidates");assertThat(actual.size()).isLessThanOrEqualTo((int)configuration.get("candidateBudget"));
            assertThat(actual).containsAll(outcome.chunks());
        }
        assertThat(configurations).filteredOn(c->c.get("strategy").equals("LEGACY_WHOLE_PHRASE")).allSatisfy(c->assertThat(c.get("foundOccurrences")).isEqualTo(0));
        assertThat(configurations).filteredOn(c->c.get("strategy").equals("HAN_BIGRAM_IDF")).allSatisfy(c->assertThat((double)c.get("recallAt5")).isGreaterThanOrEqualTo(.9));
    }
    @Test void experimentalRankingCannotBypassActualEligibility() throws Exception {
        var r=experimental();String query="公共选修课每学期最高几学分";assertThat(r.retrieve(query,20)).isNotEmpty();
        var outcomes=new ArrayList<Map<String,Object>>();
        // schema禁止未分享或草稿仍为学生可见；反例必须是可存储的合法状态，不移除约束造坏行。
        for(String sql:List.of("UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY' WHERE id=?","UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=FALSE WHERE id=?","UPDATE knowledge_source SET visibility_scope='INTERNAL_ONLY',publication_status='DRAFT' WHERE id=?","UPDATE knowledge_source SET knowledge_scope='OTHER' WHERE id=?")){
            trial.update(sql,trial.source);var chunks=r.retrieve(query,20);outcomes.add(Map.of("mutation",sql,"actualCandidates",chunks));
            trial.update("UPDATE knowledge_source SET visibility_scope='STUDENT_VISIBLE',sharing_rights_confirmed=TRUE,publication_status='PUBLISHED',knowledge_scope=? WHERE id=?",Reviewed2025Trial.SCOPE,trial.source);
        }
        trial.update("UPDATE retrieval_index_entry SET chunking_version='other' WHERE profile_id=?",trial.profile);outcomes.add(Map.of("mutation","WRONG_CHUNKING","actualCandidates",r.retrieve(query,20)));
        trial.update("UPDATE retrieval_index_entry SET chunking_version=?,content_checksum='bad' WHERE profile_id=?",Reviewed2025Trial.CUT,trial.profile);outcomes.add(Map.of("mutation","INDEX_HASH_MISMATCH","actualCandidates",r.retrieve(query,20)));
        trial.save("lexical-eligibility-controls.json",outcomes);
        assertThat(outcomes).allSatisfy(o->assertThat((List<?>)o.get("actualCandidates")).isEmpty());
    }
}
