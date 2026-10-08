package com.zhku.knowledge.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class LocalPreviewBindingGuardTest {
    @Test
    void literalValidationDoesNotNeedApplicationOrNetwork() {
        for (String address : new String[] { "127.0.0.1", "::1", "0:0:0:0:0:0:0:1" })
            assertThatCode(() -> new LocalPreviewBindingGuard(address)).doesNotThrowAnyException();
        for (String address : new String[] { null, "", "localhost", "0.0.0.0", "::", "192.168.1.10" })
            assertThatThrownBy(() -> new LocalPreviewBindingGuard(address))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApplicationContextRunner preview() {
        return new ApplicationContextRunner().withUserConfiguration(ZhkuLocalPreviewConfiguration.class)
                .withPropertyValues("spring.profiles.active=zhku-local-preview", "zhku.local-preview.enabled=true",
                        "ZHKU_DB_PASSWORD=test-only");
    }

    @Test
    void acceptsExplicitLoopbackLiterals() {
        for (String address : new String[] { "127.0.0.1", "::1", "0:0:0:0:0:0:0:1" }) {
            preview().withPropertyValues("server.address=" + address)
                    .run(context -> assertThat(context).hasNotFailed().hasBean("zhkuLocalPreviewBindingGuard"));
        }
    }

    @Test
    void rejectsWildcardLanHostnameAndMissingAddress() {
        for (String address : new String[] { "", "localhost", "0.0.0.0", "::", "192.168.1.8" }) {
            preview().withPropertyValues("server.address=" + address)
                    .run(context -> assertThat(context).hasFailed().getFailure()
                            .hasMessageContaining("server.address"));
        }
        preview().run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("server.address"));
    }

    @Test
    void effectiveExternalOverrideIsChecked() {
        preview().withPropertyValues("server.address=127.0.0.1")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("external-override", Map.of("server.address", "0.0.0.0"))))
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("server.address"));
    }

    @Test
    void disabledPreviewDoesNotApplyGuard() {
        new ApplicationContextRunner().withUserConfiguration(ZhkuLocalPreviewConfiguration.class)
                .withPropertyValues("server.address=0.0.0.0")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("zhkuLocalPreviewBindingGuard"));
    }
}
