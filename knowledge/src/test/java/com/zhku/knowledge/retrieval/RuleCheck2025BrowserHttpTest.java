package com.zhku.knowledge.retrieval;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;
import static org.assertj.core.api.Assertions.*;

/** 有界浏览器宿主：已审真实资料、固定开发选题、临时账号；JUnit退出不等于页面验收通过。 */
@Timeout(720)
class RuleCheck2025BrowserHttpTest {
    static final String SYNTHETIC_PASSWORD="Synthetic-Rulecheck-Only-2026!";
    @Test void boundedReviewed2025BrowserHostClosesNormally()throws Exception {
        String directory=System.getProperty("zhku.rulecheck.evidenceDir","");
        assertThat(directory).isNotBlank();Path evidence=Path.of(directory).toAbsolutePath().normalize();
        assertThat(evidence.getFileName().toString()).startsWith("c05-http-browser-live-");
        withApp(RuleCheck2025HttpTest.Configuration2025.class,app->{
            var f=app.context().getBean(RuleCheck2025HttpTest.Fixture.class);
            try(var user=new User(app.port())) {
                RuleCheckOriginalHttpTest.login(user,app.accounts().getFirst());
                var result=RuleCheck2025HttpTest.ask(user,RuleCheck2025HttpTest.reference(f,"Q12").path("question").asText());
                assertThat(result.path("scope").path("questionScope").asText()).contains("第8条没有列出具体例外及审批办法。");
            }
            // 仅本次随机临时账号使用已知合成口令；不读取或复用真实学生密码。
            for(var account:app.accounts())f.trial.update("UPDATE preview_identity SET password_hash=?,auth_revision=auth_revision+1 WHERE user_id=?",
                new BCryptPasswordEncoder(10).encode(SYNTHETIC_PASSWORD),account.id());
            var accounts=app.accounts().stream().map(a->Map.of("login",a.login(),"role",a.role())).toList();
            f.trial.save("browser-ready.json",Map.of("backendPort",app.port(),"accounts",accounts,"selectionMode","EXACT_DEVELOPMENT_FIXED_ID_CONTROL_NOT_MODEL",
                "sourceMode","REVIEWED_2025_10_CLAUSES","providerCalls",0,"deadlineSeconds",600));
            Instant deadline=Instant.now().plusSeconds(600);boolean stopped=false;
            Path command=evidence.resolve("browser-command.txt");
            while(Instant.now().isBefore(deadline)) {
                if(Files.exists(command)) {
                    String action=Files.readString(command).trim();Files.delete(command);
                    if(action.equals("STOP")){stopped=true;break;}
                    if(action.equals("WITHDRAW"))f.trial.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",f.trial.source);
                    else if(action.equals("RESTORE"))f.trial.update("UPDATE knowledge_source SET publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE' WHERE id=?",f.trial.source);
                    else throw new IllegalArgumentException("未知浏览器验收指令");
                    f.trial.save("browser-command-"+UUID.randomUUID()+".json",Map.of("action",action,"at",Instant.now().toString()));
                }
                Thread.sleep(100);
            }
            // 到期失败；停止宿主只证明资源可回收，浏览器结果必须另留DOM/截图证据。
            assertThat(stopped).as("浏览器宿主需在有界时间内显式STOP").isTrue();
            f.trial.save("browser-host-stop.json",Map.of("explicitStop",true,"providerCalls",0,"uiOutcomeStoredSeparately",true));
        });
    }
}
