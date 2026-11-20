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
import java.util.concurrent.atomic.AtomicInteger;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** Sequential frozen subset/full-set orchestration. Each case owns its planned turns' allowance. */
public final class RuleFactBatchHost {
    static ObjectNode definition(Path root)throws Exception {
        return definition(root,false);
    }
    static ObjectNode definition(Path root,boolean full)throws Exception {
        return definition(root,full?RuleFactBatchPlan.Selection.FULL_20:RuleFactBatchPlan.Selection.SUBSET_10);
    }
    static ObjectNode definition(Path root,RuleFactBatchPlan.Selection selection)throws Exception {
        return definition(root,selection,RuleAgentProvider.MODEL);
    }
    static ObjectNode definition(Path root,RuleFactBatchPlan.Selection selection,String model)throws Exception {
        boolean full=selection==RuleFactBatchPlan.Selection.FULL_20;
        if(!RuleAgentProvider.MODEL.equals(model)&&!(full&&RuleAgentProvider.QWEN4_MAX.equals(model)))
            throw new IllegalArgumentException("unsupported model for batch selection");
        var plan=RuleFactBatchPlan.load(root,selection);var pack=RuleFactOnlineHost.definition(root,"RFE-001");
        pack.put("model",model);
        pack.remove("evaluationTurn");pack.put("purpose","FROZEN_M2_TEN_CASE_QUALITY_BASELINE");
        pack.put("executionPolicyRevision","FROZEN_TEN_CASE_BATCH_01");pack.set("batchPlan",JSON.valueToTree(plan));
        var cases=pack.putArray("cases");for(var c:plan.cases())cases.addObject().put("id",c.caseId()).put("question",c.question()).put("clarificationReply",c.clarificationReply());
        pack.put("maximumProviderCalls",plan.maximumCalls());pack.put("maximumTasksThisBatch",plan.maximumTasks());
        pack.put("maximumTaskRunsThisBatch",plan.maximumRuns());pack.put("ownedProcessMillis",plan.ownedProcessMillis());
        pack.put("remainingBudgetRule","Ten fixed tasks sequentially; five calls/run, one run per case except RFE-013 ASK + predeclared PUBLIC continuation; at most 11 runs/55 calls; no retry, replacement or quota reuse. Per-run time/output limits unchanged.");
        pack.put("failurePolicy","Retain every selected case; case failures continue; MODEL_AUTH_FAILURE, MODEL_CONNECTION_FAILURE, MODEL_UNAVAILABLE, batch deadline or unclosed resources stop remaining cases. Missing cases retain harness causes.");
        if(full){pack.put("purpose","FROZEN_M3_FULL_DEVELOPMENT_BASELINE");pack.put("executionPolicyRevision","FROZEN_FULL_TWENTY_CASE_BATCH_01");
            pack.put("remainingBudgetRule","All 20 frozen cases, one trial each; RFE-013 ASK plus predeclared PUBLIC continuation. At most 21 runs/105 calls, five calls per run. No repeats, retries, replacement or quota reuse.");}
        if(selection==RuleFactBatchPlan.Selection.REPAIR_9){
            pack.put("purpose","FROZEN_M3_REPAIR_REPRESENTATIVE_CHECK");
            pack.put("executionPolicyRevision","FROZEN_REPAIR_NINE_CASE_BATCH_01");
            pack.put("remainingBudgetRule","Nine fixed cases, 10 runs/50 calls maximum; RFE-013 continues only with declared PUBLIC. No repeats, retries, replacement or quota reuse.");
        }
        pack.put("repetitions",1);
        pack.put("repeatPolicy","No repeat trials in this package; a separate package/quota/approval is required after baseline review.");
        pack.put("localExchangeRecording","DISABLED_FOR_BATCH; provider metadata, actions, observations, publication issues and published snapshots retained; no raw provider request/response archive");
        pack.put("supervisorSha256",sha(Files.readAllBytes(root.resolve("knowledge/scripts/Invoke-ZhkuEvaluationProcess.ps1"))));
        pack.set("baselineIdentity",RuleFactBaselineIdentity.create(root,pack,full));
        // Compare the same JSON representation that will be frozen on disk (including numeric node types).
        return (ObjectNode)JSON.readTree(JSON.writeValueAsBytes(pack));
    }
    static void validateApproval(com.fasterxml.jackson.databind.JsonNode approval,String sha,RuleFactBatchPlan.Plan plan,Instant now) {
        if(!"APPROVED".equals(approval.path("status").asText())||!sha.equals(approval.path("packageSha256").asText())
                ||approval.path("maximumCalls").asInt()!=plan.maximumCalls()||approval.path("maximumTasks").asInt()!=plan.maximumTasks()
                ||approval.path("maximumRuns").asInt()!=plan.maximumRuns()||approval.path("automaticRetries").asInt(-1)!=0
                ||!now.plusMillis(plan.ownedProcessMillis()).isBefore(Instant.parse(approval.path("validUntil").asText())))
            throw new IllegalArgumentException("batch approval identity/quota/validity mismatch");
        for(var limit:Map.of("maximumCallsPerRun",DEFAULT_BUDGET.maxModelAttemptsPerRun(),"maximumOutputTokensPerCall",DEFAULT_BUDGET.maxOutputTokens(),"maximumCallMillis",DEFAULT_BUDGET.maxModelMillis(),"runTimeoutMillis",DEFAULT_BUDGET.maxRunMillis()).entrySet())
            if(approval.has(limit.getKey())&&approval.path(limit.getKey()).asLong()!=limit.getValue().longValue())throw new IllegalArgumentException("approval per-run limit mismatch");
        if(approval.has("dedicatedTemporaryDatabaseOnly")&&!approval.path("dedicatedTemporaryDatabaseOnly").asBoolean()||approval.has("clarificationReply")&&!"PUBLIC".equals(approval.path("clarificationReply").asText()))throw new IllegalArgumentException("approval isolation/continuation mismatch");
        if(approval.has("caseIds")){var ids=new ArrayList<String>();approval.path("caseIds").forEach(n->ids.add(n.asText()));if(!ids.equals(plan.cases().stream().map(RuleFactBatchPlan.CasePlan::caseId).toList()))throw new IllegalArgumentException("approval selection mismatch");}
    }
    public static void main(String[] args)throws Exception {
        if(args.length<4)throw new IllegalArgumentException("prepare/check/assembly/run root selection package arguments required");
        Path root=Path.of(args[1]).toRealPath();
        if("prepare".equals(args[0])) {
            Path file=RuleFactOnlineHost.packagePath(root,args[3]);
            String model=args.length==5?args[4]:RuleAgentProvider.MODEL;
            byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(definition(root,RuleFactBatchPlan.Selection.valueOf(args[2]),model));
            Files.createDirectories(file.getParent());Files.write(file,bytes,StandardOpenOption.CREATE_NEW);System.out.println("PACKAGE_SHA256="+sha(bytes));return;
        }
        Path file=RuleFactOnlineHost.packagePath(root,args[2]);byte[] bytes=Files.readAllBytes(file);
        var supplied=JSON.readTree(bytes);var selection=RuleFactBatchPlan.selectionFor(supplied.path("batchPlan").path("maximumTasks").asInt());
        String model=supplied.path("model").asText();
        if(!sha(bytes).equals(args[3])||!definition(root,selection,model).equals(supplied))throw new IllegalArgumentException("batch package/build/assets drift");
        var plan=RuleFactBatchPlan.load(root,selection);
        if("check".equals(args[0])){System.out.println("PASS ZERO_CALL_"+selection+"_BATCH_CHECK "+args[3]);return;}
        if("assembly".equals(args[0])&&args.length==5){checkBatchDatabase(root,RuleFactOnlineHost.packagePath(root,args[4]),plan);return;}
        if(!"run".equals(args[0])||args.length!=5)throw new IllegalArgumentException("invalid batch operation");
        byte[] approvalBytes=Files.readAllBytes(file.resolveSibling("execution-approval.json"));
        var approval=JSON.readTree(approvalBytes);
        validateApproval(approval,args[3],plan,Instant.now());
        if((RuleAgentProvider.QWEN4_MAX.equals(model)||approval.has("model"))&&!model.equals(approval.path("model").asText()))
            throw new IllegalArgumentException("batch approval model mismatch");
        if(!args[3].equals(System.getenv("ZHKU_RULE_AGENT_AUTHORIZATION_ID")))throw new IllegalArgumentException("batch provider authorization mismatch");
        var environment=RuleAgentProvider.Settings.environment(System.getenv());
        // 模型从已校验 SHA 且获批的包读取，防止配置与实际请求或留痕漂移。
        var provider=new RuleAgentProvider.Settings(environment.endpoint(),model,environment.authorizationId());
        String url=RuleFactOnlineHost.dedicatedDatabaseUrl(System.getenv("ZHKU_DB_URL"));
        Path out=RuleFactOnlineHost.packagePath(root,args[4]);if(Files.exists(out))throw new FileAlreadyExistsException(out.toString());
        Files.writeString(file.resolveSibling("execution-reserved.json"),JSON.writeValueAsString(Map.of("packageSha256",args[3],"startedAt",Instant.now().toString(),"maximumCalls",plan.maximumCalls())),StandardOpenOption.CREATE_NEW);
        var consumed=new AtomicInteger();boolean complete=false;
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(plan.ownedProcessMillis()-60000);
        try {
            Files.createDirectories(out);byte[] reservationBytes=Files.readAllBytes(file.resolveSibling("execution-reserved.json"));
            Files.write(out.resolve("execution-approval.json"),approvalBytes,StandardOpenOption.CREATE_NEW);Files.write(out.resolve("execution-reserved.json"),reservationBytes,StandardOpenOption.CREATE_NEW);
            write(out.resolve("batch-plan.json"),Map.of("packageSha256",args[3],"plan",plan,"startedAt",Instant.now().toString(),"approvalSha256",sha(approvalBytes),"reservationSha256",sha(reservationBytes)));
            write(out.resolve("baseline-identity.json"),supplied.path("baselineIdentity"));
            for(var c:plan.cases())for(var turn:c.turns())Files.createDirectories(out.resolve(c.caseId()).resolve(turn.kind()));
            var db=new DriverManagerDataSource(url,"zhkuagent",required("ZHKU_DB_PASSWORD"));
            Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
            try(var catalogue=new Reviewed2025ControlledCatalogue(root)) {
                catalogue.installOnIsolatedDatabase(db);
                for(var c:plan.cases()) {
                    if(System.nanoTime()>=deadline)throw new TimeoutException("batch deadline");
                    executeCase(root,out,c,catalogue,db,provider,args[3],deadline,consumed);
                }
            }
            complete=true;
        } catch(Exception failure) {
            try{Files.createDirectories(out);write(out.resolve("batch-error.json"),Map.of("code",failureCode(failure),"remainingCasesRetained",true));}
            catch(Exception diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
        finally {
            try{Files.createDirectories(out);write(out.resolve("batch-closeout.json"),Map.of("executionCompleted",complete,"consumedCalls",consumed.get(),"unusedCallsClosed",plan.maximumCalls()-consumed.get(),"remainingAuthorizedCalls",0,"closedAt",Instant.now().toString()));}
            catch(Exception failure){System.err.println("BATCH_CLOSEOUT_WRITE_FAILED:"+failureCode(failure));}
            writeReports(root,out);
        }
        System.out.println("BATCH_EVIDENCE_DIRECTORY="+root.relativize(out).toString().replace('\\','/'));
    }
    private static void executeCase(Path root,Path out,RuleFactBatchPlan.CasePlan c,Reviewed2025ControlledCatalogue catalogue,
                                    DriverManagerDataSource db,RuleAgentProvider.Settings provider,String packageSha,long deadline,AtomicInteger consumed)throws Exception {
        Path folder=out.resolve(c.caseId());write(folder.resolve("case-started.json"),Map.of("caseId",c.caseId(),"startedAt",Instant.now().toString(),"maximumCalls",c.maximumCalls()));
        Clock clock=Clock.systemUTC();var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());var executor=Executors.newSingleThreadExecutor();
        var outcomes=new ConcurrentHashMap<UUID,RuleAgentRunner.Outcome>();var sources=Collections.synchronizedList(new ArrayList<RuleAgentEvidenceTools.SearchSources>());
        RuleAgentOnlineFactory factory=null;String unsafe=null;String phase="CREATE";
        try {
            var activeFactory=new RuleAgentOnlineFactory(provider,()->System.getenv("DASHSCOPE_API_KEY"),(actor,task)->tools(root,catalogue,db,actor,task,clock,sources),
                    trace->{try{Files.writeString(folder.resolve("provider-traces.jsonl"),JSON.writeValueAsString(trace)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ex){throw new IllegalStateException("provider audit unavailable",ex);}},c.maximumCalls(),clock);
            factory=activeFactory;
            RuleAgentTaskStore.Factory recording=(actor,task)->{var work=activeFactory.open(actor,task);return new RuleAgentTaskStore.Work(){
                public RuleAgentRunner.Outcome run(com.zhku.agent.agent.runtime.AgentRunContext context,RuleAgentRunner.Input input){var result=work.run(context,input);outcomes.put(context.runId(),result);return result;}
                public boolean revalidate(ResultEnvelope prior){return work.revalidate(prior);}};};
            try(var store=new RuleAgentTaskStore(executor,recording,clock,RuleAgentTaskStore.DEFAULT_LIMITS)) {
                var accepted=store.create(owner,new CreateTask(c.question(),UUID.randomUUID()));
                phase="FIRST_TURN";
                var first=executeTurn(root,folder,c.turns().getFirst(),store,owner,accepted,outcomes,sources,provider.model(),packageSha,deadline);
                if(c.turns().size()>1) {
                    var clarification=first.result().items().stream().flatMap(i->i.clarification().stream()).findFirst();
                    if(first.taskStatus()==TaskStatus.WAITING_USER&&clarification.isPresent()&&c.conditionId().equals(clarification.get().conditionId())&&clarification.get().allowedValues().contains(c.clarificationReply())) {
                        phase="CONTINUATION_MESSAGE";var next=store.message(owner,accepted.taskId(),new Message(c.clarificationReply(),first.continuationToken(),first.version(),UUID.randomUUID()));
                        write(folder.resolve(c.turns().get(1).kind()).resolve("continuation-link.json"),Map.of(
                                "previousCaptureSha256",sha(Files.readAllBytes(folder.resolve(c.turns().getFirst().kind()).resolve("capture.json"))),
                                "taskId",next.taskId(),"previousRunId",first.runId(),"runId",next.runId(),
                                "conditionId",c.conditionId(),"reply",c.clarificationReply()));
                        phase="CONTINUATION_TURN";executeTurn(root,folder,c.turns().get(1),store,owner,next,outcomes,sources,provider.model(),packageSha,deadline);
                    } else {
                        String reason=first.taskStatus()!=TaskStatus.WAITING_USER?"NO_WAITING_USER":clarification.isEmpty()?"CLARIFICATION_MISSING":!c.conditionId().equals(clarification.get().conditionId())?"WRONG_CONDITION":"REPLY_NOT_ALLOWED";
                        write(folder.resolve(c.turns().get(1).kind()).resolve("turn-skipped.json"),Map.of("code",reason,"taskStatus",first.taskStatus(),"observedClarifications",first.result().items().stream().flatMap(i->i.clarification().stream()).toList(),"noAdditionalCalls",true));
                    }
                }
                unsafe=store.traceHistory(owner,accepted.taskId()).stream().flatMap(t->t.result().issues().stream()).map(i->i.reasonCode()).filter(RuleFactBatchHost::batchFatal).findFirst().orElse(null);
            }
        } catch(Exception failure){write(folder.resolve("case-error.json"),Map.of("code",failureCode(failure),"phase",phase,"noRetry",true));}
        finally {
            int used=factory==null?0:c.maximumCalls()-factory.remainingCalls();consumed.addAndGet(used);
            var ownedFactory=factory;boolean closed=closeCaseResources(factory,()->ownedFactory==null||ownedFactory.resourcesClosed(),executor);
            write(folder.resolve("case-closeout.json"),Map.of("consumedCalls",used,"resourcesClosed",closed,"finishedAt",Instant.now().toString()));
            if(!closed)throw new BatchStop("CASE_RESOURCES_NOT_CLOSED");
        }
        if(unsafe!=null)throw new BatchStop(unsafe);
    }
    private static TaskSnapshot executeTurn(Path root,Path folder,Turn turn,RuleAgentTaskStore store,Owner owner,RuleAgentTaskStore.Accepted accepted,
                                            Map<UUID,RuleAgentRunner.Outcome> outcomes,List<RuleAgentEvidenceTools.SearchSources> sources,String model,String packageSha,long batchDeadline)throws Exception {
        Path output=folder.resolve(turn.kind());write(output.resolve("turn-started.json"),Map.of("taskId",accepted.taskId(),"runId",accepted.runId(),"turnId",accepted.turnId(),"startedAt",Instant.now().toString()));
        TaskSnapshot snapshot=null;
        try {
            long deadline=Math.min(batchDeadline,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(DEFAULT_BUDGET.maxRunMillis()+3000));
            while((snapshot=store.snapshot(owner,accepted.taskId())).taskStatus()==TaskStatus.ACTIVE){if(System.nanoTime()>=deadline)throw new TimeoutException("run deadline");Thread.sleep(20);}
            write(output.resolve("published-snapshot.json"),snapshot);write(output.resolve("trace-history.json"),store.traceHistory(owner,accepted.taskId()));
            var frozen=RuleFactExecutionContract.load(root);var identity=new RuleFactExecutionEvaluation.Identity(turn.caseId(),snapshot.taskId(),snapshot.runId(),"v1.0",frozen.sha256(),model,packageSha);
            var outcome=requireOutcome(outcomes.get(snapshot.runId()));
            var captured=RuleFactBoundEvaluation.capture(frozen,turn,identity,snapshot,outcome,List.copyOf(sources));
            Path file=output.resolve("capture.json");RuleFactBoundEvaluation.write(file,captured);
            write(output.resolve("publication-diagnostics.json"),RuleFactOnlineHost.publicationDiagnostics(identity,sha(Files.readAllBytes(file)),outcome.events()));
            var annotation=new RuleFactBoundEvaluation.Annotation(sha(Files.readAllBytes(file)),sha(captured.execution().answer().getBytes(java.nio.charset.StandardCharsets.UTF_8)),false,Set.of(),false,Map.of(),Map.of());
            write(output.resolve("annotation-pending.json"),annotation);
            var appliedConditions=store.history(owner,accepted.taskId()).stream()
                    .filter(h->h.runId().equals(accepted.runId())).findFirst().orElseThrow().conditions();
            write(output.resolve("turn-finished.json"),Map.of("taskStatus",snapshot.taskStatus(),"runState",snapshot.runState(),"coverage",snapshot.result().coverage(),"appliedConditions",appliedConditions,"finishedAt",Instant.now().toString()));
            return snapshot;
        } catch(Exception failure){var details=new LinkedHashMap<String,Object>();details.put("taskId",accepted.taskId());details.put("runId",accepted.runId());details.put("code",failureCode(failure));details.put("noRetry",true);
            if(snapshot!=null){details.put("taskStatus",snapshot.taskStatus());details.put("runState",snapshot.runState());}write(output.resolve("turn-error.json"),details);throw failure;}
    }
    static long resourceCloseWaitSeconds(){return (DEFAULT_BUDGET.maxModelMillis()+999)/1000+5;}
    static boolean closeCaseResources(AutoCloseable factory,java.util.function.BooleanSupplier resourcesClosed,ExecutorService executor) {
        boolean closed=true;try{if(factory!=null)factory.close();}catch(Exception failure){closed=false;}finally{executor.shutdownNow();}
        try{closed=executor.awaitTermination(resourceCloseWaitSeconds(),TimeUnit.SECONDS)&&closed;return resourcesClosed.getAsBoolean()&&closed;}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}catch(RuntimeException failed){return false;}
    }
    static final class BatchStop extends IllegalStateException {final String code;BatchStop(String code){this.code=code;}}
    static boolean batchFatal(String reason){return reason!=null&&Set.of("MODEL_AUTH_FAILURE","MODEL_CONNECTION_FAILURE","MODEL_UNAVAILABLE").contains(reason);}
    static final class OutcomeMissing extends IllegalStateException {}
    static RuleAgentRunner.Outcome requireOutcome(RuleAgentRunner.Outcome outcome){if(outcome==null)throw new OutcomeMissing();return outcome;}
    static String failureCode(Exception failure){
        if(failure instanceof RuleAgentTaskStore.Failure f)return "TASK_STORE_"+f.code.name();
        if(failure instanceof BatchStop stop)return stop.code;
        if(failure instanceof OutcomeMissing)return "RUN_OUTCOME_MISSING";
        if(failure instanceof TimeoutException)return "RUN_DEADLINE_EXCEEDED";
        return failure.getClass().getSimpleName();
    }
    static void writeReports(Path root,Path out) {
        try{var sources=new TreeMap<String,String>();var report=RuleFactBatchReport.collect(root,out,sources);
            write(out.resolve("report-unreviewed.json"),Map.of("qualityReport",report,"executionIdentity",RuleFactBatchReport.executionIdentity(out),"sources",sources));
            Files.writeString(out.resolve("report-unreviewed.md"),RuleFactBatchReport.markdown(report),java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }catch(Exception failure){try{Files.createDirectories(out);write(out.resolve("report-error.json"),Map.of("code",failureCode(failure),"offlineRecovery","RuleFactBatchReport.main","executionEvidenceRetained",true));}
            catch(Exception diagnostic){System.err.println("REPORT_ERROR_PERSISTENCE_FAILED:"+failureCode(diagnostic));}}
    }
    private static RuleAgentEvidenceTools tools(Path root,Reviewed2025ControlledCatalogue catalogue,DriverManagerDataSource db,
                                                Owner owner,UUID task,Clock clock,List<RuleAgentEvidenceTools.SearchSources> sources) {
        var settings=catalogue.settings();
        return new RuleAgentEvidenceTools(owner,task,settings,()->{
            try{return RuleArticle4Overlay.apply(root,catalogue.reviewedCatalogue(root));}catch(Exception ex){throw new IllegalStateException(ex);}},
                JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(db,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
                new JdbcRuleCheckEvidenceReader(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),
                new JdbcRuleCheckCitationAuditRepository(db,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock).withSearchAudit(sources::add);
    }
    private static void checkBatchDatabase(Path root,Path out,RuleFactBatchPlan.Plan plan)throws Exception {
        if(Files.exists(out))throw new FileAlreadyExistsException(out.toString());Files.createDirectories(out);
        var frozen=RuleFactExecutionContract.load(root);Clock clock=Clock.systemUTC();
        var db=new DriverManagerDataSource(RuleFactOnlineHost.dedicatedDatabaseUrl(System.getenv("ZHKU_DB_URL")),"zhkuagent",required("ZHKU_DB_PASSWORD"));
        Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
        try(var catalogue=new Reviewed2025ControlledCatalogue(root)) {
            catalogue.installOnIsolatedDatabase(db);
            for(var c:plan.cases()) {
                var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());UUID task=UUID.randomUUID(),run=UUID.randomUUID();
                var sources=new ArrayList<RuleAgentEvidenceTools.SearchSources>();var tool=tools(root,catalogue,db,owner,task,clock,sources);
                var observation=tool.search(owner,task,run,1,new Search("q1",c.question()));
                var locators=new TreeSet<String>();for(var side:sources)for(var candidate:side.candidates()) {String locator=frozen.locator(candidate.revision(),candidate.checksum());if(locator!=null)locators.add(locator);}
                String readStatus="NOT_REQUESTED_NO_CANDIDATE";
                if(observation.status()==ObservationStatus.SUCCEEDED) {
                    // Port readiness uses one candidate; the real Agent chooses its own bounded read set.
                    var selected=observation.candidates().stream().limit(1).map(Candidate::handle).toList();
                    readStatus=tool.read(owner,task,run,2,new Read("q1",selected)).observation().status().name();
                }
                write(out.resolve(c.caseId()+"-assembly.json"),Map.of("caseId",c.caseId(),"origin","DATABASE_TOOL_READINESS_NOT_AGENT_EXECUTION","providerCalls",0,
                        "searchStatus",observation.status(),"readStatus",readStatus,"retrievedLocators",locators,"retrievalSources",sources));
                if(observation.status()==ObservationStatus.UNAVAILABLE||observation.status()==ObservationStatus.REJECTED||readStatus.equals("UNAVAILABLE")||readStatus.equals("REJECTED"))throw new IllegalStateException("database tool unavailable: "+c.caseId());
            }
        }
        System.out.println("PASS "+plan.maximumTasks()+"_CASE_DATABASE_TOOL_ASSEMBLY providerCalls=0");
    }
    private static void write(Path file,Object value)throws Exception {
        var node=JSON.valueToTree(value);redact(node);Files.write(file,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(node),StandardOpenOption.CREATE_NEW);
    }
    private static void redact(com.fasterxml.jackson.databind.JsonNode node){if(node.isObject())((ObjectNode)node).remove(List.of("originalToken","continuationToken"));node.forEach(RuleFactBatchHost::redact);}
    private static String required(String name){var value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("missing "+name);return value;}
}
