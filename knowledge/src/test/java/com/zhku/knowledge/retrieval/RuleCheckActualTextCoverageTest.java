package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 实际已审核原文上的本地词法回归，无数据库、向量、真实模型或人工新评分。 */
class RuleCheckActualTextCoverageTest {
    @Test void actualTwentyArticlesRetainGradeCompositionAndSubmissionWithinFiveCaptures() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();var json=new ObjectMapper();
        var chunks=new ArrayList<RetrievedChunk>();var snapshots=new HashMap<UUID,RuleCheckEvidenceSnapshot>();
        var entries=new ArrayList<RuleCheckApplicationService.CatalogEntry>();UUID source=UUID.randomUUID();
        for(String filename:List.of("rule-check-2025-verbatim-import-draft.json","rule-check-2025-report-source-supplement.json")) {
            for(var c:json.readTree(Files.readAllBytes(root.resolve("docs/product/"+filename))).path("chunks")) {
                UUID id=UUID.randomUUID();String text=c.path("content").asText(),hash=c.path("contentChecksum").asText();int article=c.path("article").asInt();
                chunks.add(new RetrievedChunk(id,text,"2025版原文",c.path("sourceLocator").asText(),c.path("physicalPage").asInt(),"2025.9.2",hash,source));
                snapshots.put(id,new RuleCheckEvidenceSnapshot("fresh",source,id,"2025.9.2",hash,text,true));
                if(article==16||article==17) {
                    var binding=new RuleCheckRuleTemplate.EvidenceBinding(source,id,"2025.9.2",hash,0,text.length(),text);
                    var rule=new RuleCheckRuleTemplate("A%02d/GENERAL".formatted(article),"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,
                        "本地回归装配",LocalDate.of(2026,10,11),List.of(new RuleCheckRuleTemplate.Fact("article"+article,text,true,Map.of(),List.of(binding))),List.of(),List.of(),List.of());
                    entries.add(new RuleCheckApplicationService.CatalogEntry(rule,text,
                        new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),
                        RuleCheckTopicGuard.Coverage.declared(article==16?RuleCheckTopicGuard.Topic.GRADE_COMPOSITION:RuleCheckTopicGuard.Topic.GRADE_SUBMISSION)));
                }
            }
        }
        assertThat(chunks).hasSize(20);
        var verifier=new RuleCheckEvidenceVerifier((s,c)->Optional.ofNullable(s.equals(source)?snapshots.get(c):null));
        var capture=new RuleCheckEvidenceCapture(verifier,()->UUID.randomUUID().toString(),r->{},Clock.systemUTC());
        var app=RuleCheckApplicationService.evidenceFirst(new RuleCheckRuntimeSettings("synthetic-index-actual-text","cut","scope","2025.9.2",20,5),
            ()->entries,(q,o)->new RuleCheckTaskPlan(List.of(new RuleCheckTaskPlan.Task("A17/GENERAL",RuleCheckTaskPlan.Action.EXPLAIN_RULE,"",""))),
            new HybridRetrievalService(new HybridCandidateFuser()),(q,n)->ChineseBigramLexicalRanker.rank(q,chunks,n),(q,n)->List.of(),capture,new RuleCheckDecisionService(verifier));
        var result=app.checkWithContext("仅依据2025版手册历史文本：最终成绩的组成是什么，谁负责按规定时间提交学生成绩？",Map.of());
        assertThat(result.decision().status()).isEqualTo(RuleCheckDecision.Status.ANSWERED);
        assertThat(result.decision().confirmedFacts()).extracting(RuleCheckDecision.SupportedFact::id).contains("article16","article17");
        assertThat(result.retrievalTrace().references()).hasSizeLessThanOrEqualTo(5);
        assertThat(result.retrievalTrace().originalLocators()).anyMatch(s->s.endsWith("第16条")).anyMatch(s->s.endsWith("第17条"));
        assertThat(app.isCurrent(result)).isTrue();
    }
}
