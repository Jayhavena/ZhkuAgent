package com.zhku.knowledge.eval;

import com.zhku.knowledge.ruleagent.*;
import java.nio.file.*;
import java.util.*;
import static com.zhku.knowledge.eval.RuleFactExecutionEvaluation.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;

/** Immutable machine capture and separately hash-bound human annotations. Reuses the existing graders. */
public final class RuleFactBoundEvaluation {
    public record Search(UUID observationId, UUID runId, int step, String status, List<RuleAgentContract.Candidate> candidates) {}
    public record Captured(String datasetSha256, Turn turn, UUID turnId, long version,
                           Execution execution, com.fasterxml.jackson.databind.JsonNode publishedResult,
                           List<Search> searches, List<RuleAgentEvidenceTools.SearchSources> retrieval) {
        public Captured { retrieval=List.copyOf(retrieval); }
    }
    public record Annotation(String captureSha256, String answerSha256, boolean factsComplete, Set<String> matchedFacts,
                             boolean citationsComplete, Map<String,Verification> citationReviews, Map<String,Review> reviews) {}
    public static Captured capture(Frozen frozen, Turn turn, Identity identity, RuleAgentContract.TaskSnapshot published,
                                   RuleAgentRunner.Outcome outcome, List<RuleAgentEvidenceTools.SearchSources> retrieval) {
        if (!frozen.sha256().equals(identity.datasetSha256()) || !frozen.turns(turn.caseId()).contains(turn)
                || !turn.caseId().equals(identity.caseId()) || !published.result().equals(outcome.snapshot().result())
                || published.version()!=outcome.snapshot().version() || !published.runId().equals(outcome.snapshot().runId()))
            throw new IllegalArgumentException("capture publication/contract mismatch");
        var bundle=RuleFactExecutionCapture.capture(identity,published,outcome.events());
        var searches=new ArrayList<Search>();
        for(var e:outcome.events())if("SEARCH_REVIEWED_RULES".equals(e.action())) {
            var o=e.observation();searches.add(o==null?new Search(null,published.runId(),0,"MISSING",List.of()):
                    new Search(o.actionId(),o.runId(),o.step(),o.status().name(),o.candidates()));
        }
        var result=JSON.valueToTree(published.result());redact(result);
        return new Captured(frozen.sha256(),turn,published.turnId(),published.version(),bundle.execution(),result,searches,retrieval);
    }
    public static Result evaluate(Frozen frozen, Captured captured, String captureSha, Annotation annotation) {
        var original=captured.execution(); var turn=captured.turn();
        String observedAnswer=original.answer()==null?"":original.answer();
        if (!frozen.sha256().equals(captured.datasetSha256()) || !frozen.turns(turn.caseId()).contains(turn)
                || !captureSha.equals(annotation.captureSha256())
                || !sha(observedAnswer.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(annotation.answerSha256()))
            throw new IllegalArgumentException("stale annotation or contract");
        if(original.trace()!=null && (!captured.turnId().equals(original.trace().turnId()) || captured.version()!=original.trace().version()))
            throw new IllegalArgumentException("capture turn/version mismatch");
        if(original.trace()==null || observedAnswer.isBlank()) {
            // Missing observations are not an infrastructure crash. Keep all dimensions and fail closed;
            // a known FAILED/STOPPED trace still fails independently of missing answer text.
            var missing=new Execution(original.identity(),original.answer(),original.trace(),null,false,null,false,null,false,Map.of());
            return RuleFactExecutionEvaluation.evaluate(frozen.dataset(),frozen.sha256(),missing,turn.alternatives().getFirst());
        }
        Set<String> retrieved=new HashSet<>(); boolean complete=true, searched=false;
        for (var obs:captured.searches()) {
            searched=true;
            if (!original.identity().runId().equals(obs.runId())) {complete=false;continue;}
            boolean traced=original.trace().actions().stream().anyMatch(a->a.step()==obs.step()
                    && "SEARCH_REVIEWED_RULES".equals(a.type()) && a.observation()!=null && obs.status().equals(a.observation().status()));
            if(!traced){complete=false;continue;}
            if ("NO_MATCH".equals(obs.status())) continue;
            if (!"SUCCEEDED".equals(obs.status())) {complete=false;continue;}
            var sides=captured.retrieval().stream().filter(s->s.observationId().equals(obs.observationId())
                    && s.runId().equals(obs.runId()) && s.taskId().equals(original.identity().taskId()) && s.step()==obs.step()).toList();
            if(sides.size()!=1){complete=false;continue;}
            var side=sides.getFirst();
            if(side.candidates().size()!=obs.candidates().size()){complete=false;continue;}
            for(var candidate:obs.candidates()) {
                var found=side.candidates().stream().filter(s->s.handle().equals(candidate.handle()) && s.revision().equals(candidate.revision())).toList();
                if(found.size()!=1){complete=false;continue;}
                String locator=frozen.locator(found.getFirst().revision(),found.getFirst().checksum());
                if(locator==null){complete=false;continue;} retrieved.add(locator);
            }
        }
        var citations=new ArrayList<Citation>();
        // Only published evidence is allowed into the citation inventory.
        var result=captured.publishedResult();
        for(var evidence:result.path("evidence")) {
            String locator=frozen.locator(evidence.path("revision").asText(),evidence.path("checksum").asText());
            Verification verified=annotation.citationReviews()==null?Verification.UNKNOWN:
                    annotation.citationReviews().getOrDefault(evidence.path("id").asText(),Verification.UNKNOWN);
            if(locator==null) {locator="UNMAPPED:"+evidence.path("id").asText();verified=Verification.MISMATCH;}
            citations.add(new Citation(locator,verified));
        }
        var c=frozen.dataset().caseById(turn.caseId());
        var scoped=new Case(c.id(),turn.factIds(),c.optionalFactIds(),c.requiredLocators());
        var input=new Execution(original.identity(),original.answer(),original.trace(),retrieved,complete&&searched,
                annotation.matchedFacts(),annotation.factsComplete(),citations,annotation.citationsComplete(),annotation.reviews());
        // Choose only among alternatives fixed before execution. No actual value is used to construct expectations.
        Result best=null;
        for(var expectation:turn.alternatives()) {
            var r=RuleFactExecutionEvaluation.evaluate(new Dataset(Map.of(c.id(),scoped)),frozen.sha256(),input,expectation);
            if(best==null || rank(r.outcome())<rank(best.outcome()))best=r;
        }
        if(best==null)throw new IllegalArgumentException("no execution contract");
        boolean noClaims=true;for(var item:captured.publishedResult().path("items"))if(!item.path("claims").isEmpty())noClaims=false;
        if(turn.factIds().isEmpty()&&noClaims&&best.dimensions().get("factSupport").verdict()==Verdict.PASS) {
            var dimensions=new LinkedHashMap<>(best.dimensions());
            dimensions.put("factSupport",new Dimension(Verdict.NOT_APPLICABLE,List.of("NO_REQUIRED_FACTS_FOR_TURN_AND_NO_OBSERVED_CLAIMS; unsupported claims independently reviewed")));
            best=new Result(best.identity(),best.outcome(),dimensions);
        }
        return best;
    }
    private static int rank(String outcome){return switch(outcome){case "PASS"->0;case "UNJUDGEABLE"->1;default->2;};}
    public record Report(String datasetSha256, String captureSha256, int denominator, long passed, long failed,
                         long unjudgeable, Map<String,Result> cases, Result observedTurn) {}
    public static Report report(Frozen frozen,Captured captured,String captureSha,Annotation annotation) {
        Result result=evaluate(frozen,captured,captureSha,annotation);
        Map<String,Result> cases=new TreeMap<>();
        for(String id:frozen.dataset().cases().keySet()) {
            Map<String,Dimension> missing=new LinkedHashMap<>();
            DIMENSIONS.forEach(k->missing.put(k,new Dimension(Verdict.UNJUDGEABLE,List.of("EXECUTION_MISSING"))));
            cases.put(id,new Result(null,"UNJUDGEABLE",missing));
        }
        // Continuations do not substitute for the frozen question's required clarification turn.
        if(captured.turn().equals(frozen.turns(captured.turn().caseId()).getFirst()))cases.put(captured.turn().caseId(),result);
        return new Report(frozen.sha256(),captureSha,cases.size(),cases.values().stream().filter(r->"PASS".equals(r.outcome())).count(),
                cases.values().stream().filter(r->"FAIL".equals(r.outcome())).count(),
                cases.values().stream().filter(r->"UNJUDGEABLE".equals(r.outcome())).count(),cases,result);
    }
    public static void write(Path file,Captured captured)throws Exception {
        var node=JSON.valueToTree(captured);redact(node);
        Files.write(file,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(node),StandardOpenOption.CREATE_NEW);
    }
    private static void redact(com.fasterxml.jackson.databind.JsonNode node){
        if(node.isObject())((com.fasterxml.jackson.databind.node.ObjectNode)node).remove(List.of("originalToken","continuationToken"));
        node.forEach(RuleFactBoundEvaluation::redact);
    }
    /** root capture.json annotation.json -> six dimensional result; no provider calls. */
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("root capture annotation required");
        byte[] bytes=Files.readAllBytes(Path.of(args[1]));
        System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report(load(Path.of(args[0])),
                JSON.readValue(bytes,Captured.class),sha(bytes),JSON.readValue(Files.readAllBytes(Path.of(args[2])),Annotation.class))));
    }
}
