package com.zhku.knowledge.rulecheck.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;

/** 本机受控宿主：真实资料/数据库/认证，封存选题回放；EOF/STOP均关闭自己拥有的Context。 */
public final class Controlled2025RuleCheckHost {
    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("参数只由本机启动器环境提供");
        var root=Path.of(required("ZHKU_RULECHECK_ROOT")).toAbsolutePath().normalize();
        var evidence=Path.of(required("ZHKU_RULECHECK_EVIDENCE")).toAbsolutePath().normalize();
        if(!evidence.startsWith(root.resolve("docs/evidence")))throw new IllegalArgumentException("证据目录超界");
        int port=Integer.parseInt(required("ZHKU_RULECHECK_PORT"));
        if(port<1024||port>65535)throw new IllegalArgumentException("监听端口非法");
        String password=required("ZHKU_RULECHECK_STUDENT_PASSWORD");
        if(password.length()<16||password.length()>64)throw new IllegalArgumentException("合成账号密码长度非法");
        boolean taskReplay=System.getenv("ZHKU_RULECHECK_TASK_BATCH_DIRECTORY")!=null;
        var arguments=new ArrayList<>(List.of(
            "--server.port="+port,"--spring.main.banner-mode=off","--logging.level.root=ERROR",
            "--zhku.db.url="+required("ZHKU_DB_URL"),"--zhku.db.username=zhkuagent",
            "--zhku.rule-check-preview.enabled=true","--zhku.rule-check-preview.initialize=true",
            "--zhku.rule-check-preview.catalogue-mode=REVIEWED_2025_CONTROLLED",
            "--zhku.rule-check-preview.selector-mode="+(taskReplay?"TASK_PLAN_REPLAY":"SEALED_REPLAY"),"--zhku.rule-check-preview.repository-root="+root,
            "--zhku.rule-check-preview.profile-id=controlled-2025-local","--zhku.rule-check-preview.chunking-version=handbook-2025-clause-verbatim-v1",
            "--zhku.rule-check-preview.required-scope=RULE_CHECK_PUBLIC_ELECTIVE_2025","--zhku.rule-check-preview.reference-revision=2025.9.2",
            "--zhku.rule-check-preview.candidate-limit=20","--zhku.rule-check-preview.result-limit=5"));
        // 最终任务回放须显式钉住请求/响应身份，不能让旧启动器静默退回单规则路径。
        if(taskReplay){
            arguments.add("--zhku.rule-check-preview.task-batch-directory="+required("ZHKU_RULECHECK_TASK_BATCH_DIRECTORY"));
            arguments.add("--zhku.rule-check-preview.task-package-sha256="+required("ZHKU_RULECHECK_TASK_PACKAGE_SHA256"));
            arguments.add("--zhku.rule-check-preview.task-summary-sha256="+required("ZHKU_RULECHECK_TASK_SUMMARY_SHA256"));
        }
        var context=(ServletWebServerApplicationContext)RuleCheckPreviewApplication.application().run(arguments.toArray(String[]::new));
        var source=context.getBean(DataSource.class);var identity=context.getBean(PreviewIdentityRepository.class);
        java.util.function.IntSupplier workers=taskReplay?context.getBean(com.zhku.knowledge.rulecheck.SealedRuleTaskReplay.class)::liveWorkers
            :context.getBean(SealedRuleSelectionReplay.class)::liveWorkers;UUID account=UUID.randomUUID();
        boolean accountInserted=false;
        var json=new ObjectMapper().findAndRegisterModules();
        try {
            try(var connection=source.getConnection();var statement=connection.prepareStatement(
                    "INSERT INTO preview_identity(user_id,login_name,password_hash,role,enabled,auth_revision) VALUES(?,?,?,'USER',true,0)")) {
                statement.setObject(1,account);statement.setString(2,"controlled-2025-student");statement.setString(3,new BCryptPasswordEncoder(10).encode(password));
                statement.executeUpdate();accountInserted=true;
            }
            password=null;
            Files.write(evidence.resolve("host-ready.json"),json.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
                "pid",ProcessHandle.current().pid(),"port",context.getWebServer().getPort(),"selectorMode",taskReplay?"TASK_PLAN_REPLAY_NOT_LIVE":"SEALED_REPLAY_NOT_LIVE",
                "providerCalls",0,"runtimeSettings",context.getBean(RuleCheckRuntimeSettings.class),"productionClasspathOnly",true)),StandardOpenOption.CREATE_NEW);
            System.out.println("RULECHECK_HOST_READY 离线回放，不是在线模型");System.out.flush();
            try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
                String line;while((line=input.readLine())!=null&&!line.equals("STOP")){}
            }
        } finally {
            // 即使账号准备/证据写入失败，也关闭宿主；只删除本次账号，资料Bean负责自己的随机身份。
            try {if(accountInserted)try(var connection=source.getConnection();var statement=connection.prepareStatement("DELETE FROM preview_identity WHERE user_id=?")) {
                statement.setObject(1,account);statement.executeUpdate();
            }}finally{context.close();}
        }
        long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
        while(workers.getAsInt()!=0&&System.nanoTime()<deadline)Thread.sleep(10);
        boolean closed=identity.status().closed()&&identity.status().liveThreads()==0&&identity.status().inFlight()==0&&workers.getAsInt()==0;
        Files.write(evidence.resolve("host-closed.json"),json.writeValueAsBytes(Map.of("resourcesClosed",closed,"providerCalls",0)),StandardOpenOption.CREATE_NEW);
        System.out.println("RULECHECK_HOST_CLOSED resources="+closed);
        if(!closed)System.exit(1);
    }
    private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("缺少启动环境："+name);return value;}
}
