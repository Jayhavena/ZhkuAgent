package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.rag.CitationAuditRecord;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 本批真实供应商响应的离线重放；DB审计/原文回读不代表在线重复成功或人工语义评分。 */
class RuleCheck2025SelectionReplayTest {
    private static final String BASE="docs/evidence/rule-check-refactor/2026-10-10/c05-selection-01/";
    private static final String PACKAGE_SHA="1187efb9f005d972e60fc4f0462e04e67f399028b43f6ffd824c51710dc3c1d8";
    private static final String SUMMARY_SHA="c1b7ecee07e09177f4e2618734226d6d05f6e43d70cfcea94afd007d0bd8ff3a";
    private Reviewed2025Trial trial;
    private RuleCheckDevelopmentInputs.Loaded inputs;
    private Path root;
    private DataSource ds;
    @BeforeEach void start() throws Exception {
        root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
        String target=System.getProperty("zhku.db.url"),user=System.getProperty("zhku.db.user","zhkuagent");
        RuleCheckDatabaseTestTarget.verify(target);
        ds=new DriverManagerDataSource(target,user,ZhkuDatabaseTestConfig.databasePassword());
        ZhkuFlywayTestSupport.migrate(target,user,ZhkuDatabaseTestConfig.databasePassword());trial.importAndBind(ds);
    }
    @AfterEach void close() throws Exception {if(trial!=null)trial.close();}
    private int actualAuditCount() throws Exception {
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM citation_audit a JOIN retrieval_index_entry e ON a.index_entry_id=e.id WHERE e.profile_id=?")) {
            p.setString(1,trial.profile);try(var r=p.executeQuery()){assertThat(r.next()).isTrue();return r.getInt(1);}
        }
    }
    private Map<String,Object> run(String question,JsonNode item,JsonNode observation) throws Exception {
        var selected=new BoundedRuleSelector.Outcome[1];
        try(var selector=new BoundedRuleSelector(request->{
            try {
                assertThat(request.systemConstraints()).isEqualTo(item.path("body").path("messages").get(0).path("content").asText());
                assertThat(trial.mapper.readTree(request.userPayload())).isEqualTo(trial.mapper.readTree(item.path("body").path("messages").get(1).path("content").asText()));
                return observation.path("rawModelContent").asText();
            }catch(java.io.IOException invalid){throw new IllegalArgumentException("回放请求不符",invalid);}
        },java.time.Duration.ofSeconds(5),1)) {
        int auditsBefore=actualAuditCount();
        var auditWrites=new ArrayList<CitationAuditRecord>();var retrievalCalls=new AtomicInteger();
        var verifier=new RuleCheckEvidenceVerifier(trial.reader());
        var actualAudit=new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE);
        var capture=new RuleCheckEvidenceCapture(verifier,()->"c05-"+UUID.randomUUID(),row->{actualAudit.record(row);auditWrites.add(row);},Clock.systemUTC());
        var lex=JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(Clock.systemUTC()),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
        var app=new RuleCheckApplicationService(new RuleCheckRuntimeSettings(trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5),
            ()->List.copyOf(trial.catalogue),(q,o)->{selected[0]=selector.selectOutcome(q,o);return selected[0].ruleId();},new HybridRetrievalService(new HybridCandidateFuser()),
            (q,l)->{retrievalCalls.incrementAndGet();return lex.retrieve(q,l);},(q,l)->List.of(),capture,new RuleCheckDecisionService(verifier));
        var result=app.checkWithContext(question,Map.of());var persisted=new ArrayList<CitationAuditRecord>();
        int auditsAfter=actualAuditCount();assertThat(auditsAfter-auditsBefore).isEqualTo(auditWrites.size());
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT a.citation_id,a.route,a.created_at,e.chunk_id,k.source_id,e.source_version,e.content_checksum,k.content FROM citation_audit a JOIN retrieval_index_entry e ON e.id=a.index_entry_id JOIN knowledge_chunk k ON k.id=e.chunk_id WHERE a.citation_id=? AND e.profile_id=?")) {
            for(var write:auditWrites){p.setString(1,write.citationId());p.setString(2,trial.profile);try(var r=p.executeQuery()){
                assertThat(r.next()).isTrue();persisted.add(new CitationAuditRecord(r.getString(1),r.getObject(4,UUID.class),r.getString(2),r.getTimestamp(3).toInstant(),r.getObject(5,UUID.class),r.getString(6),r.getString(7),r.getString(8)));assertThat(r.next()).isFalse();
            }}
        }
        assertThat(persisted).hasSize(auditWrites.size());
        // PostgreSQL时间精度与Java纳秒不同；引用身份、版本、hash、正文和路径必须精确一致。
        for(int i=0;i<persisted.size();i++)assertThat(persisted.get(i)).usingRecursiveComparison().ignoringFields("issuedAt").isEqualTo(auditWrites.get(i));
        var locations=new ArrayList<RuleCheckOriginalEvidenceService.Result>();
        for(var fact:result.decision().confirmedFacts())for(var quote:fact.evidence()){
            var original=trial.originals().locate(quote);locations.add(original);assertThat(original.code()).isEqualTo(RuleCheckOriginalEvidenceService.Code.FOUND);
            assertThat(persisted).anySatisfy(a->{assertThat(a.citationId()).isEqualTo(quote.citationId());assertThat(a.chunkId()).isEqualTo(quote.chunkId());assertThat(a.sourceId()).isEqualTo(quote.sourceId());assertThat(a.contentChecksum()).isEqualToIgnoringCase(quote.checksum());assertThat(a.content()).isEqualTo(quote.text());});
            var view=original.original();assertThat(view.contextText().substring(quote.startInclusive()-view.contextStartInclusive(),quote.endExclusive()-view.contextStartInclusive())).isEqualTo(quote.text());
        }
        assertThat(app.isCurrent(result)).isTrue();assertThat(selected[0]).isNotNull();assertThat(selector.attempts()).isEqualTo(1);
        return Map.of("selectorOutcome",selected[0],"result",result,"retrievalCalls",retrievalCalls.get(),"actualAuditWrites",auditWrites,"persistedAudits",persisted,"actualOriginalLocations",locations,"current",true,"actualAuditCountBefore",auditsBefore,"actualAuditCountAfter",auditsAfter);
        } finally {
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.getName().startsWith("rule-check-selector-")&&t.isAlive())&&System.nanoTime()<end)Thread.sleep(5);
            assertThat(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.getName().startsWith("rule-check-selector-")&&t.isAlive())).isFalse();
        }
    }
    @Test void replaysActualTwentyFourResponsesAndVerifiesDownstreamAuditsWithoutSemanticPassAssertions() throws Exception {
        Path base=root.resolve(BASE);var pack=trial.read(base.resolve("request-package.json"));
        assertThat(Reviewed2025Trial.sha(Files.readAllBytes(base.resolve("request-package.json")))).isEqualTo(PACKAGE_SHA);
        Path summaryPath=base.resolve("provider-capture/summary.json");
        assertThat(Reviewed2025Trial.sha(Files.readAllBytes(summaryPath))).isEqualTo(SUMMARY_SHA);
        var summary=trial.read(summaryPath);assertThat(summary.path("completedAllRequests").asBoolean()).isTrue();
        assertThat(summary.path("sourceUnchanged").asBoolean()).isTrue();assertThat(summary.path("records")).hasSize(24);
        var auth=trial.read(base.resolve("external-transfer-authorization.json"));
        assertThat(auth.path("authority").asText()).isEqualTo("DIRECT_USER_MESSAGE");assertThat(auth.path("decision").asText()).isEqualTo("CONFIRMED");
        assertThat(auth.path("packageSha256").asText()).isEqualTo(PACKAGE_SHA);
        var rows=new ArrayList<Map<String,Object>>();
        for(int i=0;i<24;i++){
            var item=pack.path("requests").get(i);String id=item.path("caseId").asText();
            assertThat(id).isEqualTo(i<20?"Q%02d".formatted(i+1):"O%02d".formatted(i-19));
            var identity=summary.path("records").get(i);assertThat(identity.path("caseId").asText()).isEqualTo(id);
            assertThat(identity.path("path").asText()).isEqualTo(id+".json");Path observedPath=base.resolve("provider-capture/"+id+".json");
            assertThat(Reviewed2025Trial.sha(Files.readAllBytes(observedPath))).isEqualTo(identity.path("sha256").asText());
            var observation=trial.read(observedPath);assertThat(observation.path("status").asText()).isEqualTo("RESPONSE_RECEIVED");
            assertThat(observation.path("caseId").asText()).isEqualTo(id);
            String question=trial.mapper.readTree(item.path("body").path("messages").get(1).path("content").asText()).path("question").asText();
            if(i<20)assertThat(question).isEqualTo(inputs.reference().path("records").get(i).path("question").asText());
            var path=run(question,item,observation);var outcome=(BoundedRuleSelector.Outcome)path.get("selectorOutcome");
            assertThat(outcome.code().name()).isEqualTo(observation.path("protocolDiagnostic").asText());
            var row=new LinkedHashMap<String,Object>();row.put("caseId",id);row.put("question",question);row.put("providerRecordSha256",identity.path("sha256").asText());row.put("path",path);
            if(i<20){
                var ref=inputs.reference().path("records").get(i);String expected=ref.path("humanReview").path("acceptedStatus").asText();
                row.put("referenceStatus",expected);row.put("statusMatchesReference",((RuleCheckScopedResult)path.get("result")).decision().status().name().equals(expected));
                row.put("originalControlId",inputs.controls().path("controls").path(id).asText());
                // 状态一致和控制ID一致均不能证明回答了所问的事实；参考ID从未被确认成选题真值。
                row.put("humanSemanticGrade","NOT_SCORED");
            }else row.put("humanSemanticGrade","UNGRADED_PROBE_NO_REFERENCE");
            rows.add(row);trial.save("selection-replay-"+id+".json",row);
        }
        trial.save("selection-replay-summary.json",Map.of("cases",rows,"actualBindings",trial.bindings.values(),"selectionMode","ACTUAL_PROVIDER_RESPONSE_OFFLINE_REPLAY",
            "providerCallsInThisReplay",0,"capturedProviderResponses",24,"humanSemanticGradesProduced",false,"reportCasesConsumed",0));
        // 已知对象/时间/个人缺口只核对保护行为；与主题错选、条件遗漏的评分相互独立。
        for(var row:rows)if(List.of("Q02","Q09","Q13","Q16","Q18").contains(row.get("caseId")))
            assertThat(((RuleCheckScopedResult)((Map<?,?>)row.get("path")).get("result")).decision().status()).isNotEqualTo(ANSWERED);
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
        assertThat(Reviewed2025Trial.sha(Files.readAllBytes(base.resolve("request-package.json")))).isEqualTo(PACKAGE_SHA);
        assertThat(Reviewed2025Trial.sha(Files.readAllBytes(summaryPath))).isEqualTo(SUMMARY_SHA);
    }
}
