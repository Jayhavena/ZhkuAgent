package com.yupi.yuaiagent.zhku.knowledge.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 本机问答页面路由的 HTTP 契约测试。 */
class ZhkuLocalPreviewPageControllerTest {

    @Test
    void shouldReturnUtf8LocalPreviewPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewPageController()
        ).build();

        mockMvc.perform(get("/zhku/local-preview"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "text/html;charset=UTF-8"
                ))
                .andExpect(content().string(containsString(
                        "<title>仲园 Agent｜校园资料问答</title>"
                )));
    }
}
