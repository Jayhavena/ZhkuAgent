package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.conversation.elective.ElectiveRetakeAnswer;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReply;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeReplyType;

/** 学分上限规则的内部结果，适配既有阶段 5 回答卡传输结构。 */
public record CreditLimitReply(
        ElectiveRetakeReplyType type,
        String message,
        CreditLimitAnswer answer
) {

    public static CreditLimitReply answer(CreditLimitAnswer answer) {
        return new CreditLimitReply(ElectiveRetakeReplyType.ANSWER, answer.conclusion(), answer);
    }

    public static CreditLimitReply outOfScope() {
        return new CreditLimitReply(
                ElectiveRetakeReplyType.OUT_OF_SCOPE,
                "当前问题不属于已支持的公共选修课单学期学分上限咨询范围。",
                null
        );
    }

    public static CreditLimitReply evidenceUnavailable() {
        return new CreditLimitReply(
                ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE,
                "当前没有可确认的有效依据，无法给出确定性结论。",
                null
        );
    }

    /** 阶段 5 暂以既有回答协议输出，字段逐项复制，不能丢失来源追溯信息。 */
    public ElectiveRetakeReply toElectiveRetakeReply() {
        ElectiveRetakeAnswer mappedAnswer = answer == null ? null : new ElectiveRetakeAnswer(
                answer.conclusion(),
                answer.basis(),
                answer.sourceId(),
                answer.sourceTitle(),
                answer.sourceVersion(),
                answer.sourceLocator(),
                answer.applicableScope()
        );
        return new ElectiveRetakeReply(type, message, mappedAnswer);
    }
}
