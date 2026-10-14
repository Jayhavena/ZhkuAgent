package com.zhku.knowledge.ruleagent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
/**
 * 零调用测量核验请求载荷：与真机同一套 RulePublicationContract.input + 能力目录 + reviewPayload。
 * 目的：判断 P01 独立核验超时是"载荷偏大"还是"生成耗时"，不涉及任何外发。
 */
@Timeout(30)
class RuleAgentReviewPayloadBudgetTest {
    static final String P01_QUESTION="专业选修课和公共选修课在修读安排上有什么不同？";
    final RuleAgentRunnerTest fixture=new RuleAgentRunnerTest();
    /** 与生产探针同源：真实已审核第四条导入件 + 人审增量覆盖，不安装数据库。 */
    List<RuleCheckApplicationService.CatalogEntry> reviewedArticle4()throws Exception{
        var root=java.nio.file.Path.of("..").toAbsolutePath().normalize();
        var bundle=new ObjectMapper().readTree(java.nio.file.Files.readAllBytes(root.resolve("docs/product/rule-check-2025-verbatim-import-draft.json")));
        var row=java.util.stream.StreamSupport.stream(bundle.path("chunks").spliterator(),false).filter(c->c.path("article").asInt()==4).findFirst().orElseThrow();
        String text=row.path("content").asText();
        String checksum=row.path("contentChecksum").asText();
        var binding=new RuleCheckRuleTemplate.EvidenceBinding(fixture.source,fixture.chunk,"2025.9.2",checksum,0,text.length(),text);
        var baseFact=new RuleCheckRuleTemplate.Fact("binding-only","仅供原文绑定",true,Map.of(),List.of(binding));
        var template=new RuleCheckRuleTemplate("S02/GENERAL","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"LOCAL_BINDING_TEST",java.time.LocalDate.of(2026,10,14),List.of(baseFact),List.of(),List.of(),List.of());
        var entry=new RuleCheckApplicationService.CatalogEntry(template,"本地绑定测试",new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),RuleCheckTopicGuard.Coverage.declared(RuleCheckTopicGuard.Topic.CATEGORY));
        return RuleArticle4Overlay.apply(root,List.of(entry));
    }
    RulePublicationContract.SemanticInput realReviewInput(List<RuleCheckApplicationService.CatalogEntry> entries){
        var input=RuleAgentRunner.Input.single(fixture.owner,fixture.task,P01_QUESTION);
        var facts=new ArrayList<RuleAgentEvidenceTools.VerifiedFact>();
        for(var entry:entries)for(var fact:entry.template().facts())facts.add(new RuleAgentEvidenceTools.VerifiedFact(entry.template().ruleId()+":"+fact.id(),entry.template().ruleId(),fact));
        return RulePublicationContract.input(input,List.copyOf(facts),RuleFactCapabilityCatalogue.fromReviewed(entries));
    }
    RuleAgentProvider port(){
        return new RuleAgentProvider(new RuleAgentProvider.Settings(URI.create("http://127.0.0.1:19999/chat/completions"),RuleAgentProvider.MODEL,"LOCAL_TEST_ONLY"),
            UUID.randomUUID(),UUID.randomUUID(),()->"local-test-placeholder",t->{},new AtomicInteger(0));
    }
    @Test void reviewPayloadStaysWithinRequestBudgetAndBreakdownIsRecorded()throws Exception{
        var entries=reviewedArticle4();
        var safe=realReviewInput(entries);
        assertThat(safe.facts()).isNotEmpty();
        assertThat(safe.capabilities()).isNotEmpty();
        try(var provider=port()){
            String payload=provider.reviewPayload(safe);
            int total=payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            assertThat(total).isLessThan(DEFAULT_BUDGET.maxRequestBytes());
            var json=new ObjectMapper().readTree(payload);
            var messages=json.path("messages");
            int systemBytes=messages.get(0).path("content").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            var data=new ObjectMapper().readTree(messages.get(1).path("content").asText());
            var mb=(java.util.function.Function<com.fasterxml.jackson.databind.JsonNode,Integer>)n->{
                try{return new ObjectMapper().writeValueAsBytes(n).length;}catch(Exception e){throw new IllegalStateException(e);}
            };
            var inputNode=data.path("input");
            Map<String,Integer> breakdown=new LinkedHashMap<>();
            breakdown.put("systemPrompt",systemBytes);
            breakdown.put("reviewContract",mb.apply(data.path("reviewContract")));
            breakdown.put("remainingBudget",mb.apply(data.path("remainingBudget")));
            breakdown.put("input.facts",mb.apply(inputNode.path("facts")));
            breakdown.put("input.capabilities",mb.apply(inputNode.path("capabilities")));
            breakdown.put("input.questions",mb.apply(inputNode.path("questions")));
            breakdown.put("input.conditions",mb.apply(inputNode.path("conditions")));
            breakdown.put("input.wrapper",mb.apply(inputNode));
            breakdown.put("total",total);
            int facts=inputNode.path("facts").size();
            var out=new ObjectMapper().createObjectNode();
            out.put("model",RuleAgentProvider.MODEL);
            out.put("maximumRequestBytes",DEFAULT_BUDGET.maxRequestBytes());
            out.put("measuredRequestBytes",total);
            out.put("reviewContractRevision",RulePublicationContract.REVISION);
            out.put("factsIncluded",facts);
            out.put("capabilitiesIncluded",inputNode.path("capabilities").size());
            var bd=out.putObject("breakdownBytes");
            breakdown.forEach(bd::put);
            java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("zhku.rulecheck.evidenceDir"),"review-payload-budget.json"),
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(out),
                java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            // 记录可见断言：测量结果必须在预算内，且事实与能力目录非空，否则测量无意义。
            assertThat(breakdown.get("total")).isEqualTo(total);
            assertThat(facts).isPositive();
        }
    }
}
