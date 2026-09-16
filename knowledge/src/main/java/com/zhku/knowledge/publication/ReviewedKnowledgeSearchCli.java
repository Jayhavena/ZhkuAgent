package com.zhku.knowledge.publication;

/**
 * 本地已发布资料的关键词检索入口。
 *
 * <p>该入口只展示已发布片段及其出处，用于人工核验；它不构成面向学生的 Web 接口。</p>
 */
public final class ReviewedKnowledgeSearchCli {

    private ReviewedKnowledgeSearchCli() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("请传入一个检索关键词");
        }

        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
        String result = new PublishedKnowledgeSearchPresenter().present(
                arguments[0],
                publisher.searchPublished(arguments[0])
        );
        System.out.print(result);
    }
}
