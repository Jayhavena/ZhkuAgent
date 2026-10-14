package com.zhku.knowledge.ruleagent.web;

import com.zhku.knowledge.ruleagent.RuleAgentTaskStore;
import java.time.Clock;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

/** 显式开关且依赖外部提供真实Factory/原文端口；缺配置启动失败，不加载测试替身。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="zhku.rule-agent-http.enabled",havingValue="true")
@Import({RuleAgentTaskController.class,RuleAgentOnlineHttpConfiguration.class})
public class RuleAgentHttpConfiguration {
    @Bean(name="ruleAgentHttpExecutor",destroyMethod="shutdown")
    @ConditionalOnMissingBean(name="ruleAgentHttpExecutor")
    ExecutorService ruleAgentExecutor(){return new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->new Thread(r,"zhku-t06-task"),new ThreadPoolExecutor.AbortPolicy());}
    @Bean(destroyMethod="close") RuleAgentTaskStore ruleAgentTaskStore(@Qualifier("ruleAgentHttpExecutor") Executor executor,RuleAgentTaskStore.Factory factory,Clock clock){return new RuleAgentTaskStore(executor,factory,clock,RuleAgentTaskStore.DEFAULT_LIMITS);}
}
