package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.rag.CitationIdFactory;
import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.zhku.knowledge.rulecheck.RuleCheckDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 合法ID注入是工程反例，不是模型错误率；审核正文始终为合成一般规则。 */
class RuleCheckApplicabilityTest {
    private final UUID source=UUID.randomUUID(),chunk=UUID.randomUUID();
    private final String text="合成的一般选修上限为4，不能证明个人资格或当期安排。";
    private final AtomicInteger retrievalCalls=new AtomicInteger(),auditCalls=new AtomicInteger();
    private RuleCheckApplicabilityGuard.Coverage coverage=RuleCheckApplicabilityGuard.Coverage.unspecified();
    private RuleCheckApplicationService app(boolean independent) throws Exception {
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        var fact=new RuleCheckRuleTemplate.Fact("limit","合成一般上限4。",independent,Map.of(),List.of(
            new RuleCheckRuleTemplate.EvidenceBinding(source,chunk,"2025.9.2",hash,0,text.length(),text)));
        var template=new RuleCheckRuleTemplate("legal/GENERAL","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,
            "合成审核者",LocalDate.of(2026,10,9),List.of(fact),List.of(),List.of(),List.of());
        var verifier=new RuleCheckEvidenceVerifier((s,c)->Optional.of(new RuleCheckEvidenceSnapshot("fresh",source,chunk,"2025.9.2",hash,text,true)));
        var capture=new RuleCheckEvidenceCapture(verifier,(CitationIdFactory)()->"scope-"+UUID.randomUUID(),r->auditCalls.incrementAndGet(),Clock.systemUTC());
        return new RuleCheckApplicationService(new RuleCheckRuntimeSettings("fixture","cut","scope","2025.9.2",20,5),
            ()->List.of(new RuleCheckApplicationService.CatalogEntry(template,"声称可查询任意对象、当期和个人资格的范围文字",coverage)),
            (q,options)->"legal/GENERAL",new HybridRetrievalService(new HybridCandidateFuser()),
            (q,l)->{retrievalCalls.incrementAndGet();return List.of(new RetrievedChunk(chunk,text,"合成","合成页",1,"2025.9.2",hash,source));},
            (q,l)->List.of(),capture,new RuleCheckDecisionService(verifier));
    }
    @Test void graduateQuestionCannotBorrowLegallySelectedUndergraduateOrUnspecifiedRule() throws Exception {
        var result=app(true).checkWithContext("这份办法是否也能直接用于研究生的选修要求？",Map.of());
        assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(result.decision().confirmedFacts()).isEmpty();assertThat(retrievalCalls).hasValue(0);assertThat(auditCalls).hasValue(0);
    }
    @Test void currentPortalCannotBeAnsweredByHistoricalGeneralRule() throws Exception {
        var result=app(true).checkWithContext("今天网上公选入口是否开放？",Map.of());
        assertThat(result.decision().status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.decision().confirmedFacts()).isEmpty();
    }
    @Test void anotherYearCannotBorrowMatchingCitationFrom2025() throws Exception {
        assertThat(app(true).check("2026年公共选修上限是多少？",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
    }
    @Test void individualEligibilityKeepsOnlyIndependentlyReviewedGeneralFacts() throws Exception {
        var app=app(true);var result=app.checkWithContext("我口述已选3学分，想再选2学分，能确认我有选课资格吗？",Map.of());
        assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);assertThat(result.decision().confirmedFacts()).hasSize(1);
        assertThat(result.decision().unknownItems()).isNotEmpty();assertThat(auditCalls).hasValue(1);assertThat(app.isCurrent(result)).isTrue();
    }
    @Test void actualOpeningCannotBeConfirmedFromGeneralAttendanceThreshold() throws Exception {
        assertThat(app(true).check("现在有31人选了某门课，你能确认它一定开班吗？",Map.of()).status()).isEqualTo(PARTIALLY_ANSWERED);
    }
    @Test void personalRequestCannotPromoteNonIndependentFactToPartialAnswer() throws Exception {
        var result=app(false).check("仅凭手册能判断我毕业还缺多少选修学分吗？",Map.of());
        assertThat(result.status()).isEqualTo(INSUFFICIENT_EVIDENCE);assertThat(result.confirmedFacts()).isEmpty();
    }
    @Test void firstPersonRuleExplanationDoesNotRequirePersonalRecords() throws Exception {
        var result=app(true).check("我想了解2025版每学期公共选修的一般上限规则。",Map.of());
        assertThat(result.status()).isEqualTo(ANSWERED);assertThat(result.confirmedFacts()).hasSize(1);
    }
    @Test void declaredAudienceAllowsGeneralUndergraduateRuleButNotPartTimeExtension() throws Exception {
        coverage=new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE));
        var app=app(true);
        assertThat(app.check("全日制普通本科生的一般选修上限规则是什么？",Map.of()).status()).isEqualTo(ANSWERED);
        assertThat(app.check("非全日制本科生也能直接适用吗？",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(retrievalCalls).hasValue(1);
    }
    @Test void unstructuredScopeTextCannotGrantUnspecifiedAudience() throws Exception {
        assertThat(app(true).check("本科生的一般规则是什么？",Map.of()).status()).isEqualTo(INSUFFICIENT_EVIDENCE);
        assertThat(retrievalCalls).hasValue(0);
    }
    @Test void releaseReplayCannotAcceptForgedFullAnswerForPersonalQuestion() throws Exception {
        var app=app(true);var partial=app.checkWithContext("我已选3学分，能确认个人选课资格吗？",Map.of());
        var forged=new RuleCheckDecision(ANSWERED,partial.decision().referenceRevision(),partial.decision().confirmedFacts(),List.of(),List.of(),List.of());
        assertThat(app.isCurrent(new RuleCheckScopedResult(forged,partial.scope(),null,partial.releaseBinding(),partial.applicability()))).isFalse();
        assertThat(app.isCurrent(partial)).isTrue();
        coverage=RuleCheckApplicabilityGuard.Coverage.unspecified();
    }
    @Test void negatedOwnIdentityKeepsOnlyActiveAudienceAndOriginalQuestion() throws Exception {
        coverage=new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE));
        String question="我不是研究生，是全日制普通本科生，公共选修的一般上限规则是什么？";
        var service=app(true);var result=service.checkWithContext(question,Map.of());
        assertThat(result.decision().status()).isEqualTo(ANSWERED);assertThat(result.releaseBinding().question()).isEqualTo(question);
        assertThat(result.applicability().requestedAudiences()).containsExactly(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE);
        assertThat(service.isCurrent(result)).isTrue();
    }
    @Test void excludingPersonalLabelCannotHideRemainingImplicitApplication() throws Exception {
        var result=app(true).checkWithContext("不确认个人资格，但是按我的情况还能报这门公选吗？",Map.of());
        assertThat(result.decision().status()).isEqualTo(PARTIALLY_ANSWERED);assertThat(result.applicability().personalRecordsRequired()).isTrue();
        assertThat(result.decision().unknownItems()).isNotEmpty();assertThat(result.decision().confirmedFacts()).hasSize(1);
    }
}
