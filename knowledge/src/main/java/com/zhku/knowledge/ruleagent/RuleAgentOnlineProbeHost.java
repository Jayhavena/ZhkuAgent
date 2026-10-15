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
    private static final String PACKAGE="docs/evidence/rule-agent-provider/2026-10-15/t05-38-request/request-package.json";
    private static final String P01_QUESTION="专业选修课和公共选修课在修读安排上有什么不同？";
    private static final int MAXIMUM=DEFAULT_BUDGET.maxModelAttemptsPerRun();
    private static JsonNode expectedCases(){
        var cases=JSON.createArrayNode();
        cases.addObject().put("id","P01").put("question",P01_QUESTION);
        return cases;
    }
    public static Map<String,Object> environmentCheck(Map<String,String> env){
        var settings=RuleAgentProvider.Settings.environment(env);String sha=env.get("ZHKU_RULE_AGENT_PROBE_SHA256");
        if(sha==null||!sha.matches("[a-f0-9]{64}")||!settings.authorizationId().equals(sha))throw new IllegalArgumentException("探针未绑定具体请求包授权");
        String selected=env.get("ZHKU_RULE_AGENT_PROBE_CASE");
        if(!"P01_TRANSPORT_CLOSEOUT_01".equals(selected))throw new IllegalArgumentException("本批只允许P01诊断候选（统一真实HTTP诊断与截止时间，模型/协议/预算不变），不接受旧批次或P02");
        return Map.of("mode","ONLINE","endpoint",settings.endpoint().toString(),"model",settings.model(),"maximumProviderCalls",MAXIMUM,"providerCalls",0,"databaseOperations",0,"builds",0,"budgetRevision",BUDGET_REVISION,"timeoutMillis",DEFAULT_BUDGET.maxModelMillis(),"runTimeoutMillis",DEFAULT_BUDGET.maxRunMillis());
    }
    static void validatePackage(JsonNode pack){
        // 当前批次只允许一次P01；旧12次批次与P02必须在建库和创建客户端前拒绝。
        if(!pack.path("executionPolicyRevision").asText().equals("STATE_AWARE_TRANSPORT_CLOSEOUT_01")
            ||!pack.path("protocolRevision").asText().equals(RuleAgentProtocol.REVISION)
            ||!pack.path("actionContract").equals(RuleAgentProtocol.description())
            ||!pack.path("actionSchema").equals(RuleAgentProtocol.decisionSchema(List.of()))
            ||!pack.path("remainingBudgetMaximum").equals(RuleAgentProtocol.remainingBudget(RulePublicationContract.MAX_DECISION_CALLS,DEFAULT_BUDGET.maxToolStepsPerRun()))
            ||!pack.path("publicationReviewProtocolRevision").asText().equals(RulePublicationContract.REVISION)
            ||!pack.path("publicationReviewContract").equals(RulePublicationProtocol.description())
            ||!pack.path("publicationReviewPolicy").equals(JSON.valueToTree(RulePublicationContract.policy()))
            ||!pack.path("reviewSystemPrompt").asText().equals(RuleAgentProvider.REVIEW_SYSTEM)
            ||!pack.path("endpoint").asText().equals(RuleAgentProvider.BEIJING.toString())||!pack.path("model").asText().equals(RuleAgentProvider.MODEL)||!pack.path("budgetRevision").asText().equals(BUDGET_REVISION)
            ||pack.path("maximumProviderCalls").asInt(-1)!=MAXIMUM||pack.path("perRunCalls").asInt(-1)!=DEFAULT_BUDGET.maxModelAttemptsPerRun()||pack.path("timeoutMillis").asInt(-1)!=DEFAULT_BUDGET.maxModelMillis()
            ||pack.path("runTimeoutMillis").asInt(-1)!=DEFAULT_BUDGET.maxRunMillis()||pack.path("maximumOutputTokens").asInt(-1)!=DEFAULT_BUDGET.maxOutputTokens()||pack.path("automaticRetries").asInt(-1)!=0
            ||!pack.path("systemPrompt").asText().equals(RuleAgentProvider.SYSTEM)||!pack.path("budget").equals(JSON.valueToTree(DEFAULT_BUDGET))||!pack.path("cases").equals(expectedCases()))throw new IllegalArgumentException("请求包预算、问题或协议身份变化");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("只允许单一探针操作");
        if(args[0].equals("--CheckEnvironment")){System.out.println(JSON.writeValueAsString(environmentCheck(System.getenv())));return;}
        Path root=Path.of(required("ZHKU_RULE_AGENT_ROOT")).toRealPath();
        if(args[0].equals("--CheckRuntimeAssets")) {
            try(var catalogue=new Reviewed2025ControlledCatalogue(root)) {
                System.out.println(JSON.writeValueAsString(Map.of("passed",true,"providerCalls",0,"databaseOperations",0,"runtimeAssets",catalogue.runtimeAssetManifest())));
            }return;
        }
        if(args[0].equals("--CheckRuntimeAssembly")){checkRuntimeAssembly(root);return;}
        if(args[0].equals("--PrepareProbe")){prepare(root);return;}
        boolean checkOnly=args[0].equals("--CheckPackage");
        if(!checkOnly&&!args[0].equals("--Probe"))throw new IllegalArgumentException("未知探针操作");
        int maximum=checkOnly?MAXIMUM:(Integer)environmentCheck(System.getenv()).get("maximumProviderCalls");
        byte[] bytes=Files.readAllBytes(root.resolve(PACKAGE));
        if(!sha(bytes).equals(required("ZHKU_RULE_AGENT_PROBE_SHA256")))throw new IllegalArgumentException("请求包与授权指纹不同");
        JsonNode pack=JSON.readTree(bytes);validatePackage(pack);if(!pack.equals(packageNode(root)))throw new IllegalArgumentException("本机审核资产或请求定义已漂移");
        if(checkOnly) {
            // 只核对包/本机审核资产；在构造数据源与模型工厂前退出，不要求或读取实际凭证。
            System.out.println(JSON.writeValueAsString(Map.of("mode","PACKAGE_CHECK_ONLY","passed",true,"packageSha256",sha(bytes),"protocolRevision",RuleAgentProtocol.REVISION,"maximumProviderCalls",maximum,"providerCalls",0,"databaseOperations",0)));
            return;
        }
        String url=required("ZHKU_DB_URL");var matcher=java.util.regex.Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$").matcher(url);
        if(!matcher.matches()||Integer.parseInt(matcher.group(1))==54329)throw new IllegalArgumentException("拒绝原库或非专用临时库");
        Path evidence=root.resolve(required("ZHKU_RULE_AGENT_EVIDENCE")).normalize();if(!evidence.startsWith(root.resolve("docs/evidence/rule-agent-provider"))||Files.exists(evidence))throw new IllegalArgumentException("证据目录越界或已存在");Files.createDirectories(evidence);
        var db=new DriverManagerDataSource(url,"zhkuagent",required("ZHKU_DB_PASSWORD"));Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
        Clock clock=Clock.systemUTC();var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());var traces=new ArrayList<RuleAgentProvider.Trace>();
        var executor=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),Thread.ofPlatform().name("zhku-rule-probe-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
        boolean passed=false,closed=false;int used=0;var outcomes=new ConcurrentHashMap<UUID,RuleAgentRunner.Outcome>();
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)){
            catalogue.installOnIsolatedDatabase(db);var settings=catalogue.settings();
            try(var factory=new RuleAgentOnlineFactory(RuleAgentProvider.Settings.environment(System.getenv()),()->System.getenv("DASHSCOPE_API_KEY"),(actor,task)->new RuleAgentEvidenceTools(actor,task,settings,()->{try{return RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));}catch(Exception invalid){throw new IllegalArgumentException("目录不可用");}},JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock),trace->{synchronized(traces){try{Files.writeString(evidence.resolve("provider-traces.jsonl"),JSON.writeValueAsString(trace)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);traces.add(trace);}catch(Exception failure){throw new IllegalStateException("调用审计未保存");}}},maximum,clock)){
                RuleAgentTaskStore.Factory recording=(actor,task)->{var actual=factory.open(actor,task);return new RuleAgentTaskStore.Work(){public RuleAgentRunner.Outcome run(com.zhku.agent.agent.runtime.AgentRunContext context,RuleAgentRunner.Input input){var output=actual.run(context,input);outcomes.put(context.runId(),output);try{Files.write(evidence.resolve(context.runId()+"-outcome.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(output),StandardOpenOption.CREATE_NEW);}catch(Exception failure){throw new IllegalStateException("运行产物未保存");}return output;}public boolean revalidate(ResultEnvelope previous){return actual.revalidate(previous);}};};
                try(var store=new RuleAgentTaskStore(executor,recording,clock,RuleAgentTaskStore.DEFAULT_LIMITS)){
                    var outputs=new ArrayList<TaskSnapshot>();
                    for(JsonNode test:pack.path("cases")){
                        var accepted=store.create(owner,new CreateTask(test.path("question").asText(),UUID.randomUUID()));TaskSnapshot output=waitFor(store,owner,accepted.taskId());outputs.add(output);
                        if(test.path("id").asText().equals("P01")){
                            if(!p01Passed(outcomes.get(output.runId())))throw new IllegalStateException("P01未达标，停止本批不追加调用");
                        }
                    }
                    boolean p01Passed=!outputs.isEmpty()&&p01Passed(outcomes.get(outputs.get(0).runId()));
                    passed=p01Passed&&outputs.size()==1;
                    Files.write(evidence.resolve("task-results.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(outputs),StandardOpenOption.CREATE_NEW);
                }finally{used=maximum-factory.remainingCalls();factory.close();closed=factory.resourcesClosed();}
            }
        }finally{executor.shutdownNow();closed=closed&&executor.awaitTermination(3,TimeUnit.SECONDS);Files.write(evidence.resolve("probe-result.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("passed",passed&&closed,"resourcesClosed",closed,"allowanceConsumed",used,"maximumProviderCalls",maximum,"caseSelection","P01_TRANSPORT_CLOSEOUT_01","languageQualityNotScored",true,"budgetRevision",BUDGET_REVISION)),StandardOpenOption.CREATE_NEW);}
        if(!passed||!closed)throw new IllegalStateException("真实探针未达退出标准；不重试");
    }
    /** 阶段到达不能代替整链成功；必须有同run核验通过、完整发布和准确事实集合。 */
    static boolean p01Passed(RuleAgentRunner.Outcome outcome){
        if(outcome==null)return false;var s=outcome.snapshot();
        var required=Set.of("T02/ARTICLE4:professional-elective-study-arrangement","T02/ARTICLE4:public-elective-self-selection");
        return s.taskStatus()==TaskStatus.RESOLVED&&s.runState()==com.zhku.agent.agent.runtime.AgentRunState.SUCCEEDED&&s.terminated()
            &&s.execution().answerSource()==AnswerSource.LIVE_AGENT&&s.execution().providerResponseReceived()&&s.result().coverage()==Coverage.COMPLETE
            &&s.result().items().size()==1&&s.result().items().getFirst().issues().isEmpty()
            &&new HashSet<>(s.result().items().getFirst().claims().stream().map(Claim::factId).toList()).equals(required)
            &&s.result().items().getFirst().claims().size()==2&&!s.result().evidence().isEmpty()&&!s.result().evidenceRelations().isEmpty()
            &&outcome.events().stream().anyMatch(e->e.action().equals("SEARCH_REVIEWED_RULES")&&e.observation()!=null&&e.observation().status()==ObservationStatus.SUCCEEDED)
            &&outcome.events().stream().anyMatch(e->e.action().equals("READ_VERIFIED_RULE")&&e.observation()!=null&&e.observation().dataLevel()==DataLevel.VERIFIED_FACT)
            &&outcome.events().stream().anyMatch(e->e.action().equals("FINISH_CHECK")&&e.reason()==null)
            &&outcome.events().stream().anyMatch(e->e.action().equals("PUBLICATION_REVIEW")&&e.reason()==null&&e.publicationReason()==null);
    }
    private static TaskSnapshot waitFor(RuleAgentTaskStore store,Owner owner,UUID task)throws Exception{long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(DEFAULT_BUDGET.maxRunMillis()+3000);TaskSnapshot result;while((result=store.snapshot(owner,task)).taskStatus()==TaskStatus.ACTIVE){if(System.nanoTime()>=deadline)throw new IllegalStateException("探针运行预算耗尽");Thread.sleep(20);}return result;}
    private static void prepare(Path root)throws Exception{
        var pack=packageNode(root);validatePackage(pack);Path path=root.resolve(PACKAGE);Files.createDirectories(path.getParent());byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(pack);Files.write(path,bytes,StandardOpenOption.CREATE_NEW);System.out.println("PROBE_PACKAGE_SHA256="+sha(bytes));
    }
    private static ObjectNode packageNode(Path root)throws Exception{
        // 构造审核目录只验证本机资产；不安装数据库、不创建模型客户端。
        try(var qualification=new Reviewed2025ControlledCatalogue(root)){
            var bundle=JSON.readTree(Files.readAllBytes(root.resolve("docs/product/rule-check-2025-verbatim-import-draft.json")));var semantics=JSON.readTree(Files.readAllBytes(root.resolve("docs/product/rule-check-2025-b02-semantic-review.json")));var overlay=JSON.readTree(Files.readAllBytes(root.resolve("docs/evidence/rule-agent-evidence/2026-10-13/t02-01/article4-overlay-draft.json")));
            var pack=JSON.createObjectNode();
            pack.set("runtimeAssets",JSON.valueToTree(qualification.runtimeAssetManifest()));
            pack.put("purpose","T05_P01_READ_REFERENCE_HARDENING_FULL_FLOW_NOT_PRODUCT_QUALITY_EVAL");
            pack.put("protocolRevision",RuleAgentProtocol.REVISION);
            pack.set("actionContract",RuleAgentProtocol.description());
            // 决策侧实际下发的机器可读动作定义；候选枚举按当次集合注入，这里记录静态部分。
            pack.set("actionSchema",RuleAgentProtocol.decisionSchema(List.of()));
            pack.put("actionSchemaNote","READ_VERIFIED_RULE.handles 的 items.enum 与 currentCandidates 编号同源，按每次检索的完整候选集合注入，不预置也不截断；minItems/maxItems 取自领域预算。");
            pack.set("remainingBudgetMaximum",RuleAgentProtocol.remainingBudget(RulePublicationContract.MAX_DECISION_CALLS,DEFAULT_BUDGET.maxToolStepsPerRun()));
            pack.put("publicationReviewProtocolRevision",RulePublicationContract.REVISION);
            pack.set("publicationReviewContract",RulePublicationProtocol.description());
            pack.set("publicationReviewPolicy",JSON.valueToTree(RulePublicationContract.policy()));
            pack.put("reviewSystemPrompt",RuleAgentProvider.REVIEW_SYSTEM);
            pack.put("remainingBudgetRule","每run最多4次决策加1次独立核验，共5次/110秒；单次20秒/1024输出token，无自动重试。保留核验20秒和发布5000ms，状态不允许的探索动作不执行。新检索使旧编号失效。");
            pack.put("endpoint",RuleAgentProvider.BEIJING.toString());
            pack.put("model",RuleAgentProvider.MODEL);
            pack.put("budgetRevision",BUDGET_REVISION);
            pack.set("budget",JSON.valueToTree(DEFAULT_BUDGET));
            pack.put("maximumProviderCalls",MAXIMUM);
            pack.put("perRunCalls",DEFAULT_BUDGET.maxModelAttemptsPerRun());
            pack.put("timeoutMillis",DEFAULT_BUDGET.maxModelMillis());
            pack.put("runTimeoutMillis",DEFAULT_BUDGET.maxRunMillis());
            pack.put("maximumOutputTokens",DEFAULT_BUDGET.maxOutputTokens());
            pack.put("automaticRetries",0);
            pack.put("executionPolicyRevision","STATE_AWARE_TRANSPORT_CLOSEOUT_01");
            pack.put("monetaryApprovalRequired",true);
            pack.put("publicationReserveMillis",5000);
            pack.put("readStateFeedback","完整候选均保留并标记alreadyRead；反馈证据剩余份数/字节、run剩余时间、核验预留；不按模型自报完整发布。");
            pack.put("referenceRevision","2025.9.2");
            pack.put("authorizationStatus","NOT_AUTHORIZED");
            pack.put("systemPrompt",RuleAgentProvider.SYSTEM);
            pack.set("cases",expectedCases());
            var excerpts=pack.putArray("maximumReviewedExcerpts");for(var item:bundle.path("chunks")){var e=excerpts.addObject();e.put("article",item.path("article").asInt());e.put("content",item.path("content").asText());e.put("checksum",item.path("contentChecksum").asText());}
            var rules=pack.putArray("maximumReviewedRuleSemantics");for(var rule:semantics.path("rules")){var e=rules.addObject();e.put("article",rule.path("article").asInt());e.put("scope",rule.path("questionScopeDraft").asText());var facts=e.putArray("facts");for(var fact:rule.path("facts")){var f=facts.addObject();f.put("id",fact.path("id").asText());f.put("text",fact.path("text").asText());f.set("when",fact.path("when"));}e.set("conditions",rule.path("conditionsDraft"));}pack.set("article4ApprovedFacts",overlay.path("facts"));
            // 运行目录只有安装后才有绑定UUID；零调用准备阶段只绑定能力权威版本/源码，不初始化数据库。
            pack.put("factCapabilityVersion",RuleFactCapabilityCatalogue.VERSION);
            pack.put("factCapabilityAuthoritySha256",sha(Files.readAllBytes(root.resolve("knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleFactCapabilityCatalogue.java"))));
            pack.put("dynamicFeedback","完整当前候选摘要、c编号、宿主集合代号及与编号同源的工具枚举；实际UUID映射仅留宿主。已读取的审核原文、事实、条件及独立核验能力/来源指纹不变。不发送owner/session/token/凭证/参考答案/整本PDF。");
            // 新候选只修正实际交换诊断和deadline；旧申请身份失效，业务合同和全部候选保持不变。
            pack.put("executionReadiness","PACKAGE_ONLY; one actual HTTP exchange with shared deadline and safe diagnostics; old batch and policy rejected; require pinned source/jar/package and new explicit authorization before any call");
            return pack;
        }
    }
    /** 仅临时库装配验收；本入口不构造Provider、任务或发布核验，不消耗在线额度。 */
    private static void checkRuntimeAssembly(Path root) throws Exception {
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)) {
            var manifest=catalogue.runtimeAssetManifest();
            String url=required("ZHKU_DB_URL");
            var matcher=java.util.regex.Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$").matcher(url);
            if(!matcher.matches()||Integer.parseInt(matcher.group(1))==54329)throw new IllegalArgumentException("拒绝原库或非专用临时库");
            var db=new DriverManagerDataSource(url,"zhkuagent",required("ZHKU_DB_PASSWORD"));
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            int entries;
            try {
                catalogue.installOnIsolatedDatabase(db);
                entries=RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root)).size();
                if(entries!=17)throw new IllegalStateException("运行目录不完整");
                var settings=catalogue.settings();
                var retriever=JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(Clock.systemUTC()),settings.profileId(),settings.requiredScope(),settings.chunkingVersion());
                var candidates=retriever.retrieve(P01_QUESTION,10);
                if(candidates.isEmpty())throw new IllegalStateException("词法检索无结果");
                var reader=new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope());
                for(var candidate:candidates)if(reader.read(candidate.sourceId(),candidate.chunkId()).isEmpty())throw new IllegalStateException("检索结果未能回读");
            } finally {catalogue.close();}
            try(var connection=db.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT (SELECT count(*) FROM knowledge_source)+(SELECT count(*) FROM knowledge_chunk)+(SELECT count(*) FROM embedding_profile)+(SELECT count(*) FROM retrieval_index_entry)+(SELECT count(*) FROM retrieval_index_run)+(SELECT count(*) FROM citation_audit)")) {
                rows.next();if(rows.getLong(1)!=0)throw new IllegalStateException("临时资料未清理");
            }
            System.out.println(JSON.writeValueAsString(Map.of("passed",true,"mode","LOCAL_RUNTIME_ASSEMBLY_ONLY","catalogueEntries",entries,"providerCalls",0,"resourcesClosed",true,"runtimeAssets",manifest)));
        }
    }
    private static String required(String name){var value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("缺少启动环境："+name);return value;}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
