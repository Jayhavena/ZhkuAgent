package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 实际证据工具配合合成存储；不建立数据库或真实供应商端口。 */
@Timeout(15)
class RuleAgentTaskToolsRegistryTest {
    final Owner owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
    final UUID task=UUID.randomUUID(),source=UUID.randomUUID(),chunk=UUID.randomUUID();
    final String text="合成条款：课程类别仅用于装配检查。";
    final AtomicReference<Instant> time=new AtomicReference<>(Instant.parse("2026-10-14T10:00:00Z"));
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time.get();}};
    final AtomicBoolean eligible=new AtomicBoolean(true),changedText=new AtomicBoolean();
    final AtomicInteger builds=new AtomicInteger();
    final AtomicReference<List<RuleCheckApplicationService.CatalogEntry>> catalogue=new AtomicReference<>();
    RuleAgentEvidenceTools build(Owner actor,UUID id){
        builds.incrementAndGet();
        try{
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
            var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,"2025.9.2",hash,0,text.length(),text);
            var fact=new RuleCheckRuleTemplate.Fact("elective-category-distinction","合成课程分类",true,Map.of(),List.of(binding));
            var template=new RuleCheckRuleTemplate("S02/GENERAL","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"SYNTHETIC_TEST",LocalDate.of(2026,10,14),List.of(fact),List.of(),List.of(),List.of());
            catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(template,"合成课程范围")));
            var candidate=new RetrievedChunk(chunk,text,"synthetic","synthetic",1,"2025.9.2",hash,source);
            return new RuleAgentEvidenceTools(actor,id,new RuleCheckRuntimeSettings("p","c","s","2025.9.2",20,5),catalogue::get,(q,n)->List.of(candidate),
                (s,c)->Optional.of(new RuleCheckEvidenceSnapshot("current",source,chunk,"2025.9.2",hash,changedText.get()?"已变化":text,eligible.get())),r->{},clock);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    UUID proof(RuleAgentEvidenceTools tools){
        var found=tools.search(owner,task,UUID.randomUUID(),1,new Search("q1","合成课程"));
        var read=tools.read(owner,task,UUID.randomUUID(),2,new Read("q1",List.of(found.candidates().getFirst().handle())));
        assertThat(read.observation().status()).isEqualTo(ObservationStatus.SUCCEEDED);
        return read.observation().evidence().getFirst().originalToken();
    }
    @Test void sameTaskReusesToolsButDifferentOwnerSessionOrTaskCannotRead(){
        try(var registry=new RuleAgentTaskToolsRegistry(this::build,clock,8)){
            var tools=registry.open(owner,task);assertThat(registry.open(owner,task)).isSameAs(tools);assertThat(builds.get()).isEqualTo(1);
            UUID token=proof(tools);assertThat(registry.read(owner,task,token)).isPresent();
            assertThat(registry.read(new Owner(UUID.randomUUID(),owner.sessionNonce()),task,token)).isEmpty();
            assertThat(registry.read(new Owner(owner.actorId(),UUID.randomUUID()),task,token)).isEmpty();
            assertThat(registry.read(owner,UUID.randomUUID(),token)).isEmpty();assertThat(registry.read(owner,task,UUID.randomUUID())).isEmpty();
        }
    }
    @Test void originalAlwaysRechecksCurrentEligibilityTextAndCatalogue(){
        try(var registry=new RuleAgentTaskToolsRegistry(this::build,clock,8)){
            UUID token=proof(registry.open(owner,task));eligible.set(false);assertThat(registry.read(owner,task,token)).isEmpty();
            eligible.set(true);changedText.set(true);assertThat(registry.read(owner,task,token)).isEmpty();
            changedText.set(false);catalogue.set(List.of());assertThat(registry.read(owner,task,token)).isEmpty();
        }
    }
    @Test void expiryIsBoundedAndCloseRevokesHandles(){
        var registry=new RuleAgentTaskToolsRegistry(this::build,clock,1);UUID token=proof(registry.open(owner,task));
        assertThatThrownBy(()->registry.open(owner,UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        time.set(time.get().plusMillis(DEFAULT_BUDGET.taskTtlMillis()));assertThat(registry.read(owner,task,token)).isEmpty();
        registry.open(owner,UUID.randomUUID());assertThat(registry.size()).isEqualTo(1);registry.close();assertThat(registry.size()).isZero();
        assertThat(registry.read(owner,task,token)).isEmpty();assertThatThrownBy(()->registry.open(owner,task)).isInstanceOf(IllegalStateException.class);
    }
    @Test void failedCreationReleasesCapacity(){
        var attempts=new AtomicInteger();try(var registry=new RuleAgentTaskToolsRegistry((o,t)->{if(attempts.getAndIncrement()==0)throw new IllegalStateException("synthetic");return build(o,t);},clock,1)){
            assertThatThrownBy(()->registry.open(owner,task)).isInstanceOf(IllegalStateException.class);assertThat(registry.size()).isZero();
            assertThat(registry.open(owner,task)).isNotNull();assertThat(registry.size()).isEqualTo(1);
        }
    }
    @Test void closeDuringBuildCannotPublishLateTools()throws Exception{
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        try(var registry=new RuleAgentTaskToolsRegistry((o,t)->{started.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException();}return build(o,t);},clock,1)){
            var result=executor.submit(()->registry.open(owner,task));assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();registry.close();release.countDown();
            assertThatThrownBy(()->result.get(2,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);assertThat(registry.size()).isZero();
        }finally{release.countDown();executor.shutdownNow();assertThat(executor.awaitTermination(2,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void officialConfigurationRejectsLocalTestProvider(){
        var local=new RuleAgentProvider.Settings(java.net.URI.create("http://127.0.0.1:1/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY");
        assertThatThrownBy(()->new RuleAgentOnlineHttpConfiguration.Options(local,()->"synthetic",t->{},1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void configuredFrozenFactoryCreatesTaskToolsWithoutReadingKeyOrOpeningClient(){
        var options=new RuleAgentOnlineHttpConfiguration.Options(new RuleAgentProvider.Settings(RuleAgentProvider.BEIJING,RuleAgentProvider.MODEL,"T06_CONFIG_ONLY_NO_DISPATCH"),()->{throw new AssertionError("本轮禁止密钥读取/外发");},t->{},1);
        new ApplicationContextRunner().withUserConfiguration(RuleAgentOnlineHttpConfiguration.class)
            .withBean(Clock.class,()->clock).withBean(RuleAgentTaskToolsRegistry.ToolFactory.class,()->this::build)
            .withBean(RuleAgentOnlineHttpConfiguration.Options.class,()->options)
            .withPropertyValues("zhku.rule-agent-http.enabled=true","zhku.rule-agent-http.online-enabled=true")
            .run(context->{assertThat(context).hasNotFailed();var factory=context.getBean(RuleAgentOnlineFactory.class);factory.open(owner,task);
                var registry=context.getBean(RuleAgentTaskToolsRegistry.class);assertThat(builds.get()).isEqualTo(1);UUID token=proof(registry.open(owner,task));assertThat(registry.read(owner,task,token)).isPresent();
                assertThat(factory.remainingCalls()).isEqualTo(1);assertThat(factory.resourcesClosed()).isTrue();});
    }
    @Configuration(proxyBeanMethods=false) static class Dependencies {
        @Bean Clock clock(){return Clock.systemUTC();}
        @Bean RuleAgentTaskToolsRegistry.ToolFactory tools(){return (o,t)->{throw new AssertionError("未运行任务，不应构造工具");};}
    }
    @Test void onlineConfigurationIsOffByDefaultAndMissingExplicitOptionsFails(){
        var runner=new ApplicationContextRunner().withUserConfiguration(RuleAgentOnlineHttpConfiguration.class,Dependencies.class);
        runner.run(context->assertThat(context).doesNotHaveBean(RuleAgentOnlineFactory.class));
        runner.withPropertyValues("zhku.rule-agent-http.enabled=true","zhku.rule-agent-http.online-enabled=true").run(context->assertThat(context).hasFailed());
    }
    @Test void explicitOnlineConfigurationConnectsSameRegistryWithoutOpeningProvider(){
        var options=new RuleAgentOnlineHttpConfiguration.Options(new RuleAgentProvider.Settings(RuleAgentProvider.BEIJING,RuleAgentProvider.MODEL,"T06_CONFIG_ONLY_NO_DISPATCH"),()->{throw new AssertionError("配置不得读取密钥");},t->{},1);
        new ApplicationContextRunner().withUserConfiguration(RuleAgentOnlineHttpConfiguration.class,Dependencies.class)
            .withBean(RuleAgentOnlineHttpConfiguration.Options.class,()->options)
            .withPropertyValues("zhku.rule-agent-http.enabled=true","zhku.rule-agent-http.online-enabled=true")
            .run(context->{assertThat(context).hasNotFailed();var registry=context.getBean(RuleAgentTaskToolsRegistry.class);assertThat(context.getBean(RuleAgentTaskController.OriginalReader.class)).isSameAs(registry);assertThat(context.getBean(RuleAgentOnlineFactory.class).remainingCalls()).isEqualTo(1);assertThat(context.getBean(RuleAgentOnlineFactory.class).resourcesClosed()).isTrue();assertThat(registry.size()).isZero();});
    }
}
