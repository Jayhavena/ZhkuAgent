package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Topic.*;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 开发者独立声明的工程正反例，不是新增人工语义参考或模型评分。 */
class RuleCheckTopicGuardTest {
    @Test void coveredTopicCannotHideExplicitUnsupportedAdditionalDemand() throws Exception {
        var result=app(RuleCheckTopicGuard.Coverage.declared(SEMESTER_CREDITS))
            .check("一学期公共选修最多多少学分？另外，学生医保怎么报销？",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.confirmedFacts()).isEmpty();assertThat(retrieved).hasValue(0);assertThat(audited).hasValue(0);
        var hours=RuleCheckTopicGuard.Coverage.declared(HOURS);
        assertThat(guard.assess("公共选修课学时上限是多少？还请解释奖学金申请条件。",hours).blocked()).isTrue();
        assertThat(guard.assess("不问医保报销，只解释公共选修课学时上限。",hours).blocked()).isFalse();
        assertThat(guard.assess("不问医保报销还请解释公选学时上限。",hours).blocked()).isFalse();
        assertThat(guard.assess("不问医保报销并确认宿舍申请，再解释公选学时上限。",hours).blocked()).isTrue();
    }
    private final RuleCheckTopicGuard guard=new RuleCheckTopicGuard();
    private final AtomicInteger retrieved=new AtomicInteger(),audited=new AtomicInteger();
    private RuleCheckApplicationService app(RuleCheckTopicGuard.Coverage coverage) throws Exception {
        return app(coverage,false);
    }
    private RuleCheckApplicationService app(RuleCheckTopicGuard.Coverage coverage,boolean conditional) throws Exception {
        String text=conditional?"合成公共选修重修规则。":"合成每学期学分上限4。";
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        UUID source=UUID.randomUUID(),chunk=UUID.randomUUID();
        var fact=new RuleCheckRuleTemplate.Fact("synthetic",text,!conditional,conditional?Map.of("courseType","PUBLIC"):Map.of(),List.of(new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,"2025.9.2",hash,0,text.length(),text)));
        var conditions=conditional?List.of(new RuleCheckRuleTemplate.Condition("courseType","请选择课程类别",List.of("PUBLIC","PROFESSIONAL"))):List.<RuleCheckRuleTemplate.Condition>of();
        var rule=new RuleCheckRuleTemplate("LEGAL/CREDIT","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"合成审核者",LocalDate.of(2026,10,10),List.of(fact),conditions,List.of(),List.of());
        var entry=new RuleCheckApplicationService.CatalogEntry(rule,"声称可以回答任何学时或比例问题的非结构化文案",RuleCheckApplicabilityGuard.Coverage.unspecified(),coverage);
        var verifier=new RuleCheckEvidenceVerifier((s,c)->Optional.of(new RuleCheckEvidenceSnapshot("fresh",source,chunk,"2025.9.2",hash,text,true)));
        var capture=new RuleCheckEvidenceCapture(verifier,()->UUID.randomUUID().toString(),row->audited.incrementAndGet(),Clock.systemUTC());
        return new RuleCheckApplicationService(new RuleCheckRuntimeSettings("fixture","cut","scope","2025.9.2",20,5),()->List.of(entry),(q,o)->"LEGAL/CREDIT",
            new HybridRetrievalService(new HybridCandidateFuser()),(q,l)->{retrieved.incrementAndGet();return List.of(new RetrievedChunk(chunk,text,"合成","合成页",1,"2025.9.2",hash,source));},(q,l)->List.of(),capture,new RuleCheckDecisionService(verifier));
    }
    @Test void wrongLegalCreditIdCannotAnswerHoursEvenWhenItsEvidenceIsAvailable() throws Exception {
        var result=app(RuleCheckTopicGuard.Coverage.declared(SEMESTER_CREDITS)).check("公共选修课的学时上限是什么？",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
        assertThat(result.unknownItems()).anyMatch(t->t.contains("主题"));assertThat(retrieved).hasValue(0);assertThat(audited).hasValue(0);
    }
    @Test void proportionAndPlanBasisCannotBorrowCreditOrObligationCoverage() {
        assertThat(guard.assess("网络公选课是不是在所有情况下绝对不能超过一半？",RuleCheckTopicGuard.Coverage.declared(SEMESTER_CREDITS)).blocked()).isTrue();
        assertThat(guard.assess("判断选修要求应先看什么资料？",RuleCheckTopicGuard.Coverage.declared(OBLIGATION)).blocked()).isTrue();
    }
    @Test void matchesCoveredHoursCreditProportionAndPlanRequests() {
        var cases=Map.of("公共选修课通常安排多少学时？",HOURS,"一学期最多选多少公共选修学分？",SEMESTER_CREDITS,
            "网络公选课的比例规则，分母是什么？",NETWORK_PROPORTION,"判断选修要求应先看什么资料？",PLAN_BASIS);
        cases.forEach((q,t)->{var a=guard.assess(q,RuleCheckTopicGuard.Coverage.declared(t));assertThat(a.evaluated()).isTrue();assertThat(a.requestedTopics()).containsExactly(t);assertThat(a.blocked()).isFalse();});
    }
    @Test void declaredCoverageRejectsUnknownAndMixedUncoveredTopics() {
        assertThat(guard.assess("学生医保怎么报销？",RuleCheckTopicGuard.Coverage.declared(SEMESTER_CREDITS)).blocked()).isTrue();
        assertThat(guard.assess("比较公选的学时上限和每学期学分上限。",RuleCheckTopicGuard.Coverage.declared(HOURS)).blocked()).isTrue();
    }
    @Test void explicitExclusionAndExampleDoNotHideActiveWrongTopic() {
        var c=RuleCheckTopicGuard.Coverage.declared(HOURS);
        assertThat(guard.assess("不问每学期学分上限，只解释公共选修课的学时上限。",c).blocked()).isFalse();
        assertThat(guard.assess("不问学时上限，但是请比较每学期学分上限。",c).blocked()).isTrue();
        assertThat(guard.assess("“每学期学分上限”只是例句；我想了解公选的一般学时上限。",c).blocked()).isFalse();
        assertThat(guard.assess("“每学期学分上限”只是例句；请回答这句。",c).blocked()).isTrue();
    }
    @Test void topicalMetadataCannotBeInferredFromScopeTextOrRuleId() {
        var assessment=guard.assess("合成任意查询",RuleCheckTopicGuard.Coverage.unspecified());
        assertThat(assessment.evaluated()).isFalse();assertThat(assessment.requestedTopics()).isEmpty();
        assertThatThrownBy(()->new RuleCheckTopicGuard.Coverage(Set.of(),true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void currentReplayRechecksQuestionTopicInsteadOfTrustingExistingQuotes() throws Exception {
        var app=app(RuleCheckTopicGuard.Coverage.declared(SEMESTER_CREDITS));var valid=app.checkWithContext("一学期公共选修最多多少学分？",Map.of());
        assertThat(valid.decision().status()).isEqualTo(ANSWERED);assertThat(app.isCurrent(valid)).isTrue();
        var b=valid.releaseBinding();var forged=new RuleCheckScopedResult.ReleaseBinding(b.settings(),b.entry(),"公共选修课学时上限是什么？",b.confirmedConditions(),b.references());
        assertThat(app.isCurrent(new RuleCheckScopedResult(valid.decision(),valid.scope(),null,forged,valid.applicability()))).isFalse();
    }
    @Test void independentCategoryRequestCannotBeSuppressedByRetakeInAnotherClause() {
        var coverage=RuleCheckTopicGuard.Coverage.declared(RETAKE);
        assertThat(guard.assess("公共选修课程有哪些类别？另外，不合格课程如何重修？",coverage).blocked()).isTrue();
        assertThat(guard.assess("按课程类别解释重修规定。",coverage).blocked()).isFalse();
        assertThat(guard.assess("解释类别定义和重修规定。",coverage).blocked()).isTrue();
        assertThat(guard.assess("课程类别和重修分别如何规定？",coverage).blocked()).isTrue();
        assertThat(guard.assess("我未说明课程类别，能判断缓考和重修吗？",RuleCheckTopicGuard.Coverage.declared(DEFERRED_EXAM,RETAKE)).blocked()).isFalse();
    }
    @Test void continuationKeepsValidRetakeButRejectsChangedHoursQuestion() throws Exception {
        var app=app(RuleCheckTopicGuard.Coverage.declared(RETAKE),true);
        var missing=app.checkWithContext("这门公共选修不合格能重修吗？",Map.of());
        assertThat(missing.decision().status()).isEqualTo(NEED_CLARIFICATION);assertThat(app.isCurrent(missing)).isTrue();
        var answer=app.resume(missing.continuation(),Map.of("courseType","PUBLIC"));
        assertThat(answer.decision().status()).isEqualTo(ANSWERED);assertThat(app.isCurrent(answer)).isTrue();
        var c=missing.continuation();var altered=new RuleCheckScopedResult.Continuation(c.settings(),c.entry(),"公共选修的学时上限是什么？",c.confirmedConditions(),c.nextCondition());
        int before=retrieved.get();assertThat(app.resume(altered,Map.of("courseType","PUBLIC")).decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(retrieved).hasValue(before);
    }
}
