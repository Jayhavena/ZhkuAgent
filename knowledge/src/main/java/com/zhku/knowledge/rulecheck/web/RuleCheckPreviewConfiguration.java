package com.zhku.knowledge.rulecheck.web;

import org.springframework.core.env.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.zhku.knowledge.security.RuleCheckPreviewSecurityConfiguration;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.Reviewed2025ControlledCatalogue;
import com.zhku.knowledge.retrieval.Reviewed2025ReportCatalogue;
import org.springframework.beans.factory.ObjectProvider;
import com.zhku.knowledge.retrieval.EmbeddingClient;
import com.zhku.knowledge.retrieval.RetrieverUnavailableException;
import java.time.Clock;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** 默认关闭；已审核2025单主题只由专用回环临时库的显式受控入口装配。 */
@Configuration(proxyBeanMethods=false)
public class RuleCheckPreviewConfiguration {
    private static final String PREFIX="zhku.rule-check-preview.";
    private static final Pattern TARGET=Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$");
    @Bean static BeanFactoryPostProcessor ruleCheckEarlyGuard(Environment environment) {
        validateEnvironment(environment);return factory->{};
    }
    public static void validateEnvironment(Environment environment) {
        RuleCheckPreviewSecurityConfiguration.validateEnvironment(environment);
        var match=TARGET.matcher(environment.getProperty("zhku.db.url",""));
        // 暂只允许验收库；取得密码或打开initialize也不授权迁移原演示库。
        if(!match.matches())throw new IllegalArgumentException("查证启动仅允许显式专用回环临时数据库");
        int port=Integer.parseInt(match.group(1));
        if(port<1024||port>65535||port==54329)throw new IllegalArgumentException("查证数据库端口非法或指向原库");
        String username=environment.getProperty("zhku.db.username");
        if(username==null||!username.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")
                ||!username.equals(environment.getProperty("zhku.db.user",username)))
            throw new IllegalArgumentException("查证数据库用户名必须显式且一致");
        for(String key:new String[]{"spring.datasource.url","spring.datasource.username","spring.datasource.password",
                "spring.flyway.url","spring.flyway.user","spring.flyway.password"})
            if(environment.getProperty(key)!=null)throw new IllegalArgumentException("查证数据库不接受其他目标或凭据装配");
        if(environment instanceof ConfigurableEnvironment configured)
            for(var source:configured.getPropertySources())
                if(source instanceof EnumerablePropertySource<?> enumerable && !(source instanceof SystemEnvironmentPropertySource))
                    for(String key:enumerable.getPropertyNames())
                        if(key.equals("zhku.db.password")||key.equals("ZHKU_DB_PASSWORD"))
                            throw new IllegalArgumentException("查证数据库密码仅来自进程环境");
        if(System.getProperty("ZHKU_DB_PASSWORD")!=null)throw new IllegalArgumentException("查证数据库密码仅来自进程环境");
        if(!"true".equals(environment.getProperty(PREFIX+"initialize"))
                ||!"false".equals(environment.getProperty("spring.flyway.enabled"))
                ||!"false".equals(environment.getProperty("spring.flyway.out-of-order")))
            throw new IllegalArgumentException("查证迁移要求显式同意且禁止自动与乱序迁移");
        if(!"CLOSED".equals(environment.getProperty(PREFIX+"catalogue-mode"))&&!controlled(environment))
            throw new IllegalArgumentException("真实资料未通过时查证目录必须关闭");
        validatePersistentIdentity(environment,port);
        runtimeSettings(environment);
        if(controlled(environment)) {
            if(!java.util.Set.of("SEALED_REPLAY","TASK_PLAN_REPLAY").contains(environment.getProperty(PREFIX+"selector-mode",""))
                    ||!"controlled-2025-local".equals(environment.getProperty(PREFIX+"profile-id"))
                    ||!"handbook-2025-clause-verbatim-v1".equals(environment.getProperty(PREFIX+"chunking-version"))
                    ||!"RULE_CHECK_PUBLIC_ELECTIVE_2025".equals(environment.getProperty(PREFIX+"required-scope"))
                    ||!"20".equals(environment.getProperty(PREFIX+"candidate-limit"))
                    ||!"5".equals(environment.getProperty(PREFIX+"result-limit")))
                throw new IllegalArgumentException("受控2025组合必须完整且显式，当前只允许封存回放");
            try {
                var root=java.nio.file.Path.of(environment.getProperty(PREFIX+"repository-root","")).normalize();
                if(!root.isAbsolute()||!java.nio.file.Files.isDirectory(root.resolve("knowledge/src")))throw new IllegalArgumentException();
                // 资料和回放资格在数据库连接/迁移/监听前检查；目录开关不能代替原件及直接审核记录。
                if(taskReplay(environment)) {
                    new Reviewed2025ReportCatalogue(root).close();
                    try(var replay=new SealedRuleTaskReplay(root,environment.getProperty(PREFIX+"task-batch-directory",SealedRuleTaskReplay.BASE),environment.getProperty(PREFIX+"task-package-sha256"),environment.getProperty(PREFIX+"task-summary-sha256"))){}
                } else {
                    new Reviewed2025ControlledCatalogue(root).close();
                    try(var replay=new SealedRuleSelectionReplay(root)){}
                }
            }catch(Exception invalid){throw new IllegalArgumentException("受控资料、回放或根目录不合格");}
        }
    }
    private static void validatePersistentIdentity(Environment environment,int rulePort) {
        String enabled=environment.getProperty(PREFIX+"persistent-identity.enabled","false");
        if(!"true".equals(enabled)&&!"false".equals(enabled))
            throw new IllegalArgumentException("持久身份开关必须为true或false");
        if(!"true".equals(enabled))return;
        // 限定唯一库名、回环地址与独立端口；禁止URL参数、别名和指向演示实例。
        var target=Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_identity$")
                .matcher(environment.getProperty(PREFIX+"persistent-identity.url",""));
        if(!target.matches())throw new IllegalArgumentException("持久身份库必须是明确命名的专用回环数据库");
        int port=Integer.parseInt(target.group(1));
        if(port<1024||port>65535||port==54329||port==rulePort)
            throw new IllegalArgumentException("持久身份库必须使用独立且非演示库端口");
        String username=System.getenv("ZHKU_RULECHECK_IDENTITY_DB_USER");
        String password=System.getenv("ZHKU_RULECHECK_IDENTITY_DB_PASSWORD");
        if(username==null||!username.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")||password==null||password.isBlank())
            throw new IllegalArgumentException("持久身份库要求显式进程环境凭据");
    }
    static boolean controlled(Environment e){return "REVIEWED_2025_CONTROLLED".equals(e.getProperty(PREFIX+"catalogue-mode"));}
    static boolean taskReplay(Environment e){return controlled(e)&&"TASK_PLAN_REPLAY".equals(e.getProperty(PREFIX+"selector-mode"));}
    static RuleCheckRuntimeSettings runtimeSettings(Environment environment) {
        try {
            String[] keys={"profile-id","chunking-version","required-scope","reference-revision"};
            var values=new String[4];
            for(int i=0;i<keys.length;i++) {
                values[i]=environment.getProperty(PREFIX+keys[i]);
                if(values[i]==null||values[i].isBlank()||values[i].length()>128)throw new IllegalArgumentException();
            }
            if(!"2025.9.2".equals(values[3]))throw new IllegalArgumentException();
            return new RuleCheckRuntimeSettings(values[0],values[1],values[2],values[3],
                Integer.parseInt(environment.getProperty(PREFIX+"candidate-limit","")),
                Integer.parseInt(environment.getProperty(PREFIX+"result-limit","")));
        } catch(RuntimeException invalid) {throw new IllegalArgumentException("查证运行边界与预算必须显式且合格");}
    }
    @Bean public DataSource ruleCheckDataSource(Environment environment) {
        validateEnvironment(environment);
        String password=System.getenv("ZHKU_DB_PASSWORD");
        if(password==null||password.isBlank())throw new IllegalArgumentException("缺少数据库环境凭据");
        var source=new DriverManagerDataSource(environment.getProperty("zhku.db.url"),environment.getProperty("zhku.db.username"),password);
        var limits=new Properties();limits.setProperty("connectTimeout","1");limits.setProperty("socketTimeout","2");limits.setProperty("cancelSignalTimeout","1");
        source.setConnectionProperties(limits);return source;
    }
    @Bean(initMethod="migrate") public Flyway ruleCheckMigration(DataSource ruleCheckDataSource) {
        return Flyway.configure().dataSource(ruleCheckDataSource).locations("classpath:db/migration").outOfOrder(false).load();
    }
    @Bean(destroyMethod="close") public PreviewIdentityRepository ruleCheckIdentityRepository(DataSource ruleCheckDataSource,Flyway ruleCheckMigration,Environment environment) {
        if(!"true".equals(environment.getProperty(PREFIX+"persistent-identity.enabled","false")))
            return new PreviewIdentityRepository(ruleCheckDataSource);
        validateEnvironment(environment);
        // 身份源和Flyway不注册为Bean，防止规则库、资料安装或其他类型注入误选持久库。
        var identitySource=new DriverManagerDataSource(environment.getProperty(PREFIX+"persistent-identity.url"),
                System.getenv("ZHKU_RULECHECK_IDENTITY_DB_USER"),System.getenv("ZHKU_RULECHECK_IDENTITY_DB_PASSWORD"));
        var limits=new Properties();limits.setProperty("connectTimeout","1");limits.setProperty("socketTimeout","2");limits.setProperty("cancelSignalTimeout","1");
        identitySource.setConnectionProperties(limits);
        // 独立位置只包含V19身份约束和V23登录表，绝不运行规则资料迁移或清库。
        Flyway.configure().dataSource(identitySource).locations("classpath:db/rulecheck-identity-migration").outOfOrder(false).load().migrate();
        return new PreviewIdentityRepository(identitySource);
    }
    @Bean public Clock ruleCheckClock() {return Clock.systemUTC();}
    @Bean public RuleCheckClarificationStore ruleCheckClarificationStore(Clock ruleCheckClock) {return new RuleCheckClarificationStore(ruleCheckClock);}
    @Bean public RuleCheckCitationStore ruleCheckCitationStore(Clock ruleCheckClock) {return new RuleCheckCitationStore(ruleCheckClock);}
    @Bean public RuleCheckOriginalEvidenceService ruleCheckOriginalEvidenceService(DataSource ruleCheckDataSource,
            Flyway ruleCheckMigration,RuleCheckRuntimeSettings settings) {
        var reader=new JdbcRuleCheckEvidenceReader(ruleCheckDataSource,settings.profileId(),settings.chunkingVersion(),settings.requiredScope());
        // 未审核官网host前只提供库内定位，不把资料字段自动升级为安全原件链接。
        return new RuleCheckOriginalEvidenceService(reader::readLocated,settings.referenceRevision(),java.util.Set.of());
    }
    @Bean(destroyMethod="close") public Reviewed2025ControlledCatalogue reviewed2025Catalogue(Environment environment,
            DataSource ruleCheckDataSource,Flyway ruleCheckMigration)throws Exception {
        if(!controlled(environment))return null;
        var root=java.nio.file.Path.of(environment.getProperty(PREFIX+"repository-root"));
        Reviewed2025ControlledCatalogue catalogue=taskReplay(environment)?new Reviewed2025ReportCatalogue(root):new Reviewed2025ControlledCatalogue(root);
        catalogue.installOnIsolatedDatabase(ruleCheckDataSource);return catalogue;
    }
    @Bean(destroyMethod="close") public SealedRuleSelectionReplay ruleCheckSelectionReplay(Environment environment)throws Exception {
        return controlled(environment)&&!taskReplay(environment)?new SealedRuleSelectionReplay(java.nio.file.Path.of(environment.getProperty(PREFIX+"repository-root"))):null;
    }
    @Bean(destroyMethod="close") public SealedRuleTaskReplay ruleCheckTaskReplay(Environment environment)throws Exception {
        return taskReplay(environment)?new SealedRuleTaskReplay(java.nio.file.Path.of(environment.getProperty(PREFIX+"repository-root")),
            environment.getProperty(PREFIX+"task-batch-directory",SealedRuleTaskReplay.BASE),
            environment.getProperty(PREFIX+"task-package-sha256"),environment.getProperty(PREFIX+"task-summary-sha256")):null;
    }
    @Bean public RuleCheckRuntimeSettings ruleCheckRuntimeSettings(Environment environment,ObjectProvider<Reviewed2025ControlledCatalogue> catalogue) {
        var actual=catalogue.getIfAvailable();return actual==null?runtimeSettings(environment):actual.settings();
    }
    @Bean public RuleCheckApplicationService ruleCheckApplicationService(DataSource ruleCheckDataSource,Flyway ruleCheckMigration,
            RuleCheckRuntimeSettings settings,Clock ruleCheckClock,Environment environment,
            ObjectProvider<Reviewed2025ControlledCatalogue> catalogue,ObjectProvider<SealedRuleSelectionReplay> replay,
            ObjectProvider<SealedRuleTaskReplay> taskReplay)throws Exception {
        if(controlled(environment)) {
            var actual=catalogue.getObject();var entries=actual.reviewedCatalogue(java.nio.file.Path.of(environment.getProperty(PREFIX+"repository-root")));
            if(taskReplay(environment))return RuleCheckRuntimeFactory.createEvidenceFirst2025(ruleCheckDataSource,settings,()->{
                // 审核资格不是启动时的永久许可；每次选择、发布和续答都重新核对直接审核记录。
                try{return actual.reviewedCatalogue(java.nio.file.Path.of(environment.getProperty(PREFIX+"repository-root")));}
                catch(Exception revoked){throw new IllegalStateException("审核目录资格已变化");}
            },taskReplay.getObject(),()->java.util.UUID.randomUUID().toString(),ruleCheckClock);
            return RuleCheckRuntimeFactory.createControlled2025(ruleCheckDataSource,settings,()->entries,replay.getObject(),()->java.util.UUID.randomUUID().toString(),ruleCheckClock);
        }
        // 关闭目录在选择/检索之前返回缺证；不能用合成模板或一般RAG偷接真实学生入口。
        return RuleCheckRuntimeFactory.create(ruleCheckDataSource,settings,List::of,
            (question,options)->{throw new IllegalStateException("规则选择尚未启用");},
            new EmbeddingClient() {
                public float[] embed(String text) {throw new RetrieverUnavailableException("向量外发尚未启用");}
                public int dimensions() {return 1024;}
            },()->java.util.UUID.randomUUID().toString(),ruleCheckClock);
    }
    @Bean public Jackson2ObjectMapperBuilderCustomizer ruleCheckStrictRequests() {
        // 重复键会覆盖先前的条件，尾随JSON会制造两个解读；都不能默默接受。
        return builder->builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS,com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
}
