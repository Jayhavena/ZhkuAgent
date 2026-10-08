package com.zhku.knowledge.controlled;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;

/** 旧v1真实鉴权/事务/派发兼容；合成模型仅在test classpath，不调用供应商。 */
@Timeout(720)
class RuleCheckLegacyBrowserHttpTest {
    static final String PASSWORD="Synthetic-Rulecheck-Only-2026!";
    @Test void legacySubmissionConflictAndOptionalBrowserLease() throws Exception {
        String url=ZhkuDatabaseTestConfig.databaseUrl();
        var ds=new DriverManagerDataSource(url,"zhkuagent",ZhkuDatabaseTestConfig.databasePassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var accounts=new ArrayList<Account>(); UUID source=UUID.randomUUID();
        Data data=null;
        try {
            try(var connection=ds.getConnection()) {
                for(String role:List.of("USER","USER","MAINTAINER")) {
                    var account=new Account(UUID.randomUUID(),"test-"+UUID.randomUUID().toString().substring(0,12),PASSWORD);
                    accounts.add(account);
                    try(var p=connection.prepareStatement("INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled,auth_revision) VALUES(?,?,?,?,true,0)")) {
                        p.setObject(1,account.id());p.setString(2,account.login());p.setString(3,new BCryptPasswordEncoder(10).encode(PASSWORD));p.setString(4,role);p.executeUpdate();
                    }
                }
                try(var p=connection.prepareStatement("INSERT INTO knowledge_source(id,title,source_type,source_version,publication_status,published_at,content_checksum,visibility_scope,publication_scope,sharing_rights_confirmed,document_family,knowledge_scope) VALUES(?,'合成学分规则','PDF','synthetic-v1','PUBLISHED',CURRENT_TIMESTAMP,?,'STUDENT_VISIBLE','合成本科生',true,'STUDENT_HANDBOOK','PUBLIC_ELECTIVE_CREDIT_LIMIT')")) {
                    p.setObject(1,source);p.setString(2,"0".repeat(64));p.executeUpdate();
                }
                try(var p=connection.prepareStatement("INSERT INTO knowledge_chunk(id,source_id,chunk_index,source_locator,content,content_checksum) VALUES(?,?,0,'合成第1条','每学期公共选修课最高不得超过 4 学分',?)")) {
                    p.setObject(1,UUID.randomUUID());p.setObject(2,source);p.setString(3,"0".repeat(64));p.executeUpdate();
                }
            }
            data=new Data(ds,accounts.get(0),accounts.get(1),accounts.get(2),source);
            var model=new Model();
            try(var app=start(data,model);var user=app.user()) {
                assertThat(user.login(data.a()).statusCode()).isEqualTo(200);
                UUID key=UUID.randomUUID();
                var accepted=user.submitWithKey(QUESTION,COURSES,key);assertThat(accepted.statusCode()).isEqualTo(202);
                UUID run=UUID.fromString(JSON.readTree(accepted.body()).path("runId").asText());
                user.awaitTerminal(run);
                var conflict=user.submitWithKey(QUESTION,COURSES.replace("1.5","1.0"),key);
                assertThat(conflict.statusCode()).isEqualTo(409);
                assertThat(JSON.readTree(conflict.body()).path("code").asText()).isEqualTo("SUBMISSION_CONTENT_CONFLICT");
                String directory=System.getProperty("zhku.rulecheck.evidenceDir","");
                if(directory.isBlank()||!Path.of(directory).getFileName().toString().startsWith("r5-legacy-live-"))return;
                Path evidence=Path.of(directory).toAbsolutePath().normalize();
                assertThat(Files.isDirectory(evidence)).isTrue();
                Files.writeString(evidence.resolve("browser-ready.json"),JSON.writeValueAsString(Map.of("backendPort",app.port,
                    "accounts",accounts.stream().map(a->Map.of("login",a.login(),"id",a.id().toString())).toList(),
                    "synthetic",true,"provider",false,"deadlineSeconds",600)));
                Instant deadline=Instant.now().plusSeconds(600);boolean stopped=false;
                Path command=evidence.resolve("browser-command.txt");
                while(Instant.now().isBefore(deadline)) {
                    if(Files.exists(command)) {
                        String action=Files.readString(command).trim();Files.delete(command);
                        if(!action.equals("STOP"))throw new IllegalArgumentException("旧v1浏览器验收只接受STOP");
                        stopped=true;break;
                    }
                    Thread.sleep(100);
                }
                assertThat(stopped).as("旧v1浏览器必须显式结束，超时不算通过").isTrue();
                assertThat(model.active).hasValue(0);
                Files.writeString(evidence.resolve("browser-host-finished.json"),"{\"explicitStop\":true,\"synthetic\":true,\"providerCalls\":0}");
            }
        } finally {
            // 只清本夹具身份和资料，不清表、不复用原库；Context先正常排空工作线程。
            if(data!=null)cleanup(data);
            else try(var c=ds.getConnection()) {
                try(var p=c.prepareStatement("DELETE FROM knowledge_chunk WHERE source_id=?")){p.setObject(1,source);p.executeUpdate();}
                try(var p=c.prepareStatement("DELETE FROM knowledge_source WHERE id=?")){p.setObject(1,source);p.executeUpdate();}
                for(var a:accounts)try(var p=c.prepareStatement("DELETE FROM preview_identity WHERE user_id=?")){p.setObject(1,a.id());p.executeUpdate();}
            }
        }
    }
}
