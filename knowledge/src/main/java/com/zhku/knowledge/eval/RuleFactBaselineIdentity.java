package com.zhku.knowledge.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;

/** 比较实际配置身份，不能把不同分母或不同提示词的分数直接解释为质量变化。 */
public final class RuleFactBaselineIdentity {
    static ObjectNode create(Path root,ObjectNode pack,boolean full)throws Exception {
        full=pack.path("batchPlan").path("maximumTasks").asInt()==20;
        for(String key:List.of("datasetSha256","model","endpoint","systemPrompt","reviewSystemPrompt","jarSha256"))
            if(pack.path(key).asText().isBlank())throw new IllegalArgumentException("baseline identity missing: "+key);
        var result=JSON.createObjectNode();result.put("baselineId",full?"gold-v1.0-full-development-single-pass":
                pack.path("batchPlan").path("maximumTasks").asInt()==9?"gold-v1.0-repair-nine-single-pass":"gold-v1.0-m2-subset-single-pass");
        result.put("datasetVersion",load(root).data().path("datasetVersion").asText());result.put("datasetSha256",pack.path("datasetSha256").asText());
        result.put("caseSelectionSha256",pack.path("batchPlan").path("selectionSha256").asText());
        result.put("maximumTasks",pack.path("batchPlan").path("maximumTasks").asInt());result.put("maximumRuns",pack.path("batchPlan").path("maximumRuns").asInt());
        result.put("requestedModel",pack.path("model").asText());result.put("endpoint",pack.path("endpoint").asText());
        result.put("decisionPromptSha256",sha(pack.path("systemPrompt").asText().getBytes(StandardCharsets.UTF_8)));
        result.put("reviewPromptSha256",sha(pack.path("reviewSystemPrompt").asText().getBytes(StandardCharsets.UTF_8)));
        result.put("actionProtocolRevision",pack.path("protocolRevision").asText());result.put("actionSchemaSha256",stableSha(pack.path("actionSchema")));
        result.put("publicationProtocolRevision",pack.path("publicationReviewProtocolRevision").asText());
        result.put("publicationContractSha256",stableSha(pack.path("publicationReviewContract")));
        result.put("perRunBudgetSha256",stableSha(pack.path("budget")));
        result.put("runtimeAssetsSha256",stableSha(pack.path("runtimeAssets")));
        result.put("actionContractSha256",stableSha(pack.path("actionContract")));
        result.put("factCapabilityVersion",pack.path("factCapabilityVersion").asText());
        result.put("factCapabilityAuthoritySha256",pack.path("factCapabilityAuthoritySha256").asText());
        result.put("providerSourceSha256",source(root,pack,"ruleagent/RuleAgentProvider.java"));
        var business=result.putObject("knowledgeBusinessSources");
        pack.path("sourceIdentity").fields().forEachRemaining(e->{if(!e.getKey().contains("/knowledge/eval/"))business.set(e.getKey(),e.getValue());});
        var harness=result.putObject("evaluationSources");
        pack.path("sourceIdentity").fields().forEachRemaining(e->{if(e.getKey().contains("/knowledge/eval/"))harness.set(e.getKey(),e.getValue());});
        var tools=result.putObject("toolSources");for(String file:List.of("ruleagent/RuleAgentEvidenceTools.java","ruleagent/RuleActionBroker.java"))tools.put(file,source(root,pack,file));
        var graders=result.putObject("gradingSources");for(String file:List.of("eval/RuleFactExecutionContract.java","eval/RuleFactExecutionEvaluation.java","eval/RuleFactOfflineScorer.java","eval/RuleFactBoundEvaluation.java","ruleagent/RuleAgentTraceEvaluator.java"))graders.put(file,source(root,pack,file));
        result.put("runtimeJarSha256",pack.path("jarSha256").asText());result.put("repetitions",1);result.put("failureTaxonomyRevision","OBSERVED_LAYER_V1");
        result.put("annotationPolicy","Independent review, not user human sign-off; required historical/branch explanations judged in answer text; correct metadata alone does not supply missing prose. Failure notifications do not establish substantive scope/gap quality.");
        return result;
    }
    private static String source(Path root,JsonNode pack,String suffix)throws Exception {
        String path="knowledge/src/main/java/com/zhku/knowledge/"+suffix;
        if(!pack.path("sourceIdentity").has(path))throw new IllegalArgumentException("source identity missing: "+path);
        return pack.path("sourceIdentity").path(path).asText();
    }
    static String stableSha(JsonNode node)throws Exception {return sha(JSON.writeValueAsBytes(canonical(node)));}
    private static JsonNode canonical(JsonNode node) {
        if(node.isObject()){var result=JSON.createObjectNode();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);keys.forEach(k->result.set(k,canonical(node.get(k))));return result;}
        if(node.isArray()){var result=JSON.createArrayNode();node.forEach(n->result.add(canonical(n)));return result;}
        return node.deepCopy();
    }
    public static Map<String,Boolean> compare(JsonNode previous,JsonNode current) {
        var result=new LinkedHashMap<String,Boolean>();for(String key:List.of("datasetSha256","requestedModel","endpoint","decisionPromptSha256","reviewPromptSha256","actionProtocolRevision","actionSchemaSha256","actionContractSha256","publicationProtocolRevision","publicationContractSha256","perRunBudgetSha256","runtimeAssetsSha256","factCapabilityVersion","factCapabilityAuthoritySha256","providerSourceSha256","knowledgeBusinessSources","toolSources","gradingSources"))
            result.put(key,!previous.path(key).isMissingNode()&&!current.path(key).isMissingNode()&&previous.path(key).equals(current.path(key)));
        for(String key:List.of("datasetVersion","caseSelectionSha256","maximumTasks","maximumRuns","runtimeJarSha256","evaluationSources"))
            result.put(key,!previous.path(key).isMissingNode()&&!current.path(key).isMissingNode()&&previous.path(key).equals(current.path(key)));
        return result;
    }
    /** Offline identity export from two frozen packages; no model/database access. */
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("root previous-package current-package output required");
        var root=Path.of(args[0]);var before=create(root,(ObjectNode)JSON.readTree(Files.readAllBytes(Path.of(args[1]))),false);
        var after=create(root,(ObjectNode)JSON.readTree(Files.readAllBytes(Path.of(args[2]))),true);
        Files.write(Path.of(args[3]),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("previous",before,"current",after,"equalFields",compare(before,after))),StandardOpenOption.CREATE_NEW);
    }
}
