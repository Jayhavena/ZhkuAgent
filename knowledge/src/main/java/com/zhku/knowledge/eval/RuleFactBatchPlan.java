package com.zhku.knowledge.eval;

import java.nio.file.*;
import java.util.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;

/** Frozen subset or complete development set; no sampling/replacement after observing answers. */
public final class RuleFactBatchPlan {
    public static final String SELECTION=DIRECTORY+"/smoke-subset.json";
    public static final List<String> IDS=List.of("RFE-001","RFE-002","RFE-003","RFE-004","RFE-005","RFE-006","RFE-007","RFE-013","RFE-018","RFE-020");
    public static final List<String> REPAIR_IDS=List.of("RFE-001","RFE-004","RFE-007","RFE-008","RFE-010","RFE-013","RFE-014","RFE-015","RFE-016");
    public enum Selection { SUBSET_10, FULL_20, REPAIR_9 }
    static Selection selectionFor(int taskCount) {
        return switch(taskCount) {
            case 9 -> Selection.REPAIR_9;
            case 10 -> Selection.SUBSET_10;
            case 20 -> Selection.FULL_20;
            default -> throw new IllegalArgumentException("unknown frozen plan size");
        };
    }
    public record CasePlan(String caseId,String question,String clarificationReply,String conditionId,
                           List<Turn> turns,int maximumCalls) {}
    public record Plan(String datasetSha256,String selectionSha256,List<CasePlan> cases,
                       int maximumTasks,int maximumRuns,int maximumCalls,long ownedProcessMillis) {}
    public static Plan load(Path root)throws Exception {
        return load(root,false);
    }
    public static Plan load(Path root,boolean full)throws Exception {
        return load(root,full?Selection.FULL_20:Selection.SUBSET_10);
    }
    public static Plan load(Path root,Selection selectionMode)throws Exception {
        var frozen=RuleFactExecutionContract.load(root);byte[] bytes;var ids=new ArrayList<String>();
        var asking=new TreeSet<String>();for(var item:frozen.data().path("cases"))if("ASK_CLARIFICATION".equals(item.path("expectedDisposition").asText()))asking.add(item.path("caseId").asText());
        if(!asking.equals(Set.of("RFE-013")))throw new IllegalArgumentException("clarification selection drift");
        if(selectionMode==Selection.FULL_20){ids.addAll(new TreeSet<>(frozen.dataset().cases().keySet()));bytes=JSON.writeValueAsBytes(ids);}
        else if(selectionMode==Selection.REPAIR_9){ids.addAll(REPAIR_IDS);bytes=JSON.writeValueAsBytes(ids);}
        else {bytes=Files.readAllBytes(root.resolve(SELECTION));var selection=JSON.readTree(bytes);selection.path("caseIds").forEach(n->ids.add(n.asText()));
            if(!ids.equals(IDS)||!frozen.sha256().equals(selection.path("datasetSha256").asText()))throw new IllegalArgumentException("ten-case selection drift");}
        var cases=new ArrayList<CasePlan>();int runs=0,calls=0;
        for(String id:ids) {
            var turns=frozen.turns(id);boolean clarification="RFE-013".equals(id);
            var selected=clarification?List.of(turns.get(0),turns.get(1)):List.of(turns.getFirst());
            if(clarification&&(!"ASK".equals(selected.getFirst().kind())||!"RESOLVE_PUBLIC".equals(selected.get(1).kind())||!"PUBLIC".equals(selected.get(1).reply())))throw new IllegalArgumentException("clarification contract drift");
            int maximum=selected.size()*com.zhku.knowledge.ruleagent.RuleAgentContract.DEFAULT_BUDGET.maxModelAttemptsPerRun();
            cases.add(new CasePlan(id,turns.getFirst().question(),clarification?"PUBLIC":null,clarification?"courseType":null,selected,maximum));
            runs+=selected.size();calls+=maximum;
        }
        long hostMillis=switch(selectionMode){case FULL_20 -> 2_550_000;case SUBSET_10 -> 1_350_000;case REPAIR_9 -> 1_250_000;};
        return new Plan(frozen.sha256(),sha(bytes),List.copyOf(cases),cases.size(),runs,calls,hostMillis);
    }
}
