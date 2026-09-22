package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;

import java.util.Objects;

/**
 * "选课调整办理"主题的受限编排（形态对齐 {@link PublicElectiveCreditLimitService}）。
 *
 * <p><b>为什么必须是一个显式主题，而不是往既有分支塞关键词</b>：五级路由靠主题边界决定
 * "能答什么"；把"调整/退选"塞进学分上限或重修的分支，会让不相干的问题命中错误的规则，
 * 也会让"回答卡附哪份清单"失去可审核的边界。这里把判定集中声明、用已发布证据兜底、
 * 用测试锁定，边界才是可审的。</p>
 *
 * <p><b>主题边界（显式声明）</b>：讲"选课、退选、补选、改选的办理"的问法归本主题；
 * 只谈到"选课时间/入口"的仍归当期通知级，谈到"学分上限"的归学分上限主题——
 * 本服务不吞并它们。</p>
 */
public final class PublicElectiveAdjustmentService {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final AdjustmentEvidenceFinder evidenceFinder;

    public PublicElectiveAdjustmentService(AdjustmentEvidenceFinder evidenceFinder) {
        this.evidenceFinder = Objects.requireNonNull(evidenceFinder, "证据检索器不能为空");
    }

    public ElectiveRetakeReply handle(String message) {
        String normalized = normalize(message);
        if (!isAdjustmentQuestion(normalized)) {
            return outOfScope();
        }
        return evidenceFinder.find()
                .filter(evidence -> evidence.content()
                        .contains(JdbcAdjustmentEvidenceFinder.ADJUSTMENT_CLAUSE_PHRASE))
                .map(evidence -> answer(evidence, normalized))
                .orElseGet(PublicElectiveAdjustmentService::evidenceUnavailable);
    }

    private ElectiveRetakeReply answer(AdjustmentEvidence evidence, String message) {
        String conclusion = "公共选修课的选课、退选、补选和改选，按教务处公布的当学期课程安排"
                + "在规定时间内通过教务系统办理；未选课的课程不得参加考核。";
        StringBuilder basis = new StringBuilder(
                "本回答只说明办理路径，不代办、不认定选课资格，也不承诺调整成功；"
                        + "开放时间窗与系统入口以当学期《课程选课工作通知》为准。");
        if (needsIndividualReview(message)) {
            // 涉及个人资格或超限调整时必须明确转人工，不能让清单看起来像"照做即可办结"。
            basis.append("涉及个人选课资格、超限调整或特殊情况的，须经教务部门个案确认。");
        }
        ElectiveRetakeAnswer answer = new ElectiveRetakeAnswer(
                conclusion,
                basis.toString(),
                evidence.sourceId(),
                evidence.sourceTitle(),
                evidence.sourceVersion(),
                evidence.sourceLocator(),
                evidence.applicableScope());
        return new ElectiveRetakeReply(ElectiveRetakeReplyType.ANSWER, conclusion, answer);
    }

    private static boolean needsIndividualReview(String message) {
        return message.contains("我")
                || message.contains("补选")
                || message.contains("超")
                || message.contains("特殊情况");
    }

    private static boolean isAdjustmentQuestion(String message) {
        boolean adjustment = message.contains("调整")
                || message.contains("退选")
                || message.contains("改选")
                || message.contains("补选");
        boolean elective = message.contains("选修") || message.contains("公选") || message.contains("选课");
        // 排除已由其他两个主题承担的问法，避免主题互相吞并导致回答错位。
        boolean ownedElsewhere = message.contains("重修") || message.contains("缓考")
                || message.contains("学分上限") || message.contains("每学期");
        return adjustment && elective && !ownedElsewhere;
    }

    private static ElectiveRetakeReply outOfScope() {
        return new ElectiveRetakeReply(
                ElectiveRetakeReplyType.OUT_OF_SCOPE, "该问题不属于选课调整办理范围。", null);
    }

    private static ElectiveRetakeReply evidenceUnavailable() {
        return new ElectiveRetakeReply(
                ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE,
                "当前没有可用于选课调整办理的已发布依据，请通过人工渠道咨询教务部门。",
                null);
    }

    private static String normalize(String message) {
        Objects.requireNonNull(message, "消息不能为空");
        String normalized = message.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("消息不能超过 500 个字符");
        }
        return normalized;
    }
}
