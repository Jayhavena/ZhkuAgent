package com.zhku.agent.controller;

import com.zhku.agent.agent.DefaultAgentFactory;
import com.zhku.agent.app.LoveApp;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 默认运行配置下，恋爱应用不能再作为 HTTP 功能被发现或调用。
 *
 * <p>这里验证 Spring MVC 的真实路由解析结果，而不是检查控制器源码是否包含某个字符串。
 * 这能防止后续有人重新暴露旧路径却没有被发现。</p>
 */
@WebMvcTest(controllers = AiController.class,
        properties = "features.legacy-love.enabled=false")
class LegacyLoveEndpointIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * RED 阶段旧控制器仍依赖 LoveApp；mock 仅用于让 MVC 上下文启动，
     * 不参与路由断言，也不替代被测业务行为。
     */
    @MockitoBean
    private LoveApp loveApp;

    /**
     * 控制器仍保留 Manus 入口，使用 mock 隔离其构造依赖。
     */
    @MockitoBean
    private DefaultAgentFactory defaultAgentFactory;

    @ParameterizedTest
    @ValueSource(strings = {
            "/ai/love_app/chat/sync",
            "/ai/love_app/chat/sse",
            "/ai/love_app/chat/server_sent_event",
            "/ai/love_app/chat/sse_emitter"
    })
    void defaultRuntimeShouldNotExposeLegacyLoveEndpoints(String path) throws Exception {
        mockMvc.perform(get(path)
                        .param("message", "test")
                        .param("chatId", "test-chat"))
                .andExpect(status().isNotFound());
    }
}
