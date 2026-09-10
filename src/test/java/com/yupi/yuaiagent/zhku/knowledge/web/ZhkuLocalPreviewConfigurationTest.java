package com.yupi.yuaiagent.zhku.knowledge.web;

import com.yupi.yuaiagent.zhku.knowledge.answer.GroundedAnswerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 本机预览配置的启用边界测试。 */
class ZhkuLocalPreviewConfigurationTest {

    @Test
    void shouldNotCreateKnowledgeAnswerServiceOutsideLocalPreviewProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewConfiguration.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(GroundedAnswerService.class));
    }

    @Test
    void shouldNotRegisterPreviewControllerOutsideLocalPreviewProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewController.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ZhkuLocalPreviewController.class));
    }
}
