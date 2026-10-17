package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.security.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 启动装配兼容性核查：只验证三个入口各需要哪些开关与 bean、以及失败是否明确。
 *
 * <p>为什么用非 Web 上下文：本轮只回答"装配期"问题。绑定端口会把结果混入宿主环境因素
 * （本机存在 WorkBuddy 注入的 {@code SERVER__PORT}），而端口装配检查本来就不能冒充实际数据库或在线验收。
 * 因此这里全部使用 {@link WebApplicationType#NONE}：不开库、不读密钥、不运行任务、不外发。
 *
 * <p>关键证据设计：在线装配使用的密钥 Supplier 与调用审计 Consumer 都被做成"一旦被调用就抛错"。
 * 上下文能启动，本身就证明装配期没有读取密钥、也没有产生任何供应商调用；再配合
 * {@code remainingCalls()} 未减少与 {@code activeJobs()==0}，构成"零外发"的可复核证据。
 */
@Timeout(90)
class RuleAgentLaunchCompatibilityTest {

    static Map<String, Object> base() {
        var props = new HashMap<String, Object>();
        props.put("logging.level.root", "ERROR");
        props.put("spring.main.banner-mode", "off");
        return props;
    }

    static Map<String, Object> props(Object... pairs) {
        var map = base();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    private static SpringApplication app(Map<String, Object> properties, Class<?>... sources) {
        var application = new SpringApplication(sources);
        // 非 Web：不绑定任何端口，避免把宿主端口占用误读成装配缺陷。
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(properties);
        return application;
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties, Class<?>... sources) {
        return app(properties, sources).run();
    }

    private static Throwable startExpectingFailure(Map<String, Object> properties, Class<?>... sources) {
        var failure = catchThrowable(() -> app(properties, sources).run());
        assertThat(failure).as("该组合必须启动失败").isNotNull();
        return failure;
    }

    /* ---------- 公共装配（身份仓储为测试替身；资料工具在装配检查中不得被创建） ---------- */

    @Configuration(proxyBeanMethods = false)
    static class CoreBeans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean RuleCheckOriginalEvidenceService locator() {
            return new RuleCheckOriginalEvidenceService((s, c) -> Optional.<RuleCheckLocatedEvidence>empty(), "2025.9.2", Set.of());
        }
        @Bean PreviewIdentityRepository identities() { return mock(PreviewIdentityRepository.class); }
    }

    /** 非在线入口自己提供资料工具索引（在线入口由 RuleAgentOnlineHttpConfiguration 自己建，避免出现两个索引）。 */
    @Configuration(proxyBeanMethods = false)
    static class RegistryBean {
        @Bean RuleAgentTaskToolsRegistry registry(Clock clock) {
            return new RuleAgentTaskToolsRegistry((owner, task) -> {
                throw new IllegalStateException("装配检查不得创建资料工具");
            }, clock, RuleAgentTaskStore.DEFAULT_LIMITS.maxTasks());
        }
    }

    /** 在线入口所需的工具创建器；构造期不创建任何工具。 */
    @Configuration(proxyBeanMethods = false)
    static class ToolFactoryBean {
        @Bean RuleAgentTaskToolsRegistry.ToolFactory ruleAgentReviewedTools() {
            return (owner, task) -> { throw new IllegalStateException("装配检查不得创建资料工具"); };
        }
    }

    /** 在线装配输入：密钥与审计一旦被调用即抛错，用来证明装配期零读取、零外发。 */
    @Configuration(proxyBeanMethods = false)
    static class OnlineOptions {
        @Bean RuleAgentOnlineHttpConfiguration.Options ruleAgentOnlineOptions() {
            return new RuleAgentOnlineHttpConfiguration.Options(
                    new RuleAgentProvider.Settings(RuleAgentProvider.BEIJING, RuleAgentProvider.MODEL, "compatibility-check-placeholder"),
                    () -> { throw new AssertionError("装配期不得读取密钥"); },
                    trace -> { throw new AssertionError("装配期不得产生供应商调用审计"); },
                    2);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostFactory {
        @Bean RuleAgentTaskStore.Factory ruleAgentHostFactory() {
            return (owner, task) -> { throw new IllegalStateException("装配检查不得执行任务"); };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SimulatedHost {
        @Bean(name = RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN)
        RuleAgentTaskStore.Factory ruleAgentSimulatedExecutor() {
            return (owner, task) -> { throw new IllegalStateException("装配检查不得执行任务"); };
        }
    }

    /* ---------- 1. 原入口：不应受本轮改动影响 ---------- */

    @Test void existingHttpEntryStillReceivesHostFactoryInjection() {
        try (var context = start(props("zhku.rule-agent-http.enabled", "true"),
                CoreBeans.class, RegistryBean.class, HostFactory.class, RuleAgentHttpConfiguration.class)) {
            var factory = context.getBean(RuleAgentTaskStore.Factory.class);
            assertThat(factory).isSameAs(context.getBean("ruleAgentHostFactory"));
            assertThat(context.getBean(RuleAgentTaskStore.class)).isNotNull();
            assertThat(context.getBean(RuleAgentTaskController.class)).isNotNull();
            // 未开启在线装配时，在线工厂 bean 根本不存在；autowireCandidate 标记无从生效。
            assertThat(context.getBeanNamesForType(RuleAgentOnlineFactory.class)).isEmpty();
        }
    }

    /* ---------- 2. ONLINE 入口：装配可用、零读取、零外发 ---------- */

    @Test void onlineEntryAssemblesWithoutReadingKeyOrSendingAnything() {
        try (var context = start(props(
                        "zhku.rule-agent-http.enabled", "true",
                        "zhku.rule-agent-launch.enabled", "true",
                        RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, "ONLINE",
                        "zhku.rule-agent-http.online-enabled", "true"),
                CoreBeans.class, ToolFactoryBean.class, OnlineOptions.class, RuleAgentHttpConfiguration.class,
                RuleAgentControlledLaunchConfiguration.class)) {
            // autowireCandidate=false 只取消"按类注入候选"，不取消 bean 本身的可解析性。
            var online = context.getBean(RuleAgentOnlineFactory.class);
            assertThat(online).isNotNull();
            // TaskStore 仍然拿到 Factory，且正是受控入口显式暴露的在线工厂实例。
            assertThat(context.getBean(RuleAgentTaskStore.Factory.class)).isSameAs(online);
            // 零外发与零执行：额度未消耗、无运行中的作业、供应商资源未被创建。
            assertThat(online.remainingCalls()).isEqualTo(2);
            assertThat(online.resourcesClosed()).isTrue();
            assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();
            assertThat(context.getBean(RuleAgentTaskToolsRegistry.class).size()).isZero();
        }
    }

    /* ---------- 3. 重复 Factory 必须明确失败 ---------- */

    @Test void duplicateFactoryFailsExplicitly() {
        var failure = startExpectingFailure(props(
                        "zhku.rule-agent-http.enabled", "true",
                        "zhku.rule-agent-launch.enabled", "true",
                        RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, "ONLINE",
                        "zhku.rule-agent-http.online-enabled", "true"),
                CoreBeans.class, ToolFactoryBean.class, OnlineOptions.class, HostFactory.class, RuleAgentHttpConfiguration.class,
                RuleAgentControlledLaunchConfiguration.class);
        assertThat(failure).hasStackTraceContaining("同时存在多个执行工厂");
    }

    /* ---------- 4. SIMULATED 入口：需要自己的开关与宿主执行器 ---------- */

    @Test void simulatedEntryAssemblesWithItsOwnSwitchesAndHostExecutor() {
        try (var context = start(props(
                        "zhku.rule-agent-http.enabled", "true",
                        "zhku.rule-agent-launch.enabled", "true",
                        RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, "SIMULATED"),
                CoreBeans.class, RegistryBean.class, SimulatedHost.class, RuleAgentHttpConfiguration.class,
                RuleAgentControlledLaunchConfiguration.class)) {
            assertThat(context.getBean(RuleAgentTaskStore.Factory.class))
                    .isSameAs(context.getBean(RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN));
            assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();
            // 模拟模式绝不创建在线工厂：供应商入口在装配期就不存在。
            assertThat(context.getBeanNamesForType(RuleAgentOnlineFactory.class)).isEmpty();
        }
    }

    @Test void simulatedEntryFailsWithoutHostExecutor() {
        var failure = startExpectingFailure(props(
                        "zhku.rule-agent-http.enabled", "true",
                        "zhku.rule-agent-launch.enabled", "true",
                        RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, "SIMULATED"),
                CoreBeans.class, RegistryBean.class, RuleAgentHttpConfiguration.class, RuleAgentControlledLaunchConfiguration.class);
        assertThat(failure).hasStackTraceContaining(RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN);
    }

    /* ---------- 5. 开关/模式组合失败矩阵 ---------- */

    @Test void launchSwitchAloneIsInertAndCreatesNoFactory() {
        try (var context = start(props("zhku.rule-agent-launch.enabled", "true"),
                CoreBeans.class, RegistryBean.class, SimulatedHost.class, RuleAgentControlledLaunchConfiguration.class)) {
            // 缺 http 开关时受控入口整体不生效：不产生 TaskStore，也不静默启用在线，
            // 且执行工厂只剩宿主自己定义的那一个（受控入口没有额外贡献任何工厂）。
            assertThat(context.getBeanNamesForType(RuleAgentTaskStore.class)).isEmpty();
            assertThat(context.getBeanNamesForType(RuleAgentOnlineFactory.class)).isEmpty();
            assertThat(context.getBeanNamesForType(RuleAgentTaskStore.Factory.class))
                    .containsExactly(RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN);
        }
    }

    @Test void httpSwitchWithoutLaunchSwitchLeavesFactoryUnsatisfied() {
        // 只开 http 开关、不给 launch 开关：既不启用模拟也不启用在线，TaskStore 必须因缺工厂而明确失败。
        var failure = startExpectingFailure(props("zhku.rule-agent-http.enabled", "true"),
                CoreBeans.class, RegistryBean.class, RuleAgentHttpConfiguration.class);
        assertThat(failure).hasStackTraceContaining("RuleAgentTaskStore");
    }

    @Test void onlineModeWithoutOnlineSwitchFailsExplicitly() {
        var failure = startExpectingFailure(props(
                        "zhku.rule-agent-http.enabled", "true",
                        "zhku.rule-agent-launch.enabled", "true",
                        RuleAgentControlledLaunchConfiguration.MODE_PROPERTY, "ONLINE"),
                CoreBeans.class, RegistryBean.class, RuleAgentHttpConfiguration.class, RuleAgentControlledLaunchConfiguration.class);
        assertThat(failure).hasStackTraceContaining("online-enabled");
    }
}
