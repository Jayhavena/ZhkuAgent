package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.conversation.ConversationStore;
import com.zhku.knowledge.conversation.PendingIntent;
import com.zhku.knowledge.conversation.StoreResult;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;
import com.zhku.knowledge.notice.CourseSelectionNoticeService;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** 本机预览的公共选修规则分发器，先保护已有重修待澄清，再处理新的明确规则。 */
public final class PublicElectiveConversationRouter {

    private final ConversationStore store;
    private final ElectiveRetakeConversationService electiveRetakeService;
    private final PublicElectiveCreditLimitService creditLimitService;
    private final CourseSelectionNoticeService courseSelectionNoticeService;
    private final PublicElectiveAdjustmentService adjustmentService;
    private final Clock clock;

    public PublicElectiveConversationRouter(
            ConversationStore store,
            ElectiveRetakeConversationService electiveRetakeService,
            PublicElectiveCreditLimitService creditLimitService,
            Clock clock
    ) {
        this(store, electiveRetakeService, creditLimitService, null, clock);
    }

    public PublicElectiveConversationRouter(
            ConversationStore store,
            ElectiveRetakeConversationService electiveRetakeService,
            PublicElectiveCreditLimitService creditLimitService,
            CourseSelectionNoticeService courseSelectionNoticeService,
            Clock clock
    ) {
        this(store, electiveRetakeService, creditLimitService, courseSelectionNoticeService, null,
                clock);
    }

    /**
     * 完整装配（阶段 9 A-4 批2 追加"选课调整办理"主题）。
     *
     * <p>{@code adjustmentService} 允许为 null：历史装配（含既有测试）传 null 时该主题整体跳过，
     * 行为与追加前完全一致——新增主题不应强迫所有调用方一起改。</p>
     */
    public PublicElectiveConversationRouter(
            ConversationStore store,
            ElectiveRetakeConversationService electiveRetakeService,
            PublicElectiveCreditLimitService creditLimitService,
            CourseSelectionNoticeService courseSelectionNoticeService,
            PublicElectiveAdjustmentService adjustmentService,
            Clock clock
    ) {
        this.store = Objects.requireNonNull(store, "会话存储不能为空");
        this.electiveRetakeService = Objects.requireNonNull(electiveRetakeService, "重修服务不能为空");
        this.creditLimitService = Objects.requireNonNull(creditLimitService, "学分上限服务不能为空");
        this.courseSelectionNoticeService = courseSelectionNoticeService;
        this.adjustmentService = adjustmentService;
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    public ElectiveRetakeReply route(UUID sessionId, String message) {
        Objects.requireNonNull(sessionId, "会话编号不能为空");
        Objects.requireNonNull(message, "消息不能为空");
        StoreResult visible = store.find(sessionId, clock);
        if (visible.outcome() == StoreResult.Outcome.FOUND
                && visible.state().pendingIntent() == PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE) {
            return electiveRetakeService.handle(sessionId, message);
        }
        if (courseSelectionNoticeService != null) {
            ElectiveRetakeReply noticeReply = courseSelectionNoticeService.handle(message);
            if (noticeReply.type() != ElectiveRetakeReplyType.OUT_OF_SCOPE) {
                // 动态通知在静态规则前判定，避免旧手册被误当作当期办理依据。
                return noticeReply;
            }
        }
        if (isElectiveRetakeQuestion(message)) {
            return electiveRetakeService.handle(sessionId, message);
        }
        if (visible.outcome() != StoreResult.Outcome.FOUND) {
            return electiveRetakeService.handle(sessionId, message);
        }
        // 第四主题：选课调整办理。放在学分上限回退之前、既有三条主题之后——
        // 位置即边界：通知与重修优先，调整次之，学分上限兜底；未装配该主题时整段跳过。
        if (adjustmentService != null) {
            ElectiveRetakeReply adjustmentReply = adjustmentService.handle(message);
            if (adjustmentReply.type() != ElectiveRetakeReplyType.OUT_OF_SCOPE) {
                return adjustmentReply;
            }
        }
        return creditLimitService.handle(message).toElectiveRetakeReply();
    }

    private static boolean isElectiveRetakeQuestion(String message) {
        boolean failure = message.contains("不及格") || message.contains("挂科") || message.contains("没及格");
        return message.contains("选修") && failure && message.contains("重修");
    }
}
