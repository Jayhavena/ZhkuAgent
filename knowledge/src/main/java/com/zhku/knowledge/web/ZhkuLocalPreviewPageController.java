package com.zhku.knowledge.web;

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

/** 仲园 Agent 本机问答页面的受控访问入口。 */
@RestController
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-preview")
public class ZhkuLocalPreviewPageController {

    private static final MediaType HTML_UTF8 = new MediaType(
            MediaType.TEXT_HTML,
            StandardCharsets.UTF_8
    );

    @GetMapping
    public ResponseEntity<Resource> page() {
        // 显式控制页面路由，避免本机预览资源随通用应用入口意外公开。
        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                .body(new ClassPathResource("zhku/local-preview/index.html"));
    }
}
