package com.zhku.knowledge.web;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.InMemoryConversationStore;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.publicelective.AdjustmentEvidence;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveAdjustmentService;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveConversationRouter;
import com.zhku.knowledge.conversation.publicelective.PublicElectiveCreditLimitService;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.telemetry.TelemetryRecorder;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 遥测接线验收（链路/门面层，不是只测仓库层）。
 *
 * <p>三条判据：① 一次问答恰好记 1 条、只含白名单维度、值都符合形态（无自由文本）；
 * ② 命中/降级/转人工分别映射到 `route`/`failureCategory`/`handoffRequired`；
 * ③ **遥测是旁路**——写入抛异常时作答照常返回（这是硬约束，不能只靠"仓库层能写"来证明）。</p>
 */
class LocalPreviewTelemetryWiringTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Pattern CODE_DIMENSION = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern PROFILE_DIMENSION = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final List<String> WHITELIST = List.of("route", "retrievalProfile",
            "generatorProfile", "handoffRequired", "failureCategory", "latencyBucket",
            "estimatedCostBucket", "eventDate");

    private final ConversationStore store = new InMemoryConversationStore();
    private final List<Map<String, Object>> recorded = new ArrayList<>();

    @Test
    void shouldRecordExactlyOneWhitelistedRowForOneAnswer() {
        LocalPreviewConversationFacade facade = facade(recorded::add);

        facade.sendMessage(facade.createConversation().sessionId(), "公共选修课选课调整怎么办理");

        assertThat(recorded).hasSize(1);
        Map<String, Object> row = recorded.getFirst();
        // 只落白名单维度：多一个字段都算越界。
        assertThat(row.keySet()).isSubsetOf(WHITELIST);
        assertThat(row.keySet()).contains("route", "handoffRequired", "failureCategory",
                "latencyBucket", "eventDate");
        assertThat((String) row.get("route")).matches(CODE_DIMENSION);
        assertThat((String) row.get("failureCategory")).matches(CODE_DIMENSION);
        assertThat((String) row.get("latencyBucket")).matches(CODE_DIMENSION);
        assertThat((String) row.get("eventDate")).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(row.get("handoffRequired")).isInstanceOf(Boolean.class);
        row.values().stream().filter(String.class::isInstance).map(String.class::cast)
                .forEach(value -> assertThat(value).matches(PROFILE_DIMENSION));
    }

    @Test
    void shouldMapHitDegradeAndHandoffToExpectedDimensions() {
        LocalPreviewConversationFacade facade = facade(recorded::add);

        // 命中规则级：failureCategory=NONE、不转人工。
        facade.sendMessage(facade.createConversation().sessionId(), "公共选修课选课调整怎么办理");
        assertThat(recorded.getFirst())
                .containsEntry("route", "HIGH_RISK_RULE")
                .containsEntry("failureCategory", "NONE")
                .containsEntry("handoffRequired", false);

        // 证据不足：转人工 + RETRIEVAL_MISS。
        facade.sendMessage(facade.createConversation().sessionId(), "公共选修课教室在哪里");
        Map<String, Object> second = recorded.get(1);
        assertThat(second)
                .containsEntry("route", AnswerRoute.INSUFFICIENT_EVIDENCE.name())
                .containsEntry("failureCategory", "RETRIEVAL_MISS")
                .containsEntry("handoffRequired", true);
    }

    @Test
    void shouldStillAnswerWhenTelemetryWriteFails() {
        // 旁路硬约束：记录实现直接抛异常，作答必须与未装配遥测时完全一致。
        LocalPreviewConversationFacade withBrokenTelemetry = facade(fields -> {
            throw new IllegalStateException("遥测库不可用");
        });
        LocalPreviewConversationFacade withoutTelemetry = facade(null);

        LocalPreviewConversationFacade.ConversationReply broken = withBrokenTelemetry.sendMessage(
                withBrokenTelemetry.createConversation().sessionId(), "公共选修课选课调整怎么办理");
        LocalPreviewConversationFacade.ConversationReply plain = withoutTelemetry.sendMessage(
                withoutTelemetry.createConversation().sessionId(), "公共选修课选课调整怎么办理");

        assertThat(broken.type()).isEqualTo(plain.type());
        assertThat(broken.route()).isEqualTo(plain.route());
        assertThat(broken.message()).isEqualTo(plain.message());
        assertThat(broken.answer()).isEqualTo(plain.answer());
        assertThat(broken.handoffRequired()).isEqualTo(plain.handoffRequired());
        assertThat(broken.guideCard()).isEqualTo(plain.guideCard());
    }

    @Test
    void shouldNotRecordSessionIssues() {
        LocalPreviewConversationFacade facade = facade(recorded::add);

        // 未知会话不是知识事件：不记录（记录它只会污染链路统计）。
        facade.sendMessage(UUID.fromString("bb111111-1111-4111-8111-111111111111"), "任意问题");

        assertThat(recorded).isEmpty();
    }

    private LocalPreviewConversationFacade facade(TelemetryRecorder telemetry) {
        ElectiveRetakeConversationService retakeService = new ElectiveRetakeConversationService(
                store, courseType -> Optional.empty(), CLOCK, TTL);
        PublicElectiveCreditLimitService creditLimitService =
                new PublicElectiveCreditLimitService(Optional::empty);
        PublicElectiveAdjustmentService adjustmentService = new PublicElectiveAdjustmentService(
                () -> Optional.of(new AdjustmentEvidence(
                        UUID.fromString("6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05"),
                        "本科生学生手册（2025版）·公共选修课修读与考核规则",
                        "2025版-公共选修修读与考核-学生可见",
                        "PDF 第 77 页，第 9、13 条",
                        "办理公共选修课选课与修读的全日制普通本科生",
                        "选课、退选、补选和改选应按照每学期《课程选课工作通知》办理。")));
        PublicElectiveConversationRouter router = new PublicElectiveConversationRouter(
                store, retakeService, creditLimitService, null, adjustmentService, CLOCK);
        LocalPreviewAnswerChain chain = new LocalPreviewAnswerChain(store, router, null, null, CLOCK);
        return new LocalPreviewConversationFacade(
                store, chain, CLOCK, TTL, null, null, telemetry);
    }
}
