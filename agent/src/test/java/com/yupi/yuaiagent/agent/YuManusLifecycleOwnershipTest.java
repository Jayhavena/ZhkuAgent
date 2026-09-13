package com.yupi.yuaiagent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * YuManus 的 Spring 生命周期归属测试。
 *
 * <p>容器可以持有创建 Agent 所需的共享依赖和工厂，但不能预先持有一个会被
 * 多个请求复用的 YuManus 单例。</p>
 */
class YuManusLifecycleOwnershipTest {

    /**
     * 验证组件扫描不会把 YuManus 本身注册为 Spring 单例。
     */
    @Test
    void shouldNotExposeYuManusAsSingletonBean() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            assertThat(context.getBeansOfType(YuManus.class))
                    .as("Spring 容器不应持有可被请求共享的 YuManus 单例")
                    .isEmpty();
        }
    }

    /**
     * 验证工厂每次创建新的 Agent，而不是缓存上一次请求的实例。
     */
    @Test
    void shouldCreateDistinctYuManusForEachRequest() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            YuManusFactory factory = context.getBean(
                    YuManusFactory.class
            );

            YuManus first = factory.create();
            YuManus second = factory.create();

            assertThat(first).isNotSameAs(second);
        }
    }

    /** 创建只包含 Agent 工厂及其共享依赖的轻量 Spring 上下文。 */
    private static AnnotationConfigApplicationContext createContext() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();
        context.registerBean(
                "dashscopeChatModel",
                ChatModel.class,
                () -> mock(ChatModel.class)
        );
        context.registerBean(
                "agentRunExecutor",
                Executor.class,
                () -> Runnable::run
        );
        context.registerBean(
                "allTools",
                ToolCallback[].class,
                () -> new ToolCallback[0]
        );
        context.scan("com.yupi.yuaiagent.agent");
        context.refresh();
        return context;
    }
}
