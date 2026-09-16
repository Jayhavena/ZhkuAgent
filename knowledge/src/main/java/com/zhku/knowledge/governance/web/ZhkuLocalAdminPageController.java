package com.zhku.knowledge.governance.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** 仲园 Agent 本机资料治理页面的受控访问入口。 */
@RestController
@Profile("zhku-local-admin")
@ConditionalOnProperty(
        prefix = "zhku.local-admin",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-admin")
public class ZhkuLocalAdminPageController {

    private static final MediaType HTML_UTF8 = new MediaType(
            MediaType.TEXT_HTML,
            StandardCharsets.UTF_8
    );

    @GetMapping
    public ResponseEntity<Resource> page() {
        // 页面只随本机管理 Profile 注册，避免它成为普通应用的静态公网资源。
        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                .body(new ClassPathResource("zhku/local-admin/index.html"));
    }
}
