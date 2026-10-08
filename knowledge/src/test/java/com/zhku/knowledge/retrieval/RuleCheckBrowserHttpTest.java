package com.zhku.knowledge.retrieval;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.retrieval.RuleCheckPreviewHttpTest.*;

/** 实际临时库/HTTP工程链；仅特定live批次等待本机浏览器，普通回归不等待。 */
@Timeout(720)
class RuleCheckBrowserHttpTest {
    // 仅随机临时库账号的公开合成口令，不读取/复用真实用户密码，不进入生产配置。
    static final String SYNTHETIC_PASSWORD="Synthetic-Rulecheck-Only-2026!";
    @Test void actualAnswerOriginalAndOptionalBoundedBrowserLease() throws Exception {
        withApp(RuleCheckOriginalHttpTest.FixtureConfiguration.class,app->{
            var fixture=app.context().getBean(RuleCheckOriginalHttpTest.OriginalFixture.class);
            UUID secondChunk=UUID.randomUUID(),secondEntry=UUID.randomUUID();
            String secondText="合成条款：上限6学分。";
            var base=fixture.catalogue.get().getFirst().template();
            var secondBinding=new RuleCheckRuleTemplate.EvidenceBinding(fixture.source,secondChunk,base.referenceRevision(),
                RuleCheckOriginalHttpTest.sha(secondText),0,secondText.length(),secondText);
            withSecondChunk(fixture,secondChunk,secondEntry,fixture.run,secondText,()->{
            try(var user=new User(app.port())) {
                RuleCheckOriginalHttpTest.login(user,app.accounts().get(0));
                // 同一真实SQL/Runtime链预检五状态；规则内容仅合成，不代表真实政策语义成绩。
                for(String mode:List.of("ANSWERED","PARTIAL","CLARIFY","CONFLICT","EMPTY")) {
                    configure(fixture,base,secondBinding,mode);
                    var response=user.json(RuleCheckOriginalHttpTest.BODY,true,false);
                    assertThat(response.statusCode()).isEqualTo(200);
                    var body=JSON.readTree(response.body());
                    String expected=switch(mode){case "PARTIAL"->"PARTIALLY_ANSWERED";case "CLARIFY"->"NEED_CLARIFICATION";case "CONFLICT"->"CONFLICTING_EVIDENCE";case "EMPTY"->"INSUFFICIENT_EVIDENCE";default->"ANSWERED";};
                    assertThat(body.path("status").asText()).isEqualTo(expected);
                    if(mode.equals("CLARIFY")) {
                        String continuation=JSON.writeValueAsString(Map.of("question","合成条款","confirmedConditions",Map.of("year","A"),"clarificationToken",body.path("clarificationToken").asText()));
                        var resumed=user.json(continuation,true,false);
                        assertThat(resumed.statusCode()).isEqualTo(200);
                        assertThat(JSON.readTree(resumed.body()).path("status").asText()).isEqualTo("ANSWERED");
                    }
                }
                configure(fixture,base,secondBinding,"ANSWERED");
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
                "synthetic",true,"provider",false,"deadlineSeconds",600)));
            // 有界人工浏览器租约留出完整矩阵时间；到期仍失败，不能靠延时把未验收算通过。
            Instant deadline=Instant.now().plusSeconds(600);boolean stopped=false;
            Path command=evidence.resolve("browser-command.txt");
            while(Instant.now().isBefore(deadline)) {
                if(Files.exists(command)) {
                    String action=Files.readString(command).trim();Files.delete(command);
                    if(action.equals("STOP")){stopped=true;break;}
                    if(action.equals("WITHDRAW"))fixture.update("UPDATE knowledge_source SET publication_status='ARCHIVED',visibility_scope='INTERNAL_ONLY' WHERE id=?",fixture.source);
                    else if(action.equals("RESTORE"))fixture.update("UPDATE knowledge_source SET publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE' WHERE id=?",fixture.source);
                    else if(Set.of("ANSWERED","PARTIAL","CLARIFY","CONFLICT","EMPTY").contains(action))configure(fixture,base,secondBinding,action);
                    else throw new IllegalArgumentException("未知本机验收动作");
                    Files.writeString(evidence.resolve("browser-command-ack.txt"),action);
                }
                Thread.sleep(100);
            }
            assertThat(stopped).as("浏览器必须显式结束；超时不能冒称验收完成").isTrue();
            Files.writeString(evidence.resolve("browser-host-finished.json"),"{\"explicitStop\":true,\"synthetic\":true}");
            });
        });
    }
    @Test void secondInsertFailureMustRemoveFirstInsertWithoutDeletingBaseEvidence() throws Exception {
        withApp(RuleCheckOriginalHttpTest.FixtureConfiguration.class,app->{
            var fixture=app.context().getBean(RuleCheckOriginalHttpTest.OriginalFixture.class);
            UUID chunk=UUID.randomUUID(),entry=UUID.randomUUID();
            // 不mock连接：不存在的run使第二INSERT真实触发FK拒绝，第一INSERT已提交。
            assertThatThrownBy(()->withSecondChunk(fixture,chunk,entry,UUID.randomUUID(),"合成条款：上限6学分。",()->{
                throw new AssertionError("初始化失败后不得进入浏览器动作");
            })).isInstanceOf(java.sql.SQLException.class);
            try(var c=fixture.ds.getConnection();var p=c.prepareStatement("SELECT (SELECT count(*) FROM knowledge_chunk WHERE id=?),(SELECT count(*) FROM retrieval_index_entry WHERE id=?),(SELECT count(*) FROM knowledge_chunk WHERE id=?),(SELECT count(*) FROM knowledge_source WHERE id=?)")) {
                p.setObject(1,chunk);p.setObject(2,entry);p.setObject(3,fixture.chunk);p.setObject(4,fixture.source);
                try(var rows=p.executeQuery()){rows.next();assertThat(rows.getInt(1)).as("第一INSERT不得残留").isZero();assertThat(rows.getInt(2)).isZero();assertThat(rows.getInt(3)).isEqualTo(1);assertThat(rows.getInt(4)).isEqualTo(1);}
            }
        });
    }
    @FunctionalInterface interface CheckedAction {void run() throws Exception;}
    static void withSecondChunk(RuleCheckOriginalHttpTest.OriginalFixture fixture,UUID chunk,UUID entry,UUID run,String text,CheckedAction action)throws Exception {
        // 初始化也必须进入清理范围；第二条写入失败时第一条已提交，不能依赖外层只清原chunk。
        try {
        fixture.update("INSERT INTO knowledge_chunk(id,source_id,chunk_index,source_locator,page_number,content,content_checksum) VALUES(?,?,1,'合成页2',2,?,?)",chunk,fixture.source,text,RuleCheckOriginalHttpTest.sha(text));
        fixture.update("INSERT INTO retrieval_index_entry(id,run_id,profile_id,chunk_id,source_version,chunking_version,content_checksum) VALUES(?,?,'synthetic-http',?,'2025.9.2','synthetic-v1',?)",entry,run,chunk,RuleCheckOriginalHttpTest.sha(text));
        action.run();}
        finally {
            fixture.update("DELETE FROM citation_audit WHERE index_entry_id=?",entry);
            fixture.update("DELETE FROM retrieval_index_entry WHERE id=?",entry);
            fixture.update("DELETE FROM knowledge_chunk WHERE id=?",chunk);
        }
    }
    static void configure(RuleCheckOriginalHttpTest.OriginalFixture fixture,RuleCheckRuleTemplate base,
                          RuleCheckRuleTemplate.EvidenceBinding second,String mode) {
        if(mode.equals("EMPTY")){fixture.catalogue.set(List.of());return;}
        var facts=base.facts();var conditions=base.conditions();var gaps=base.evidenceGaps();var conflicts=base.conflicts();
        if(mode.equals("PARTIAL"))gaps=List.of("合成例外尚未核实");
        if(mode.equals("CLARIFY")) {
            conditions=List.of(new RuleCheckRuleTemplate.Condition("year","合成年级",List.of("A","B")));
            var fact=base.facts().getFirst();
            facts=List.of(new RuleCheckRuleTemplate.Fact(fact.id(),fact.text(),false,Map.of("year","A"),fact.evidenceBindings()));
        }
        if(mode.equals("CONFLICT")) {
            var other=new RuleCheckRuleTemplate.Fact("limit-six","合成上限6",true,Map.of(),List.of(second));
            conflicts=List.of(new RuleCheckRuleTemplate.Conflict("合成上限条款冲突",List.of(base.facts().getFirst(),other)));
        }
        var rule=new RuleCheckRuleTemplate(base.ruleId(),base.referenceRevision(),base.reviewState(),base.reviewer(),base.reviewedAt(),facts,conditions,gaps,conflicts);
        fixture.catalogue.set(List.of(new RuleCheckApplicationService.CatalogEntry(rule,"合成条款")));
    }
}
