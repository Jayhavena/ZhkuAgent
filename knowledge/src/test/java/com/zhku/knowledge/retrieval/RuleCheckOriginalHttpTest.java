package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rulecheck.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;

/** 真实HTTP/认证/Runtime/词法/审计/复查；仅临时库合成条款，不调用模型或审批真实资料。 */
@Timeout(60)
class RuleCheckOriginalHttpTest {
    static final String TEXT="合成条款：上限4学分。";
    static final String BODY="{\"question\":\"合成条款\",\"confirmedConditions\":{}}";
    static void run(Checked action) {assertThatCode(()->withApp(FixtureConfiguration.class,action)).doesNotThrowAnyException();}
    static void login(User user,Account account)throws Exception {
        user.csrf();assertThat(user.login(account,true).statusCode()).isEqualTo(200);user.csrf();
    }
    static String answer(User user)throws Exception {
        var response=user.json(BODY,true,false);assertThat(response.statusCode()).isEqualTo(200);
        var body=JSON.readTree(response.body());assertThat(body.path("status").asText()).isEqualTo("ANSWERED");
        String handle=body.path("confirmedFacts").get(0).path("citations").get(0).path("originalToken").asText();
        assertThat(UUID.fromString(handle).toString()).isEqualTo(handle);return handle;
    }
    static void rejected(java.net.http.HttpResponse<String> response,int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.body()).doesNotContain(TEXT,"confirmedFacts","contextText","originalToken");
    }
    @Test void actualRuntimeIssuedHandleOpensCurrentQualifiedOriginal() {run(app->{
        var fixture=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port())) {
            login(user,app.accounts().get(0));String handle=answer(user);var response=user.get("/rule-check/originals/"+handle);
            assertThat(response.statusCode()).isEqualTo(200);var original=JSON.readTree(response.body());
            assertThat(original.path("contextText").asText()).isEqualTo(TEXT);
            assertThat(original.path("sourceId").asText()).isEqualTo(fixture.source.toString());
            assertThat(original.path("recordedPageNumber").asInt()).isEqualTo(1);
            assertThat(original.path("pageNumberKind").asText()).isEqualTo("RECORDED_SOURCE_PAGE");
            assertThat(original.path("safeSourceUrl").asText()).isEqualTo("https://official.example/fixture.pdf");
            assertThat(original.path("unknownItems").toString()).contains("映射尚未核实");
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            assertThat(fixture.countAudit()).isEqualTo(1);
        }
    });}
    @Test void handlesRequireOwnSessionCurrentRoundAndExactDeadline() {run(app->{
        var fixture=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port());var other=new User(app.port())) {
            login(user,app.accounts().get(0));login(other,app.accounts().get(1));String old=answer(user);
            rejected(other.get("/rule-check/originals/"+old),404);
            rejected(user.get("/rule-check/originals/"+UUID.randomUUID()),404);
            rejected(user.get("/rule-check/originals/not-a-uuid"),404);
            rejected(user.get("/rule-check/originals/"+old+"?sourceId="+fixture.source),400);
            String current=answer(user);rejected(user.get("/rule-check/originals/"+old),404);
            assertThat(user.get("/rule-check/originals/"+current).statusCode()).isEqualTo(200);
            fixture.clock.now=fixture.clock.now.plusSeconds(300);rejected(user.get("/rule-check/originals/"+current),404);
        }
    });}
    @Test void originalRequiresCurrentPublicationVersionHashAndCut() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port())) {
            login(user,app.accounts().get(0));String handle=answer(user);String path="/rule-check/originals/"+handle;
            f.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",f.source);rejected(user.get(path),409);
            f.update("UPDATE knowledge_source SET publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE' WHERE id=?",f.source);
            f.update("UPDATE knowledge_source SET source_version='other' WHERE id=?",f.source);rejected(user.get(path),409);
            f.update("UPDATE knowledge_source SET source_version='2025.9.2' WHERE id=?",f.source);
            f.update("UPDATE knowledge_chunk SET content=? WHERE id=?",TEXT+"变化",f.chunk);rejected(user.get(path),409);
            f.update("UPDATE knowledge_chunk SET content=? WHERE id=?",TEXT,f.chunk);
            f.update("UPDATE retrieval_index_entry SET chunking_version='other' WHERE id=?",f.entry);rejected(user.get(path),409);
        }
    });}
    @Test void decisionWithdrawalOrCatalogueChangeBeforeReleaseNeverReturnsFacts() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port())) {
            login(user,app.accounts().get(0));
            f.afterDecision=()->f.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",f.source);
            rejected(user.json(BODY,true,false),409);assertThat(f.countAudit()).isEqualTo(1);
            f.update("UPDATE knowledge_source SET publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE' WHERE id=?",f.source);
            f.afterDecision=()->f.catalogue.set(List.of());rejected(user.json(BODY,true,false),409);
        }
    });}
    @Test void identityChangedAfterDecisionIsRejectedWithoutLateCookieDeletion() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port())) {
            login(user,app.accounts().get(0));f.afterDecision=()->f.revoke(app.accounts().get(0));
            var response=user.json(BODY,true,false);rejected(response,401);
            assertThat(response.headers().allValues("set-cookie").toString()).doesNotContain("Max-Age=0");
            assertThat(user.get("/auth/me").statusCode()).isEqualTo(401);
        }
    });}
    @Test void originalRechecksAfterReadForWithdrawalMetadataAndIdentity() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port())) {
            login(user,app.accounts().get(0));String handle=answer(user);String path="/rule-check/originals/"+handle;
            f.afterLocate=()->f.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",f.source);rejected(user.get(path),409);
            f.update("UPDATE knowledge_source SET publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE' WHERE id=?",f.source);
            f.afterLocate=()->f.update("UPDATE knowledge_chunk SET source_locator='合成修改定位' WHERE id=?",f.chunk);rejected(user.get(path),409);
            f.afterLocate=()->f.revoke(app.accounts().get(0));rejected(user.get(path),401);
        }
    });}
    @Test void accountSwitchDuringSlowDecisionCannotReleaseOldFactsOrDeleteNewLogin() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port());var executor=Executors.newSingleThreadExecutor()) {
            login(user,app.accounts().get(0));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            f.afterDecision=()->{entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw new IllegalStateException("夹具未释放");};
            var old=executor.submit(()->user.json(BODY,true,false));
            try {
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();login(user,app.accounts().get(1));release.countDown();
                var response=old.get(4,TimeUnit.SECONDS);rejected(response,409);
                assertThat(response.headers().allValues("set-cookie").toString()).doesNotContain("Max-Age=0");
                assertThat(JSON.readTree(user.get("/auth/me").body()).path("userId").asText()).isEqualTo(app.accounts().get(1).id().toString());
                answer(user);
            } finally {release.countDown();}
        }
    });}
    @Test void newQuestionDuringOriginalIoRejectsOldHandleWithoutClearingNewRound() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port());var executor=Executors.newSingleThreadExecutor()) {
            login(user,app.accounts().get(0));String handle=answer(user);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            f.afterLocate=()->{entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw new IllegalStateException("夹具未释放");};
            var old=executor.submit(()->user.get("/rule-check/originals/"+handle));
            try {
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();String current=answer(user);release.countDown();
                rejected(old.get(4,TimeUnit.SECONDS),409);assertThat(user.get("/rule-check/originals/"+current).statusCode()).isEqualTo(200);
            } finally {release.countDown();}
        }
    });}
    @Test void delayedOldRequestBeforeGenerationCannotOverwriteCompletedNewAccountQuestion() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port());var executor=Executors.newSingleThreadExecutor()) {
            login(user,app.accounts().get(0));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            // 入口仓储仍执行真实SQL；只在许可已核对、进入Controller建立轮次前暂停。
            f.afterIdentity=()->{entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw new IllegalStateException("夹具未释放");};
            var old=executor.submit(()->user.json(BODY,true,false));
            try {
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();login(user,app.accounts().get(1));String current=answer(user);
                release.countDown();var response=old.get(4,TimeUnit.SECONDS);rejected(response,409);
                assertThat(user.get("/rule-check/originals/"+current).statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(user.get("/auth/me").body()).path("userId").asText()).isEqualTo(app.accounts().get(1).id().toString());
                assertThat(f.countAudit()).isEqualTo(1);
            } finally {release.countDown();}
        }
    });}
    @Test void delayedEntryRevocationCannotInvalidateNewLoginOrDeleteItsCookie() {run(app->{
        var f=app.context().getBean(OriginalFixture.class);
        try(var user=new User(app.port());var executor=Executors.newSingleThreadExecutor()) {
            login(user,app.accounts().get(0));
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            // 首次SQL核对后暂停，再以真实SQL读已撤销A；制造入口异常跨越B登录的窗口。
            f.afterIdentity=()->{entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw new IllegalStateException("夹具未释放");
                app.context().getBean(com.zhku.knowledge.security.PreviewIdentityRepository.class).requireCurrent(
                    new com.zhku.knowledge.security.TrustedActor(app.accounts().get(0).id(),com.zhku.knowledge.security.TrustedActor.Role.USER,0));};
            var old=executor.submit(()->user.json(BODY,true,false));
            try {
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
                // A此刻仍有效，B先完成登录和新题，再撤销A并交付旧请求失败。
                assertThat(user.login(app.accounts().get(1),true).statusCode()).isEqualTo(200);user.csrf();
                String current=answer(user);f.revoke(app.accounts().get(0));release.countDown();var response=old.get(4,TimeUnit.SECONDS);
                rejected(response,401);assertThat(response.headers().allValues("set-cookie")).isEmpty();
                assertThat(user.get("/rule-check/originals/"+current).statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(user.get("/auth/me").body()).path("userId").asText()).isEqualTo(app.accounts().get(1).id().toString());
                assertThat(f.countAudit()).isEqualTo(1);
            } finally {release.countDown();}
        }
    });}
    @FunctionalInterface interface Hook {void run()throws Exception;}
    static final class OriginalFixture implements AutoCloseable {
        final UUID source=UUID.randomUUID(),chunk=UUID.randomUUID(),run=UUID.randomUUID(),entry=UUID.randomUUID();
        final DataSource ds;final FixtureClock clock=new FixtureClock();
        final AtomicReference<List<RuleCheckApplicationService.CatalogEntry>> catalogue=new AtomicReference<>();
        volatile Hook afterDecision=()->{},afterLocate=()->{},afterIdentity=()->{};
        OriginalFixture(DataSource ds,RuleCheckRuntimeSettings settings)throws Exception {
            this.ds=ds;clock.now=Instant.now();
            update("""
                INSERT INTO knowledge_source(id,title,source_type,source_version,publication_status,published_at,content_checksum,
                visibility_scope,publication_scope,review_notice,sharing_rights_confirmed,document_family,knowledge_scope,source_url)
                VALUES(?,'合成HTTP原文','STUDENT_HANDBOOK','2025.9.2','PUBLISHED',CURRENT_TIMESTAMP,?,
                'STUDENT_VISIBLE','本地验证','非真实审核',true,'STUDENT_HANDBOOK','PUBLIC_ELECTIVE_CREDIT_LIMIT','https://official.example/fixture.pdf')
                """,source,sha(TEXT));
            update("INSERT INTO knowledge_chunk(id,source_id,chunk_index,source_locator,page_number,content,content_checksum) VALUES(?,?,0,'合成页1',1,?,?)",chunk,source,TEXT,sha(TEXT));
            update("INSERT INTO embedding_profile(profile_id,provider,model,dimensions,status,activated_at) VALUES(?,'fixture','none',3,'ACTIVE',CURRENT_TIMESTAMP)",settings.profileId());
            update("INSERT INTO retrieval_index_run(id,profile_id,chunking_version,status,finished_at,indexed_entries) VALUES(?,?,?,'COMPLETED',CURRENT_TIMESTAMP,1)",run,settings.profileId(),settings.chunkingVersion());
            update("INSERT INTO retrieval_index_entry(id,run_id,profile_id,chunk_id,source_version,chunking_version,content_checksum) VALUES(?,?,?,?,'2025.9.2',?,?)",entry,run,settings.profileId(),chunk,settings.chunkingVersion(),sha(TEXT));
            var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,settings.referenceRevision(),sha(TEXT),0,TEXT.length(),TEXT);
            var rule=new RuleCheckRuleTemplate("synthetic-only",settings.referenceRevision(),RuleCheckRuleTemplate.ReviewState.APPROVED,"合成测试责任",java.time.LocalDate.of(2026,10,8),
                List.of(new RuleCheckRuleTemplate.Fact("limit","合成上限4",true,Map.of(),List.of(binding))),List.of(),List.of(),List.of());
            catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(rule,"合成条款")));
        }
        void update(String sql,Object... values)throws Exception {
            try(var c=ds.getConnection();var p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();}
        }
        void revoke(Account account)throws Exception {update("UPDATE preview_identity SET auth_revision=auth_revision+1 WHERE user_id=?",account.id());}
        int countAudit()throws Exception {
            try(var c=ds.getConnection();var p=c.prepareStatement("SELECT count(*) FROM citation_audit WHERE index_entry_id=?")) {
                p.setObject(1,entry);try(var rows=p.executeQuery()){rows.next();return rows.getInt(1);}
            }
        }
        void decisionHook()throws Exception {Hook hook=afterDecision;afterDecision=()->{};hook.run();}
        void locateHook()throws Exception {Hook hook=afterLocate;afterLocate=()->{};hook.run();}
        void identityHook()throws Exception {Hook hook=afterIdentity;afterIdentity=()->{};hook.run();}
        public void close()throws Exception {
            update("DELETE FROM citation_audit WHERE index_entry_id=?",entry);update("DELETE FROM retrieval_index_entry WHERE id=?",entry);
            update("DELETE FROM retrieval_index_run WHERE id=?",run);update("DELETE FROM embedding_profile WHERE profile_id='synthetic-http'");
            update("DELETE FROM knowledge_chunk WHERE id=?",chunk);update("DELETE FROM knowledge_source WHERE id=?",source);
        }
    }
    static String sha(String text)throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    @Configuration(proxyBeanMethods=false) static class FixtureConfiguration {
        @Bean(destroyMethod="close") OriginalFixture originalFixture(DataSource ds,Flyway migration,RuleCheckRuntimeSettings settings)throws Exception {return new OriginalFixture(ds,settings);}
        @Bean @Primary FixtureClock originalClock(OriginalFixture fixture){return fixture.clock;}
        @Bean(destroyMethod="close") @Primary com.zhku.knowledge.security.PreviewIdentityRepository actualIdentity(OriginalFixture fixture) {
            var identities=spy(new com.zhku.knowledge.security.PreviewIdentityRepository(fixture.ds));
            doAnswer(i->{i.callRealMethod();fixture.identityHook();return null;}).when(identities).requireCurrent(any());return identities;
        }
        @Bean @Primary RuleCheckApplicationService actualSyntheticService(OriginalFixture fixture,RuleCheckRuntimeSettings settings) {
            EmbeddingClient noProvider=new EmbeddingClient() {
                public float[] embed(String text){throw new RetrieverUnavailableException("合成测试仅词法");}public int dimensions(){return 3;}
            };
            var service=spy(RuleCheckRuntimeFactory.create(fixture.ds,settings,fixture.catalogue::get,
                (q,options)->"synthetic-only",noProvider,()->UUID.randomUUID().toString(),fixture.clock));
            doAnswer(i->{var result=i.callRealMethod();fixture.decisionHook();return result;}).when(service).checkWithContext(anyString(),anyMap());return service;
        }
        @Bean @Primary RuleCheckOriginalEvidenceService actualSyntheticOriginal(OriginalFixture fixture,RuleCheckRuntimeSettings settings) {
            var reader=new JdbcRuleCheckEvidenceReader(fixture.ds,settings.profileId(),settings.chunkingVersion(),settings.requiredScope());
            var service=spy(new RuleCheckOriginalEvidenceService(reader::readLocated,settings.referenceRevision(),Set.of("official.example")));
            doAnswer(i->{var result=i.callRealMethod();fixture.locateHook();return result;}).when(service).locate(any());return service;
        }
    }
}
