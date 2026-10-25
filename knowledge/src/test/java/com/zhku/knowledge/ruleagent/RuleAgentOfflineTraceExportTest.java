package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.agent.agent.runtime.AgentRunContext;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
import static org.assertj.core.api.Assertions.*;

/** 由控制器在定向 Maven 测试中运行；输出只来自实际 Runner 发布进 TaskStore 的 traceHistory。 */
class RuleAgentOfflineTraceExportTest {
    private static final ObjectMapper JSON=new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
    private static final java.util.regex.Pattern UUID_SUBSTRING=java.util.regex.Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",java.util.regex.Pattern.CASE_INSENSITIVE);

    @Test void actualRunnerTraceHistoryExportsOnlySafeT08Fields() throws Exception {
        // 两次独立运行仍会生成随机 citation；局部标签消除随机关联标识对规范输出的影响。
        String serialized=exportOnce();
        assertThat(exportOnce()).as("相同离线输入的规范脱敏 JSON 必须稳定").isEqualTo(serialized);
            String output=System.getProperty("zhku.t08.export");
            if(output!=null&&!output.isBlank()){
                // An exported trace must never inherit a synthetic default candidate/source identity.
                assertThat(System.getProperty("zhku.t08.candidateId")).as("explicit export candidateId required").isNotBlank();
                assertThat(System.getProperty("zhku.t08.sourceSha")).as("explicit export sourceSha required").isNotBlank();
                var working=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
                var repositoryRoot="knowledge".equalsIgnoreCase(String.valueOf(working.getFileName()))?working.getParent():working;
                var destination=exportDestination(repositoryRoot,Path.of(output));
                Files.writeString(destination,serialized+"\n",StandardOpenOption.CREATE_NEW);
            }
    }

    @Test void rejectsHiddenUuidAndUnreviewedRevision() {
        assertThatThrownBy(()->requireSafeJson("prefix-123e4567-e89b-12d3-a456-426614174000:0-20"))
                .isInstanceOf(IllegalArgumentException.class);
        for(String unsafe:List.of("sk-test-marker","SECRET_TEST_MARKER","2026.1","prefix-123e4567-e89b-12d3-a456-426614174000"))
            assertThatThrownBy(()->reviewedRevision(unsafe)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsParentJunctionBeforeCreatingExport(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var root=Files.createDirectory(temp.resolve("repository"));
        var outside=Files.createDirectory(temp.resolve("outside"));
        var link=root.resolve("redirect");
        if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")){
            var process=new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",link.toString(),outside.toString())
                    .redirectErrorStream(true).start();
            assertThat(process.waitFor(15,TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as("创建受控临时junction失败").isZero();
        } else Files.createSymbolicLink(link,outside);
        try {
            assertThatThrownBy(()->exportDestination(root,link.resolve("export.json")))
                    .isInstanceOf(AssertionError.class);
            assertThat(Files.exists(outside.resolve("export.json"))).isFalse();
        } finally {
            // 只删除本测试创建的链接，不递归进入真实目标。
            Files.deleteIfExists(link);
        }
    }

    private static Path exportDestination(Path repositoryRoot,Path output) throws Exception {
        repositoryRoot=repositoryRoot.toAbsolutePath().normalize();
        // Windows junction不是符号链接；真实路径必须与词法路径一致，根目录也不能是别名。
        assertThat(repositoryRoot.toRealPath()).isEqualTo(repositoryRoot);
        var destination=output.toAbsolutePath().normalize();
        assertThat(destination.startsWith(repositoryRoot)&&!destination.equals(repositoryRoot))
                .as("T08 export must remain inside this repository").isTrue();
        var current=repositoryRoot;var parent=repositoryRoot.relativize(destination).getParent();
        if(parent!=null)for(var part:parent){
            current=current.resolve(part);
            assertThat(Files.isSymbolicLink(current)).isFalse();
            assertThat(current.toRealPath()).as("导出父目录不能重定向到真实目标").isEqualTo(current);
        }
        return destination;
    }

    private static void requireSafeJson(String serialized) {
        if(UUID_SUBSTRING.matcher(serialized).find())throw new IllegalArgumentException("UUID in T08 export");
    }

    private static String reviewedRevision(String revision) {
        // 修订号也可能承载来源文本或 token，通用 ASCII 正则不能充当安全白名单。
        if(!"2025.9.2".equals(revision))throw new IllegalArgumentException("Unreviewed T08 revision");
        return revision;
    }

    private String exportOnce() throws Exception {
        String candidate=System.getProperty("zhku.t08.candidateId","offline-smoke-candidate");
        String source=System.getProperty("zhku.t08.sourceSha","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        String caseId=System.getProperty("zhku.t08.caseId","java-offline-runner-pass");
        String datasetPath=System.getProperty("zhku.t08.dataset",Path.of("..","tools","t08-acceptance","sprint02","java-offline-runner-dataset.json").toAbsolutePath().normalize().toString());
        assertThat(candidate).matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
        assertThat(caseId).matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
        assertThat(source).matches("[a-f0-9]{40}([a-f0-9]{24})?");
        byte[] datasetBytes=Files.readAllBytes(Path.of(datasetPath));
        var dataset=JSON.readTree(datasetBytes);var cases=dataset.path("cases").elements();
        com.fasterxml.jackson.databind.JsonNode selectedCase=null;
        while(cases.hasNext()){var candidateCase=cases.next();if(caseId.equals(candidateCase.path("caseId").asText())){selectedCase=candidateCase;break;}}
        assertThat(selectedCase).as("冻结数据集 caseId 不存在").isNotNull();
        assertThat(dataset.path("split").asText()).isEqualTo("development");
        assertThat(dataset.path("cases").size()).isEqualTo(1);
        assertThat(selectedCase.path("group").asText()).isEqualTo("G2");
        var input=selectedCase.path("input");String question=input.path("question").asText();
        assertThat(question).isNotBlank();
        assertThat(input.path("supplements").isArray()&&input.path("supplements").isEmpty())
                .as("当前离线导出器仅支持单轮 case；多轮需由适配器显式执行每次续答").isTrue();
        String datasetSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datasetBytes));
        var queued=new ConcurrentLinkedQueue<Runnable>();
        var owner=new Owner(UUID.randomUUID(),UUID.randomUUID());
        var runnerFixture=new RuleAgentRunnerTest();
        try(var store=new RuleAgentTaskStore(queued::add,(actor,task)->{
            RuleAgentEvidenceTools tools;
            try{tools=runnerFixture.tools(actor,task,true,true,false,false);}
            catch(Exception unavailable){throw new IllegalStateException("离线工具初始化失败",unavailable);}
            var decision=runnerFixture.responding(false);
            return new RuleAgentTaskStore.Work(){
                public RuleAgentRunner.Outcome run(AgentRunContext context,RuleAgentRunner.Input input){
                    return new RuleAgentRunner(decision,new RuleActionBroker(tools),Clock.systemUTC()).run(context,input);
                }
                public boolean revalidate(ResultEnvelope previous){return true;}
            };
        },Clock.systemUTC(),new RuleAgentTaskStore.Limits(4,2,8))){
            var accepted=store.create(owner,new CreateTask(question,UUID.randomUUID()));
            while(!queued.isEmpty()){
                var job=queued.remove();job.run();
                if(job instanceof FutureTask<?> future)future.get();
            }
            var traces=store.traceHistory(owner,accepted.taskId());
            assertThat(traces).hasSize(1);
            assertThat(traces.getFirst().executionActions()).extracting(RuleAgentRunTrace.ActionView::type)
                    .contains("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","FINISH_CHECK");
            assertThat(traces.getFirst().executionObservations()).extracting(RuleAgentRunTrace.ObservationView::status)
                    .contains("SUCCEEDED","NOT_OBSERVED");
            assertThat(traces.getFirst().result().evidence()).isNotEmpty();
            assertThat(traces.getFirst().result().evidenceRelations()).isNotEmpty();
            var turns=new ArrayList<Map<String,Object>>();
            for(int ti=0;ti<traces.size();ti++){
                var trace=traces.get(ti);String taskId="task-1",turnId="turn-"+(ti+1),runId="run-"+(ti+1);
                var actions=new ArrayList<Map<String,Object>>();var observations=new ArrayList<Map<String,Object>>();
                var actionIds=new HashMap<String,String>();int actionIndex=0;
                for(var action:trace.executionActions()){
                    String opaqueActionId="action-"+(++actionIndex);actionIds.put(action.actionId(),opaqueActionId);
                    actions.add(Map.of("actionId",opaqueActionId,"type",action.type(),"step",action.step(),"modelAttempt",action.modelAttempt()));
                }
                for(var observation:trace.executionObservations()){
                    var item=new LinkedHashMap<String,Object>();item.put("actionId",actionIds.get(observation.actionId()));
                    item.put("status",observation.status());item.put("dataLevel",observation.dataLevel());
                    if(observation.reasonCode()!=null)item.put("reasonCode",ReasonCode.valueOf(observation.reasonCode()));observations.add(item);
                }
                var result=trace.result();
                var evidenceIds=new LinkedHashMap<String,String>();
                var claimIds=new LinkedHashMap<String,String>();
                // source/citation UUID 及 claim 关联标识不跨过导出边界，关系只引用局部标签。
                for(var e:result.evidence())assertThat(evidenceIds.putIfAbsent(e.id(),"evidence-"+(evidenceIds.size()+1))).isNull();
                for(var item:result.items())for(var claim:item.claims())
                    assertThat(claimIds.putIfAbsent(claim.id(),"claim-"+(claimIds.size()+1))).isNull();
                var evidence=result.evidence().stream().map(e->Map.<String,Object>of(
                        "id",evidenceIds.get(e.id()),"revision",reviewedRevision(e.revision()))).toList();
                var relations=result.evidenceRelations().stream().map(r->Map.<String,Object>of(
                        "targetId",Objects.requireNonNull(claimIds.get(r.targetId())),
                        "evidenceId",Objects.requireNonNull(evidenceIds.get(r.evidenceId())),"role",EvidenceRole.valueOf(r.role()))).toList();
                var items=result.items().stream().map(i->Map.<String,Object>of("claims",i.claims().stream().map(c->{
                    assertThat(c.factId()).matches("sha256:[a-f0-9]{64}");
                    return Map.<String,Object>of("id",claimIds.get(c.id()),"factId",c.factId());
                }).toList())).toList();
                var issues=result.issues().stream().map(i->Map.<String,Object>of("kind",IssueKind.valueOf(i.kind()),"reasonCode",ReasonCode.valueOf(i.reasonCode()))).toList();
                turns.add(Map.ofEntries(Map.entry("taskId",taskId),Map.entry("turnId",turnId),Map.entry("runId",runId),
                        Map.entry("version",trace.version()),Map.entry("taskStatus",trace.taskStatus()),Map.entry("runState",trace.runState()),
                        Map.entry("exceptionalFailure",trace.exceptionalFailure()),Map.entry("truncated",trace.truncated()),
                        Map.entry("actions",actions),Map.entry("observations",observations),
                        Map.entry("result",Map.of("coverage",result.coverage(),"evidence",evidence,
                                "evidenceRelations",relations,"items",items,"issues",issues))));
            }
            var export=Map.of("recordType","T08_JAVA_TRACE_EXPORT","schemaVersion",1,
                    "sourceLayer","ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED","executionMode","OFFLINE_SIMULATED",
                    "providerCalls",0,"candidateId",candidate,"sourceSha",source,"caseId",caseId,
                    "datasetSha256",datasetSha,"traces",turns);
            String serialized=JSON.writeValueAsString(export);
            requireSafeJson(serialized);
            assertThat(serialized).doesNotContain(question).doesNotContain("合成分类规则")
                    .doesNotContain("sourceId").doesNotContain("chunkId").doesNotContain("continuationToken");
            return serialized;
        }
    }
}
