package com.zhku.knowledge;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;

/** 不建连接；非法目标只测校验/代表性静态字段，绝不调用旧setup/cleanup或迁移。 */
class ZhkuDatabaseTestTargetTest {
    static final String SAFE="jdbc:postgresql://127.0.0.1:55467/zhkuagent_rulecheck_e2e";
    @Test void missingAndBlankTargetsFailClosed() {for(String url:new String[]{null,""," "})reject(url);}
    @Test void originalDatabaseAndReservedPortAreRejected() {reject("jdbc:postgresql://127.0.0.1:54329/zhkuagent");reject(SAFE.replace("55467","54329"));}
    @Test void onlyExplicitExactLoopbackHostIsAllowed() {for(String host:new String[]{"localhost","remote.invalid","[::1]","127.0.0.1.evil"})reject(SAFE.replace("127.0.0.1",host));}
    @Test void onlyExplicitExistingDisposableDatabaseNamesAreAllowed() {for(String name:new String[]{"zhkuagent","postgres","zhkuagent_e2e_backup","zhkuagent_rulecheck_e2e_backup","zhkuagent_run_archive_e2e_backup"})reject(SAFE.replace("zhkuagent_rulecheck_e2e",name));}
    @Test void queryCredentialsFragmentAndTrailingContentCannotExtendTarget() {
        for(String suffix:new String[]{"?options=x","?password=synthetic","#fragment","/"," ",";other"})reject(SAFE+suffix);
        reject(SAFE.replace("//127.0.0.1","//synthetic@127.0.0.1"));
    }
    @Test void invalidPortsAreRejected() {for(String port:new String[]{"0","1023","65536","-1","123456"})reject(SAFE.replace("55467",port));}
    @Test void knownDisposableTargetsPassWithoutConnection() {
        assertThatNoException().isThrownBy(()->ZhkuDatabaseTestConfig.verifyTarget(SAFE));
        assertThatNoException().isThrownBy(()->ZhkuDatabaseTestConfig.verifyTarget(SAFE.replace("zhkuagent_rulecheck_e2e","zhkuagent_run_archive_e2e")));
    }
    @Test void existingLiveFixtureTargetRemainsCompatibleWithoutCallingProvider() {
        assertThatNoException().isThrownBy(()->ZhkuDatabaseTestConfig.verifyTarget(SAFE.replace("zhkuagent_rulecheck_e2e","zhkuagent_e2e")));
    }
    @Test void isolatedProductionProbeRejectsInheritedOriginalOrOtherDatabaseBeforeStartup() {
        for(String url:new String[]{null,"jdbc:postgresql://127.0.0.1:54329/zhkuagent",SAFE.replace("zhkuagent_rulecheck_e2e","zhkuagent"),SAFE,SAFE.replace("127.0.0.1","localhost"),SAFE.replace("zhkuagent_rulecheck_e2e","zhkuagent_run_archive_e2e")+"?options=x"})
            assertThatThrownBy(()->com.zhku.knowledge.controlled.SubmissionProductionStartupProbe.validateDatabaseTarget(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void isolatedProductionProbeAcceptsOnlyItsExactArchiveFixtureWithoutStartup() {
        assertThatNoException().isThrownBy(()->com.zhku.knowledge.controlled.SubmissionProductionStartupProbe.validateDatabaseTarget(SAFE.replace("zhkuagent_rulecheck_e2e","zhkuagent_run_archive_e2e")));
    }
    @Test void urlGetterRequiresSystemPropertyInsteadOfDefaultOrEnvironment() {
        withUrl(null,()->assertThatThrownBy(ZhkuDatabaseTestConfig::databaseUrl).isInstanceOf(IllegalArgumentException.class));
        withUrl("jdbc:postgresql://127.0.0.1:54329/zhkuagent",()->assertThatThrownBy(ZhkuDatabaseTestConfig::databaseUrl).isInstanceOf(IllegalArgumentException.class));
        withUrl(SAFE,()->assertThat(ZhkuDatabaseTestConfig.databaseUrl()).isEqualTo(SAFE));
    }
    static Stream<String> fixtures(){return Stream.of("com.zhku.knowledge.KnowledgeSchemaTest","com.zhku.knowledge.feedback.JdbcFeedbackRepositoryTest","com.zhku.knowledge.ZhkuFlywayTestSupportTest");}
    @ParameterizedTest @MethodSource("fixtures") void representativeLegacyStaticFieldsRejectOriginalTargetBeforeLifecycle(String fixture) {
        withUrl("jdbc:postgresql://127.0.0.1:54329/zhkuagent",()-> {
            var loader=new FixtureLoader(getClass().getClassLoader(),fixture);
            assertThatThrownBy(()->Class.forName(fixture,true,loader)).isInstanceOf(ExceptionInInitializerError.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        });
    }
    static void reject(String url){assertThatThrownBy(()->ZhkuDatabaseTestConfig.verifyTarget(url)).isInstanceOf(IllegalArgumentException.class);}
    static void withUrl(String value,Runnable assertion){String previous=System.getProperty("zhku.db.url");try{if(value==null)System.clearProperty("zhku.db.url");else System.setProperty("zhku.db.url",value);assertion.run();}finally{if(previous==null)System.clearProperty("zhku.db.url");else System.setProperty("zhku.db.url",previous);}}
    static final class FixtureLoader extends ClassLoader {
        private final String fixture;
        FixtureLoader(ClassLoader parent,String fixture){super(parent);this.fixture=fixture;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.equals(fixture))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)) {
                Class<?> type=findLoadedClass(name);
                if(type==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                    if(input==null)throw new ClassNotFoundException(name);byte[] bytes=input.readAllBytes();type=defineClass(name,bytes,0,bytes.length);
                } catch(java.io.IOException missing){throw new ClassNotFoundException(name,missing);}
                if(resolve)resolveClass(type);return type;
            }
        }
    }
}
