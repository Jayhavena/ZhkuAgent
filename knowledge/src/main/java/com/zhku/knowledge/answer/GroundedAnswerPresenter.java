package com.zhku.knowledge.answer;

import java.util.Objects;

/** 将有依据问答结果展示为适合本地人工核验的文本。 */
public final class GroundedAnswerPresenter {

    public String present(GroundedAnswer answer) {
        Objects.requireNonNull(answer, "问答结果不能为空");

        StringBuilder result = new StringBuilder()
                .append("回答模式：").append(answer.mode())
                .append(System.lineSeparator())
                .append("回答：").append(answer.answer())
                .append(System.lineSeparator());
        if (answer.citations().isEmpty()) {
            return result.toString();
        }

        result.append("出处：").append(System.lineSeparator());
        for (int index = 0; index < answer.citations().size(); index++) {
            GroundedCitation citation = answer.citations().get(index);
            result.append(index + 1).append(". ")
                    .append(citation.sourceTitle()).append(System.lineSeparator())
                    .append("   定位：").append(citation.sourceLocator())
                    .append(System.lineSeparator())
                    .append("   物理页码：").append(citation.pageNumber())
                    .append(System.lineSeparator());
        }
        return result.toString();
    }
}
