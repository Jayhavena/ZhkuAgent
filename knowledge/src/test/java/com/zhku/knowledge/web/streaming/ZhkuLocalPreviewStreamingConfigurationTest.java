package com.zhku.knowledge.web.streaming;

import com.zhku.knowledge.web.ZhkuLocalPreviewConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

import static org.assertj.core.api.Assertions.assertThat;

/** SSE 必须是本机预览的显式可选能力，不能因普通应用启用而悄悄创建后台工作线程。 */
class ZhkuLocalPreviewStreamingConfigurationTest {

    @Test
    void shouldNotRegisterStreamerWhenStreamingSwitchIsOff() {
        new ApplicationContextRunner()
                .withInitializer(context -> TestPropertyValues.of(
                        "ZHKU_DB_PASSWORD=test-password"
                ).applyTo(context.getEnvironment()))
                .withPropertyValues(
                        "spring.profiles.active=zhku-local-preview",
                        "zhku.local-preview.enabled=true",
                        "zhku.streaming.enabled=false"
                )
                .withUserConfiguration(
                        ZhkuLocalPreviewConfiguration.class,
                        ZhkuLocalPreviewStreamingConfiguration.class
                )
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SseConversationStreamer.class));
    }

    @Test
    void shouldRegisterBoundedStreamerOnlyWhenAllPreviewSwitchesAreEnabled() {
        new ApplicationContextRunner()
                .withInitializer(context -> TestPropertyValues.of(
                        "ZHKU_DB_PASSWORD=test-password"
                ).applyTo(context.getEnvironment()))
                .withPropertyValues(
                        "spring.profiles.active=zhku-local-preview",
                        "zhku.local-preview.enabled=true",
                        "zhku.streaming.enabled=true",
                        "zhku.streaming.timeout-millis=30000",
                        "zhku.streaming.worker-threads=2",
                        "zhku.streaming.queue-capacity=16"
                )
                .withUserConfiguration(
                        ZhkuLocalPreviewConfiguration.class,
                        ZhkuLocalPreviewStreamingConfiguration.class
                )
                .run(context -> assertThat(context)
                        .hasSingleBean(SseConversationStreamer.class));
    }
}
