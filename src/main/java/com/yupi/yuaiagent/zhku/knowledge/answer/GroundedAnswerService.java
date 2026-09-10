package com.yupi.yuaiagent.zhku.knowledge.answer;

import com.yupi.yuaiagent.zhku.knowledge.publication.PublishedKnowledgeChunk;

import java.util.List;
import java.util.Objects;

/**
 * 公共选修课程首批资料的有依据问答服务。
 *
 * <p>当前通过有限的确定性路由验证“问题—证据—出处”闭环，而不让大模型在资料不足时猜测规则。
 * 后续接入语义检索时仍必须保留 {@link AnswerMode#INSUFFICIENT_EVIDENCE} 降级分支。</p>
 */
public final class GroundedAnswerService {

    private static final int MAX_QUESTION_LENGTH = 500;

    private final PublishedKnowledgeSearcher searcher;

    public GroundedAnswerService(PublishedKnowledgeSearcher searcher) {
        this.searcher = Objects.requireNonNull(searcher, "已发布知识检索器不能为空");
    }

    public GroundedAnswer answer(String question) {
        String normalizedQuestion = normalizeQuestion(question);
        String keyword = resolvePublicElectiveKeyword(normalizedQuestion);
        if (keyword == null) {
            return insufficient("当前已发布资料未覆盖该问题，无法给出确定性回答。");
        }

        List<PublishedKnowledgeChunk> chunks = searcher.searchPublished(keyword);
        if (chunks.isEmpty()) {
            return insufficient("没有找到足够的已发布资料，无法给出确定性回答。");
        }

        PublishedKnowledgeChunk evidence = chunks.getFirst();
        return new GroundedAnswer(
                AnswerMode.GROUNDED,
                evidence.content(),
                List.of(new GroundedCitation(
                        evidence.sourceTitle(),
                        evidence.sourceLocator(),
                        evidence.pageNumber()
                ))
        );
    }

    private static GroundedAnswer insufficient(String message) {
        return new GroundedAnswer(
                AnswerMode.INSUFFICIENT_EVIDENCE,
                message,
                List.of()
        );
    }

    private static String normalizeQuestion(String question) {
        Objects.requireNonNull(question, "问题不能为空");
        String normalized = question.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (normalized.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("问题不能超过 500 个字符");
        }
        return normalized;
    }

    private static String resolvePublicElectiveKeyword(String question) {
        // 路由表刻意很小：它仅支撑已人工验收的 8 类问题，不把未知问法强行映射到看似相关的规则。
        if (question.contains("缓考") || question.contains("重修")) {
            return "不办理缓考";
        }
        if (question.contains("缺课")
                || question.contains("不上课")
                || question.contains("不去上课")) {
            return "缺课累计超过";
        }
        if (question.contains("没选课") || question.contains("没有选课")) {
            return "未选课";
        }
        if (question.contains("什么时候选") || question.contains("哪里选")) {
            return "网上选课";
        }
        if (question.contains("网络") && question.contains("选修")) {
            return "网络公共选修课一般不能超过";
        }
        if (question.contains("相近")
                || question.contains("很像")
                || question.contains("重复修读")) {
            return "相同或相近的课程";
        }
        if (question.contains("学分")
                && (question.contains("多少") || question.contains("最多"))) {
            return "每学期公共选修课最高";
        }
        if (question.contains("区别")
                && question.contains("公共选修")
                && question.contains("专业选修")) {
            return "公共选修课包括";
        }
        return null;
    }
}
