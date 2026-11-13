package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.security.RuleCheckPreviewSecurityConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class RuleCheckPreviewConfigurationTest {
    @Test @SuppressWarnings("unchecked") void productionTaskAssemblyReloadsReviewQualificationPerRequest()throws Exception {
        var actual=org.mockito.Mockito.mock(com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue.class);
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();
        org.mockito.Mockito.when(actual.reviewedCatalogue(root)).thenReturn(java.util.List.of()).thenThrow(new IllegalStateException("审核已撤销"));
        org.springframework.beans.factory.ObjectProvider<com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue> catalogue=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(catalogue.getObject()).thenReturn(actual);
        org.springframework.beans.factory.ObjectProvider<com.zhku.knowledge.rulecheck.SealedRuleSelectionReplay> oldReplay=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<com.zhku.knowledge.rulecheck.SealedRuleTaskReplay> newReplay=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        var planner=org.mockito.Mockito.mock(com.zhku.knowledge.rulecheck.SealedRuleTaskReplay.class);org.mockito.Mockito.when(newReplay.getObject()).thenReturn(planner);
        var ds=org.mockito.Mockito.mock(javax.sql.DataSource.class);
        var environment=valid().withProperty("zhku.rule-check-preview.catalogue-mode","REVIEWED_2025_CONTROLLED")
            .withProperty("zhku.rule-check-preview.selector-mode","TASK_PLAN_REPLAY").withProperty("zhku.rule-check-preview.repository-root",root.toString());
        var app=new RuleCheckPreviewConfiguration().ruleCheckApplicationService(ds,null,new com.zhku.knowledge.rulecheck.RuleCheckRuntimeSettings("p","cut","scope","2025.9.2",20,5),
            java.time.Clock.systemUTC(),environment,catalogue,oldReplay,newReplay);
        assertThat(app.check("合成问题",java.util.Map.of()).status()).isEqualTo(com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE);
        org.mockito.Mockito.verify(actual,org.mockito.Mockito.times(2)).reviewedCatalogue(root);
        org.mockito.Mockito.verifyNoInteractions(ds,planner);
    }
    @Test void newTaskModeCannotStartWithoutActualPinnedBatch() {
        var e=valid().withProperty("zhku.rule-check-preview.catalogue-mode","REVIEWED_2025_CONTROLLED")
            .withProperty("zhku.rule-check-preview.repository-root",java.nio.file.Path.of("..").toAbsolutePath().normalize().toString())
            .withProperty("zhku.rule-check-preview.selector-mode","TASK_PLAN_REPLAY")
            .withProperty("zhku.rule-check-preview.profile-id","controlled-2025-local")
            .withProperty("zhku.rule-check-preview.chunking-version","handbook-2025-clause-verbatim-v1")
            .withProperty("zhku.rule-check-preview.required-scope","RULE_CHECK_PUBLIC_ELECTIVE_2025")
            .withProperty("zhku.rule-check-preview.candidate-limit","20");
        assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
        e.withProperty("zhku.rule-check-preview.task-package-sha256","a".repeat(64)).withProperty("zhku.rule-check-preview.task-summary-sha256","b".repeat(64));
        assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
    }
    static MockEnvironment valid() {
        var e=new MockEnvironment();e.setActiveProfiles("zhku-rule-check-preview");
        Map.ofEntries(
            Map.entry("zhku.rule-check-preview.enabled","true"),Map.entry("server.address","127.0.0.1"),
            Map.entry("server.servlet.context-path","/api"),Map.entry("server.servlet.session.timeout","15m"),
            Map.entry("server.servlet.session.persistent","false"),Map.entry("server.servlet.session.tracking-modes","cookie"),
            Map.entry("server.servlet.session.cookie.name","ZHKU_RULE_CHECK_SESSION"),
            Map.entry("server.servlet.session.cookie.path","/api/zhku/controlled-preview/v2"),
            Map.entry("server.servlet.session.cookie.http-only","true"),Map.entry("server.servlet.session.cookie.secure","false"),
            Map.entry("server.servlet.session.cookie.same-site","strict"),Map.entry("spring.flyway.enabled","false"),
            Map.entry("spring.flyway.out-of-order","false"),Map.entry("zhku.db.url","jdbc:postgresql://127.0.0.1:55462/zhkuagent_rulecheck_e2e"),
            Map.entry("zhku.db.username","zhkuagent"),Map.entry("zhku.rule-check-preview.initialize","true"),
            Map.entry("zhku.rule-check-preview.catalogue-mode","CLOSED"),Map.entry("zhku.rule-check-preview.profile-id","synthetic-http"),
            Map.entry("zhku.rule-check-preview.chunking-version","synthetic-v1"),Map.entry("zhku.rule-check-preview.required-scope","PUBLIC_ELECTIVE_CREDIT_LIMIT"),
            Map.entry("zhku.rule-check-preview.reference-revision","2025.9.2"),Map.entry("zhku.rule-check-preview.candidate-limit","40"),
            Map.entry("zhku.rule-check-preview.result-limit","5")
        ).forEach(e::setProperty);return e;
    }
    @Test void reviewedControlledModeRequiresExactBoundaryAndExplicitTransport() {
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();
        var e=valid().withProperty("zhku.rule-check-preview.catalogue-mode","REVIEWED_2025_CONTROLLED")
            .withProperty("zhku.rule-check-preview.repository-root",root.toString())
            .withProperty("zhku.rule-check-preview.selector-mode","SEALED_REPLAY")
            .withProperty("zhku.rule-check-preview.profile-id","controlled-2025-local")
            .withProperty("zhku.rule-check-preview.chunking-version","handbook-2025-clause-verbatim-v1")
            .withProperty("zhku.rule-check-preview.required-scope","RULE_CHECK_PUBLIC_ELECTIVE_2025")
            .withProperty("zhku.rule-check-preview.candidate-limit","20");
        // 参数边界先于资料读取校验，不依赖外部原件是否存在。
        for(String key:java.util.List.of("selector-mode","chunking-version","required-scope","candidate-limit","repository-root")) {
            String before=e.getProperty("zhku.rule-check-preview."+key);
            e.setProperty("zhku.rule-check-preview."+key,"wrong");
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
            e.setProperty("zhku.rule-check-preview."+key,before);
        }
        // 2026-10-27 工作区合并后学生手册原件不再随仓库存放；缺原件时如实跳过完整装配，
        // 不能把“原件不在本机”当成配置通过的证据。
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.isRegularFile(com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue.handbookPdf(root)),
                "本机缺少已审2025学生手册原件，受控完整装配未验证");
        assertThatCode(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).doesNotThrowAnyException();
    }
    @Test void explicitValidBoundaryDoesNotNeedCredentialsOrOpenResources() {
        assertThatCode(()->RuleCheckPreviewConfiguration.validateEnvironment(valid())).doesNotThrowAnyException();
    }
    @Test void validatesExistingSecurityGuardInsteadOfSilentlyBypassingIt() {
        for(String key:new String[]{"zhku.rule-check-preview.enabled","server.servlet.session.cookie.path","server.address"}) {
            var e=valid().withProperty(key,"wrong");
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
        }
        var e=valid();e.setActiveProfiles("zhku-rule-check-preview","zhku-controlled-preview");
        assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void onlyDedicatedTemporaryTargetIsAllowedBeforeAnyConnection() {
        for(String url:new String[]{"jdbc:postgresql://127.0.0.1:54329/zhkuagent_rulecheck_e2e",
                "jdbc:postgresql://127.0.0.1:55462/zhkuagent","jdbc:postgresql://localhost:55462/zhkuagent_rulecheck_e2e",
                "jdbc:postgresql://127.0.0.1:55462/zhkuagent_rulecheck_e2e?password=do-not-echo",
                "jdbc:postgresql://do-not-echo@127.0.0.1:55462/zhkuagent_rulecheck_e2e",
                "jdbc:postgresql://127.0.0.1:99999/zhkuagent_rulecheck_e2e","bad-do-not-echo"}) {
            var e=valid().withProperty("zhku.db.url",url);
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("do-not-echo").hasCause(null);
        }
    }
    @Test void databaseUsernameAndAliasMustBeExplicitConsistent() {
        for(String user:new String[]{"","-bad","x".repeat(64)}) {
            var e=valid().withProperty("zhku.db.username",user);
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
        }
        var e=valid().withProperty("zhku.db.user","other");
        assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void passwordsAndAlternateDatasourceOrFlywayTargetsCannotBeConfigured() {
        for(String key:new String[]{"zhku.db.password","ZHKU_DB_PASSWORD","spring.datasource.url","spring.datasource.username",
                "spring.datasource.password","spring.flyway.url","spring.flyway.user","spring.flyway.password"}) {
            var e=valid().withProperty(key,"do-not-echo");
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("do-not-echo").hasCause(null);
        }
    }
    @Test void migrationNeedsExplicitConsentAndAutomaticMigrationMustRemainOff() {
        for(String key:new String[]{"zhku.rule-check-preview.initialize","spring.flyway.enabled","spring.flyway.out-of-order"}) {
            var e=valid().withProperty(key,key.endsWith("initialize")?"false":"true");
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void closedCatalogueCannotBeReplacedWithFakeApprovalOrLiveTransport() {
        for(String mode:new String[]{"","APPROVED","LIVE","DRAFT"}) {
            var e=valid().withProperty("zhku.rule-check-preview.catalogue-mode",mode);
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void runtimeBoundariesAndBudgetsAreExplicitAndBounded() {
        for(String key:new String[]{"profile-id","chunking-version","required-scope","reference-revision","candidate-limit","result-limit"}) {
            var e=valid().withProperty("zhku.rule-check-preview."+key,"");
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
        }
        for(String budget:new String[]{"201","4","0","not-a-number"}) {
            var e=valid().withProperty("zhku.rule-check-preview.candidate-limit",budget);
            assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void startupApplicationUsesExplicitImportsWithoutComponentScanning() {
        assertThat(RuleCheckPreviewApplication.class.getAnnotation(SpringBootConfiguration.class)).isNotNull();
        assertThat(RuleCheckPreviewApplication.class.getAnnotation(ComponentScan.class)).isNull();
        var imports=RuleCheckPreviewApplication.class.getAnnotation(Import.class);assertThat(imports).isNotNull();
        assertThat(imports.value()).containsExactlyInAnyOrder(RuleCheckPreviewConfiguration.class,
                RuleCheckPreviewSecurityConfiguration.class,RuleCheckAuthController.class,RuleCheckController.class,
                com.zhku.knowledge.ruleagent.web.RuleAgentHttpConfiguration.class,
                com.zhku.knowledge.controlled.PersistentStudentTaskConfiguration.class);
    }
    @Test void profileYamlKeepsEntryAndInitializationClosedByDefault() throws Exception {
        var resource=new ClassPathResource("application-zhku-rule-check-preview.yml");assertThat(resource.exists()).isTrue();
        var sources=new YamlPropertySourceLoader().load("rulecheck-default",resource);
        var e=new MockEnvironment();e.setActiveProfiles("zhku-rule-check-preview");
        sources.forEach(s->e.getPropertySources().addLast(s));
        assertThat(e.getProperty("zhku.rule-check-preview.enabled")).isEqualTo("false");
        assertThat(e.getProperty("zhku.rule-check-preview.initialize")).isEqualTo("false");
        assertThat(e.getProperty("server.servlet.session.cookie.path")).isEqualTo("/api/zhku/controlled-preview/v2");
        assertThatThrownBy(()->RuleCheckPreviewConfiguration.validateEnvironment(e)).isInstanceOf(IllegalArgumentException.class);
    }
}
