package com.yupi.yuaiagent.zhku.knowledge.governance.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 本机资料治理页面路由的 HTTP 契约测试。 */
class ZhkuLocalAdminPageControllerTest {

    @Test
    void shouldReturnUtf8LocalAdminPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ZhkuLocalAdminPageController()
        ).build();

        mockMvc.perform(get("/zhku/local-admin"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "text/html;charset=UTF-8"
                ))
                .andExpect(content().string(containsString(
                        "<title>仲园 Agent｜本机资料治理</title>"
                )));
    }
}
