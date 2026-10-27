package com.zhku.knowledge.ruleagent.web;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import static org.assertj.core.api.Assertions.*;
import com.zhku.knowledge.ruleagent.RuleAgentTaskStore;

/** 仅显式属性开启浏览器租约；模拟主体/领域结果复用HTTP夹具，无数据库/模型端口。 */
@Timeout(600)
class RuleAgentTaskBrowserHttpTest {
    @Test void explicitSyntheticBrowserLeaseAndOwnedResourceExit()throws Exception{
        Assumptions.assumeTrue(Boolean.getBoolean("zhku.t07.browserLease"), "浏览器宿主必须显式开启，普通回归不监听端口");
        runFixture();
    }
    static void runFixture()throws Exception{
        if (!Boolean.getBoolean("zhku.t07.browserLease")) throw new IllegalArgumentException("页面夹具须显式租约");
        RuleAgentTaskHttpTest.queue.clear();RuleAgentTaskHttpTest.originalCurrent.set(true);
        var app=new SpringApplication(RuleAgentTaskHttpTest.App.class);app.setAdditionalProfiles("zhku-rule-check-preview");
        var settings=new HashMap<String,Object>();
        settings.put("server.port",System.getProperty("zhku.t07.port","0"));settings.put("server.address","127.0.0.1");settings.put("server.servlet.context-path","/api");
        settings.put("server.servlet.session.timeout","15m");settings.put("server.servlet.session.persistent","false");settings.put("server.servlet.session.tracking-modes","cookie");
        settings.put("server.servlet.session.cookie.name","ZHKU_RULE_CHECK_SESSION");settings.put("server.servlet.session.cookie.path",RuleAgentTaskHttpTest.P);
        settings.put("server.servlet.session.cookie.http-only","true");settings.put("server.servlet.session.cookie.secure","false");settings.put("server.servlet.session.cookie.same-site","strict");
        settings.put("zhku.rule-check-preview.enabled","true");settings.put("zhku.rule-agent-http.enabled","true");settings.put("logging.level.root","ERROR");
        settings.put("spring.main.allow-bean-definition-overriding","true");settings.put("spring.config.location","optional:classpath:/http-fixture-no-config.yml");app.setDefaultProperties(settings);
        var stop=new AtomicBoolean();
        Thread worker=new Thread(()->{while(!stop.get()){try{var job=RuleAgentTaskHttpTest.queue.poll(100,TimeUnit.MILLISECONDS);if(job!=null){try{Thread.sleep(1800);}catch(InterruptedException ignored){}job.run();}}catch(InterruptedException ignored){}}},"zhku-t07-browser-synthetic");
        Path evidence=Path.of(System.getProperty("zhku.rulecheck.evidenceDir"));int port;
        try(var context=(ServletWebServerApplicationContext)app.run("--server.port="+System.getProperty("zhku.t07.port","0"), "--server.address=127.0.0.1", "--zhku.rule-agent-http.online-enabled=false")){
            // 显式证明此分支未装配真实供应商、在线工厂或数据库；不通过读取凭据来推断零收费。
            assertThat(context.getBeansOfType(com.zhku.knowledge.ruleagent.RuleAgentOnlineFactory.class)).isEmpty();
            assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
            port=context.getWebServer().getPort();worker.start();
            try{
                if(Boolean.getBoolean("zhku.t07.browserLease")){
                    RuleAgentTaskHttpTest.JSON.writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("browser-ready.json").toFile(),Map.of("backendPort",port,"executionMode","TEST_SIMULATION_NO_PROVIDER","schemaVersion",3,"providerCalls",0,"databaseCalls",0,"sourceSha",System.getProperty("zhku.sourceSha","uncommitted-with-manifest")));
                    Instant until=Instant.now().plusSeconds(480);while(!Files.exists(evidence.resolve("STOP"))&&Instant.now().isBefore(until))Thread.sleep(100);
                    assertThat(Files.exists(evidence.resolve("STOP"))).as("租约必须显式结束").isTrue();
                }
            }finally{stop.set(true);worker.interrupt();worker.join(3000);}
            assertThat(worker.isAlive()).isFalse();assertThat(context.getBean(RuleAgentTaskStore.class).activeJobs()).isZero();assertThat(RuleAgentTaskHttpTest.queue).isEmpty();
        }
        try(var socket=new java.net.Socket()){assertThatThrownBy(()->socket.connect(new java.net.InetSocketAddress("127.0.0.1",port),500)).isInstanceOf(java.io.IOException.class);}
        Files.writeString(evidence.resolve("browser-host-exit.json"),"{\"backendListenerExited\":true,\"workerExited\":true,\"activeJobs\":0,\"providerCalls\":0,\"databaseCalls\":0}");
    }
}
