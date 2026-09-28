package com.zhku.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 防止已下线的示例应用及语料重新混入 Agent 运行产物。 */
class LegacyDomainRemovalTest {

    @Test
    void retiredApplicationAndCorpusAreAbsentFromRuntimeClasspath() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThat(loader.getResource("com/zhku/agent/app/LoveApp.class")).isNull();
        assertThat(loader.getResource("document/恋爱常见问题和回答 - 单身篇.md")).isNull();
        assertThat(loader.getResource("document/恋爱常见问题和回答 - 恋爱篇.md")).isNull();
        assertThat(loader.getResource("document/恋爱常见问题和回答 - 已婚篇.md")).isNull();
    }
}
