package com.zhku.knowledge.answer;

import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.zhku.knowledge.publication.ReviewedKnowledgePublisher;

/**
 * 本地有依据问答入口。
 *
 * <p>该入口仅供开发阶段人工验证，未注册为 Web 接口，因此不会被学生端直接调用。</p>
 */
public final class GroundedAnswerCli {

    private GroundedAnswerCli() {
    }

    /**
     * 执行本地有依据问答。
     *
     * @param arguments 唯一参数为用户提出的校园问题
     */
    public static void main(String[] arguments) {
        // 命令行一次仅处理一个问题，方便人工验证问答结果。
        if (arguments.length != 1) {
            throw new IllegalArgumentException("请传入一个校园问题");
        }

        // 仅检索已审核发布的知识，避免草稿内容进入回答。
        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
        // 将已发布知识的检索能力交给问答服务，生成包含引用或证据不足结果的回答。
        GroundedAnswer answer = new GroundedAnswerService(
                publisher::searchPublished
        ).answer(arguments[0]);
        // 将内部回答结果转换为终端可读文本。
        System.out.print(new GroundedAnswerPresenter().present(answer));
    }
}
