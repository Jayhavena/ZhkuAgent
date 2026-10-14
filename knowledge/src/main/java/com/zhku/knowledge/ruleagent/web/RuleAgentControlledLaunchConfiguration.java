package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.RuleAgentOnlineFactory;
import com.zhku.knowledge.ruleagent.RuleAgentTaskStore;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * T06 受控启动入口：把既有身份仓储、TaskStore、执行工厂、受控资料工具与原文定位服务装配成一个可启动的 HTTP 宿主。
 *
 * <p>为什么这样设计：
 * <ul>
 *   <li><b>双重显式开关，默认关闭。</b>必须同时打开 {@code zhku.rule-agent-http.enabled} 与
 *       {@code zhku.rule-agent-launch.enabled} 才生效。任何打包默认值或 profile 都不能顺手把任务接口暴露出去。</li>
 *   <li><b>执行模式无默认值。</b>{@code zhku.rule-agent-launch.executor} 只接受 {@code SIMULATED} 或
 *       {@code ONLINE}；缺值与未知取值一律让上下文启动失败。</li>
 *   <li><b>模拟执行器不进本模块。</b>模拟模式下必须由宿主显式提供名为
 *       {@value #SIMULATED_EXECUTOR_BEAN} 的 {@link RuleAgentTaskStore.Factory}。仓库源码里没有"模拟实现"，
 *       因此它不可能被顺手带进正式在线装配，也不可能成为供应商故障时的自动回退。</li>
 * </ul>
 *
 * <p>不这样做的风险：以"缺配置就退回可用替身"的方式启动，会让工程验收用的模拟执行被读成真实在线能力，
 * 也会掩盖在线装配缺失。本项目 T05 反复踩到的正是"看起来接线完成、实际未生效"，因此这里宁可启动即失败，
 * 也不做静默降级。
 *
 * <p>边界：本类只做装配与准入校验。它不构造供应商凭证、不初始化数据库、不安装资料，也不代表任何外发许可；
 * 在线分支是否可用仍取决于宿主是否提供了真实供应商配置与独立授权。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"zhku.rule-agent-http.enabled", "zhku.rule-agent-launch.enabled"}, havingValue = "true")
public class RuleAgentControlledLaunchConfiguration {

    /** 执行模式开关名；大小写敏感，避免含糊取值被当默认。 */
    public static final String MODE_PROPERTY = "zhku.rule-agent-launch.executor";
    public static final String SIMULATED = "SIMULATED";
    public static final String ONLINE = "ONLINE";

    /** 模拟执行器必须由宿主显式提供的 bean 名；本模块不提供任何模拟实现。 */
    public static final String SIMULATED_EXECUTOR_BEAN = "ruleAgentSimulatedExecutor";

    /**
     * 准入校验前置到 bean 定义阶段：非法模式必须在上下文刷新时失败，而不是等到第一个任务请求才发现。
     * 这里只读取 Environment 与 bean 定义，不触发数据源、身份仓储或供应商任何资源。
     */
    @Bean
    static BeanFactoryPostProcessor ruleAgentLaunchModeGuard(Environment environment) {
        return beanFactory -> {
            String mode = environment.getProperty(MODE_PROPERTY);
            if (!SIMULATED.equals(mode) && !ONLINE.equals(mode)) {
                throw new IllegalStateException("受控启动必须显式声明 " + MODE_PROPERTY + " 为 " + SIMULATED
                        + " 或 " + ONLINE + "，不接受缺省或未知取值");
            }
            // 模拟执行不得与在线装配共存：否则一次配置失误就可能让"工程模拟"被当成真实供应商链路。
            if (SIMULATED.equals(mode) && environment.getProperty("zhku.rule-agent-http.online-enabled", Boolean.class, false)) {
                throw new IllegalStateException(SIMULATED + " 模式不得同时开启在线装配（zhku.rule-agent-http.online-enabled=true）");
            }
            if (SIMULATED.equals(mode) && !beanFactory.containsBeanDefinition(SIMULATED_EXECUTOR_BEAN)) {
                throw new IllegalStateException(SIMULATED + " 模式必须由宿主显式提供 " + SIMULATED_EXECUTOR_BEAN
                        + " 执行器，不接受缺失或隐式替代");
            }
        };
    }

    /**
     * 在线分支。复用 {@link RuleAgentOnlineHttpConfiguration} 已装配的工厂，并把它显式暴露为
     * TaskStore 需要的 Factory。
     *
     * <p>装配通过不等于获得外发许可：额度、模型与授权仍由宿主配置单独决定，本类不复用任何已关闭额度。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = MODE_PROPERTY, havingValue = ONLINE)
    static class OnlineLaunch {
        @Bean
        RuleAgentTaskStore.Factory ruleAgentOnlineTaskFactory(RuleAgentOnlineFactory factory) {
            return factory;
        }
    }
}
