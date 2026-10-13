package com.zhku.knowledge.retrieval;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;

/** 本地实验请求准备；不连接供应商，不能把准备成功算作实际模型选题成绩。 */
final class RuleCheckSelectionPreparation {
    static List<Map<String,Object>> build(Path root,Reviewed2025Trial trial,RuleCheckDevelopmentInputs.Loaded inputs) throws Exception {
        return build(root,trial,inputs,null);
    }
    /** resolver只接受应用实际传给selector的候选；不能按题号或人工参考收窄。 */
    static List<Map<String,Object>> build(Path root,Reviewed2025Trial trial,RuleCheckDevelopmentInputs.Loaded inputs,
            java.util.function.Function<String,List<RuleCheckApplicationService.RuleOption>> resolver) throws Exception {
        trial.validate();RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);
        byte[] language=Files.readAllBytes(root.resolve("docs/product/rule-check-c05-language-cases.json"));
        if(!Reviewed2025Trial.sha(language).equals("e6e6f9d21ff1c5341fc5c0a7ed2c795a97ae6421b1b3a48a5026bff9d0964277"))
            throw new IllegalArgumentException("未评分探针来源发生变化");
        var mapper=new ObjectMapper();var probes=mapper.readTree(language).path("observationOnlyProbes");
        var fullOptions=trial.catalogue.stream().map(e->new RuleCheckApplicationService.RuleOption(e.template().ruleId(),e.questionScope())).toList();
        var rows=new ArrayList<Map<String,Object>>();
        for(int i=0;i<24;i++){
            var item=i<20?inputs.reference().path("records").get(i):probes.get(i-20);
            String id=i<20?"Q%02d".formatted(i+1):"O%02d".formatted(i-19),question=item.path("question").asText();
            var options=resolver==null?fullOptions:List.copyOf(resolver.apply(question));
            if(options.isEmpty()){
                rows.add(Map.of("caseId",id,"kind",i<20?"DEVELOPMENT_ORIGINAL":"UNGRADED_RISK_PROBE",
                    "question",question,"semanticGradeAvailable",false,"localBlocked",true,"providerCalls",0));
                continue;
            }
            var seen=new AtomicReference<BoundedRuleSelector.Request>();
            var selector=new BoundedRuleSelector(request->{seen.set(request);return "{\"ruleId\":null}";},Duration.ofSeconds(5),1);
            BoundedRuleSelector.Outcome outcome;
            try {outcome=selector.selectOutcome(question,options);}
            finally {
                selector.close();long deadline=System.nanoTime()+Duration.ofSeconds(2).toNanos();
                while((selector.inFlight()!=0||selector.liveWorkers()!=0)&&System.nanoTime()<deadline)Thread.sleep(2);
                if(selector.inFlight()!=0||selector.liveWorkers()!=0)throw new IllegalStateException("本地准备worker未退出");
            }
            if(outcome.code()!=BoundedRuleSelector.Code.ABSTAINED||seen.get()==null)
                throw new IllegalArgumentException("选题请求未通过现有准入合同："+outcome.code());
            var request=seen.get();
            // 使用实际selector组装消息；不把人工答案、固定ID控制或数据库绑定放入用户payload。
            var body=Map.<String,Object>of("model","qwen-plus","messages",List.of(
                Map.of("role","system","content",request.systemConstraints()),Map.of("role","user","content",request.userPayload())),
                "response_format",Map.of("type","json_object"),"enable_thinking",false,"stream",false,"temperature",0,"max_tokens",128);
            if(mapper.writeValueAsBytes(body).length>BoundedRuleSelector.MAX_REQUEST_BYTES)throw new IllegalArgumentException("HTTP请求正文超预算");
            rows.add(Map.of("caseId",id,"kind",i<20?"DEVELOPMENT_ORIGINAL":"UNGRADED_RISK_PROBE","semanticGradeAvailable",false,
                "syntheticTransportOutcome",outcome.code().name(),"body",body,"maxInnerResponseBytes",request.maxResponseBytes()));
        }
        RuleCheckDevelopmentInputs.verifyUnchanged(root,inputs);return List.copyOf(rows);
    }
}
