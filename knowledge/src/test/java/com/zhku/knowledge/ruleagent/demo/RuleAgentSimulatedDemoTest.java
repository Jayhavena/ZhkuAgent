package com.zhku.knowledge.ruleagent.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.ruleagent.web.*;
import org.springframework.context.annotation.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static com.zhku.knowledge.ruleagent.RulePublicationContract.*;
import static org.assertj.core.api.Assertions.*;

/** 不创建 Spring Web 宿主或数据库；主入口必须能从正式 classpath 单独找到。 */
class RuleAgentSimulatedDemoTest {
    @Test void uncoveredInputIsExplicitReplayMissWithoutToolOrModelExecution() {
        var registry=org.mockito.Mockito.mock(RuleAgentTaskToolsRegistry.class);
        var tools=org.mockito.Mockito.mock(RuleAgentEvidenceTools.class);
        org.mockito.Mockito.when(registry.open(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(tools);
        var factory=new RuleAgentSimulatedDemoFactory(registry,Clock.systemUTC());
        var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());var task=UUID.randomUUID();
        var context=com.zhku.agent.agent.runtime.AgentRunContext.create("未覆盖的合成问题");
        var output=factory.open(owner,task).run(context,RuleAgentRunner.Input.single(owner,task,"未覆盖的合成问题"));
        assertThat(output.snapshot().execution().replayCoverage()).isEqualTo(ReplayCoverage.MISS);
        assertThat(output.snapshot().result().items()).allSatisfy(q->assertThat(q.issues()).anyMatch(i->i.reasonCode()==ReasonCode.REPLAY_NOT_COVERED));
        assertThat(factory.simulatedPortCalls()).isZero();
        assertThat(context.termination().isDone()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(tools);
    }
    @TempDir Path temporary;
    @Test void standaloneMainExistsOutsideJUnit() {
        assertThatCode(() -> Class.forName("com.zhku.knowledge.ruleagent.demo.RuleAgentSimulatedDemoHost")
                .getMethod("main", String[].class)).doesNotThrowAnyException();
    }

    Map<String,String> validEnvironment() throws Exception {
        Path assets=Files.createDirectory(temporary.resolve("assets")); Files.createDirectories(assets.resolve("knowledge/src"));
        Path run=Files.createDirectory(temporary.resolve("m104b-test-20261020"));
        return new HashMap<>(Map.of("ZHKU_SIM_DEMO_BATCH",run.getFileName().toString(),"ZHKU_SIM_DEMO_APPROVED_BATCH",run.getFileName().toString(),
                "ZHKU_SIM_DEMO_ASSETS",assets.toString(),"ZHKU_SIM_DEMO_RUN",run.toString(),"ZHKU_SIM_DEMO_SECONDS","30",
                "ZHKU_DB_URL","jdbc:postgresql://127.0.0.1:55441/zhkuagent_rulecheck_e2e","ZHKU_SIM_DEMO_PORT","58141"));
    }
    @Test void rejectsUnapprovedDatabaseAndOldOrOverlappingPortBeforeAnyConnection() throws Exception {
        var env=validEnvironment(); assertThat(RuleAgentSimulatedDemoHost.Options.read(env).port()).isEqualTo(58141);
        env.remove("ZHKU_SIM_DEMO_APPROVED_BATCH");assertThatThrownBy(()->RuleAgentSimulatedDemoHost.Options.read(env)).isInstanceOf(IllegalArgumentException.class);
        env.put("ZHKU_SIM_DEMO_APPROVED_BATCH",env.get("ZHKU_SIM_DEMO_BATCH"));env.put("ZHKU_DB_URL","jdbc:postgresql://127.0.0.1:54329/zhkuagent_rulecheck_e2e");
        assertThatThrownBy(()->RuleAgentSimulatedDemoHost.Options.read(env)).hasMessageContaining("端口");
        env.put("ZHKU_DB_URL","jdbc:postgresql://127.0.0.1:58141/zhkuagent_rulecheck_e2e");
        assertThatThrownBy(()->RuleAgentSimulatedDemoHost.Options.read(env)).hasMessageContaining("端口");
    }
    @Test void realSpringDefinitionsContainSingleSimulatedFactoryAndNoOnlineFactoryWithoutRefreshingResources() {
        try(var context=new GenericApplicationContext()) {
            var settings=new HashMap<String,Object>();for(String arg:RuleAgentSimulatedDemoHost.arguments(58141)){
                int split=arg.indexOf('=');settings.put(arg.substring(2,split),arg.substring(split+1));
            }
            context.getEnvironment().setActiveProfiles("zhku-rule-check-preview");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("demo",settings));
            new AnnotatedBeanDefinitionReader(context).register(RuleAgentSimulatedDemoHost.App.class);
            var parser=new ConfigurationClassPostProcessor(); parser.setEnvironment(context.getEnvironment());
            parser.postProcessBeanDefinitionRegistry(context.getDefaultListableBeanFactory());
            // 只解析真实配置，不 refresh，不创建 DataSource/身份资源，也不绑定任何 HTTP 端口。
            assertThat(context.containsBeanDefinition("ruleAgentSimulatedExecutor")).isTrue();
            assertThat(context.containsBeanDefinition("ruleAgentOnlineHttpFactory")).isFalse();
            assertThat(context.getBeanFactory().getBeanNamesForType(RuleAgentTaskStore.Factory.class,true,false)).containsExactly("ruleAgentSimulatedExecutor");
            assertThat(context.getBeanFactory().getBeanNamesForType(RuleAgentTaskStore.class,true,false)).containsExactly("ruleAgentTaskStore");
            assertThat(context.getBeanFactory().getBeanNamesForType(RuleAgentTaskController.class,true,false)).hasSize(1);
            assertThat(context.getBeanFactory().getBeanNamesForType(com.zhku.knowledge.rulecheck.web.RuleCheckAuthController.class,true,false)).hasSize(1);
            assertThat(settings).containsEntry("zhku.rule-agent-launch.executor","SIMULATED").containsEntry("zhku.rule-agent-http.online-enabled","false");
        }
    }
    @Test void inheritedConfigurationOrProviderKeyIsRejectedBeforeDatabaseBootstrap() {
        assertThatCode(()->RuleAgentSimulatedDemoHost.validateAmbient(Set.of("PATH","ZHKU_DB_PASSWORD"),Set.of("loader.main","java.home"))).doesNotThrowAnyException();
        for(String key:List.of("SPRING_CONFIG_IMPORT","LOADER_PATH","SERVER__PORT","DASHSCOPE_API_KEY"))
            assertThatThrownBy(()->RuleAgentSimulatedDemoHost.validateAmbient(Set.of(key),Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->RuleAgentSimulatedDemoHost.validateAmbient(Set.of(),Set.of("spring.main.sources"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void simulatedDecisionCannotBroadenQuestionOrSendProviderResponse() {
        var question=RuleAgentSimulatedDemoFactory.QUESTION;
        var view=new RuleAgentRunner.View(question,List.of(new RuleAgentRunner.Question("q1",question)),Map.of(),List.of(),List.of(),List.of(),List.of(),4,6);
        var reply=RuleAgentSimulatedDemoFactory.decide(view);
        assertThat(reply.providerResponseReceived()).isFalse();assertThat(RuleAgentJson.readAction(reply.actionJson())).isInstanceOf(Search.class);
        var unknown=new RuleAgentRunner.View("随意问题",List.of(new RuleAgentRunner.Question("q1","随意问题")),Map.of(),List.of(),List.of(),List.of(),List.of(),4,6);
        assertThatThrownBy(()->RuleAgentSimulatedDemoFactory.decide(unknown)).isInstanceOf(RuleAgentRunner.Rejected.class);
    }
    @Test void simulatedReviewEnumeratesBothBranchesAndEchoesRealInputFingerprint() {
        var q=new RuleAgentRunner.Question("q1",RuleAgentSimulatedDemoFactory.QUESTION);
        var input=new SemanticInput(REVISION,RuleFactCapabilityCatalogue.VERSION,"a".repeat(64),List.of(q),Map.of(),List.of(),Map.of());
        var review=RuleAgentSimulatedDemoFactory.assess(input);
        assertThat(review.inputFingerprint()).isEqualTo(input.inputFingerprint());
        assertThat(review.requirements()).extracting(Requirement::subject).containsExactly(Subject.PUBLIC_ELECTIVE,Subject.PROFESSIONAL_ELECTIVE);
        assertThat(review.bindings()).extracting(SemanticBinding::factId).containsExactly(RuleAgentSimulatedDemoFactory.PUBLIC_FACT,RuleAgentSimulatedDemoFactory.PROFESSIONAL_FACT);
        // 空读事实不能被模拟核验器变成发布许可：真实 Gate 必须拒绝。
        var runnerInput=RuleAgentRunner.Input.single(new Owner(UUID.randomUUID(),UUID.randomUUID()),UUID.randomUUID(),q.text());
        var caps=RuleFactCapabilityCatalogue.fromReviewed(List.of());
        var matching=RuleAgentSimulatedDemoFactory.assess(RulePublicationContract.input(runnerInput,List.of(),caps));
        assertThatThrownBy(()->new RulePublicationGate(caps).assess(runnerInput,
                new Ask("q1","courseType","工程模拟",List.of(RuleAgentSimulatedDemoFactory.PUBLIC_FACT)),matching,List.of())).hasMessage("FACT_NOT_READ");
    }
}
