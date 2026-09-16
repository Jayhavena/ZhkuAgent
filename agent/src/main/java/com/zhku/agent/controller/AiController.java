package com.zhku.agent.controller;

import com.zhku.agent.agent.DefaultAgentFactory;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private DefaultAgentFactory defaultAgentFactory;

    /**
     * 流式调用任务 Agent。
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithAgent(String message) {
        return defaultAgentFactory.create().runStream(message);
    }
}
