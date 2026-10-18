package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 同候选 HTTP 工程审包；只读取资料与构建字节，不能创建数据库、Provider 或恢复旧额度。 */
public final class RuleAgentHttpRequestPackage {
    public static final String PURPOSE="T06_HTTP_BROWSER_P02_ENGINEERING_ONLY";
    public static final String QUESTION="我这门选修课没过，能不能重修？", REPLY="PUBLIC";
    public static final int MAX_CALLS=10;
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    private static final List<String> INPUT_ROOTS=List.of(".gitattributes","AGENTS.md",".mvn","mvnw","mvnw.cmd","pom.xml","agent-runtime","agent","knowledge","scripts");
    private RuleAgentHttpRequestPackage() {}

    public static String prepare(Path codeRoot,Path assetsRoot,Path dist,Path directory)throws Exception {
        // 在任何输出前拒绝复用；失败包也不覆盖，另建批次保留原轨迹。
        if(Files.exists(directory))throw new FileAlreadyExistsException(directory.toString());
        return writeNew(directory,definition(codeRoot,assetsRoot,dist,UUID.randomUUID().toString()));
    }
    public static JsonNode verify(Path codeRoot,Path assetsRoot,Path dist,Path directory,String sha)throws Exception {
        byte[] frozen=Files.readAllBytes(directory.resolve("request-package.json"));
        if(sha==null||!sha.matches("[a-f0-9]{64}")||!sha(frozen).equals(sha))throw new IllegalArgumentException("请求包原始字节与明确指纹不同");
        String batchId=JSON.readTree(frozen).path("batchId").asText();
        if(!UUID.fromString(batchId).toString().equals(batchId))throw new IllegalArgumentException("独立批次身份无效");
        return verifyBytes(directory,sha,definition(codeRoot,assetsRoot,dist,batchId));
    }
    static String writeNew(Path directory,JsonNode definition)throws Exception {
        Files.createDirectories(directory.toAbsolutePath().getParent());Files.createDirectory(directory);
        byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(definition);
        Files.write(directory.resolve("request-package.json"),bytes,StandardOpenOption.CREATE_NEW);
        String sha=sha(bytes);Files.writeString(directory.resolve("request-package.sha256"),sha+"\n",StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);return sha;
    }
    static JsonNode verifyBytes(Path directory,String sha,JsonNode expected)throws Exception {
        byte[] bytes=Files.readAllBytes(directory.resolve("request-package.json"));
        if(sha==null||!sha.matches("[a-f0-9]{64}")||!sha(bytes).equals(sha))throw new IllegalArgumentException("新批次包字节与已审指纹不同");
        JsonNode actual=JSON.readTree(bytes);
        if(!PURPOSE.equals(actual.path("purpose").asText())||!"NOT_AUTHORIZED".equals(actual.path("authorizationStatus").asText())||!actual.equals(expected))throw new IllegalArgumentException("旧批次、构建、资料或请求定义已改变；不得创建外部资源");
        return actual;
    }
    /** 包SHA在本候选全局只能消费一次；复制目录、重启JVM和失败都不能重获额度。 */
    public static void claimReservedExecution(Path codeRoot,Path directory,String sha,String token)throws Exception {
        if(sha==null||!sha.matches("[a-f0-9]{64}")||token==null||!UUID.fromString(token).toString().equals(token))throw new IllegalArgumentException("缺少有效的一次性占用身份");
        Path reservations=codeRoot.resolve("docs/evidence/rule-agent-integration/authorization-reservations");
        JsonNode reserved=JSON.readTree(Files.readAllBytes(reservations.resolve(sha+".json")));
        if(!sha.equals(reserved.path("packageSha256").asText())||!token.equals(reserved.path("reservationId").asText()))throw new IllegalArgumentException("本包一次性占用记录不匹配");
        var claim=JSON.createObjectNode().put("packageSha256",sha).put("reservationId",token).put("packageDirectory",directory.toAbsolutePath().toString()).put("hostStartedAt",java.time.Instant.now().toString()).put("status","STARTED_NONREFUNDABLE");
        // CREATE_NEW是跨进程竞争的原子门；位置按SHA全局确定，而不是每个复制包各有一次。
        Files.write(reservations.resolve(sha+".host-started.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(claim),StandardOpenOption.CREATE_NEW);
    }
    static void bindCapabilityAuthority(ObjectNode model,Path codeRoot)throws Exception {model.put("factCapabilityAuthoritySha256",sha(Files.readAllBytes(codeRoot.resolve("knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleFactCapabilityCatalogue.java"))));}    private static ObjectNode definition(Path codeRoot,Path assetsRoot,Path dist,String batchId)throws Exception {
        codeRoot=codeRoot.toRealPath();assetsRoot=assetsRoot.toRealPath();dist=dist.toRealPath();
        if(!dist.startsWith(codeRoot.resolve("agent/frontend"))||!"dist-student".equals(dist.getFileName().toString())||!Files.isRegularFile(dist.resolve("index.html")))throw new IllegalArgumentException("须使用当前候选显式构建的 dist-student");
        var dirty=new ArrayList<>(List.of("diff","--quiet","HEAD","--"));dirty.addAll(INPUT_ROOTS);
        if(gitExit(codeRoot,dirty)!=0)throw new IllegalArgumentException("审包输入须先完成源码检查点提交");
        var unknown=new ArrayList<>(List.of("ls-files","--others","--exclude-standard","-z","--"));unknown.addAll(INPUT_ROOTS);
        // 未提交的新宿主也会参与编译；只检查 tracked diff 会漏掉这类实际执行输入。
        String untracked=git(codeRoot,unknown);if(!untracked.isEmpty())throw new IllegalArgumentException("存在未提交构建输入；先保存明确源码检查点");
        var tracked=new ArrayList<>(List.of("ls-files","-z","--"));tracked.addAll(INPUT_ROOTS);
        List<String> sources=Arrays.stream(git(codeRoot,tracked).split("\u0000")).filter(s->!s.isEmpty()).sorted().toList();
        var revisionArgs=new ArrayList<>(List.of("log","-1","--format=%H","--"));revisionArgs.addAll(INPUT_ROOTS);
        String commit=git(codeRoot,revisionArgs).trim(),tree=git(codeRoot,List.of("rev-parse",commit+"^{tree}")).trim();
        if(!commit.matches("[a-f0-9]{40}")||!tree.matches("[a-f0-9]{40}"))throw new IllegalArgumentException("源码身份不可用");
        String rawClasspath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        List<Path> classpath=Arrays.stream(rawClasspath.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))).filter(s->!s.isBlank()).map(Path::of).toList();
        var identity=fingerprintInputs(codeRoot,dist,sources,classpath,commit,tree);
        var pack=JSON.createObjectNode();pack.put("purpose",PURPOSE);pack.put("authorizationStatus","NOT_AUTHORIZED");pack.put("batchId",batchId);
        pack.put("priorAuthorizations","ALL_CLOSED; THIS_PACKAGE_AND_ITS_SHA_ARE_NOT_HUMAN_APPROVAL");
        pack.set("buildIdentity",identity);
        // 复用 T05 的完整提示词/动作/核验/允许资料披露；仅从 AssetsRoot 读取审核资料，绝不加载其代码/JAR。
        var model=RuleAgentOnlineProbeHost.controlledRequestDefinition(assetsRoot);
        bindCapabilityAuthority(model,codeRoot);
        model.put("remainingBudgetRule","同一task最多两run；每run4决策+1独立核验共5次/110秒，单次20秒/1024输出token；整批最多10次/220秒模型运行时间。人工浏览器900秒租约，宿主1000秒watchdog。无重试、无第三run、失败不补额。");
        model.put("purpose",PURPOSE);model.put("executionPolicyRevision","HTTP_P02_SINGLE_TASK_TWO_RUNS_01");
        model.put("localExchangeRecording","TRACE_DIAGNOSTICS_AND_TASK_OUTCOMES_ONLY; NO_HTTP_HEADERS; NO_MODEL_REQUEST_RESPONSE_BODY; NO_LOGIN_BODY");
        model.put("ownedProcessMillis",1000000);model.put("executionReadiness","PACKAGE_ONLY; REQUIRE_NEW_EXPLICIT_HUMAN_APPROVAL; NO_REAL_PROVIDER_CALLS_DURING_PREPARE_OR_CHECK");
        pack.set("modelContract",model);
        var wire=pack.putObject("wireEnvelope");wire.put("method","POST");wire.put("endpoint",RuleAgentProvider.BEIJING.toString());wire.put("model",RuleAgentProvider.MODEL);wire.put("stream",false);wire.put("enable_thinking",false);wire.put("max_completion_tokens",RuleAgentContract.DEFAULT_BUDGET.maxOutputTokens());wire.put("redirects","NEVER");wire.put("decisionResponseFormat","json_schema: name=zhku_rule_agent_action, strict=true, schema=actionSchema with actual current candidates and allowed actions");wire.put("reviewResponseFormat","json_object");wire.put("messages","two messages: system=systemPrompt or reviewSystemPrompt; user=serialized decision data or semantic review input");
        wire.put("decisionDataFields","candidate projection/currentCandidates and current set id; executionState; question; questions; userConditions; ruleContexts; actionContract; remainingBudget; optional publicationFactIds; facts(id,ruleId,text,when,optional publicationEligible); last two rejectedActions; availableConditions; observations(step,status,dataLevel,reasonCode,factIds,evidence.quote/revision); no raw candidate UUIDs or original tokens");
        wire.put("reviewDataFields","input=RulePublicationContract.SemanticInput including protocol/capability/input fingerprint, original questions, declared conditions, actual read facts and approved quotes, fact capabilities and supported conditions; reviewContract; remainingBudget");
        var scope=pack.putObject("scope");scope.put("question",QUESTION);scope.put("reply",REPLY);scope.put("maximumTasks",1);scope.put("maximumRuns",2);scope.put("maximumProviderCalls",MAX_CALLS);scope.put("leaseSeconds",900);scope.put("hostWatchdogSeconds",1000);scope.put("automaticRetries",0);
        scope.put("mode","ONLINE_ONLY");scope.put("extraInputPolicy","EXACT_QUESTION_AND_SINGLE_courseType_PUBLIC; BLOCK_BEFORE_PROVIDER; NO_THIRD_RUN_OR_NEXT_BATCH");
        scope.put("success","SAME_TASK: ONLINE_WAITING_USER_THEN_ONLINE_RESOLVED_COMPLETE_WITH_EVIDENCE; INDEPENDENT_PUBLICATION_REVIEW; ACTUAL_HTTP_AND_STUDENT_BROWSER_REQUIRED");
        var money=pack.putObject("proposedCost");money.put("maximumRmb",10);money.putNull("estimatedActualRmb");money.put("conservativeModelContextCeilingRmb",8.027648);money.put("tariffVerifiedAt","2026-10-18 Asia/Shanghai");money.put("tariffSource","https://help.aliyun.com/zh/model-studio/qwen3-8-flash");money.put("inputRmbPerMillionTokens",0.8);money.put("outputRmbPerMillionTokens",2.7);money.put("estimateBasis","10*(1000000 input tokens*0.8 + 1024 output tokens*2.7)/1000000; conservative full model context, not expected usage; no cache/discount/free allowance assumed");money.put("actualBill","UNKNOWN; no automatic refill; dispatch timeout counts against attempts and may be billable; require renewed tariff check if executed after 2026-10-25");
        pack.put("localBoundary","ONLY_NEW_LOOPBACK_TEMP_DATABASE_zhkuagent_rulecheck_e2e; SYNTHETIC_TEST_IDENTITIES; 2025.9.2_REVIEWED_HISTORICAL_ASSETS; NO_ORIGINAL_DATABASE_WRITES");
        pack.put("recording","LOCAL_ONLY: provider trace whitelist, task snapshots/events and resource receipts; no login body, cookie, CSRF token, key or HTTP authorization headers; no upload");
        pack.put("notSent","owner/session/continuation/original tokens, DB password, API key except TLS Authorization transport, source code, full PDF, true student records, reference answers");
        return pack;
    }
    static ObjectNode fingerprintInputs(Path codeRoot,Path dist,List<String> sources,List<Path> classpath,String commit,String tree)throws Exception {
        var identity=JSON.createObjectNode();identity.put("sourceCommit",commit);identity.put("sourceTree",tree);
        var sourceHashes=identity.putObject("sourceInputsSha256");
        for(String name:sources){Path path=codeRoot.resolve(name).normalize();if(!path.startsWith(codeRoot))throw new IllegalArgumentException("代码路径越界");sourceHashes.put(name.replace('\\','/'),sha(Files.readAllBytes(path)));}
        identity.put("controlledJarSha256",sha(Files.readAllBytes(codeRoot.resolve("knowledge/target/knowledge-0.1.0-rc.1-controlled.jar"))));
        identity.put("studentBuildMode","student");identity.set("studentDistSha256",treeHashes(dist));
        var runtime=identity.putObject("runtimeClasspathSha256");
        for(Path path:classpath){path=path.toAbsolutePath().normalize();if(Files.isDirectory(path))runtime.set(path.toString(),treeHashes(path));else runtime.put(path.toString(),sha(Files.readAllBytes(path)));}
        Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
        identity.put("javaExecutableSha256",sha(Files.readAllBytes(java)));return identity;
    }
    private static ObjectNode treeHashes(Path root)throws Exception {
        var result=JSON.createObjectNode();try(var files=Files.walk(root)){for(Path path:files.filter(Files::isRegularFile).sorted().toList())result.put(root.relativize(path).toString().replace('\\','/'),sha(Files.readAllBytes(path)));}
        return result;
    }
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String git(Path root,List<String> args)throws Exception {
        var command=new ArrayList<>(List.of("git","-C",root.toString()));command.addAll(args);var process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{byte[] bytes=process.getInputStream().readAllBytes();if(!process.waitFor(10,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IllegalArgumentException("无法读取本地源码身份");return new String(bytes,StandardCharsets.UTF_8);}finally{if(process.isAlive())process.destroyForcibly();}
    }
    private static int gitExit(Path root,List<String> args)throws Exception {
        var command=new ArrayList<>(List.of("git","-C",root.toString()));command.addAll(args);var process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{if(!process.waitFor(10,TimeUnit.SECONDS))throw new IllegalArgumentException("源码检查超时");return process.exitValue();}finally{if(process.isAlive())process.destroyForcibly();}
    }
}