package com.zhku.knowledge.controlled;

import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.ruleagent.demo.RuleAgentSimulatedDemoFactory;
import com.zhku.knowledge.ruleagent.web.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** 持久身份宿主的显式任务装配；模式互斥，在线缺配置时绝不回退工程模拟。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="zhku.persistent-student.enabled",havingValue="true")
@Import({PersistentStudentRuntimeController.class,RuleAgentControlledLaunchConfiguration.class,PersistentStudentTaskConfiguration.Simulated.class,
        PersistentStudentTaskConfiguration.Online.class})
public class PersistentStudentTaskConfiguration {
    @Bean RuleAgentReviewedToolsConfiguration.Assets persistentStudentAssets(Environment env) {
        return new RuleAgentReviewedToolsConfiguration.Assets(Path.of(env.getRequiredProperty("zhku.rule-check-preview.repository-root")));
    }
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name=RuleAgentControlledLaunchConfiguration.MODE_PROPERTY,havingValue="SIMULATED")
    static class Simulated {
        @Bean(destroyMethod="close") RuleAgentTaskToolsRegistry persistentStudentRegistry(RuleAgentTaskToolsRegistry.ToolFactory tools,Clock clock) {
            return new RuleAgentTaskToolsRegistry(tools,clock,RuleAgentTaskStore.DEFAULT_LIMITS.maxTasks());
        }
        @Bean(name=RuleAgentControlledLaunchConfiguration.SIMULATED_EXECUTOR_BEAN)
        RuleAgentSimulatedDemoFactory persistentStudentSimulation(RuleAgentTaskToolsRegistry registry,Clock clock) {
            return new RuleAgentSimulatedDemoFactory(registry,clock);
        }
    }
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name=RuleAgentControlledLaunchConfiguration.MODE_PROPERTY,havingValue="ONLINE")
    static class Online {
        @Bean RuleAgentOnlineHttpConfiguration.Options persistentStudentOnlineOptions() {
            Map<String,String> env=System.getenv();
            var settings=RuleAgentProvider.Settings.environment(env);
            int allowance=Integer.parseInt(env.getOrDefault("ZHKU_RULE_AGENT_CALL_ALLOWANCE","0"));
            // 仅保存白名单用量元数据，不保存请求/响应正文或密钥；额度归宿主，澄清不能重新领额度。
            var log=org.slf4j.LoggerFactory.getLogger(PersistentStudentTaskConfiguration.class);
            return new RuleAgentOnlineHttpConfiguration.Options(settings,()->System.getenv("DASHSCOPE_API_KEY"),
                trace->log.info("STUDENT_PROVIDER taskId={} runId={} purpose={} phase={} status={} reason={} inputTokens={} outputTokens={}",
                    trace.taskId(),trace.runId(),trace.purpose(),trace.phase(),trace.status(),trace.reason(),trace.inputTokens(),trace.outputTokens()),allowance);
        }
    }
}
