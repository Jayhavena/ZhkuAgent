package com.zhku.knowledge.ruleagent.web;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 不开端口、不创建库或Provider；页面预检参数不能扩大为收费入口。 */
class RuleAgentOnlinePagePreflightTest {
    @Test void onlyExplicitAssemblyAcceptsProxyPort() {
        assertThatCode(() -> RuleAgentOnlinePagePreflight.validate("assembly", 5174)).doesNotThrowAnyException();
    }
    @Test void livePrepareVerifyAndFixtureCannotUsePagePreflight() {
        for (String operation : List.of("live", "prepare", "verify", "page-fixture", "", "ASSEMBLY"))
            assertThatThrownBy(() -> RuleAgentOnlinePagePreflight.validate(operation, 5174)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidPortsAreRejectedWithoutResources() {
        for (int port : List.of(-1, 0, 80, 1023, 65536))
            assertThatThrownBy(() -> RuleAgentOnlinePagePreflight.validate("assembly", port)).isInstanceOf(IllegalArgumentException.class);
    }
}
