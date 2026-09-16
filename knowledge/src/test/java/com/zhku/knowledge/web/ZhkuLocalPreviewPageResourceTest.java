package com.zhku.knowledge.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 本机问答页面的接口边界与安全渲染约束测试。 */
class ZhkuLocalPreviewPageResourceTest {

    @Test
    void shouldKeepPageBoundToConversationApiAndSafeTextRendering() throws Exception {
        String page = new String(
                new ClassPathResource("zhku/local-preview/index.html")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(page).contains("/api/zhku/local-preview/conversations");
        assertThat(page).contains("id=\"question-input\"");
        assertThat(page).contains("id=\"citation-panel\"");
        assertThat(page).contains("开始新对话");
        assertThat(page).contains("NEED_CLARIFICATION");
        assertThat(page).contains("OUT_OF_SCOPE");
        assertThat(page).contains("payload.message || \"当前没有可确认的有效依据，无法给出确定性结论。\"");
        assertThat(page).contains("\"当前暂无有效通知\"");
        assertThat(page).contains("SESSION_INVALID");
        assertThat(page).contains("公共选修课每学期最多可以选多少学分？");
        assertThat(page).contains("网络公共选修课的具体限制暂未纳入本机演示范围");
        assertThat(page).contains("sessionStorage");
        assertThat(page).contains("requestEpoch");
        assertThat(page).contains("createConversationOnLoad");
        assertThat(page).contains("textContent");
        assertThat(page).doesNotContain("innerHTML");
    }
}
