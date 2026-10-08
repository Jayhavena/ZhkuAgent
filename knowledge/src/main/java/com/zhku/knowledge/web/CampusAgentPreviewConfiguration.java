package com.zhku.knowledge.web;

import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.campusagent.CampusAgentResultVisibility;
import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import org.springframework.core.env.Environment;
import com.zhku.knowledge.campusagent.archive.CampusRunArchiveStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.tools.modelcall.CourseCreditChatModelFactory;
import com.zhku.knowledge.tools.modelcall.CourseCreditModelTransport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 独立开关的学生预览纵切；工具清单固定为政策查询与纯计算。 */
@Configuration(proxyBeanMethods = false)
@Profile("zhku-local-preview")
@ConditionalOnProperty(prefix = "zhku.campus-agent", name = "enabled", havingValue = "true")
public class CampusAgentPreviewConfiguration {
    @Bean
    CampusAgentResultVisibility zhkuCampusAgentResultVisibility(Environment environment) {
        // 与原问答读取同一资料库；归档开关关闭也不能绕过实时来源许可。
        return new CampusAgentResultVisibility(LocalKnowledgePublisherFactory.createDataSource(environment));
    }

    @Bean(destroyMethod = "close")
    CourseCreditModelTransport zhkuCampusAgentModelTransport(RagProviderProfile profile, ApiKeyResolver keyResolver) {
        return CourseCreditChatModelFactory.open(profile, keyResolver);
    }

    @Bean(destroyMethod = "")
    ChatModel zhkuCampusAgentChatModel(CourseCreditModelTransport transport) {
        return transport.model();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService zhkuCampusAgentWorker() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), Thread.ofPlatform().name("zhku-campus-agent-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService zhkuCampusAgentTimer() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("zhku-campus-agent-timeout").factory());
    }

    @Bean(destroyMethod = "close")
    CampusCreditAgentManager zhkuCampusCreditAgentManager(ChatModel zhkuCampusAgentChatModel,
            LocalPreviewConversationFacade zhkuLocalPreviewConversationFacade,
            ExecutorService zhkuCampusAgentWorker, ScheduledExecutorService zhkuCampusAgentTimer,
            ObjectProvider<CampusRunArchiveStore> archiveProvider, ConfigurableBeanFactory beanFactory) {
        // worker 先关闭准入；transport 后有界等待实际模型返回，不借用用户取消/超时原因。
        beanFactory.registerDependentBean("zhkuCampusAgentModelTransport", "zhkuCampusAgentWorker");
        CampusCreditAgentService service = new CampusCreditAgentService(
                zhkuCampusAgentChatModel,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> {
                    // 模型选政策查询后仍须经五级链裁决；计算前必须取得合格的高风险规则证据。
                    var session = zhkuLocalPreviewConversationFacade.createConversation();
                    return zhkuLocalPreviewConversationFacade.sendMessage(session.sessionId(), question);
                }, 3);
        var archive = archiveProvider.getIfAvailable();
        if (archive == null)
            return new CampusCreditAgentManager(service, zhkuCampusAgentWorker, zhkuCampusAgentTimer,
                    Duration.ofSeconds(30));
        // 动态ObjectProvider不代替明确销毁依赖；Manager先排空封写，Store后释放owner锁。
        beanFactory.registerDependentBean("zhkuCampusRunArchiveStore", "zhkuCampusCreditAgentManager");
        return new CampusCreditAgentManager(service, zhkuCampusAgentWorker, zhkuCampusAgentTimer,
                Duration.ofSeconds(30), archive);
    }
}
