package com.zhku.knowledge.controlled;

import com.zhku.knowledge.ruleagent.RuleAgentOnlineFactory;
import com.zhku.knowledge.ruleagent.demo.RuleAgentSimulatedDemoFactory;
import com.zhku.knowledge.ruleagent.web.RuleAgentTaskToolsRegistry;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

/** 无数据库/模型；真实安全链与路由访问另由打包宿主HTTP验收。 */
class PersistentStudentTaskConfigurationTest {
    ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PersistentStudentTaskConfiguration.class)
            .withBean(Clock.class,Clock::systemUTC)
            .withBean(RuleAgentTaskToolsRegistry.ToolFactory.class,()->(owner,task)->{throw new AssertionError("不应打开工具");})
            .withPropertyValues("zhku.rule-check-preview.repository-root="+Path.of("..").toAbsolutePath().normalize());
    }
    @Test void disabledConfigurationDoesNotInstallFactoriesOrRuntimeEndpoint() {
        runner().run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(RuleAgentSimulatedDemoFactory.class);
            assertThat(c).doesNotHaveBean(PersistentStudentRuntimeController.class);});
    }
    @Test void runtimeControllerCannotPolluteLegacyApplicationScanning() {
        new ApplicationContextRunner().withUserConfiguration(PersistentStudentRuntimeController.class)
            .run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(PersistentStudentRuntimeController.class);});
    }
    @Test void explicitSimulationHasOneRuntimeControllerAndNoOnlineFactory() {
        runner().withPropertyValues("zhku.persistent-student.enabled=true","zhku.rule-agent-http.enabled=true",
            "zhku.rule-agent-launch.enabled=true","zhku.rule-agent-launch.executor=SIMULATED").run(c->{
            assertThat(c).hasNotFailed();assertThat(c.getBeansOfType(PersistentStudentRuntimeController.class)).hasSize(1);
            assertThat(c).hasSingleBean(RuleAgentSimulatedDemoFactory.class);assertThat(c).doesNotHaveBean(RuleAgentOnlineFactory.class);
            assertThat(c.getBean(PersistentStudentRuntimeController.class).runtime().getBody().mode()).isEqualTo("SIMULATED");
        });
    }
    @Test void onlineCannotFallBackToSimulationWhenProviderAssemblyIsAbsent() {
        runner().withPropertyValues("zhku.persistent-student.enabled=true","zhku.rule-agent-http.enabled=true",
            "zhku.rule-agent-launch.enabled=true","zhku.rule-agent-launch.executor=ONLINE").run(c->assertThat(c).hasFailed());
    }
}
