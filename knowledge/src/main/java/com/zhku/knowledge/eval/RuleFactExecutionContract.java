package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.ruleagent.RuleAgentTraceEvaluator;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Pre-execution turn contracts derived from the frozen question contract, never from model output. */
public final class RuleFactExecutionContract {
    public static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    public static final String DIRECTORY = "docs/evidence/agent-eval/2026-11-17-frozen-gold-v1";
    public record Turn(String caseId, String kind, String question, String questionSha256,
                       String reply, Set<String> factIds, List<RuleAgentTraceEvaluator.Expectation> alternatives) {
        public Turn {
            factIds=Collections.unmodifiableSortedSet(new TreeSet<>(factIds));
            alternatives=List.copyOf(alternatives);
        }
    }
    public record Frozen(RuleFactOfflineScorer.Dataset dataset, String sha256, JsonNode data) {
        public JsonNode item(String id) {
            dataset.caseById(id);
            for (var item : data.path("cases")) if (id.equals(item.path("caseId").asText())) return item;
            throw new IllegalArgumentException("unknown case");
        }
        public List<Turn> turns(String id) {
            var c = item(id); String q = c.path("question").asText();
            var facts = dataset.caseById(id).requiredFactIds();
            if ("ASK_CLARIFICATION".equals(c.path("expectedDisposition").asText())) {
                // ASK is the frozen question's acceptance turn; branch continuation is a separate observation.
                return List.of(turn(id,"ASK",q,null,Set.of(), List.of(expected(id,"WAITING_USER","INCOMPLETE","NONE",false,
                        List.of("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","ASK_NECESSARY_CONDITION"),"MISSING_CONDITION"))),
                        turn(id,"RESOLVE_PUBLIC",q,"PUBLIC",Set.of("public-failed-no-retake"),
                                List.of(expected(id,"RESOLVED","SUCCEEDED","COMPLETE",true,List.of("FINISH_CHECK","PUBLICATION_REVIEW"),null))),
                        turn(id,"RESOLVE_PROFESSIONAL",q,"PROFESSIONAL",Set.of("professional-failed-retake-application"),
                                List.of(expected(id,"RESOLVED","SUCCEEDED","COMPLETE",true,List.of("FINISH_CHECK","PUBLICATION_REVIEW"),null),
                                        expected(id,"RESOLVED","INCOMPLETE","PARTIAL",true,List.of("FINISH_CHECK","PUBLICATION_REVIEW"),null))));
            }
            boolean claims = !facts.isEmpty();
            String disposition = c.path("expectedDisposition").asText();
            var alternatives = new ArrayList<RuleAgentTraceEvaluator.Expectation>();
            String issue=switch(c.path("sourceCoverage").asText()) {
                case "PERSONAL_RECORD_GAP"->"PERSONAL_RECORD_NOT_AVAILABLE";
                case "CURRENT_NOTICE_GAP"->"CURRENT_NOTICE_MISSING";
                case "CURRENT_APPLICABILITY_UNKNOWN"->"CURRENT_APPLICABILITY_UNKNOWN";
                default->null;
            };
            var actions = List.of("SEARCH_REVIEWED_RULES","READ_VERIFIED_RULE","FINISH_CHECK","PUBLICATION_REVIEW");
            if ("ANSWER_WITH_EVIDENCE".equals(disposition)) alternatives.add(expected(id,"RESOLVED","SUCCEEDED","COMPLETE",claims,actions,null));
            else if ("PARTIAL_WITH_GAP".equals(disposition) || "GAP_AND_NEXT_STEP".equals(disposition))
                alternatives.add(expected(id,"RESOLVED","INCOMPLETE",claims?"PARTIAL":"NONE",claims,
                        claims?actions:List.of("SEARCH_REVIEWED_RULES","FINISH_CHECK","PUBLICATION_REVIEW"),issue));
            else { // Qualified answers may publish a historical fact with an explicit applicability gap.
                alternatives.add(expected(id,"RESOLVED","SUCCEEDED","COMPLETE",claims,actions,null));
                alternatives.add(expected(id,"RESOLVED","INCOMPLETE","PARTIAL",claims,actions,null));
            }
            return List.of(turn(id,"ANSWER",q,null,facts,alternatives));
        }
        public String locator(String revision, String checksum) {
            if (!"2025.9.2".equals(revision)) return null;
            Set<String> matches = new HashSet<>();
            for (var c : data.path("cases")) for (var e : c.path("requiredEvidence"))
                if (e.path("contentChecksum").asText().equalsIgnoreCase(checksum)) matches.add(e.path("locatorId").asText());
            return matches.size()==1?matches.iterator().next():null;
        }
    }
    private static Turn turn(String id,String kind,String q,String reply,Set<String> facts,List<RuleAgentTraceEvaluator.Expectation> expected) {
        return new Turn(id,kind,q,sha(q.getBytes(java.nio.charset.StandardCharsets.UTF_8)),reply,facts,List.copyOf(expected));
    }
    private static RuleAgentTraceEvaluator.Expectation expected(String id,String status,String state,String coverage,boolean claims,List<String> actions,String issue) {
        return new RuleAgentTraceEvaluator.Expectation(id,status,state,coverage,actions,issue,claims,null,null,claims,false);
    }
    public static Frozen load(Path root) throws Exception {
        Path d=root.resolve(DIRECTORY), file=d.resolve("rule-fact-check-effect-v1.0.json");
        var dataset=RuleFactOfflineScorer.load(file,d.resolve("manifest.json"));
        byte[] bytes=Files.readAllBytes(file);return new Frozen(dataset,sha(bytes),JSON.readTree(bytes));
    }
    public static String sha(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
