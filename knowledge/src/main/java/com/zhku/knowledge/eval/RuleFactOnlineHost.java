package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.retrieval.*;
import com.zhku.knowledge.rulecheck.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** One frozen question, one real run, existing OnlineFactory/Runner/tools. No scoring or reference answers sent to model. */
public final class RuleFactOnlineHost {
    static ObjectNode definition(Path root,String caseId)throws Exception {
        return definition(root,caseId,RuleAgentProvider.QWEN4_MAX);
    }
    /** 单题探针沿用全量批次的模型绑定：包记录模型、Provider 按包构建、采集身份用 provider.model()。 */
    static ObjectNode definition(Path root,String caseId,String model)throws Exception {
        if(!RuleAgentProvider.MODEL.equals(model)&&!RuleAgentProvider.QWEN4_MAX.equals(model))
            throw new IllegalArgumentException("unsupported model for single-question probe");
        var frozen=load(root);var turn=frozen.turns(caseId).getFirst();
        var pack=RuleAgentOnlineProbeHost.controlledRequestDefinition(root);
        pack.put("model",model);
        pack.put("purpose","FROZEN_RULE_FACT_EXECUTION_M1_SINGLE_TURN");
        pack.put("executionPolicyRevision","FROZEN_RULE_FACT_SINGLE_TURN_02");
        pack.put("datasetSha256",frozen.sha256());
        pack.set("evaluationTurn",JSON.valueToTree(turn)); // local-only expectation; never passed to provider
        var cases=JSON.createArrayNode();cases.addObject().put("id",caseId).put("question",turn.question());pack.set("cases",cases);
        pack.put("maximumProviderCalls",DEFAULT_BUDGET.maxModelAttemptsPerRun());
        pack.put("maximumTaskRunsThisBatch",1);pack.put("ownedProcessMillis",130000);
        pack.put("remainingBudgetRule","One task/run; at most 4 decisions + 1 independent review; 110s run; no retries or continuation; max 1024 output tokens per call.");
        pack.put("authorizationStatus","NOT_AUTHORIZED");
        pack.put("retentionDays",30);
        pack.put("capturePolicy","LOCAL_CONTROLLED_ONLY; SOURCE_IDENTITIES_AND_PUBLISHED_ANSWER; NO_CAPABILITY_TOKENS; NO_REFERENCE_ANSWERS_SENT");
        pack.put("localExchangeRecording","DISABLED_FOR_SINGLE_TURN; NO_RAW_PROVIDER_REQUEST_OR_RESPONSE_ARCHIVE");
        pack.put("publicationDiagnosticPolicy","LOCAL_ONLY; FACT_NOT_RELEVANT AND VALIDATED_FINISH_EXCLUSION; CATALOGUE_FACT_IDS_AND_CAPABILITY, HOST_QUESTION_ID, ORDINAL_REQUIREMENTS_AND_VALIDATED_BINDINGS; NO_EXCERPTS_OR_MODEL_TEXT; CAPTURE_SHA_BOUND");
        pack.put("jarSha256",sha(Files.readAllBytes(root.resolve("knowledge/target/knowledge-0.1.0-rc.1-controlled.jar"))));
        pack.put("launcherSha256",sha(Files.readAllBytes(root.resolve("knowledge/scripts/start-rule-fact-eval.ps1"))));
        var sources=pack.putObject("sourceIdentity");
        try(var paths=Files.walk(root.resolve("knowledge/src/main/java"))) {
            for(var p:paths.filter(p->p.toString().endsWith(".java")).sorted().toList())
                sources.put(root.relativize(p).toString().replace('\\','/'),sha(Files.readAllBytes(p)));
        }
        return pack;
    }
    static Path packagePath(Path root,String path) {
        Path p=root.resolve(path).normalize();
        if(!p.startsWith(root.resolve("docs/evidence/rule-agent-provider")))throw new IllegalArgumentException("package outside evidence root");
        return p;
    }
    /** prepare root caseId packagePath | check root packagePath SHA | run root packagePath SHA outputDirectory */
    public static void main(String[] args)throws Exception {
        if(args.length<4)throw new IllegalArgumentException("prepare/check/run root selection package arguments required");
        Path root=Path.of(args[1]).toRealPath();
        if("prepare".equals(args[0])) {
            String model=args.length==5?args[4]:RuleAgentProvider.QWEN4_MAX;
            Path file=packagePath(root,args[3]);var pack=definition(root,args[2],model);
            byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(pack);
            Files.createDirectories(file.getParent());Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
            System.out.println("PACKAGE_SHA256="+sha(bytes));return;
        }
        Path file=packagePath(root,args[2]);byte[] bytes=Files.readAllBytes(file);
        if(!sha(bytes).equals(args[3]))throw new IllegalArgumentException("package SHA mismatch");
        var pack=JSON.readTree(bytes);String caseId=pack.path("evaluationTurn").path("caseId").asText();
        String model=pack.path("model").asText();
        if(!definition(root,caseId,model).equals(pack))throw new IllegalArgumentException("package/build/runtime assets drift");
        if("check".equals(args[0])){System.out.println("PASS ZERO_CALL_PACKAGE_CHECK "+sha(bytes));return;}
        if("assembly".equals(args[0])&&args.length==5){checkDatabase(root,caseId,packagePath(root,args[4]));return;}
        if(!"run".equals(args[0])||args.length!=5)throw new IllegalArgumentException("invalid command");
        if(!args[3].equals(System.getenv("ZHKU_RULE_AGENT_AUTHORIZATION_ID")))throw new IllegalArgumentException("authorization not bound to package");
        // The launcher must supply a package-specific, still-valid approval receipt and reserve it once.
        Path approval=file.resolveSibling("execution-approval.json");var approved=JSON.readTree(Files.readAllBytes(approval));
        if(!"APPROVED".equals(approved.path("status").asText()) || !args[3].equals(approved.path("packageSha256").asText())
                || approved.path("maximumCalls").asInt()!=DEFAULT_BUDGET.maxModelAttemptsPerRun()
                || !Instant.now().isBefore(Instant.parse(approved.path("validUntil").asText())))
            throw new IllegalArgumentException("missing valid package-specific approval");
        // 模型绑定随审批走（镜像 RuleFactBatchHost）：qwen4-max 或声明了 model 的审批必须与包一致。
        if((RuleAgentProvider.QWEN4_MAX.equals(model)||approved.has("model"))&&!model.equals(approved.path("model").asText()))
            throw new IllegalArgumentException("single approval model mismatch");
        String url=dedicatedDatabaseUrl(System.getenv("ZHKU_DB_URL"));
        Path out=packagePath(root,args[4]);if(Files.exists(out))throw new FileAlreadyExistsException(out.toString());
        // Atomic reservation is permanent even on failure; no reuse of remaining calls.
        Files.writeString(file.resolveSibling("execution-reserved.json"),JSON.writeValueAsString(Map.of("packageSha256",args[3],"startedAt",Instant.now().toString())),StandardOpenOption.CREATE_NEW);
        Files.createDirectories(out);
        var db=new DriverManagerDataSource(url,"zhkuagent",required("ZHKU_DB_PASSWORD"));
        Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
        Clock clock=Clock.systemUTC();var frozen=load(root);var turn=frozen.turns(caseId).getFirst();
        var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
        var executor=Executors.newFixedThreadPool(1);var outcomes=new ConcurrentHashMap<UUID,RuleAgentRunner.Outcome>();
        var retrieval=Collections.synchronizedList(new ArrayList<RuleAgentEvidenceTools.SearchSources>());
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)) {
            catalogue.installOnIsolatedDatabase(db);var settings=catalogue.settings();
            var environment=RuleAgentProvider.Settings.environment(System.getenv());
            // 模型从已校验 SHA 且获批的包读取，防止单题探针的配置与实际请求或留痕漂移（与批量宿主同一绑定）。
            var provider=new RuleAgentProvider.Settings(environment.endpoint(),model,environment.authorizationId());
            try(var factory=new RuleAgentOnlineFactory(provider,()->System.getenv("DASHSCOPE_API_KEY"),
                    (actor,task)->new RuleAgentEvidenceTools(actor,task,settings,()->{
                        try{return RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));}catch(Exception e){throw new IllegalStateException(e);}},
                            JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
                            new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),
                            new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock).withSearchAudit(retrieval::add),
                    trace->{try{Files.writeString(out.resolve("provider-traces.jsonl"),JSON.writeValueAsString(trace)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException(e);}},
                    DEFAULT_BUDGET.maxModelAttemptsPerRun(),clock)) {
                RuleAgentTaskStore.Factory recording=(actor,task)->{
                    var work=factory.open(actor,task);return new RuleAgentTaskStore.Work(){
                        public RuleAgentRunner.Outcome run(com.zhku.agent.agent.runtime.AgentRunContext context,RuleAgentRunner.Input input){
                            var outcome=work.run(context,input);outcomes.put(context.runId(),outcome);return outcome;}
                        public boolean revalidate(ResultEnvelope prior){return work.revalidate(prior);}};};
                try(var store=new RuleAgentTaskStore(executor,recording,clock,RuleAgentTaskStore.DEFAULT_LIMITS)) {
                    var accepted=store.create(owner,new CreateTask(turn.question(),UUID.randomUUID()));
                    long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(DEFAULT_BUDGET.maxRunMillis()+3000);
                    TaskSnapshot published;
                    while((published=store.snapshot(owner,accepted.taskId())).taskStatus()==TaskStatus.ACTIVE){
                        if(System.nanoTime()>=deadline)throw new IllegalStateException("run deadline exceeded");Thread.sleep(20);}
                    var identity=new RuleFactExecutionEvaluation.Identity(caseId,published.taskId(),published.runId(),"v1.0",frozen.sha256(),provider.model(),args[3]);
                    var outcome=Objects.requireNonNull(outcomes.get(published.runId()));
                    var captured=RuleFactBoundEvaluation.capture(frozen,turn,identity,published,outcome,List.copyOf(retrieval));
                    Path capture=out.resolve("capture.json");RuleFactBoundEvaluation.write(capture,captured);
                    var annotation=new RuleFactBoundEvaluation.Annotation(sha(Files.readAllBytes(capture)),
                            sha(captured.execution().answer().getBytes(java.nio.charset.StandardCharsets.UTF_8)),false,Set.of(),false,Map.of(),Map.of());
                    Files.write(out.resolve("annotation-pending.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(annotation),StandardOpenOption.CREATE_NEW);
                    Files.write(out.resolve("report-unreviewed.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                            RuleFactBoundEvaluation.report(frozen,captured,annotation.captureSha256(),annotation)),StandardOpenOption.CREATE_NEW);
                    Files.write(out.resolve("publication-diagnostics.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                            publicationDiagnostics(identity,annotation.captureSha256(),outcome.events())),StandardOpenOption.CREATE_NEW);
                } finally {
                    Files.writeString(out.resolve("calls.json"),JSON.writeValueAsString(Map.of("consumed",DEFAULT_BUDGET.maxModelAttemptsPerRun()-factory.remainingCalls(),"remainingAuthorizedCalls",0)),StandardOpenOption.CREATE_NEW);
                }
            }
        } finally {executor.shutdownNow();if(!executor.awaitTermination(3,TimeUnit.SECONDS))throw new IllegalStateException("executor not closed");}
    }
    static Map<String,Object> publicationDiagnostics(RuleFactExecutionEvaluation.Identity identity,String captureSha,
                                                     List<RuleAgentRunner.Event> events) {
        var entries=events.stream().filter(event->event.relevanceDiagnostic()!=null&&event.publicationReason()!=null)
                .map(event->Map.of("attempt",event.attempt(),"action",event.action(),"code",event.publicationReason(),
                        "detail",event.relevanceDiagnostic())).toList();
        var exclusions=events.stream().filter(event->event.relevanceDiagnostic()!=null&&event.publicationReason()==null)
                .map(event->Map.of("attempt",event.attempt(),"action",event.action(),"detail",event.relevanceDiagnostic())).toList();
        // 核验摘要只留在本地 sidecar；共享事件JSON不含需求/绑定细节。
        var reviews=events.stream().filter(event->event.review()!=null)
                .map(event->Map.of("attempt",event.attempt(),"action",event.action(),"assessment",event.review())).toList();
        return Map.of("identity",identity,"captureSha256",captureSha,"relevanceRejections",entries,
                "relevanceExclusions",exclusions,"semanticReviews",reviews);
    }
    static String dedicatedDatabaseUrl(String url) {
        // Reuse the reviewed installer's existing dedicated-database allowlist. Container and port are isolated by launcher.
        var matcher=java.util.regex.Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$").matcher(url==null?"":url);
        if(!matcher.matches())throw new IllegalArgumentException("dedicated temporary evaluation database required");
        int port=Integer.parseInt(matcher.group(1));
        if(port<1024||port>65535||port==54329)throw new IllegalArgumentException("shared/invalid database port");
        return url;
    }
    static void checkDatabase(Path root,String caseId,Path out)throws Exception {
        if(Files.exists(out))throw new FileAlreadyExistsException(out.toString());
        var db=new DriverManagerDataSource(dedicatedDatabaseUrl(System.getenv("ZHKU_DB_URL")),"zhkuagent",required("ZHKU_DB_PASSWORD"));
        Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
        var frozen=load(root);var turn=frozen.turns(caseId).getFirst();Clock clock=Clock.systemUTC();
        var sources=new ArrayList<RuleAgentEvidenceTools.SearchSources>();
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)) {
            catalogue.installOnIsolatedDatabase(db);var settings=catalogue.settings();
            var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());UUID task=UUID.randomUUID(),run=UUID.randomUUID();
            var tools=new RuleAgentEvidenceTools(owner,task,settings,()->{
                try{return RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));}catch(Exception ex){throw new IllegalStateException(ex);}},
                    JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
                    new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),
                    new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock).withSearchAudit(sources::add);
            var found=tools.search(owner,task,run,1,new Search("q1",turn.question()));
            if(found.status()!=ObservationStatus.SUCCEEDED||sources.size()!=1)throw new IllegalStateException("real search/provenance unavailable");
            var required=frozen.dataset().caseById(caseId).requiredLocators();var locators=new HashSet<String>();var selected=new ArrayList<UUID>();
            for(var candidate:sources.getFirst().candidates()) {
                String locator=frozen.locator(candidate.revision(),candidate.checksum());
                if(locator!=null){locators.add(locator);if(required.contains(locator))selected.add(candidate.handle());}
            }
            if(!locators.containsAll(required)||selected.isEmpty()||selected.size()>DEFAULT_BUDGET.maxReadHandles())throw new IllegalStateException("required retrieval not bound");
            var read=tools.read(owner,task,run,2,new Read("q1",selected));
            if(read.observation().status()!=ObservationStatus.SUCCEEDED||read.facts().isEmpty())throw new IllegalStateException("verified read unavailable");
            Files.createDirectories(out);
            Files.write(out.resolve("assembly.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of(
                    "status","PASS","providerCalls",0,"caseId",caseId,"taskId",task,"runId",run,"retrieval",sources,
                    "retrievedLocators",locators,"verifiedFactIds",read.observation().factIds(),"verifiedEvidenceCount",read.observation().evidence().size())),StandardOpenOption.CREATE_NEW);
        }
        System.out.println("PASS DEDICATED_DATABASE_SEARCH_READ_ASSEMBLY providerCalls=0");
    }
    private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("missing "+name);return value;}
}
