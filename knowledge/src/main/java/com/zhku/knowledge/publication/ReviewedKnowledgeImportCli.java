package com.zhku.knowledge.publication;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 本地开发阶段的已审核资料导入入口。
 *
 * <p>它不启动 Web 服务，也不读取应用配置中的模型密钥，减少首次资料入库时的环境依赖。</p>
 */
public final class ReviewedKnowledgeImportCli {

    private ReviewedKnowledgeImportCli() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException(
                    "请传入一个已审核候选知识 JSON 文件路径"
            );
        }

        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader()
                .read(Path.of(arguments[0]));
        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
        publisher.publish(publication);

        System.out.println("已导入审核通过的资料来源：" + publication.sourceId());
        System.out.println("已导入知识片段数量：" + publication.chunks().size());
    }

}
