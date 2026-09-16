package com.zhku.knowledge.web;

import com.zhku.knowledge.answer.GroundedAnswerService;
import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.publicelective.CreditLimitEvidenceFinder;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.notice.CourseSelectionNoticeService;
import com.zhku.knowledge.notice.NoticeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;

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

    @Test
    void shouldNotRegisterConversationControllerOutsideLocalPreviewProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewConversationController.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ZhkuLocalPreviewConversationController.class));
    }

    @Test
    void shouldNotRegisterPreviewPageControllerOutsideLocalPreviewProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewPageController.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ZhkuLocalPreviewPageController.class));
    }

    @Test
    void shouldRegisterConversationComponentsOnlyForEnabledLocalPreview() {
        // 配置工厂要求密码是生产安全边界；测试仅注入占位值，绝不连接真实数据库。
        TestPropertyValues.of("zhku.db.password=test-password")
                .applyToSystemProperties(() -> new ApplicationContextRunner()
                        .withPropertyValues(
                                "spring.profiles.active=zhku-local-preview",
                                "zhku.local-preview.enabled=true"
                        )
                        .withUserConfiguration(
                                ZhkuLocalPreviewConfiguration.class,
                                ZhkuLocalPreviewConversationController.class
                        )
                        .run(context -> assertThat(context)
                                .hasSingleBean(ConversationStore.class)
                                .hasSingleBean(CreditLimitEvidenceFinder.class)
                                .hasSingleBean(PublicElectiveCreditLimitService.class)
                                .hasSingleBean(PublicElectiveConversationRouter.class)
                                .hasSingleBean(LocalPreviewConversationFacade.class)
                                .hasSingleBean(ZhkuLocalPreviewConversationController.class)));
    }

    @Test
    void shouldEnableCurrentNoticeServiceOnlyWithExplicitTermAndOfficialChannel() {
        TestPropertyValues.of("zhku.db.password=test-password")
                .applyToSystemProperties(() -> new ApplicationContextRunner()
                        .withPropertyValues(
                                "spring.profiles.active=zhku-local-preview",
                                "zhku.local-preview.enabled=true",
                                "zhku.local-preview.current-academic-term=2026-2027-1",
                                "zhku.local-preview.course-selection-official-query-url=https://jwc.zhku.edu.cn"
                        )
                        .withUserConfiguration(ZhkuLocalPreviewConfiguration.class)
                        .run(context -> assertThat(context)
                                .hasSingleBean(NoticeRepository.class)
                                .hasSingleBean(CourseSelectionNoticeService.class)));
    }
}
