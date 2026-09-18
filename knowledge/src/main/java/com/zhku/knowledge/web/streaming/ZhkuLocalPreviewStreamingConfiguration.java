package com.zhku.knowledge.web.streaming;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 本机预览 SSE 的开关与资源上限，避免默认入口意外持有后台线程。 */
@Configuration(proxyBeanMethods = false)
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
public class ZhkuLocalPreviewStreamingConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(
            prefix = "zhku.streaming",
            name = "enabled",
            havingValue = "true"
    )
    ExecutorService zhkuLocalPreviewStreamingExecutor(
            @Value("${zhku.streaming.worker-threads:2}") int workerThreads,
            @Value("${zhku.streaming.queue-capacity:16}") int queueCapacity
    ) {
        if (workerThreads <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException("SSE 工作线程数和队列容量必须为正数");
        }
        return new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("zhku-sse-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "zhku.streaming",
            name = "enabled",
            havingValue = "true"
    )
    ConversationStreamProcessor zhkuLocalPreviewConversationStreamProcessor(
            LocalPreviewConversationFacade zhkuLocalPreviewConversationFacade,
            ConversationStore zhkuLocalPreviewConversationStore,
            Clock zhkuLocalPreviewClock
    ) {
        return new ConversationStreamProcessor(
                zhkuLocalPreviewConversationFacade,
                zhkuLocalPreviewConversationStore,
                zhkuLocalPreviewClock
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "zhku.streaming",
            name = "enabled",
            havingValue = "true"
    )
    SseConversationStreamer zhkuLocalPreviewConversationStreamer(
            ConversationStreamProcessor zhkuLocalPreviewConversationStreamProcessor,
            ExecutorService zhkuLocalPreviewStreamingExecutor,
            @Value("${zhku.streaming.timeout-millis:30000}") long timeoutMillis
    ) {
        return new SseConversationStreamer(
                zhkuLocalPreviewConversationStreamProcessor,
                zhkuLocalPreviewStreamingExecutor,
                timeoutMillis
        );
    }
}
