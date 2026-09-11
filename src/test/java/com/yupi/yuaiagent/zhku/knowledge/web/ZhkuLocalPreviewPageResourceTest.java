package com.yupi.yuaiagent.zhku.knowledge.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 本机问答页面的接口边界与安全渲染约束测试。 */
class ZhkuLocalPreviewPageResourceTest {

    @Test
    void shouldKeepPageBoundToExistingAnswerApiAndSafeTextRendering() throws Exception {
        String page = new String(
                new ClassPathResource("zhku/local-preview/index.html")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(page).contains("/api/zhku/local-preview/answers");
        assertThat(page).contains("id=\"question-input\"");
        assertThat(page).contains("id=\"citation-panel\"");
        assertThat(page).contains("GROUNDED");
        assertThat(page).contains("INSUFFICIENT_EVIDENCE");
        assertThat(page).contains("textContent");
        assertThat(page).doesNotContain("innerHTML");
    }
}
