package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.*;
import java.time.Clock;
import java.util.Objects;
import java.util.function.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

/** 双显式开关；配置和工具创建器必须由受控启动器提供，绝不以测试/回放代替缺失的在线装配。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"zhku.rule-agent-http.enabled","zhku.rule-agent-http.online-enabled"},havingValue="true")
public class RuleAgentOnlineHttpConfiguration {
    public record Options(RuleAgentProvider.Settings settings,Supplier<String> key,Consumer<RuleAgentProvider.Trace> audit,int allowance){
        public Options {
            Objects.requireNonNull(settings);Objects.requireNonNull(key);Objects.requireNonNull(audit);
            if("LOCAL_TEST_ONLY".equals(settings.authorizationId())||allowance<1||allowance>24)throw new IllegalArgumentException("在线HTTP装配须显式真实配置与有界额度");
        }
    }
    @Bean(destroyMethod="close") RuleAgentTaskToolsRegistry ruleAgentTaskToolsRegistry(RuleAgentTaskToolsRegistry.ToolFactory tools,Clock clock){
        return new RuleAgentTaskToolsRegistry(tools,clock,RuleAgentTaskStore.DEFAULT_LIMITS.maxTasks());
    }
    // autowireCandidate=false：在线工厂虽实现 TaskStore.Factory，但不作为该类型的按类注入候选。
    // 这样 TaskStore 只能拿到受控启动入口显式暴露的工厂，避免"顺手注入"绕过准入模式校验。
    @Bean(destroyMethod="close",autowireCandidate=false) RuleAgentOnlineFactory ruleAgentOnlineHttpFactory(Options options,RuleAgentTaskToolsRegistry registry,Clock clock){
        // 同一任务的执行和原文使用同一工具实例；原文不能另外构造绕过任务token与目录快照。
        // Options由服务端提供，本地配置值不替代新外发授权，不能复用已关闭的P01额度。
        return new RuleAgentOnlineFactory(options.settings(),options.key(),registry::open,options.audit(),options.allowance(),clock);
    }
}
