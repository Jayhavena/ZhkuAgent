package com.zhku.knowledge.eval;

import java.nio.file.*;
import java.util.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;
import static com.zhku.knowledge.eval.RuleFactExecutionEvaluation.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

/** Aggregation only: every turn verdict is produced by the existing bound evaluation/graders. */
public final class RuleFactBatchReport {
    public record TurnRecord(String caseId,String kind,Result result,String evidencePath,String evidenceSha,String error,String annotationPath,String annotationSha) {
        public TurnRecord(String caseId,String kind,Result result,String evidencePath,String evidenceSha,String error){this(caseId,kind,result,evidencePath,evidenceSha,error,null,null);}
    }
    public record CaseResult(String outcome,boolean fullyEvaluable,Map<String,Dimension> dimensions,List<TurnRecord> turns) {}
    public record Counts(long applicable,long passed,long failed,long unjudgeable,long errors,long notApplicable) {}
    public record Report(String datasetSha256,int denominator,long passed,long failed,long unjudgeable,long errors,
                         long knownOutcomes,long fullyEvaluable,double casePassRate,double knownOutcomeRatio,double fullyEvaluableRatio,
                         Map<String,CaseResult> cases,Map<String,Counts> dimensions) {}
    public static Report aggregate(RuleFactBatchPlan.Plan plan,List<TurnRecord> rows) {
        var byCase=new LinkedHashMap<String,List<TurnRecord>>();plan.cases().forEach(c->byCase.put(c.caseId(),new ArrayList<>()));
        for(var row:rows) {
            var c=plan.cases().stream().filter(p->p.caseId().equals(row.caseId())).findFirst().orElseThrow(()->new IllegalArgumentException("unexpected case"));
            if(c.turns().stream().noneMatch(t->t.kind().equals(row.kind())))throw new IllegalArgumentException("unexpected turn");
            byCase.get(row.caseId()).add(row);
        }
        var cases=new LinkedHashMap<String,CaseResult>();
        for(var c:plan.cases()) {
            var turns=new ArrayList<TurnRecord>();boolean error=false,missing=false;
            for(var expected:c.turns()) {
                var found=byCase.get(c.caseId()).stream().filter(r->r.kind().equals(expected.kind())).toList();
                if(found.size()>1){error=true;turns.add(new TurnRecord(c.caseId(),expected.kind(),null,null,null,"ERROR:DUPLICATE_TURN"));}
                else if(found.isEmpty()){missing=true;turns.add(new TurnRecord(c.caseId(),expected.kind(),null,null,null,"UNJUDGEABLE:EXECUTION_MISSING"));}
                else {var row=found.getFirst();turns.add(row);if(row.result()==null){missing=true;if(row.error()!=null&&row.error().startsWith("ERROR:"))error=true;}}
            }
            var dims=new LinkedHashMap<String,Dimension>();
            for(String key:DIMENSIONS) {
                var reasons=new ArrayList<String>();var values=new ArrayList<Verdict>();
                for(var turn:turns) {
                    var d=turn.result()==null?null:turn.result().dimensions().get(key);
                    if(d==null){values.add(Verdict.UNJUDGEABLE);reasons.add(turn.kind()+":"+(turn.error()==null?"DIMENSION_MISSING":turn.error()));}
                    else{values.add(d.verdict());d.reasons().forEach(r->reasons.add(turn.kind()+":"+r));}
                }
                Verdict verdict=values.contains(Verdict.FAIL)?Verdict.FAIL:values.contains(Verdict.UNJUDGEABLE)?Verdict.UNJUDGEABLE:
                        values.stream().allMatch(v->v==Verdict.NOT_APPLICABLE)?Verdict.NOT_APPLICABLE:Verdict.PASS;
                dims.put(key,new Dimension(verdict,List.copyOf(reasons)));
            }
            String outcome=error?"ERROR":dims.values().stream().anyMatch(d->d.verdict()==Verdict.FAIL)?"FAIL":
                    missing||dims.values().stream().anyMatch(d->d.verdict()==Verdict.UNJUDGEABLE)||dims.values().stream().noneMatch(d->d.verdict()==Verdict.PASS)?"UNJUDGEABLE":"PASS";
            boolean full=!error&&!missing&&dims.values().stream().noneMatch(d->d.verdict()==Verdict.UNJUDGEABLE)&&dims.values().stream().anyMatch(d->d.verdict()!=Verdict.NOT_APPLICABLE);
            cases.put(c.caseId(),new CaseResult(outcome,full,dims,List.copyOf(turns)));
        }
        long pass=count(cases,"PASS"),fail=count(cases,"FAIL"),unknown=count(cases,"UNJUDGEABLE"),errors=count(cases,"ERROR");
        long full=cases.values().stream().filter(CaseResult::fullyEvaluable).count();
        var dimensionCounts=new LinkedHashMap<String,Counts>();
        for(String key:DIMENSIONS) {
            long p=0,f=0,u=0,e=0,na=0;
            for(var c:cases.values())if(c.outcome().equals("ERROR"))e++;else switch(c.dimensions().get(key).verdict()) {
                case PASS->p++;case FAIL->f++;case UNJUDGEABLE->u++;case NOT_APPLICABLE->na++;
            }
            dimensionCounts.put(key,new Counts(cases.size()-na,p,f,u,e,na));
        }
        return new Report(plan.datasetSha256(),cases.size(),pass,fail,unknown,errors,pass+fail,full,
                (double)pass/cases.size(),(double)(pass+fail)/cases.size(),(double)full/cases.size(),cases,dimensionCounts);
    }
    private static long count(Map<String,CaseResult> cases,String outcome){return cases.values().stream().filter(c->c.outcome().equals(outcome)).count();}
    public static Report collect(Path root,Path directory,Map<String,String> sources)throws Exception {
        Path base=directory.toRealPath();var receipt=JSON.readTree(Files.readAllBytes(base.resolve("batch-plan.json")));
        sources.put("batch-plan.json",sha(Files.readAllBytes(base.resolve("batch-plan.json"))));
        for(String name:List.of("batch-closeout.json","batch-error.json","launcher-result.json","baseline-identity.json","failure-reviews.json","execution-approval.json","execution-reserved.json","container-cleanup.json","package-postcheck.json"))if(Files.isRegularFile(base.resolve(name)))sources.put(name,sha(Files.readAllBytes(base.resolve(name))));
        executionIdentity(base); // 带授权摘要的真实记录必须核对本地回执，离线缺证据夹具只报告缺失。
        var plan=JSON.treeToValue(receipt.path("plan"),RuleFactBatchPlan.Plan.class);
        if(!RuleFactBatchPlan.load(root,RuleFactBatchPlan.selectionFor(plan.maximumTasks())).equals(plan))throw new IllegalArgumentException("batch plan drift");
        var frozen=RuleFactExecutionContract.load(root);var rows=new ArrayList<TurnRecord>();
        var captures=new HashMap<String,RuleFactBoundEvaluation.Captured>();var results=new HashMap<String,Result>();var hashes=new HashMap<String,String>();
        for(var c:plan.cases())for(String name:List.of("case-started.json","case-error.json","case-closeout.json","provider-traces.jsonl")) {
            Path artifact=base.resolve(c.caseId()).resolve(name);if(Files.isRegularFile(artifact))sources.put(c.caseId()+"/"+name,sha(Files.readAllBytes(artifact)));
        }
        for(var c:plan.cases())for(var turn:c.turns()) {
            Path folder=base.resolve(c.caseId()).resolve(turn.kind()),capture=folder.resolve("capture.json");
            if(!Files.isRegularFile(capture)) {
                Path failure=folder.resolve("turn-error.json");String reason="UNJUDGEABLE:EXECUTION_MISSING_OR_INTERRUPTED";
                if(Files.isRegularFile(failure)){sources.put(base.relativize(failure).toString().replace('\\','/'),sha(Files.readAllBytes(failure)));reason="ERROR:"+JSON.readTree(Files.readAllBytes(failure)).path("code").asText("TURN_ERROR");}
                else for(String name:List.of("turn-skipped.json","turn-started.json"))if(Files.isRegularFile(folder.resolve(name))) {
                    byte[] state=Files.readAllBytes(folder.resolve(name));sources.put(base.relativize(folder.resolve(name)).toString().replace('\\','/'),sha(state));
                    reason="UNJUDGEABLE:"+(name.equals("turn-skipped.json")?JSON.readTree(state).path("code").asText():"RUN_STARTED_CAPTURE_MISSING");break;
                }
                Path caseFailure=base.resolve(c.caseId()).resolve("case-error.json");
                if(!Files.isRegularFile(failure)&&!Files.isRegularFile(folder.resolve("turn-skipped.json"))&&Files.isRegularFile(caseFailure))
                    reason="ERROR:CASE_"+JSON.readTree(Files.readAllBytes(caseFailure)).path("code").asText("UNKNOWN");
                rows.add(new TurnRecord(c.caseId(),turn.kind(),null,null,null,reason));continue;
            }
            byte[] bytes=Files.readAllBytes(capture);String captureSha=sha(bytes),relative=base.relativize(capture).toString().replace('\\','/');sources.put(relative,captureSha);
            for(String name:List.of("publication-diagnostics.json","turn-finished.json")) {
                Path artifact=folder.resolve(name);
                if(Files.isRegularFile(artifact))sources.put(base.relativize(artifact).toString().replace('\\','/'),sha(Files.readAllBytes(artifact)));
            }
            String stage="CAPTURE_DECODE";try {
                var captured=JSON.readValue(bytes,RuleFactBoundEvaluation.Captured.class);
                stage="CAPTURE_IDENTITY";
                if(!captured.turn().equals(turn)||!receipt.path("packageSha256").asText().equals(captured.execution().identity().configurationSha256()))throw new IllegalArgumentException("capture identity drift");
                Path annotation=folder.resolve("annotation-reviewed.json");if(!Files.isRegularFile(annotation))annotation=folder.resolve("annotation-pending.json");
                byte[] annotationBytes=Files.isRegularFile(annotation)?Files.readAllBytes(annotation):null;
                if(annotationBytes!=null)sources.put(base.relativize(annotation).toString().replace('\\','/'),sha(annotationBytes));
                stage="ANNOTATION_DECODE";
                var review=annotationBytes!=null?JSON.readValue(annotationBytes,RuleFactBoundEvaluation.Annotation.class):
                        new RuleFactBoundEvaluation.Annotation(captureSha,sha((captured.execution().answer()==null?"":captured.execution().answer()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),false,Set.of(),false,Map.of(),Map.of());
                stage="BOUND_EVALUATION";Result result=RuleFactBoundEvaluation.evaluate(frozen,captured,captureSha,review);
                Path link=folder.resolve("continuation-link.json");String priorKey=c.caseId()+":ASK";
                if(turn.kind().equals("RESOLVE_PUBLIC")&&captured.searches().isEmpty()&&Files.isRegularFile(link)) {
                    byte[] linkBytes=Files.readAllBytes(link);sources.put(base.relativize(link).toString().replace('\\','/'),sha(linkBytes));
                    var previous=captures.get(priorKey);var previousResult=results.get(priorKey);
                    if(previous!=null&&previousResult!=null&&validContinuation(previous,captured,hashes.get(priorKey),JSON.readTree(linkBytes)))
                        result=inheritRetrieval(result,previousResult,hashes.get(priorKey));
                }
                String key=c.caseId()+":"+turn.kind();captures.put(key,captured);results.put(key,result);hashes.put(key,captureSha);
                rows.add(new TurnRecord(c.caseId(),turn.kind(),result,relative,captureSha,null,
                        Files.isRegularFile(annotation)?base.relativize(annotation).toString().replace('\\','/'):null,
                        Files.isRegularFile(annotation)?sha(Files.readAllBytes(annotation)):null));
            } catch(Exception invalid) {rows.add(new TurnRecord(c.caseId(),turn.kind(),null,relative,captureSha,"ERROR:CAPTURE_OR_ANNOTATION_INVALID:"+stage+":"+invalid.getClass().getSimpleName()));}
        }
        return aggregate(plan,rows);
    }
    static boolean validContinuation(RuleFactBoundEvaluation.Captured previous,RuleFactBoundEvaluation.Captured current,String previousSha,com.fasterxml.jackson.databind.JsonNode link) {
        var before=previous.execution().identity();var after=current.execution().identity();boolean declared=false;
        for(var item:previous.publishedResult().path("items"))for(var clarification:item.path("clarification"))
            if("courseType".equals(clarification.path("conditionId").asText()))for(var allowed:clarification.path("allowedValues"))if("PUBLIC".equals(allowed.asText()))declared=true;
        return declared&&previous.turn().kind().equals("ASK")&&current.turn().kind().equals("RESOLVE_PUBLIC")
                &&before.caseId().equals(after.caseId())&&previous.execution().trace()!=null&&"WAITING_USER".equals(previous.execution().trace().taskStatus())
                &&before.taskId().equals(after.taskId())&&!before.runId().equals(after.runId())&&current.version()==previous.version()+2
                &&before.configurationSha256().equals(after.configurationSha256())&&before.datasetSha256().equals(after.datasetSha256())
                &&Objects.equals(previousSha,link.path("previousCaptureSha256").asText())&&before.taskId().toString().equals(link.path("taskId").asText())
                &&before.runId().toString().equals(link.path("previousRunId").asText())&&after.runId().toString().equals(link.path("runId").asText())
                &&"courseType".equals(link.path("conditionId").asText())&&"PUBLIC".equals(link.path("reply").asText());
    }
    static Result inheritRetrieval(Result current,Result previous,String previousSha) {
        var dims=new LinkedHashMap<>(current.dimensions());var prior=previous.dimensions().get("retrievalRecall");
        var reasons=new ArrayList<String>();reasons.add("INHERITED_VERIFIED_ASK_RETRIEVAL captureSha="+previousSha);reasons.addAll(prior.reasons());
        dims.put("retrievalRecall",new Dimension(prior.verdict(),reasons));
        String outcome=dims.values().stream().anyMatch(d->d.verdict()==Verdict.FAIL)?"FAIL":dims.values().stream().anyMatch(d->d.verdict()==Verdict.UNJUDGEABLE)?"UNJUDGEABLE":"PASS";
        return new Result(current.identity(),outcome,dims);
    }
    public static String markdown(Report report) {
        var out=new StringBuilder("# "+report.denominator()+"题 Agent 质量报告\n\n");
        out.append("RFE-013 必须分别核验 ASK 与 RESOLVE_PUBLIC 的 annotation-reviewed.json；ASK 的回答片段是实际澄清问题/缺口说明，不能只标注最终答案。未完成任一轮标注时不计完整可评估。\n\n");
        out.append("分母：").append(report.denominator()).append("；通过：").append(report.passed()).append("；失败：").append(report.failed())
                .append("；不可判定：").append(report.unjudgeable()).append("；错误：").append(report.errors()).append("；通过率：").append(String.format(Locale.ROOT,"%.1f%%",100*report.casePassRate())).append("。\n\n")
                .append("确定整题结论比例：").append(report.knownOutcomes()).append('/').append(report.denominator()).append("；全部适用维度可评估比例：").append(report.fullyEvaluable()).append('/').append(report.denominator()).append("。FAIL可与局部不可判定共存，因此两种比例分别报告。\n\n")
                .append("| 题目 | 整题 | 完整可评估 | 检索 | 事实 | 引用 | 适用性 | 澄清/缺口 | 安全 |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        report.cases().forEach((id,c)->{out.append("| ").append(id).append(" | ").append(c.outcome()).append(" | ").append(c.fullyEvaluable()).append(" | ");DIMENSIONS.forEach(k->out.append(c.dimensions().get(k).verdict()).append(" | "));out.append('\n');});
        out.append("\n| 维度 | 分母 | NA | PASS | FAIL | UNJUDGEABLE | ERROR | 已判定/适用分母 |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        report.dimensions().forEach((key,c)->out.append("| ").append(key).append(" | ").append(report.denominator()).append(" | ").append(c.notApplicable()).append(" | ").append(c.passed()).append(" | ").append(c.failed()).append(" | ").append(c.unjudgeable()).append(" | ").append(c.errors()).append(" | ").append(c.passed()+c.failed()).append('/').append(c.applicable()).append(" |\n"));
        report.cases().forEach((id,c)->{out.append("\n## ").append(id).append("\n\n");for(var turn:c.turns()) {
            out.append("- ").append(turn.kind()).append(": ").append(turn.evidencePath()==null?turn.error():"[capture]("+turn.evidencePath()+") SHA "+turn.evidenceSha());
            if(turn.annotationPath()!=null)out.append("；[annotation](").append(turn.annotationPath()).append(") SHA ").append(turn.annotationSha());out.append('\n');}
            c.dimensions().forEach((key,d)->out.append("\n- ").append(key).append(" [").append(d.verdict()).append("]: ").append(String.join("；",d.reasons()).replace("\n"," ")).append('\n'));});
        return out.append("\n语义结论来自摘要绑定的独立标注；未执行、错误和未知不计为通过。批外冻结题数：").append(20-report.denominator()).append("。不同分母不得直接比较通过率；原文与回答限本地受控留存，不自动发布。\n").toString();
    }
    static Map<String,String> executionIdentity(Path directory)throws Exception {
        var receipt=JSON.readTree(Files.readAllBytes(directory.resolve("batch-plan.json")));var result=new LinkedHashMap<String,String>();
        result.put("packageSha256",receipt.path("packageSha256").asText());boolean complete=true;
        for(String name:List.of("approval","reservation")) {
            String file=name.equals("approval")?"execution-approval.json":"execution-reserved.json";Path path=directory.resolve(file);
            if(!Files.isRegularFile(path)){if(receipt.has(name+"Sha256"))throw new IllegalArgumentException("authorization artifact missing: "+name);complete=false;continue;}
            byte[] bytes=Files.readAllBytes(path);var artifact=JSON.readTree(bytes);String hash=sha(bytes);
            if(!receipt.path("packageSha256").asText().equals(artifact.path("packageSha256").asText())||receipt.has(name+"Sha256")&&!hash.equals(receipt.path(name+"Sha256").asText()))throw new IllegalArgumentException("authorization artifact drift: "+name);
            result.put(name+"Sha256",hash);
        }
        result.put("authorizationEvidence",complete?"PACKAGE_BOUND_LOCAL_COPIES":"LOCAL_COPIES_MISSING_NOT_VERIFIED");return result;
    }
    /** root batch-directory output.json output.md; offline only, never overwrites prior reports. */
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("root batch-directory output.json output.md required");
        var sources=new TreeMap<String,String>();var report=collect(Path.of(args[0]),Path.of(args[1]),sources);
        var analysis=RuleFactFailureAnalysis.classify(report,RuleFactFailureAnalysis.loadReviews(Path.of(args[1]),report));
        Files.write(Path.of(args[2]),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("qualityReport",report,"failureAnalysis",analysis,"executionIdentity",executionIdentity(Path.of(args[1])),"sources",sources)),StandardOpenOption.CREATE_NEW);
        Files.writeString(Path.of(args[3]),markdown(report)+RuleFactFailureAnalysis.markdown(analysis),java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
