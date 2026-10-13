package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

/** 第四包只验证一个固定随机选题路径的重复可靠性；不能替代16题质量或学生收益。 */
class RuleCheck2025ReliabilityReplayTest {
    private static final String BASE="docs/evidence/rule-check-refactor/2026-10-11/package-04/";
    private static final String PACKAGE_SHA="5c67cb3209bc96fd5abb4fe727dfcdb6be27f17975883ebffdf4cd57e8483204";
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();

    @Test void packetMatchesFrozenApplicationAndReplaysCaptureWhenRequired() throws Exception {
        JsonNode packet=read(root.resolve(BASE+"request-package.json"));
        assertThat(Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(root.resolve(BASE+"request-package.json")))).isEqualTo(PACKAGE_SHA);
        assertThat(packet.path("requests")).hasSize(5);
        assertThat(packet.path("expectedRuleId").asText()).isEqualTo("S01/GENERAL");
        String question=mapper.readTree(packet.path("requests").get(0).path("body").path("messages").get(1).path("content").asText()).path("question").asText();
        for(int i=0;i<5;i++) {
            JsonNode row=packet.path("requests").get(i);
            assertThat(row.path("trialId").asText()).isEqualTo("P04-R01-T"+(i+1));
            assertThat(mapper.readTree(row.path("body").path("messages").get(1).path("content").asText()).path("question").asText()).isEqualTo(question);
        }

        boolean requireCapture=Boolean.getBoolean("zhku.rulecheck.package04.requireCapture");
        Path capture=root.resolve(BASE+"provider-capture");
        if(requireCapture)assertThat(capture.resolve("summary.json")).as("第四包真实回放必须先有不可变供应商采集").isRegularFile();

        String url=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(url);
        var ds=new DriverManagerDataSource(url,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(url,user,ZhkuDatabaseTestConfig.databasePassword());
        try(var trial=new Reviewed2025ReportCatalogue(root)) {
            trial.installOnIsolatedDatabase(ds);
            var rows=new ArrayList<Map<String,Object>>();
            if(requireCapture) {
                JsonNode auth=read(root.resolve(BASE+"external-transfer-authorization.json"));
                JsonNode summary=read(capture.resolve("summary.json"));
                assertThat(auth.path("authority").asText()).isEqualTo("DIRECT_USER_MESSAGE");
                assertThat(auth.path("decision").asText()).isEqualTo("CONFIRMED");
                assertThat(auth.path("packageSha256").asText()).isEqualTo(PACKAGE_SHA);
                assertThat(summary.path("trialDenominator").asInt()).isEqualTo(5);
                assertThat(summary.path("records")).hasSize(5);
                assertThat(summary.path("retryCount").asInt()).isZero();
                for(int i=0;i<5;i++) {
                    JsonNode identity=summary.path("records").get(i),request=packet.path("requests").get(i);
                    String id=request.path("trialId").asText();
                    assertThat(identity.path("trialId").asText()).isEqualTo(id);
                    Path responsePath=capture.resolve(identity.path("path").asText());
                    assertThat(Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(responsePath))).isEqualTo(identity.path("sha256").asText());
                    JsonNode response=read(responsePath);
                    assertThat(response.path("status").asText()).isEqualTo("RESPONSE_RECEIVED");
                    assertThat(response.path("protocolDiagnostic").asText()).isEqualTo("SELECTED");
                    assertThat(response.path("ruleId").asText()).isEqualTo("S01/GENERAL");
                    rows.add(run(ds,trial,question,request,response.path("rawModelContent").asText(),id));
                }
            } else {
                // 外发前只用协议响应验证请求包确实等于冻结应用会发送的payload；不计真实重复。
                rows.add(run(ds,trial,question,packet.path("requests").get(0),"{\"ruleId\":\"S01/GENERAL\"}","PRE_NETWORK_CONTRACT"));
            }
            if(requireCapture) {
                Path out=Path.of(System.getProperty("zhku.rulecheck.evidenceDir"));
                var result=new LinkedHashMap<String,Object>();
                result.put("mode","FIVE_CAPTURED_REAL_RESPONSES_REPLAYED_OFFLINE");result.put("trialDenominator",5);
                result.put("providerCallsInReplay",0);result.put("rows",rows);result.put("defaultSwitched",false);
                result.put("qualityClaimAllowed",false);result.put("resourcesVerified",rows.stream().allMatch(r->Boolean.TRUE.equals(r.get("selectorWorkersClosed"))));
                Files.write(out.resolve("package04-reliability-results.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),StandardOpenOption.CREATE_NEW);
            }
        }
    }

    private Map<String,Object> run(DriverManagerDataSource ds,Reviewed2025ReportCatalogue trial,String question,JsonNode requestRow,String response,String id) throws Exception {
        var outcome=new AtomicReference<BoundedRuleSelector.Outcome>();var observedRequest=new AtomicReference<BoundedRuleSelector.Request>();var candidates=new AtomicReference<List<RetrievedChunk>>(List.of());
        var retrievalCalls=new AtomicInteger();String prefix="package04-"+id+"-"+UUID.randomUUID()+"-";
        var worker=new BoundedRuleSelector(request->{observedRequest.set(request);return response;},Duration.ofSeconds(5),1);
        var selector=new RuleCheckApplicationService.RuleSelector() {
            public boolean requiresScopedCandidates(){return true;}
            public String select(String q,List<RuleCheckApplicationService.RuleOption> options){var value=worker.selectOutcome(q,options);outcome.set(value);return value.ruleId();}
        };
        var verifier=new RuleCheckEvidenceVerifier(trial.reader());
        var capture=new RuleCheckEvidenceCapture(verifier,()->prefix+UUID.randomUUID(),new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,trial.CUT,trial.SCOPE),Clock.systemUTC());
        var retriever=JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,trial.SCOPE,trial.CUT);
        var app=new RuleCheckApplicationService(trial.settings(),()->{
            try{return trial.reviewedCatalogue(root);}catch(Exception failure){throw new IllegalStateException(failure);}
        },selector,new HybridRetrievalService(new HybridCandidateFuser()),(q,limit)->{retrievalCalls.incrementAndGet();var result=retriever.retrieve(q,limit);candidates.set(result);return result;},
                (q,limit)->List.of(),capture,new RuleCheckDecisionService(verifier));
        RuleCheckScopedResult result;long started=System.nanoTime();
        try {result=app.checkWithContext(question,Map.of());}
        finally {
            worker.close();long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
            while(worker.liveWorkers()!=0&&System.nanoTime()<deadline)Thread.sleep(2);
        }
        double elapsedMs=(System.nanoTime()-started)/1e6;
        assertThat(worker.attempts()).isEqualTo(1);assertThat(worker.liveWorkers()).isZero();assertThat(worker.inFlight()).isZero();
        assertThat(observedRequest.get().systemConstraints()).isEqualTo(requestRow.path("body").path("messages").get(0).path("content").asText());
        assertThat(mapper.readTree(observedRequest.get().userPayload())).isEqualTo(mapper.readTree(requestRow.path("body").path("messages").get(1).path("content").asText()));
        assertThat(outcome.get().code()).isEqualTo(BoundedRuleSelector.Code.SELECTED);assertThat(outcome.get().ruleId()).isEqualTo("S01/GENERAL");
        assertThat(result.decision().status()).isEqualTo(RuleCheckDecision.Status.ANSWERED);
        assertThat(result.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).contains("undergraduate-plan-obligation");
        var locations=new ArrayList<Map<String,Object>>();
        for(var fact:result.decision().confirmedFacts())for(var excerpt:fact.evidence()) {
            var original=trial.originals().locate(excerpt);assertThat(original.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
            assertThat(original.original().contextText()).contains(excerpt.text());locations.add(Map.of("reference",excerpt,"located",original));
        }
        assertThat(locations).isNotEmpty();assertThat(app.isCurrent(result)).isTrue();
        int audits;
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM citation_audit WHERE citation_id LIKE ?")) {
            p.setString(1,prefix+"%");try(var rs=p.executeQuery()){rs.next();audits=rs.getInt(1);}
        }
        assertThat(audits).isGreaterThan(0);assertThat(retrievalCalls.get()).isEqualTo(1);assertThat(candidates.get()).isNotEmpty();
        var row=new LinkedHashMap<String,Object>();row.put("trialId",id);row.put("result",result);row.put("selectorOutcome",outcome.get());
        row.put("selectorAttempts",worker.attempts());row.put("retrievalCalls",retrievalCalls.get());row.put("candidateCount",candidates.get().size());
        row.put("persistedAuditCount",audits);row.put("actualOriginalLocations",locations);row.put("applicationElapsedMs",elapsedMs);
        row.put("selectorWorkersClosed",worker.liveWorkers()==0&&worker.inFlight()==0);return row;
    }

    private JsonNode read(Path path) throws Exception {return mapper.readTree(Files.readAllBytes(path));}
}
