package com.zhku.knowledge.governance.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 本机资料治理页面的接口边界与安全渲染约束测试。 */
class ZhkuLocalAdminPageResourceTest {

    @Test
    void shouldKeepPageBoundToLocalAdminApiAndSafeTextRendering() throws Exception {
        String page = new String(
                new ClassPathResource("zhku/local-admin/index.html")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(page).contains("/api/zhku/local-admin/submissions");
        assertThat(page).contains("仅本机管理员可操作");
        assertThat(page).contains("未审核资料不会进入问答");
        assertThat(page).contains("资料发布流程");
        assertThat(page).contains("发布前请确认");
        assertThat(page).contains("http://127.0.0.1:8123/api/zhku/local-preview");
        assertThat(page).contains("textContent");
        assertThat(page).doesNotContain("innerHTML");
    }
}
