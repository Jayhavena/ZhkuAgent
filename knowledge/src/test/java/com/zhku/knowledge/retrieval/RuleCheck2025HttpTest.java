package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.rulecheck.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;
import static org.assertj.core.api.Assertions.*;

/** 真实已审条款经过学生认证/HTTP/SQL/定位；固定开发ID控制，不评价自然语言选题。 */
@Timeout(90)
class RuleCheck2025HttpTest {
    @Test void productionControlledBeansUseActualScopedReplayAndWithdrawnEvidence()throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        var overrides=Map.of("zhku.rule-check-preview.catalogue-mode","REVIEWED_2025_CONTROLLED",
            "zhku.rule-check-preview.selector-mode","SEALED_REPLAY","zhku.rule-check-preview.repository-root",root.toString(),
            "zhku.rule-check-preview.profile-id","controlled-2025-local","zhku.rule-check-preview.chunking-version",Reviewed2025Trial.CUT,
            "zhku.rule-check-preview.required-scope",Reviewed2025Trial.SCOPE,"zhku.rule-check-preview.candidate-limit","20");
        withApp(null,overrides,app->{
            var actual=app.context().getBean(Reviewed2025ControlledCatalogue.class);
            var replay=app.context().getBean(SealedRuleSelectionReplay.class);
            var settings=app.context().getBean(RuleCheckRuntimeSettings.class);
            assertThat(settings.profileId()).isEqualTo(actual.profile);assertThat(settings.candidateLimit()).isEqualTo(20);
            assertThat(app.context().getBeanNamesForType(Fixture.class)).isEmpty();
            var inputs=RuleCheckDevelopmentInputs.load(root);var rows=new ArrayList<Object>();
            try(var user=new User(app.port())){
                RuleCheckOriginalHttpTest.login(user,app.accounts().getFirst());
                String q12=inputs.reference().path("records").get(11).path("question").asText();
                var answer=ask(user,q12);assertThat(answer.path("status").asText()).isEqualTo("ANSWERED");
                assertThat(answer.path("scope").path("questionScope").asText()).contains("第8条没有列出具体例外");rows.add(answer);
                var quote=answer.path("confirmedFacts").get(0).path("citations").get(0);
                String original=quote.path("originalToken").asText();assertThat(user.get("/rule-check/originals/"+original).statusCode()).isEqualTo(200);
                int before=replay.attempts();assertThat(ask(user,"研一学生的选修要求按什么办法？").path("status").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
                assertThat(replay.attempts()).isEqualTo(before);
                var first=ask(user,inputs.reference().path("records").get(4).path("question").asText());
                assertThat(first.path("status").asText()).isEqualTo("NEED_CLARIFICATION");rows.add(first);
                // 换题会失效旧展示凭据；撤回反例须取当前题的新token，不能把404会话隔离误判为撤回409失败。
                answer=ask(user,q12);original=answer.path("confirmedFacts").get(0).path("citations").get(0).path("originalToken").asText();
                actual.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",actual.source);
                assertThat(user.get("/rule-check/originals/"+original).statusCode()).isEqualTo(409);
                assertThat(ask(user,q12).path("status").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
            }
            Path output=Path.of(System.getProperty("zhku.rulecheck.evidenceDir")).resolve("package02-production-http.json");
            Files.write(output,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter().writeValueAsBytes(
                Map.of("selectionMode","SEALED_REPLAY_NOT_NEW_PROVIDER_CALL","providerCalls",0,"actualSettings",settings,"productionBeans",true,"responses",rows,"withdrawnOriginalStatus",409)));
        });
    }
    static String body(String question,Map<String,String> conditions,String token)throws Exception {
        var value=new LinkedHashMap<String,Object>();value.put("question",question);value.put("confirmedConditions",conditions);
        if(token!=null)value.put("clarificationToken",token);return JSON.writeValueAsString(value);
    }
    static JsonNode ask(User user,String question)throws Exception {
        var response=user.json(body(question,Map.of(),null),true,false);assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");return JSON.readTree(response.body());
    }
    static JsonNode reference(Fixture fixture,String id) {
        for(var row:fixture.inputs.reference().path("records"))if(row.path("reviewId").asText().equals(id))return row;
        throw new IllegalArgumentException("未知开发题");
    }
    @Test void allDevelopmentControlsPassThroughActualStudentHttp() throws Exception {
        withApp(Configuration2025.class,app->{
            var f=app.context().getBean(Fixture.class);var rows=new ArrayList<Object>();
            try(var user=new User(app.port())) {
                RuleCheckOriginalHttpTest.login(user,app.accounts().getFirst());
                for(var ref:f.inputs.reference().path("records")) {
                    var result=ask(user,ref.path("question").asText());
                    assertThat(result.path("status").asText()).as(ref.path("reviewId").asText()).isEqualTo(ref.path("humanReview").path("acceptedStatus").asText());
                    assertThat(result.path("knowledgeScope").asText()).isEqualTo(Reviewed2025Trial.SCOPE);
                    rows.add(Map.of("caseId",ref.path("reviewId").asText(),"response",result));
                }
                assertThat(ask(user,"宿舍洗衣机收费多少钱？").path("status").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
            }
            f.trial.save("c05-http-development.json",Map.of("selectionMode","EXACT_DEVELOPMENT_FIXED_ID_CONTROL_NOT_MODEL","providerCalls",0,"responses",rows));
        });
    }
    @Test void reviewedExceptionLimitAndExactOriginalSurviveHttpProjection() throws Exception {
        withApp(Configuration2025.class,app->{
            var f=app.context().getBean(Fixture.class);
            try(var user=new User(app.port());var other=new User(app.port())) {
                RuleCheckOriginalHttpTest.login(user,app.accounts().getFirst());RuleCheckOriginalHttpTest.login(other,app.accounts().get(1));
                var result=ask(user,reference(f,"Q12").path("question").asText());
                assertThat(result.path("status").asText()).isEqualTo("ANSWERED");
                assertThat(result.path("scope").path("questionScope").asText()).contains("第8条没有列出具体例外及审批办法。","现行适用性未核实");
                assertThat(result.path("confirmedFacts").toString()).contains("一般");
                var citation=result.path("confirmedFacts").get(0).path("citations").get(0);
                String handle=citation.path("originalToken").asText();assertThat(handle).isNotBlank();
                var original=user.get("/rule-check/originals/"+handle);assertThat(original.statusCode()).isEqualTo(200);
                var location=JSON.readTree(original.body());
                assertThat(location.path("contextText").asText()).contains("一般");
                assertThat(location.path("recordedPageNumber").asInt()).isIn(76,77);
                assertThat(location.path("revision").asText()).isEqualTo("2025.9.2");
                var binding=f.trial.bindings.values().stream().filter(v->v.chunkId().toString().equals(citation.path("chunkId").asText())).findFirst().orElseThrow();
                assertThat(location.path("sourceId").asText()).isEqualTo(binding.sourceId().toString());
                assertThat(location.path("chunkId").asText()).isEqualTo(binding.chunkId().toString());
                assertThat(location.path("recordedPageNumber").asInt()).isEqualTo(binding.physicalPage());
                assertThat(location.path("sourceLocator").asText()).isEqualTo(binding.locator());
                int contextStart=location.path("contextStartInclusive").asInt();
                assertThat(location.path("contextText").asText().substring(citation.path("startInclusive").asInt()-contextStart,citation.path("endExclusive").asInt()-contextStart)).isEqualTo(citation.path("text").asText());
                assertThat(other.get("/rule-check/originals/"+handle).statusCode()).isEqualTo(404);
                f.trial.save("c05-http-reviewed-original.json",Map.of("response",result,"original",location,"crossSessionStatus",404,"providerCalls",0));
            }
        });
    }
    @Test void excludedChoiceClarificationAndWithdrawalUseRealHttpBoundaries() throws Exception {
        withApp(Configuration2025.class,app->{
            var f=app.context().getBean(Fixture.class);var rows=new ArrayList<Object>();
            try(var user=new User(app.port())) {
                RuleCheckOriginalHttpTest.login(user,app.accounts().getFirst());
                String q10=reference(f,"Q10").path("question").asText();f.overrideId="S01/GENERAL";
                assertThat(ask(user,q10).path("status").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
                // 同主题本科正对照排除固定ID/null及未知主题混淆，再强制尝试同一规则的研究生问题。
                assertThat(ask(user,reference(f,"Q01").path("question").asText()).path("status").asText()).isEqualTo("ANSWERED");
                int selections=f.selectorCalls.get(),retrievals=f.retrievalCalls.get(),audits=f.auditCount();
                String postgraduate=reference(f,"Q01").path("question").asText().replace("全日制普通本科生","研一学生");
                try {
                    assertThat(ask(user,postgraduate).path("status").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
                    assertThat(f.selectorCalls.get()).isEqualTo(selections);assertThat(f.retrievalCalls.get()).isEqualTo(retrievals);assertThat(f.auditCount()).isEqualTo(audits);
                    rows.add(Map.of("postgraduateQuestion",postgraduate,"forcedId","S01/GENERAL","selectorDelta",0,"retrievalDelta",0,"actualAuditDelta",0));
                } finally {f.overrideId=null;}
                String q19=reference(f,"Q05").path("question").asText();var first=ask(user,q19);
                assertThat(first.path("status").asText()).isEqualTo("NEED_CLARIFICATION");rows.add(first);
                String token=first.path("clarificationToken").asText();
                var condition=first.path("clarificationQuestions").get(0);
                var confirmed=Map.of(condition.path("key").asText(),condition.path("allowedValues").get(0).asText());
                String continuation=body(q19,confirmed,token);var resumed=user.json(continuation,true,false);
                assertThat(resumed.statusCode()).isEqualTo(200);var second=JSON.readTree(resumed.body());
                assertThat(second.path("status").asText()).isIn("ANSWERED","PARTIALLY_ANSWERED");rows.add(second);
                assertThat(user.json(continuation,true,false).statusCode()).isEqualTo(409);
                var result=ask(user,reference(f,"Q12").path("question").asText());
                String handle=result.path("confirmedFacts").get(0).path("citations").get(0).path("originalToken").asText();
                f.trial.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",f.trial.source);
                assertThat(user.get("/rule-check/originals/"+handle).statusCode()).isEqualTo(409);
                assertThat(ask(user,reference(f,"Q12").path("question").asText()).path("status").asText()).isEqualTo("INSUFFICIENT_EVIDENCE");
            }
            f.trial.save("c05-http-boundaries.json",Map.of("clarification",rows,"wrongIdRejected",true,"postgraduateRejected",true,"consumedTokenStatus",409,"withdrawnOriginalStatus",409,"providerCalls",0));
        });
    }
    static final class Fixture implements AutoCloseable {
        final DataSource ds;
        final java.util.concurrent.atomic.AtomicInteger selectorCalls=new java.util.concurrent.atomic.AtomicInteger(),retrievalCalls=new java.util.concurrent.atomic.AtomicInteger();
        final Reviewed2025Trial trial;final RuleCheckDevelopmentInputs.Loaded inputs;
        final List<RuleCheckApplicationService.CatalogEntry> catalogue;final RuleCheckApplicationService service;
        volatile String overrideId;
        Fixture(DataSource ds)throws Exception {
            this.ds=ds;
            Path root=Path.of("..").toAbsolutePath().normalize();inputs=RuleCheckDevelopmentInputs.load(root);trial=new Reviewed2025Trial(root);
            try {
                trial.importAndBind(ds);catalogue=RuleCheckTopicCatalogueDraft.loadWithReviewedLimits(root,trial);
                var ids=new LinkedHashMap<String,String>();
                for(var ref:inputs.reference().path("records"))ids.put(ref.path("question").asText(),inputs.controls().path("controls").path(ref.path("reviewId").asText()).asText());
                var selector=new RuleCheckApplicationService.RuleSelector(){
                    public boolean requiresScopedCandidates(){return true;}
                    // 只认已声明开发控制；不拿人工答案生成事实，也不把单一候选当成成功选题。
                    public String select(String question,List<RuleCheckApplicationService.RuleOption> options){selectorCalls.incrementAndGet();return overrideId!=null?overrideId:ids.get(question);}
                };
                var clock=Clock.systemUTC();var verifier=new RuleCheckEvidenceVerifier(trial.reader());
                var capture=new RuleCheckEvidenceCapture(verifier,()->UUID.randomUUID().toString(),new JdbcRuleCheckCitationAuditRepository(ds,trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE),clock);
                var lexical=JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(ds,new RetrievalEligibilityFilter(clock),trial.profile,Reviewed2025Trial.SCOPE,Reviewed2025Trial.CUT);
                service=new RuleCheckApplicationService(new RuleCheckRuntimeSettings(trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5),
                    ()->catalogue,selector,new HybridRetrievalService(new HybridCandidateFuser()),
                    (q,l)->{retrievalCalls.incrementAndGet();return lexical.retrieve(q,l);},
                    (q,l)->List.of(),capture,new RuleCheckDecisionService(verifier));
            } catch(Exception failure){trial.close();throw failure;}
        }
        int auditCount()throws Exception {
            try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM citation_audit a JOIN retrieval_index_entry e ON e.id=a.index_entry_id WHERE e.profile_id=?")) {
                p.setString(1,trial.profile);try(var rows=p.executeQuery()){rows.next();return rows.getInt(1);}
            }
        }
        public void close()throws Exception {trial.close();}
    }
    @Configuration(proxyBeanMethods=false) static class Configuration2025 {
        // 仅测试源显式主配置使用；没有真实资料默认激活开关，关闭工厂仍接受独立回归。
        @Bean(destroyMethod="close") Fixture fixture2025(DataSource ds,Flyway migration)throws Exception{return new Fixture(ds);}
        // HTTP元数据也必须来自实际装配边界，不能沿用通用夹具的合成scope。
        @Bean @Primary RuleCheckRuntimeSettings settings2025(Fixture fixture){return new RuleCheckRuntimeSettings(fixture.trial.profile,Reviewed2025Trial.CUT,Reviewed2025Trial.SCOPE,Reviewed2025Trial.REVISION,20,5);}
        @Bean @Primary RuleCheckApplicationService service2025(Fixture fixture){return fixture.service;}
        @Bean @Primary RuleCheckOriginalEvidenceService original2025(Fixture fixture){return fixture.trial.originals();}
    }
}
