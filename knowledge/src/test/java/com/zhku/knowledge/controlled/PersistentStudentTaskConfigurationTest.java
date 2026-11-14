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
    @Test @org.junit.jupiter.api.Timeout(40)
    void onlineAssemblyWithSyntheticProcessEnvironmentCreatesOnlyOnlineFactory(@org.junit.jupiter.api.io.TempDir Path temporary) throws Exception {
        // 用隔离子进程测试真实System.getenv路径，避免修改父进程环境或碰到本机供应商密钥。
        var output=temporary.resolve("assembly.log");
        String executable=System.getProperty("os.name").startsWith("Windows")?"java.exe":"java";
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",executable).toString(),
            "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
            OnlineAssemblyChild.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile());
        var env=process.environment();
        env.keySet().removeIf(k->k.startsWith("ZHKU_")||k.equals("DASHSCOPE_API_KEY")||k.equals("POSTGRES_PASSWORD")
            ||k.equals("JAVA_TOOL_OPTIONS")||k.equals("JDK_JAVA_OPTIONS")||k.equals("_JAVA_OPTIONS"));
        env.put("ZHKU_RULE_AGENT_MODE","ONLINE");env.put("DASHSCOPE_API_KEY","SYNTHETIC_NO_NETWORK_KEY");
        env.put("ZHKU_RULE_AGENT_AUTHORIZATION_ID","ASSEMBLY_ONLY_NOT_AUTHORIZED");env.put("ZHKU_RULE_AGENT_CALL_ALLOWANCE","10");
        var child=process.start();
        try {assertThat(child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).withFailMessage("ONLINE assembly child failed: %s",java.nio.file.Files.readString(output)).isZero();
            assertThat(java.nio.file.Files.readString(output)).contains("ONLINE_ASSEMBLY_OK calls=0 tools=0");
        } finally {if(child.isAlive()){child.destroyForcibly();child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);}}
    }
    public static final class OnlineAssemblyChild {
        public static void main(String[] args) {
            new PersistentStudentTaskConfigurationTest().runner()
                .withUserConfiguration(com.zhku.knowledge.ruleagent.web.RuleAgentOnlineHttpConfiguration.class)
                .withPropertyValues("zhku.persistent-student.enabled=true","zhku.rule-agent-http.enabled=true",
                    "zhku.rule-agent-http.online-enabled=true","zhku.rule-agent-launch.enabled=true","zhku.rule-agent-launch.executor=ONLINE")
                .run(c->{assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(RuleAgentOnlineFactory.class);
                    assertThat(c).hasSingleBean(RuleAgentTaskToolsRegistry.class);assertThat(c).doesNotHaveBean(RuleAgentSimulatedDemoFactory.class);
                    var factory=c.getBean(RuleAgentOnlineFactory.class);assertThat(factory.remainingCalls()).isEqualTo(10);
                    assertThat(factory.resourcesClosed()).isTrue();
                    assertThat(c.getBean(PersistentStudentRuntimeController.class).runtime().getBody().mode()).isEqualTo("ONLINE");
                    var options=c.getBean(com.zhku.knowledge.ruleagent.web.RuleAgentOnlineHttpConfiguration.Options.class);
                    assertThat(options.settings().model()).isEqualTo(com.zhku.knowledge.ruleagent.RuleAgentProvider.MODEL);
                    assertThat(options.settings().authorizationId()).isEqualTo("ASSEMBLY_ONLY_NOT_AUTHORIZED");
                    assertThat(options.allowance()).isEqualTo(10);
                });
            System.out.println("ONLINE_ASSEMBLY_OK calls=0 tools=0");
        }
    }
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
    @Test void runtimeControllerConditionKeepsLegacyConfigurationOff() {
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
