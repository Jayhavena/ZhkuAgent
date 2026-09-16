package com.zhku.knowledge.conversation.publicelective;

import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.zhku.knowledge.publication.ReviewedKnowledgeJsonReader;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;

import java.io.IOException;
import java.nio.file.Path;

/** 将学分上限证据发布到独立范围，避免替换重修或其他公共选修资料。 */
public final class CreditLimitEvidenceImportCli {

    private static final String DOCUMENT_FAMILY = "STUDENT_HANDBOOK";
    private static final String KNOWLEDGE_SCOPE = "PUBLIC_ELECTIVE_CREDIT_LIMIT";

    private CreditLimitEvidenceImportCli() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("请传入已确认的公共选修课学分上限规则 JSON 文件路径");
        }
        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader()
                .read(Path.of(arguments[0]));
        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
        publisher.publishReplacingScope(publication, DOCUMENT_FAMILY, KNOWLEDGE_SCOPE);

        System.out.println("已发布学生可见的学分上限规则来源：" + publication.sourceId());
        System.out.println("已发布知识片段数量：" + publication.chunks().size());
    }
}
