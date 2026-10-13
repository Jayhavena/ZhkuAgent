package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** 真实选题响应回到冻结应用/SQL；基线同资料同响应，仅变词法策略，不算独立在线重复。 */
class RuleCheck2025ReportReplayTest {
    private static final String BASE="docs/evidence/rule-check-refactor/2026-10-10/package-03/";
    private static final String PACKAGE_SHA="1a93f2bc1925a6c1abe985f5959757bdd166e63fdf70f5758badadb23423ab3c";
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private Reviewed2025ReportCatalogue trial;
    private DriverManagerDataSource ds;
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    @Test void recordsAllSixteenOutcomesWithoutTuningOrStatusPassAssertions() throws Exception {
        var freeze=read(root.resolve(BASE+"system-freeze.json"));verifyFreeze(freeze);
        assertThat(Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(root.resolve(BASE+"request-package.json")))).isEqualTo(PACKAGE_SHA);
        var packet=read(root.resolve(BASE+"request-package.json"));
        var summary=read(root.resolve(BASE+"provider-capture/summary.json"));
        var auth=read(root.resolve(BASE+"external-transfer-authorization.json"));
        assertThat(auth.path("authority").asText()).isEqualTo("DIRECT_USER_MESSAGE");assertThat(auth.path("decision").asText()).isEqualTo("CONFIRMED");
        assertThat(auth.path("packageSha256").asText()).isEqualTo(PACKAGE_SHA);assertThat(auth.path("maxCalls").asInt()).isEqualTo(11);
        assertThat(summary.path("completedAllRequests").asBoolean()).isTrue();assertThat(summary.path("retryCount").asInt()).isZero();
        var requests=new LinkedHashMap<String,JsonNode>();packet.path("requests").forEach(row->requests.put(row.path("caseId").asText(),row));
        var responses=new LinkedHashMap<String,JsonNode>();
        for(var row:summary.path("records")) {
            String id=row.path("caseId").asText();assertThat(row.path("path").asText()).isEqualTo(id+".json");
            Path file=root.resolve(BASE+"provider-capture/"+id+".json");
            assertThat(Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(file))).isEqualTo(row.path("sha256").asText());
            var response=read(file);assertThat(response.path("status").asText()).isEqualTo("RESPONSE_RECEIVED");responses.put(id,response);
        }
        assertThat(responses).hasSize(11);
        String url=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");RuleCheckDatabaseTestTarget.verify(url);
        ds=new DriverManagerDataSource(url,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(url,user,ZhkuDatabaseTestConfig.databasePassword());
        trial=new Reviewed2025ReportCatalogue(root);trial.installOnIsolatedDatabase(ds);
        var rows=new ArrayList<Map<String,Object>>();
        for(var question:packet.path("questionProjection")) {
            String id=question.path("caseId").asText(),q=question.path("question").asText();
            var row=new LinkedHashMap<String,Object>();row.put("caseId",id);row.put("question",q);row.put("locallyBlocked",!requests.containsKey(id));
            row.put("actualPath",run(id,q,requests.get(id),responses.get(id),false));
            row.put("baselinePath",run(id,q,requests.get(id),responses.get(id),true));rows.add(row);
        }
        assertThat(rows).hasSize(16);verifyFreeze(freeze);
        Path out=Path.of(System.getProperty("zhku.rulecheck.evidenceDir"));
        var result=Map.of("cases",rows,"reportDenominator",16,"modelCallsInThisReplay",0,"capturedProviderResponses",11,
                "mode","CAPTURED_REAL_RESPONSE_OFFLINE_REPLAY_NOT_INDEPENDENT_REPEAT","baseline","SAME_TWENTY_CLAUSES_AND_RESPONSES_NO_DIVERSIFICATION",
                "runtimeSourceUnchanged",true,"humanSemanticGradesProduced",false,"bindings",trial.bindings.values(),"settings",trial.settings());
        Files.write(out.resolve("report-actual-results.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),StandardOpenOption.CREATE_NEW);
    }
    private Map<String,Object> run(String id,String question,JsonNode requestRow,JsonNode response,boolean baseline) throws Exception {
        String prefix="report-"+id+"-"+(baseline?"baseline":"actual")+"-"+UUID.randomUUID()+"-";
        var outcome=new AtomicReference<BoundedRuleSelector.Outcome>();var candidates=new AtomicReference<List<RetrievedChunk>>(List.of());
        var calls=new AtomicInteger();var locations=new ArrayList<Map<String,Object>>();
        var worker=new BoundedRuleSelector(request->{
            try {
                assertThat(requestRow).isNotNull();
                assertThat(request.systemConstraints()).isEqualTo(requestRow.path("body").path("messages").get(0).path("content").asText());
                assertThat(mapper.readTree(request.userPayload())).isEqualTo(mapper.readTree(requestRow.path("body").path("messages").get(1).path("content").asText()));
                return response.path("rawModelContent").asText();
            }catch(java.io.IOException invalid){throw new IllegalStateException(invalid);}
        },Duration.ofSeconds(5),1);
        var selector=new RuleCheckApplicationService.RuleSelector() {
            public boolean requiresScopedCandidates(){return true;}
            public String select(String q,List<RuleCheckApplicationService.RuleOption> options){var value=worker.selectOutcome(q,options);outcome.set(value);return value.ruleId();}
        };
        var verifier=new RuleCheckEvidenceVerifier(trial.reader());
        var capture=new RuleCheckEvidenceCapture(verifier,()->prefix+UUID.randomUUID(),new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,trial.CUT,trial.SCOPE),Clock.systemUTC());
        var retriever=baseline?JdbcLexicalCandidateRetriever.forChineseBigramBaselineEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,trial.SCOPE,trial.CUT)
                :JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,trial.SCOPE,trial.CUT);
        var app=new RuleCheckApplicationService(trial.settings(),()->{
            try{return trial.reviewedCatalogue(root);}catch(Exception failure){throw new IllegalStateException(failure);}
        },selector,new HybridRetrievalService(new HybridCandidateFuser()),(q,limit)->{calls.incrementAndGet();var result=retriever.retrieve(q,limit);candidates.set(result);return result;},
                (q,limit)->List.of(),capture,new RuleCheckDecisionService(verifier));
        RuleCheckScopedResult result;long start=System.nanoTime();
        try {result=app.checkWithContext(question,Map.of());}
        finally {
            worker.close();long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
            while(worker.liveWorkers()!=0&&System.nanoTime()<deadline)Thread.sleep(2);
            assertThat(worker.liveWorkers()).isZero();assertThat(worker.inFlight()).isZero();
        }
        double elapsed=(System.nanoTime()-start)/1e6;
        if(requestRow==null)assertThat(worker.attempts()).isZero();
        else {assertThat(worker.attempts()).isEqualTo(1);assertThat(outcome.get().code().name()).isEqualTo(response.path("protocolDiagnostic").asText());}
        var audits=new ArrayList<Map<String,Object>>();
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT a.citation_id,a.route,e.id AS entry_id,e.profile_id,e.chunking_version,e.source_version,e.content_checksum,k.id AS chunk_id,k.source_id,k.content,k.source_locator,k.page_number FROM citation_audit a JOIN retrieval_index_entry e ON e.id=a.index_entry_id JOIN knowledge_chunk k ON k.id=e.chunk_id WHERE a.citation_id LIKE ? ORDER BY a.citation_id")) {
            p.setString(1,prefix+"%");try(var r=p.executeQuery()){while(r.next()) {
                var a=new LinkedHashMap<String,Object>();
                for(String field:List.of("citation_id","route","entry_id","profile_id","chunking_version","source_version","content_checksum","chunk_id","source_id","content","source_locator","page_number"))a.put(field,r.getObject(field));
                assertThat(r.getString("profile_id")).isEqualTo(trial.profile);assertThat(r.getObject("source_id",UUID.class)).isEqualTo(trial.source);
                assertThat(r.getString("content_checksum")).isEqualToIgnoringCase(Reviewed2025ControlledCatalogue.sha(r.getString("content").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                audits.add(a);
            }}
        }
        if(calls.get()==0)assertThat(audits).isEmpty();
        else {
            var top=candidates.get().stream().limit(5).map(RetrievedChunk::chunkId).toList();
            assertThat(audits.stream().map(a->a.get("chunk_id")).toList()).containsExactlyInAnyOrderElementsOf(top);
        }
        for(var fact:result.decision().confirmedFacts())for(var excerpt:fact.evidence()) {
            var original=trial.originals().locate(excerpt);assertThat(original.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
            assertThat(original.original().contextText()).contains(excerpt.text());locations.add(Map.of("reference",excerpt,"located",original));
        }
        assertThat(app.isCurrent(result)).isTrue();
        var path=new LinkedHashMap<String,Object>();path.put("result",result);path.put("selectorOutcome",outcome.get());path.put("selectorAttempts",worker.attempts());
        path.put("retrievalCalls",calls.get());path.put("lexicalCandidates",candidates.get());path.put("top5",candidates.get().stream().limit(5).toList());
        path.put("persistedAudits",audits);path.put("actualOriginalLocations",locations);path.put("freshReleaseValidated",true);path.put("selectorWorkersClosed",true);
        path.put("replayElapsedMs",elapsed);return path;
    }
    private JsonNode read(Path path)throws Exception{return mapper.readTree(Files.readAllBytes(path));}
    private void verifyFreeze(JsonNode freeze)throws Exception {
        assertThat(freeze.path("status").asText()).isEqualTo("FROZEN_BEFORE_REPORT_QUESTION_PREPARATION");
        for(var row:freeze.path("files"))assertThat(Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(root.resolve(row.path("path").asText()))))
                .as(row.path("path").asText()).isEqualTo(row.path("sha256").asText());
    }
}
