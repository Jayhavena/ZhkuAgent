package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rulecheck.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** 只在test源码采集内部控制实验，不是自然语言选择器或在线评估开关。 */
final class RuleCheckDevelopmentCapture {
    record LocationTrace(RuleCheckOriginalEvidenceService.Result result,String errorType,long nanos){}
    static LocationTrace locate(RuleCheckVerifiedExcerpt quote,RuleCheckOriginalEvidenceService.Reader reader){
        long start=System.nanoTime();var error=new String[1];
        // 生产定位服务会安全降级；在reader边界观察异常，才能区分资料缺失与执行错误。
        var service=new RuleCheckOriginalEvidenceService((s,c)->{
            try{return reader.read(s,c);}catch(RuntimeException failure){error[0]=failure.getClass().getSimpleName();throw failure;}
        },"2025.9.2",Set.of());
        return new LocationTrace(service.locate(quote),error[0],System.nanoTime()-start);
    }
    static void persistBeforeValidation(Path file,List<String> lines,Runnable validation) throws Exception {
        // 即使独立控制失败，实际坏样本也必须保留；CREATE_NEW防止重跑覆盖历史。
        Files.write(file,lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);validation.run();
    }
    record Input(String caseId,String question,String selectedRuleId,Map<String,String> confirmedConditions){
        Input {Objects.requireNonNull(caseId);Objects.requireNonNull(question);Objects.requireNonNull(selectedRuleId);confirmedConditions=Map.copyOf(confirmedConditions);}
    }
    record PathTrace(String status,List<RetrievedChunk> candidates,String errorType,long nanos){
        PathTrace {candidates=List.copyOf(candidates);}
    }
    record Attempt(UUID attemptId,String caseId,String question,String selectedRuleId,String selectionQuality,
        String status,RetrievalOutcome retrieval,RuleCheckScopedResult output,PathTrace lexical,PathTrace vector,
        String errorType,long applicationNanos,long totalNanos){}
    @FunctionalInterface interface Check {RuleCheckScopedResult run(String q,Map<String,String> c);}
    @FunctionalInterface interface Factory {Check create(ChunkCandidateRetriever lex,ChunkCandidateRetriever vector);}
    Attempt collect(Input input,ChunkCandidateRetriever lex,ChunkCandidateRetriever vector,Factory factory){
        long start=System.nanoTime(),applicationNanos=0;
        var lexical=new Probe(lex,input.question());var semantic=new Probe(vector,input.question());
        RetrievalOutcome outcome=null;RuleCheckScopedResult output=null;String error=null;
        try {
            outcome=new HybridRetrievalService(new HybridCandidateFuser()).retrieve(input.question(),20,5,lexical,semantic);
            // 一次尝试内部回放已观察候选给真实应用：不重复数据库检索，不把该控制结果当在线端到端路径。
            long applicationStart=System.nanoTime();
            try {output=Objects.requireNonNull(factory.create(lexical::replay,semantic::replay).run(input.question(),input.confirmedConditions()));}
            finally {applicationNanos=System.nanoTime()-applicationStart;}
        } catch(RuntimeException failure){
            // 异常消息可能带凭据或私密正文；类型足够区分采集失效，不写原异常消息。
            error=failure.getClass().getSimpleName();
        }
        return new Attempt(UUID.randomUUID(),input.caseId(),input.question(),input.selectedRuleId(),"NOT_EVALUATED",
            error==null?"COLLECTED":"COLLECTION_ERROR",outcome,output,lexical.trace,semantic.trace,error,applicationNanos,System.nanoTime()-start);
    }
    private static final class Probe implements ChunkCandidateRetriever {
        private final ChunkCandidateRetriever actual;private final String question;
        private PathTrace trace=new PathTrace("NOT_RUN",List.of(),null,0);
        private Probe(ChunkCandidateRetriever actual,String question){this.actual=actual;this.question=question;}
        @Override public List<RetrievedChunk> retrieve(String q,int limit){
            long start=System.nanoTime();
            try {var values=List.copyOf(actual.retrieve(q,limit));trace=new PathTrace("AVAILABLE",values,null,System.nanoTime()-start);return values;}
            catch(RuntimeException failure){trace=new PathTrace(failure instanceof RetrieverUnavailableException?"UNAVAILABLE":"ERROR",List.of(),failure.getClass().getSimpleName(),System.nanoTime()-start);throw failure;}
        }
        private List<RetrievedChunk> replay(String q,int limit){
            if(!question.equals(q)||limit!=20)throw new IllegalArgumentException("回放问题或候选预算与本次观测不符");
            if(trace.status().equals("UNAVAILABLE"))throw new RetrieverUnavailableException("本次已观测该路不可用");
            if(!trace.status().equals("AVAILABLE"))throw new IllegalStateException("该路没有可回放的实际结果");
            return trace.candidates();
        }
    }
}
