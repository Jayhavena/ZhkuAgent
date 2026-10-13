package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Topic.*;
import static org.assertj.core.api.Assertions.*;

/** 只验证实际编排送入选择器的候选边界，不把合成选择器当真实模型效果。 */
class RuleCheckSelectionNarrowingTest {
    private final AtomicInteger selections=new AtomicInteger(),retrievals=new AtomicInteger();
    private final AtomicReference<List<RuleCheckApplicationService.RuleOption>> seen=new AtomicReference<>();
    private boolean audienceDeclared=true;
    private RuleCheckApplicationService.CatalogEntry entry(String id,RuleCheckTopicGuard.Topic topic) {
        var rule=new RuleCheckRuleTemplate(id,"2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"合成审核者",LocalDate.of(2026,10,10),List.of(),List.of(),List.of(),List.of());
        return new RuleCheckApplicationService.CatalogEntry(rule,"合成范围",
            audienceDeclared?new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)):RuleCheckApplicabilityGuard.Coverage.unspecified(),RuleCheckTopicGuard.Coverage.declared(topic));
    }
    private RuleCheckApplicationService app(boolean narrow) {
        var rules=List.of(entry("S01/GENERAL",OBLIGATION),entry("S05/GENERAL",PLAN_BASIS),entry("S05/PERSONAL",PLAN_BASIS));
        var selector=new RuleCheckApplicationService.RuleSelector(){
            public boolean requiresScopedCandidates(){return narrow;}
            public String select(String q,List<RuleCheckApplicationService.RuleOption> options){selections.incrementAndGet();seen.set(options);return null;}
        };
        var verifier=new RuleCheckEvidenceVerifier((s,c)->Optional.empty());
        return new RuleCheckApplicationService(new RuleCheckRuntimeSettings("synthetic","cut","scope","2025.9.2",20,5),()->rules,selector,
            new HybridRetrievalService(new HybridCandidateFuser()),(q,l)->{retrievals.incrementAndGet();return List.of();},(q,l)->List.of(),
            new RuleCheckEvidenceCapture(verifier,()->"synthetic",r->{throw new AssertionError("无选题不能审计");},Clock.systemUTC()),new RuleCheckDecisionService(verifier));
    }
    @Test void onlyPlanBasisCandidatesReachSelectorForQ10WithoutForcingAnAnswer() {
        var result=app(true).checkWithContext("判断选修要求应先看什么资料？",Map.of());
        assertThat(seen.get()).extracting(RuleCheckApplicationService.RuleOption::ruleId).containsExactly("S05/GENERAL","S05/PERSONAL");
        assertThat(result.decision().status()).isEqualTo(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE);
        assertThat(selections).hasValue(1);assertThat(retrievals).hasValue(0);
    }
    @Test void postgraduateAliasCannotReachUndergraduateSelectorEvenWithMatchingTopic() {
        app(true).checkWithContext("研一学生判断选修要求应先看什么资料？",Map.of());
        assertThat(selections).hasValue(0);assertThat(retrievals).hasValue(0);
    }
    @Test void mixedOrUnknownDemandsCannotBeHiddenByOneCoveredTopic() {
        app(true).checkWithContext("比较选修要求的依据和公共选修课学时上限。",Map.of());
        app(true).checkWithContext("学生医保报销怎么办？",Map.of());
        assertThat(selections).hasValue(0);assertThat(retrievals).hasValue(0);
    }
    @Test void explicitlyExcludedIdentityPreservesActiveUndergraduateRequest() {
        app(true).checkWithContext("我不是研一，只解释全日制普通本科判断选修要求先看什么资料。",Map.of());
        assertThat(seen.get()).extracting(RuleCheckApplicationService.RuleOption::ruleId).containsExactly("S05/GENERAL","S05/PERSONAL");
    }
    @Test void legacySelectorRetainsFullCatalogueUntilExplicitlyOptedIn() {
        app(false).checkWithContext("学生医保报销怎么办？",Map.of());
        assertThat(seen.get()).hasSize(3);assertThat(selections).hasValue(1);
    }
    @Test void undeclaredAudienceCannotEnterScopedSelectionWhenQuestionDoesNotNameAudience() {
        audienceDeclared=false;
        app(true).checkWithContext("判断选修要求应先看什么资料？",Map.of());
        assertThat(selections).hasValue(0);assertThat(retrievals).hasValue(0);
    }
}
