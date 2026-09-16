package com.zhku.knowledge.conversation.elective;

import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.zhku.knowledge.publication.ReviewedKnowledgeJsonReader;
import com.zhku.knowledge.publication.ReviewedKnowledgePublication;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;

import java.io.IOException;
import java.nio.file.Path;

/** 将已确认的第十八条重修证据发布到独立知识范围的本机管理入口。 */
public final class ElectiveRetakeEvidenceImportCli {

    private static final String DOCUMENT_FAMILY = "STUDENT_HANDBOOK";
    private static final String KNOWLEDGE_SCOPE = "ELECTIVE_RETAKE";

    private ElectiveRetakeEvidenceImportCli() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("请传入已确认的重修规则 JSON 文件路径");
        }
        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader()
                .read(Path.of(arguments[0]));
        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
        // 独立范围替换旧版本，避免发布本条规则时归档其他公共选修课知识。
        publisher.publishReplacingScope(publication, DOCUMENT_FAMILY, KNOWLEDGE_SCOPE);

        System.out.println("已发布学生可见的重修规则来源：" + publication.sourceId());
        System.out.println("已发布知识片段数量：" + publication.chunks().size());
    }
}
