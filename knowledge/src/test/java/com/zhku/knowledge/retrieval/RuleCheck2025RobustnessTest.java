package com.zhku.knowledge.retrieval;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.publication.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.rag.CitationAuditRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 混合实际旧10条与明确合成的同范围近词压力；固定选题不冒充自然语言路由。 */
class RuleCheck2025RobustnessTest {
    static void requireAuditBindings(RuleCheckDevelopmentCapture.Attempt attempt,List<CitationAuditRecord> audits){
        assertThat(attempt.retrieval()).isNotNull();assertThat(audits).hasSize(attempt.retrieval().chunks().size());
        assertThat(audits).extracting(CitationAuditRecord::citationId).doesNotHaveDuplicates();
        for(var chunk:attempt.retrieval().chunks())assertThat(audits).anySatisfy(a->{
            assertThat(a.chunkId()).isEqualTo(chunk.chunkId());assertThat(a.sourceId()).isEqualTo(chunk.sourceId());assertThat(a.sourceRevision()).isEqualTo(chunk.sourceVersion());
            assertThat(a.contentChecksum()).isEqualToIgnoringCase(chunk.contentChecksum());assertThat(a.content()).isEqualTo(chunk.content());assertThat(a.route()).isEqualTo("RULE_CHECK_CAPTURE");
        });
    }
    private Path root;private Reviewed2025Trial trial;private javax.sql.DataSource ds;private RuleCheckDevelopmentInputs.Loaded inputs;
    private final List<UUID> noiseSources=new ArrayList<>(),noiseRuns=new ArrayList<>();
    static void requireLexicalHealth(RetrievalOutcome outcome){assertThat(outcome.lexicalAvailable()).isTrue();assertThat(outcome.vectorAvailable()).isFalse();}
    @BeforeEach void setUp() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        String url=System.getProperty("zhku.db.url");RuleCheckDatabaseTestTarget.verify(url);String user=System.getProperty("zhku.db.user","zhkuagent"),password=ZhkuDatabaseTestConfig.databasePassword();
        ds=new DriverManagerDataSource(url,user,password);ZhkuFlywayTestSupport.migrate(url,user,password);trial.importAndBind(ds);
    }
    @AfterEach void cleanUp() throws Exception {
        if(ds!=null){
            for(UUID source:noiseSources){
                trial.update("DELETE FROM citation_audit WHERE index_entry_id IN (SELECT e.id FROM retrieval_index_entry e JOIN knowledge_chunk c ON c.id=e.chunk_id WHERE c.source_id=?)",source);
                trial.update("DELETE FROM retrieval_index_entry WHERE chunk_id IN (SELECT id FROM knowledge_chunk WHERE source_id=?)",source);
            }
            for(UUID run:noiseRuns)trial.update("DELETE FROM retrieval_index_run WHERE id=?",run);
            for(UUID source:noiseSources){trial.update("DELETE FROM knowledge_chunk WHERE source_id=?",source);trial.update("DELETE FROM knowledge_source WHERE id=?",source);}
        }
        if(trial!=null)trial.close();
    }
    private JdbcLexicalCandidateRetriever lexical(){return JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);}
    private List<CitationAuditRecord> readPersistedAudits(List<Object> attempts) throws Exception {
        var rows=new ArrayList<CitationAuditRecord>();
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT a.citation_id,a.route,a.created_at,e.chunk_id,k.source_id,e.source_version,e.content_checksum,k.content FROM citation_audit a JOIN retrieval_index_entry e ON e.id=a.index_entry_id JOIN knowledge_chunk k ON k.id=e.chunk_id WHERE a.citation_id=? AND e.profile_id=?")){
            for(var value:attempts){var audit=(CitationAuditRecord)value;p.setString(1,audit.citationId());p.setString(2,trial.profile);try(var r=p.executeQuery()){while(r.next())rows.add(new CitationAuditRecord(r.getString("citation_id"),r.getObject("chunk_id",UUID.class),r.getString("route"),r.getTimestamp("created_at").toInstant(),r.getObject("source_id",UUID.class),r.getString("source_version"),r.getString("content_checksum"),r.getString("content")));}}
        }
        return List.copyOf(rows);
    }
    private static List<RetrievedChunk> disabledVector(String q,int l){throw new RetrieverUnavailableException("本批显式禁用向量，无供应商调用");}
    private RuleCheckApplicationService app(String selectedId,ChunkCandidateRetriever lex,ChunkCandidateRetriever vec,List<Object> audits,List<String> errors){
        var verifier=new RuleCheckEvidenceVerifier((s,c)->{try{return trial.reader().read(s,c);}catch(RuntimeException failure){errors.add("CURRENT_READER:"+failure.getClass().getSimpleName());throw failure;}});
        var actualAudit=new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE);
        var capture=new RuleCheckEvidenceCapture(verifier,()->"c04-noise-"+UUID.randomUUID(),record->{try{actualAudit.record(record);audits.add(record);}catch(RuntimeException failure){errors.add("AUDIT:"+failure.getClass().getSimpleName());throw failure;}},Clock.systemUTC());
        return new RuleCheckApplicationService(new RuleCheckRuntimeSettings(trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5),
            ()->List.copyOf(trial.catalogue),(q,options)->selectedId,new HybridRetrievalService(new HybridCandidateFuser()),lex,vec,capture,new RuleCheckDecisionService(verifier));
    }
    private void addNoise(int count) throws Exception {
        UUID source=UUID.randomUUID(),run=UUID.randomUUID();noiseSources.add(source);noiseRuns.add(run);
        var chunks=new ArrayList<ReviewedKnowledgeChunk>();var texts=RuleCheckNoiseFixtures.texts(count);
        // 发布器要求正整数页码；1只表示合成资料页，定位文案明确不是手册PDF页码。
        for(int i=0;i<texts.size();i++)chunks.add(new ReviewedKnowledgeChunk(i,"合成近词族"+(i%5),"合成压力样本"+(i+1)+"，不是PDF页码",1,texts.get(i)));
        // 仅临时库合成资格夹具，APPROVED/2025字段模拟可进入同索引的机械状态，不是B03人工许可。
        var publication=new ReviewedKnowledgePublication(source,"C04合成干扰资料，非政策","TEST_NOISE",Reviewed2025Trial.REVISION,null,
            "仅专用临时库合成测试，不供学生展示","合成资格夹具，未经真实政策审核",true,true,ReviewedKnowledgePublication.APPROVED_STATUS,Instant.now(),chunks);
        new ReviewedKnowledgePublisher(ds).publishReplacingScope(publication,"C04_SYNTHETIC_NOISE",Reviewed2025Trial.SCOPE);
        try(var c=ds.getConnection()){
            c.setAutoCommit(false);
            try{
                Reviewed2025Trial.execute(c,"INSERT INTO retrieval_index_run(id,profile_id,chunking_version,status) VALUES(?,?,?,'RUNNING')",run,trial.profile,Reviewed2025Trial.CUT);
                try(var p=c.prepareStatement("SELECT id,content_checksum FROM knowledge_chunk WHERE source_id=?")){p.setObject(1,source);try(var rows=p.executeQuery()){while(rows.next())
                    Reviewed2025Trial.execute(c,"INSERT INTO retrieval_index_entry(id,run_id,profile_id,chunk_id,source_version,chunking_version,content_checksum,embedding) VALUES(?,?,?,?,?,?,?,NULL)",UUID.randomUUID(),run,trial.profile,rows.getObject("id",UUID.class),Reviewed2025Trial.REVISION,Reviewed2025Trial.CUT,rows.getString("content_checksum"));}}
                Reviewed2025Trial.execute(c,"UPDATE retrieval_index_run SET status='COMPLETED',indexed_entries=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",count,run);c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
    }
    private int activeIndexedCount() throws Exception {
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM retrieval_index_entry e JOIN knowledge_chunk k ON k.id=e.chunk_id JOIN knowledge_source s ON s.id=k.source_id JOIN retrieval_index_run r ON r.id=e.run_id WHERE e.profile_id=? AND s.publication_status='PUBLISHED' AND s.visibility_scope='STUDENT_VISIBLE' AND s.sharing_rights_confirmed=TRUE AND s.knowledge_scope=? AND e.source_version=s.source_version AND e.content_checksum=k.content_checksum AND e.chunking_version=? AND r.profile_id=e.profile_id AND r.chunking_version=e.chunking_version AND r.status='COMPLETED'")){
            p.setString(1,trial.profile);p.setString(2,Reviewed2025Trial.SCOPE);p.setString(3,Reviewed2025Trial.CUT);try(var rows=p.executeQuery()){rows.next();return rows.getInt(1);}
        }
    }
    @Test void comparesDuplicateSuppressionWithBaselineOnIdenticalEligibleCorpora() throws Exception {
        var configurations=new ArrayList<Map<String,Object>>();
        for(int noise:List.of(0,40,100)){
            if(noise>0)addNoise(noise);assertThat(activeIndexedCount()).isEqualTo(10+noise);
            for(boolean diverse:List.of(false,true)){
                var retriever=diverse?lexical():JdbcLexicalCandidateRetriever.forChineseBigramBaselineEvaluation(ds,
                    new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
                int needed=0,found=0;var cases=new ArrayList<Map<String,Object>>();
                for(var ref:inputs.reference().path("records")){
                    var candidates=retriever.retrieve(ref.path("question").asText(),20);
                    var outcome=new HybridRetrievalService(new HybridCandidateFuser()).retrieve(ref.path("question").asText(),20,5,
                        (q,l)->candidates,RuleCheck2025RobustnessTest::disabledVector);
                    var ids=outcome.chunks().stream().map(RetrievedChunk::chunkId).toList();
                    var articles=new LinkedHashSet<Integer>();ref.path("humanReview").path("acceptedEvidence").forEach(e->articles.add(e.path("article").asInt()));
                    var missing=articles.stream().filter(a->!ids.contains(trial.bindings.get(a).chunkId())).toList();
                    needed+=articles.size();found+=articles.size()-missing.size();
                    cases.add(Map.of("caseId",ref.path("reviewId").asText(),"requiredArticles",articles,"missingAt5",missing,
                        "actualCandidates",candidates,"actualOutcome",outcome));
                }
                configurations.add(Map.of("noiseCount",noise,"mode",diverse?"DUPLICATE_SUPPRESSION":"BASELINE",
                    "foundAt5",found,"requiredOccurrences",needed,"cases",cases));
            }
        }
        trial.save("package01-retrieval-comparison.json",Map.of("configurations",configurations,"providerCalls",0,
            "reportCasesConsumed",0,"onlyChangedFactor","same-source same-version repeated-record suppression","defaultRuntimeSwitched",false));
        for(var c:configurations)if(c.get("mode").equals("DUPLICATE_SUPPRESSION"))
            assertThat((double)(int)c.get("foundAt5")/(int)c.get("requiredOccurrences")).as("noise="+c.get("noiseCount")).isGreaterThanOrEqualTo(.9);
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
    @Test void originalQueriesExposeNoiseRecallAndBoundedFacts() throws Exception {
        var configurations=new ArrayList<Map<String,Object>>();
        for(int noise:List.of(0,40,100)){
            if(noise>0)addNoise(noise);int actualCount=activeIndexedCount();assertThat(actualCount).isEqualTo(10+noise);
            for(int budget:List.of(10,20,40)){
                var cases=new ArrayList<Map<String,Object>>();int required=0,foundAt5=0,foundAtCandidateBudget=0;
                for(JsonNode reference:inputs.reference().path("records")){
                    String id=reference.path("reviewId").asText(),question=reference.path("question").asText(),selectedId=inputs.controls().path("controls").path(id).asText();
                    var row=new LinkedHashMap<String,Object>();RetrievalOutcome outcome;List<RetrievedChunk> candidates;
                    if(budget==20){
                        var audits=new ArrayList<Object>();var errors=new ArrayList<String>();
                        var attempt=new RuleCheckDevelopmentCapture().collect(new RuleCheckDevelopmentCapture.Input(id,question,selectedId,Map.of()),lexical(),RuleCheck2025RobustnessTest::disabledVector,(lex,vec)->app(selectedId,lex,vec,audits,errors)::checkWithContext);
                        outcome=attempt.retrieval();candidates=attempt.lexical().candidates();row.put("attempt",attempt);row.put("stageErrors",errors);row.put("actualAuditRecords",audits);
                        try{row.put("persistedAuditRecords",readPersistedAudits(audits));}catch(Exception failure){row.put("persistedAuditRecords",List.of());errors.add("AUDIT_READ_BACK:"+failure.getClass().getSimpleName());}
                        var locations=new ArrayList<Object>();if(attempt.output()!=null)for(var fact:attempt.output().decision().confirmedFacts())for(var quote:fact.evidence())locations.add(RuleCheckDevelopmentCapture.locate(quote,trial.reader()::readLocated));
                        row.put("actualOriginalLocations",locations);row.put("expectedStatus",reference.path("humanReview").path("acceptedStatus").asText());
                    }else{
                        var observed=new ArrayList<RetrievedChunk>();long start=System.nanoTime();outcome=new HybridRetrievalService(new HybridCandidateFuser()).retrieve(question,budget,5,(q,l)->{var values=lexical().retrieve(q,l);observed.addAll(values);return values;},RuleCheck2025RobustnessTest::disabledVector);
                        candidates=List.copyOf(observed);row.put("retrievalNanos",System.nanoTime()-start);
                    }
                    // 必要依据只在原问句实际检索完成后计分，合成干扰不按题号/答案构造。
                    var needed=new LinkedHashSet<Integer>();reference.path("humanReview").path("acceptedEvidence").forEach(e->needed.add(e.path("article").asInt()));
                    Set<UUID> finalIds=new HashSet<>(),candidateIds=new HashSet<>();if(outcome!=null)outcome.chunks().forEach(c->finalIds.add(c.chunkId()));candidates.forEach(c->candidateIds.add(c.chunkId()));
                    var missingAt5=needed.stream().filter(a->!finalIds.contains(trial.bindings.get(a).chunkId())).toList();var missingCandidates=needed.stream().filter(a->!candidateIds.contains(trial.bindings.get(a).chunkId())).toList();
                    required+=needed.size();foundAt5+=needed.size()-missingAt5.size();foundAtCandidateBudget+=needed.size()-missingCandidates.size();
                    row.put("caseId",id);row.put("question",question);row.put("selectedRuleIdControl",selectedId);row.put("selectionQuality","NOT_EVALUATED_MANUAL_CONTROL");row.put("actualLexicalCandidates",candidates);row.put("outcome",outcome);row.put("requiredArticles",needed);row.put("missingAt5",missingAt5);row.put("missingAtCandidateBudget",missingCandidates);cases.add(row);
                }
                configurations.add(Map.of("syntheticNoiseCount",noise,"actualActiveIndexedCount",actualCount,"candidateBudget",budget,"resultBudget",5,"requiredOccurrences",required,"foundAt5",foundAt5,"foundAtCandidateBudget",foundAtCandidateBudget,"cases",cases));
            }
        }
        trial.save("lexical-noise-calibration.json",Map.ofEntries(Map.entry("configurations",configurations),Map.entry("actualReviewedBindings",trial.bindings.values()),Map.entry("syntheticNoiseSources",noiseSources),Map.entry("syntheticNoiseRuns",noiseRuns),Map.entry("syntheticFamiliesAt40",RuleCheckNoiseFixtures.texts(40)),Map.entry("profileId",trial.profile),Map.entry("providerCalls",0),Map.entry("reportCasesConsumed",0),Map.entry("defaultSwitched",false),Map.entry("selectionQuality","NOT_EVALUATED_MANUAL_CATALOGUE_ID"),Map.entry("semanticQuality","NOT_HUMAN_SCORED")));
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
        for(var configuration:configurations){
            // 原有准入是≥90%；上一批单次21/21不是冻结门槛。保留实际退步，不删题或改参考。
            if((int)configuration.get("syntheticNoiseCount")==0)assertThat((double)(int)configuration.get("foundAt5")/(int)configuration.get("requiredOccurrences")).isGreaterThanOrEqualTo(.9);
            for(var row:(List<Map<String,Object>>)configuration.get("cases")){
                requireLexicalHealth((RetrievalOutcome)row.get("outcome"));assertThat(((List<?>)row.get("actualLexicalCandidates")).size()).isLessThanOrEqualTo((int)configuration.get("candidateBudget"));
                if(row.containsKey("attempt")){
                    var attempt=(RuleCheckDevelopmentCapture.Attempt)row.get("attempt");assertThat(attempt.status()).isEqualTo("COLLECTED");assertThat((List<?>)row.get("stageErrors")).isEmpty();assertThat(attempt.output()).isNotNull();
                    var audits=(List<CitationAuditRecord>)row.get("persistedAuditRecords");
                    // C05在应用检索之前拒绝对象/时间缺口；诊断探针的候选不是已被应用捕获的证据。
                    // 只有明确的范围阻断且实际无事实/审计才可走此路径，Q17非空正控制继续保护后段。
                    if(attempt.output().applicability()!=null&&attempt.output().applicability().blocked()){
                        assertThat(attempt.output().decision().status()).isEqualTo(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE);
                        assertThat(attempt.output().decision().confirmedFacts()).isEmpty();assertThat(attempt.output().decision().unknownItems()).containsExactlyElementsOf(attempt.output().applicability().blockingReasons());assertThat(audits).isEmpty();
                    }else requireAuditBindings(attempt,audits);
                    assertThat(audits).extracting(CitationAuditRecord::citationId).containsExactlyInAnyOrderElementsOf(((List<CitationAuditRecord>)row.get("actualAuditRecords")).stream().map(CitationAuditRecord::citationId).toList());
                    var quotes=attempt.output().decision().confirmedFacts().stream().flatMap(f->f.evidence().stream()).toList();var locations=(List<RuleCheckDevelopmentCapture.LocationTrace>)row.get("actualOriginalLocations");assertThat(locations).hasSize(quotes.size());
                    // 非空正控制与审计回读一起保护后段，安全缺证返回不能让全体输出退化后依然绿色。
                    if((int)configuration.get("syntheticNoiseCount")==0&&row.get("caseId").equals("Q17")){assertThat(attempt.output().decision().status()).isEqualTo(RuleCheckDecision.Status.ANSWERED);assertThat(quotes).isNotEmpty();assertThat(locations).isNotEmpty();}
                    // 干扰资料可被召回和审计，但不能凭机械资格给现有已审主张提供替代绑定。
                    for(var fact:attempt.output().decision().confirmedFacts())for(var quote:fact.evidence()){assertThat(quote.sourceId()).isEqualTo(trial.source);assertThat(trial.bindings.values()).anySatisfy(b->{assertThat(quote.chunkId()).isEqualTo(b.chunkId());assertThat(quote.text()).isEqualTo(b.text());});}
                    for(int i=0;i<quotes.size();i++){
                        var quote=quotes.get(i);var located=locations.get(i);assertThat(located.errorType()).isNull();assertThat(located.result().code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);var original=located.result().original();
                        assertThat(audits).anySatisfy(a->{assertThat(a.citationId()).isEqualTo(quote.citationId());assertThat(a.sourceId()).isEqualTo(quote.sourceId());assertThat(a.chunkId()).isEqualTo(quote.chunkId());assertThat(a.contentChecksum()).isEqualToIgnoringCase(quote.checksum());});
                        assertThat(original.citationId()).isEqualTo(quote.citationId());assertThat(original.sourceId()).isEqualTo(quote.sourceId());assertThat(original.chunkId()).isEqualTo(quote.chunkId());assertThat(original.revision()).isEqualTo(quote.revision());
                        assertThat(original.quoteStartInclusive()).isEqualTo(quote.startInclusive());assertThat(original.quoteEndExclusive()).isEqualTo(quote.endExclusive());
                        assertThat(original.contextText().substring(quote.startInclusive()-original.contextStartInclusive(),quote.endExclusive()-original.contextStartInclusive())).isEqualTo(quote.text());
                        var binding=trial.bindings.values().stream().filter(b->b.chunkId().equals(quote.chunkId())).findFirst().orElseThrow();assertThat(original.sourceLocator()).isEqualTo(binding.locator());assertThat(original.recordedPageNumber()).isEqualTo(binding.physicalPage());
                    }
                }
            }
        }
    }
    @Test void knownScopeMisroutingCannotPromoteGeneralRulesToFullAnswers() throws Exception {
        var cases=new ArrayList<Map<String,Object>>();
        for(String caseId:List.of("Q09","Q13","Q16","Q18")){
            JsonNode reference=null;for(var r:inputs.reference().path("records"))if(r.path("reviewId").asText().equals(caseId))reference=r;
            String question=reference.path("question").asText(),correct=inputs.controls().path("controls").path(caseId).asText(),wrong=correct.substring(0,3)+"/GENERAL";
            var errors=new ArrayList<String>();var audits=new ArrayList<Object>();var good=app(correct,lexical(),RuleCheck2025RobustnessTest::disabledVector,audits,errors).checkWithContext(question,Map.of());
            var service=app(wrong,lexical(),RuleCheck2025RobustnessTest::disabledVector,audits,errors);var bad=service.checkWithContext(question,Map.of());var locations=new ArrayList<Object>();
            for(var fact:bad.decision().confirmedFacts())for(var quote:fact.evidence())locations.add(RuleCheckDevelopmentCapture.locate(quote,trial.reader()::readLocated));
            cases.add(Map.of("caseId",caseId,"question",question,"expectedScopedStatus",reference.path("humanReview").path("acceptedStatus").asText(),"correctSelectedId",correct,"wrongButLegalId",wrong,"correctResult",good,"misroutedResult",bad,"misroutedResultCurrent",service.isCurrent(bad),"actualOriginalLocations",locations,"stageErrors",errors));
        }
        var retrievalCalls=new int[1];var errors=new ArrayList<String>();var refused=app(null,(q,l)->{retrievalCalls[0]++;return lexical().retrieve(q,l);},RuleCheck2025RobustnessTest::disabledVector,new ArrayList<>(),errors).checkWithContext("学生医保怎么报销？",Map.of());
        trial.save("legal-id-scope-counterexamples.json",Map.of("cases",cases,"nullSelectorControl",refused,"nullSelectorRetrievalCalls",retrievalCalls[0],"selectionMode","INJECTED_CORRECT_OR_WRONG_LEGAL_ID_NOT_ACTUAL_MODEL","knownExplicitScopeCasesGuarded",true,"generalSemanticQualityProven",false,"providerCalls",0));
        assertThat(refused.decision().status()).isEqualTo(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE);assertThat(retrievalCalls[0]).isZero();
        assertThat(cases).allSatisfy(row->{
            assertThat(((RuleCheckScopedResult)row.get("correctResult")).decision().status().name()).isEqualTo(row.get("expectedScopedStatus"));
            // 原C04错误全答证据保留；新代码应阻断已知显式范围误用，不证明通用语义理解。
            assertThat(((RuleCheckScopedResult)row.get("misroutedResult")).decision().status().name()).isEqualTo(row.get("expectedScopedStatus"));assertThat(row.get("misroutedResultCurrent")).isEqualTo(true);assertThat((List<?>)row.get("stageErrors")).isEmpty();
            for(var value:(List<?>)row.get("actualOriginalLocations")){var located=(RuleCheckDevelopmentCapture.LocationTrace)value;assertThat(located.errorType()).isNull();assertThat(located.result().code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);}
        });
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
    }
}
