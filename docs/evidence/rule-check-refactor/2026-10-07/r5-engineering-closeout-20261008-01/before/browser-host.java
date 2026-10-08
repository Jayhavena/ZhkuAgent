package com.zhku.knowledge.retrieval;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;

/** 实际临时库/HTTP工程链；仅特定live批次等待本机浏览器，普通回归不等待。 */
@Timeout(420)
class RuleCheckBrowserHttpTest {
    // 仅随机临时库账号的公开合成口令，不读取/复用真实用户密码，不进入生产配置。
    static final String SYNTHETIC_PASSWORD="Synthetic-Rulecheck-Only-2026!";
    @Test void actualAnswerOriginalAndOptionalBoundedBrowserLease() throws Exception {
        withApp(RuleCheckOriginalHttpTest.FixtureConfiguration.class,app->{
            var fixture=app.context().getBean(RuleCheckOriginalHttpTest.OriginalFixture.class);
            try(var user=new User(app.port())) {
                RuleCheckOriginalHttpTest.login(user,app.accounts().get(0));
                String handle=RuleCheckOriginalHttpTest.answer(user);
                assertThat(user.get("/rule-check/originals/"+handle).statusCode()).isEqualTo(200);
            }
            String directory=System.getProperty("zhku.rulecheck.evidenceDir","");
            if(directory.isBlank() || !Path.of(directory).getFileName().toString().startsWith("r5-browser-live-"))return;
            Path evidence=Path.of(directory).toAbsolutePath().normalize();
            assertThat(Files.isDirectory(evidence)).isTrue();
            // 只修改本夹具随机账号，便于UI验收；不写口令/密钥进就绪文件或日志。
            for(var account:app.accounts())fixture.update("UPDATE preview_identity SET password_hash=?,auth_revision=auth_revision+1 WHERE user_id=?",
                new BCryptPasswordEncoder(10).encode(SYNTHETIC_PASSWORD),account.id());
            var accounts=app.accounts().stream().map(a->Map.of("login",a.login(),"role",a.role(),"id",a.id().toString())).toList();
            Files.writeString(evidence.resolve("browser-ready.json"),JSON.writeValueAsString(Map.of("backendPort",app.port(),"accounts",accounts,
                "synthetic",true,"provider",false,"deadlineSeconds",360)));
            Instant deadline=Instant.now().plusSeconds(360);boolean stopped=false;
            Path command=evidence.resolve("browser-command.txt");
            while(Instant.now().isBefore(deadline)) {
                if(Files.exists(command)) {
                    String action=Files.readString(command).trim();Files.delete(command);
                    if(action.equals("STOP")){stopped=true;break;}
                    if(action.equals("WITHDRAW"))fixture.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",fixture.source);
                    else if(action.equals("RESTORE"))fixture.update("UPDATE knowledge_source SET publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE' WHERE id=?",fixture.source);
                    else throw new IllegalArgumentException("未知本机验收动作");
                    Files.writeString(evidence.resolve("browser-command-ack.txt"),action);
                }
                Thread.sleep(100);
            }
            assertThat(stopped).as("浏览器必须显式结束；超时不能冒称验收完成").isTrue();
            Files.writeString(evidence.resolve("browser-host-finished.json"),"{\"explicitStop\":true,\"synthetic\":true}");
        });
    }
}
