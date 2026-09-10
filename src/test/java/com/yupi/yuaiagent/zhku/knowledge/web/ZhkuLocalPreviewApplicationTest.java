package com.yupi.yuaiagent.zhku.knowledge.web;

import com.yupi.yuaiagent.agent.YuManusFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 独立本机预览启动入口的组件隔离测试。 */
class ZhkuLocalPreviewApplicationTest {

    @Test
    void shouldCreatePreviewContextWithoutGenericAgentOrChatModel() {
        new ApplicationContextRunner()
                .withUserConfiguration(ZhkuLocalPreviewApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(YuManusFactory.class);
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                });
    }
}
