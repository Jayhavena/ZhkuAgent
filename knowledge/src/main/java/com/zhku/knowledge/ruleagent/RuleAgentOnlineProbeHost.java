package com.zhku.knowledge.ruleagent;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import com.zhku.knowledge.retrieval.*;
import com.zhku.knowledge.rulecheck.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
/** 独立工程探针宿主；学生HTTP入口由T06另行接入。 */
public final class RuleAgentOnlineProbeHost {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    private static final String PACKAGE="docs/evidence/rule-agent-provider/2026-10-13/t05-probe-02/request-package.json";
    private static final String P01_QUESTION="专业选修课和公共选修课在修读安排上有什么不同？";
    public static Map<String,Object> environmentCheck(Map<String,String> env){
        var settings=RuleAgentProvider.Settings.environment(env);String sha=env.get("ZHKU_RULE_AGENT_PROBE_SHA256");
        if(sha==null||!sha.matches("[a-f0-9]{64}")||!settings.authorizationId().equals(sha))throw new IllegalArgumentException("探针未绑定具体请求包授权");
        String selected=env.get("ZHKU_RULE_AGENT_PROBE_CASE");if(!"P01".equals(selected))throw new IllegalArgumentException("本批仅允许P01，不能沿用旧双题入口");
        return Map.of("mode","ONLINE","endpoint",settings.endpoint().toString(),"model",settings.model(),"maximumProviderCalls",4,"providerCalls",0,"databaseOperations",0,"builds",0,"budgetRevision",BUDGET_REVISION,"timeoutMillis",DEFAULT_BUDGET.maxModelMillis(),"runTimeoutMillis",DEFAULT_BUDGET.maxRunMillis());
    }
    static void validatePackage(JsonNode pack){
        var expectedCase=JSON.createObjectNode().put("id","P01").put("question",P01_QUESTION);
        // 哈希绑定外发许可；预算/载荷身份另行验证，禁止新jar消费旧5秒包或追加已成功的P02。
        if(!pack.path("endpoint").asText().equals(RuleAgentProvider.BEIJING.toString())||!pack.path("model").asText().equals("qwen-plus")||!pack.path("budgetRevision").asText().equals(BUDGET_REVISION)
            ||pack.path("maximumProviderCalls").asInt(-1)!=4||pack.path("perRunCalls").asInt(-1)!=DEFAULT_BUDGET.maxModelAttemptsPerRun()||pack.path("timeoutMillis").asInt(-1)!=DEFAULT_BUDGET.maxModelMillis()
            ||pack.path("runTimeoutMillis").asInt(-1)!=DEFAULT_BUDGET.maxRunMillis()||pack.path("maximumOutputTokens").asInt(-1)!=DEFAULT_BUDGET.maxOutputTokens()||pack.path("automaticRetries").asInt(-1)!=0
            ||!pack.path("systemPrompt").asText().equals(RuleAgentProvider.SYSTEM)||!pack.path("budget").equals(JSON.valueToTree(DEFAULT_BUDGET))||!pack.path("cases").isArray()||pack.path("cases").size()!=1||!pack.path("cases").get(0).equals(expectedCase))throw new IllegalArgumentException("请求包预算、问题或协议身份变化");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("只允许单一探针操作");
        if(args[0].equals("--CheckEnvironment")){System.out.println(JSON.writeValueAsString(environmentCheck(System.getenv())));return;}
        Path root=Path.of(required("ZHKU_RULE_AGENT_ROOT")).toRealPath();
        if(args[0].equals("--PrepareProbe")){prepare(root);return;}
        if(!args[0].equals("--Probe"))throw new IllegalArgumentException("未知探针操作");
        int maximum=(Integer)environmentCheck(System.getenv()).get("maximumProviderCalls");byte[] bytes=Files.readAllBytes(root.resolve(PACKAGE));
        if(!sha(bytes).equals(required("ZHKU_RULE_AGENT_PROBE_SHA256")))throw new IllegalArgumentException("请求包与授权指纹不同");
        JsonNode pack=JSON.readTree(bytes);validatePackage(pack);if(!pack.equals(packageNode(root)))throw new IllegalArgumentException("本机审核资产或请求定义已漂移");
        String url=required("ZHKU_DB_URL");var matcher=java.util.regex.Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$").matcher(url);
        if(!matcher.matches()||Integer.parseInt(matcher.group(1))==54329)throw new IllegalArgumentException("拒绝原库或非专用临时库");
        Path evidence=root.resolve(required("ZHKU_RULE_AGENT_EVIDENCE")).normalize();if(!evidence.startsWith(root.resolve("docs/evidence/rule-agent-provider"))||Files.exists(evidence))throw new IllegalArgumentException("证据目录越界或已存在");Files.createDirectories(evidence);
        var db=new DriverManagerDataSource(url,"zhkuagent",required("ZHKU_DB_PASSWORD"));Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
        Clock clock=Clock.systemUTC();var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());var traces=new ArrayList<RuleAgentProvider.Trace>();
        var executor=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),Thread.ofPlatform().name("zhku-rule-probe-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
        boolean passed=false,closed=false;int used=0;
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)){
            catalogue.installOnIsolatedDatabase(db);var settings=catalogue.settings();
            try(var factory=new RuleAgentOnlineFactory(RuleAgentProvider.Settings.environment(System.getenv()),()->System.getenv("DASHSCOPE_API_KEY"),(actor,task)->new RuleAgentEvidenceTools(actor,task,settings,()->{try{return RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));}catch(Exception invalid){throw new IllegalArgumentException("目录不可用");}},JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock),trace->{synchronized(traces){try{Files.writeString(evidence.resolve("provider-traces.jsonl"),JSON.writeValueAsString(trace)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);traces.add(trace);}catch(Exception failure){throw new IllegalStateException("调用审计未保存");}}},maximum,clock)){
                RuleAgentTaskStore.Factory recording=(actor,task)->{var actual=factory.open(actor,task);return new RuleAgentTaskStore.Work(){public RuleAgentRunner.Outcome run(com.zhku.agent.agent.runtime.AgentRunContext context,RuleAgentRunner.Input input){var output=actual.run(context,input);try{Files.write(evidence.resolve(context.runId()+"-outcome.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(output),StandardOpenOption.CREATE_NEW);}catch(Exception failure){throw new IllegalStateException("运行产物未保存");}return output;}public boolean revalidate(ResultEnvelope previous){return actual.revalidate(previous);}};};
                try(var store=new RuleAgentTaskStore(executor,recording,clock,RuleAgentTaskStore.DEFAULT_LIMITS)){
                    var outputs=new ArrayList<TaskSnapshot>();
                    for(JsonNode test:pack.path("cases")){
                        var accepted=store.create(owner,new CreateTask(test.path("question").asText(),UUID.randomUUID()));TaskSnapshot output=waitFor(store,owner,accepted.taskId());outputs.add(output);
                    }
                    boolean p01Passed=!outputs.isEmpty()&&outputs.get(0).taskStatus()==TaskStatus.RESOLVED&&outputs.get(0).result().items().getFirst().claims().stream().filter(c->c.factId().startsWith("T02/ARTICLE4:")).count()==2;
                    passed=p01Passed&&outputs.size()==1;
                    Files.write(evidence.resolve("task-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(outputs),StandardOpenOption.CREATE_NEW);
                }finally{used=maximum-factory.remainingCalls();factory.close();closed=factory.resourcesClosed();}
            }
        }finally{executor.shutdownNow();closed=closed&&executor.awaitTermination(3,TimeUnit.SECONDS);Files.write(evidence.resolve("probe-result.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("passed",passed&&closed,"resourcesClosed",closed,"allowanceConsumed",used,"maximumProviderCalls",maximum,"caseSelection","P01_ONLY","languageQualityNotScored",true,"budgetRevision",BUDGET_REVISION)),StandardOpenOption.CREATE_NEW);}
        if(!passed||!closed)throw new IllegalStateException("真实探针未达退出标准；不重试");
    }
    private static TaskSnapshot waitFor(RuleAgentTaskStore store,Owner owner,UUID task)throws Exception{long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(DEFAULT_BUDGET.maxRunMillis()+3000);TaskSnapshot result;while((result=store.snapshot(owner,task)).taskStatus()==TaskStatus.ACTIVE){if(System.nanoTime()>=deadline)throw new IllegalStateException("探针运行预算耗尽");Thread.sleep(20);}return result;}
    private static void prepare(Path root)throws Exception{
        var pack=packageNode(root);validatePackage(pack);Path path=root.resolve(PACKAGE);Files.createDirectories(path.getParent());byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(pack);Files.write(path,bytes,StandardOpenOption.CREATE_NEW);System.out.println("PROBE_PACKAGE_SHA256="+sha(bytes));
    }
    private static ObjectNode packageNode(Path root)throws Exception{
        // 构造审核目录只验证本机资产；不安装数据库、不创建模型客户端。
        try(var qualification=new Reviewed2025ControlledCatalogue(root)){
            var bundle=JSON.readTree(Files.readAllBytes(root.resolve("docs/product/rule-check-2025-verbatim-import-draft.json")));var semantics=JSON.readTree(Files.readAllBytes(root.resolve("docs/product/rule-check-2025-b02-semantic-review.json")));var overlay=JSON.readTree(Files.readAllBytes(root.resolve("docs/evidence/rule-agent-evidence/2026-10-13/t02-01/article4-overlay-draft.json")));
            var pack=JSON.createObjectNode();pack.put("purpose","T05_P01_BOUNDED_BUDGET_CORRECTION_NOT_LANGUAGE_QUALITY_EVAL");pack.put("endpoint",RuleAgentProvider.BEIJING.toString());pack.put("model","qwen-plus");pack.put("budgetRevision",BUDGET_REVISION);pack.set("budget",JSON.valueToTree(DEFAULT_BUDGET));pack.put("maximumProviderCalls",4);pack.put("perRunCalls",DEFAULT_BUDGET.maxModelAttemptsPerRun());pack.put("timeoutMillis",DEFAULT_BUDGET.maxModelMillis());pack.put("runTimeoutMillis",DEFAULT_BUDGET.maxRunMillis());pack.put("maximumOutputTokens",DEFAULT_BUDGET.maxOutputTokens());pack.put("automaticRetries",0);pack.put("referenceRevision","2025.9.2");pack.put("authorizationStatus","NOT_AUTHORIZED");pack.put("systemPrompt",RuleAgentProvider.SYSTEM);
            var cases=pack.putArray("cases");cases.addObject().put("id","P01").put("question",P01_QUESTION);
            var excerpts=pack.putArray("maximumReviewedExcerpts");for(var item:bundle.path("chunks")){var e=excerpts.addObject();e.put("article",item.path("article").asInt());e.put("content",item.path("content").asText());e.put("checksum",item.path("contentChecksum").asText());}
            var rules=pack.putArray("maximumReviewedRuleSemantics");for(var rule:semantics.path("rules")){var e=rules.addObject();e.put("article",rule.path("article").asInt());e.put("scope",rule.path("questionScopeDraft").asText());var facts=e.putArray("facts");for(var fact:rule.path("facts")){var f=facts.addObject();f.put("id",fact.path("id").asText());f.put("text",fact.path("text").asText());f.set("when",fact.path("when"));}e.set("conditions",rule.path("conditionsDraft"));}pack.set("article4ApprovedFacts",overlay.path("facts"));
            pack.put("dynamicFeedback","每轮仅发送实际候选UUID及摘要、已核验原文/事实/条件；范围限上述10条原文与审核说明。随机task/run身份只留本机，原文token、owner、凭证、个人记录、参考答案及整本PDF不外发。");
            return pack;
        }
    }
    private static String required(String name){var value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("缺少启动环境："+name);return value;}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
