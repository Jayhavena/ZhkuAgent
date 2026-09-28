package com.zhku.knowledge.web;

import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.tools.CampusToolRegistry;
import com.zhku.knowledge.tools.CourseCreditSummaryTool;
import com.zhku.knowledge.tools.modelcall.CourseCreditChatModelFactory;
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
    ChatModel zhkuCampusAgentChatModel(RagProviderProfile profile, ApiKeyResolver keyResolver) {
        return CourseCreditChatModelFactory.create(profile, keyResolver);
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

    @Bean
    CampusCreditAgentManager zhkuCampusCreditAgentManager(ChatModel zhkuCampusAgentChatModel,
            LocalPreviewConversationFacade zhkuLocalPreviewConversationFacade,
            ExecutorService zhkuCampusAgentWorker, ScheduledExecutorService zhkuCampusAgentTimer) {
        CampusCreditAgentService service = new CampusCreditAgentService(
                zhkuCampusAgentChatModel,
                new CampusToolRegistry(List.of(new CourseCreditSummaryTool())),
                question -> {
                    // 模型选政策查询后仍须经五级链裁决；计算前必须取得合格的高风险规则证据。
                    var session = zhkuLocalPreviewConversationFacade.createConversation();
                    return zhkuLocalPreviewConversationFacade.sendMessage(session.sessionId(), question);
                }, 3);
        return new CampusCreditAgentManager(service, zhkuCampusAgentWorker,
                zhkuCampusAgentTimer, Duration.ofSeconds(30));
    }
}
