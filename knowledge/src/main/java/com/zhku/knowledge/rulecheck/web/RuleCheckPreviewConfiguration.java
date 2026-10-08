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
import com.zhku.knowledge.retrieval.EmbeddingClient;
import com.zhku.knowledge.retrieval.RetrieverUnavailableException;
import java.time.Clock;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** 当前仅装配关闭目录与身份验收；不提供真实资料或供应商激活开关。 */
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
        if(!"CLOSED".equals(environment.getProperty(PREFIX+"catalogue-mode")))
            throw new IllegalArgumentException("真实资料未通过时查证目录必须关闭");
        runtimeSettings(environment);
    }
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
    @Bean(destroyMethod="close") public PreviewIdentityRepository ruleCheckIdentityRepository(DataSource ruleCheckDataSource,Flyway ruleCheckMigration) {
        return new PreviewIdentityRepository(ruleCheckDataSource);
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
    @Bean public RuleCheckRuntimeSettings ruleCheckRuntimeSettings(Environment environment) {return runtimeSettings(environment);}
    @Bean public RuleCheckApplicationService ruleCheckApplicationService(DataSource ruleCheckDataSource,Flyway ruleCheckMigration,
            RuleCheckRuntimeSettings settings,Clock ruleCheckClock) {
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
