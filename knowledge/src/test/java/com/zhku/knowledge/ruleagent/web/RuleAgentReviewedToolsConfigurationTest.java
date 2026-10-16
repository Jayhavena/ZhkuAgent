package com.zhku.knowledge.ruleagent.web;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import java.nio.file.*;
import java.time.Clock;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 显式外部只读资料检查，不安装数据库、不创建模型客户端。 */
class RuleAgentReviewedToolsConfigurationTest {
    @Test void offByDefaultAndMissingDependenciesFailClosed(){
        var runner=new ApplicationContextRunner().withUserConfiguration(RuleAgentReviewedToolsConfiguration.class);
        runner.run(c->assertThat(c).doesNotHaveBean(RuleAgentTaskToolsRegistry.ToolFactory.class));
        runner.withPropertyValues("zhku.rule-agent-http.enabled=true","zhku.rule-agent-http.reviewed-tools-enabled=true").run(c->assertThat(c).hasFailed());
    }
    @Test void assetsRequireExplicitAbsoluteRoot(){
        assertThatThrownBy(()->new RuleAgentReviewedToolsConfiguration.Assets(Path.of("."))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void externalQualificationAndConfiguredToolCreationDoNotConnect()throws Exception{
        String configured=System.getProperty("zhku.t06.reviewedRoot");Assumptions.assumeTrue(configured!=null,"仅显式只读外部资料路径才执行资格检查");
        Path root=Path.of(configured);var assets=new RuleAgentReviewedToolsConfiguration.Assets(root);assets.qualify();
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)){
            var db=mock(DriverManagerDataSource.class);when(db.getUrl()).thenReturn("jdbc:postgresql://127.0.0.1:55621/zhkuagent_rulecheck_e2e");
            new ApplicationContextRunner().withUserConfiguration(RuleAgentReviewedToolsConfiguration.class)
                .withBean(Clock.class,Clock::systemUTC).withBean(javax.sql.DataSource.class,()->db)
                .withBean(Reviewed2025ControlledCatalogue.class,()->catalogue).withBean(RuleAgentReviewedToolsConfiguration.Assets.class,()->assets)
                .withPropertyValues("zhku.rule-agent-http.enabled=true","zhku.rule-agent-http.reviewed-tools-enabled=true")
                .run(c->{assertThat(c).hasNotFailed();var tools=c.getBean(RuleAgentTaskToolsRegistry.ToolFactory.class).create(new com.zhku.knowledge.ruleagent.RuleAgentContract.Owner(java.util.UUID.randomUUID(),java.util.UUID.randomUUID()),java.util.UUID.randomUUID());assertThat(tools).isNotNull();assertThat(c.getBean(com.zhku.knowledge.rulecheck.RuleCheckOriginalEvidenceService.class)).isNotNull();});
            verify(db,never()).getConnection();
        }
    }
}