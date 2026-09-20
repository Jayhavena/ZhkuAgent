package com.zhku.knowledge.publication;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 已审核策展清单的**按范围**导入入口：`{策展 JSON} → {资料族, 知识范围}`。
 *
 * <p><b>为什么不复用既有的两个入口</b>：{@link ReviewedKnowledgeImportCli} 走 `publish()`（资料族与
 * 知识范围都写成 LEGACY、不做同范围替换），只适合首次入库；`CreditLimitEvidenceImportCli` /
 * `ElectiveRetakeEvidenceImportCli` 把资料族与范围写死在各自类里，无法服务新的主题。
 * 本入口把两者参数化，新增主题不必再复制一个 CLI。</p>
 *
 * <p><b>为什么不走 8124 管理端链路</b>：管理端表单链路的出处定位是**按候选页码现拼**的
 * （{@code KnowledgeGovernancePublisher}），审核阶段填写的 `sourceLocator` 不落库；策展清单里的
 * 定位是**条级**的（形如「PDF 第 76 页，第 2、4 条」），是回答「依据哪一条」的关键信息。混用会让
 * 同一份资料在库里出现两种定位粒度、引用可核验性下降——本项目已实际发生过一次。故：**条级定位走本入口**，
 * 管理端链路留给需要人工逐页审核的原始 PDF。</p>
 *
 * <p><b>为什么资料族与知识范围必须显式传入</b>：发布器按「同资料族 + 同知识范围」归档旧版本
 * （{@link ReviewedKnowledgePublisher#publishReplacingScope}）。同一知识范围下要并存多份来源
 * （例如面向学生的规则 + 仅供本地验证的内部参考），必须给出**不同的资料族**，否则后发布的会把
 * 先发布的静默归档，表现为「刚发布的资料突然查不到」。</p>
 *
 * <p>用法：{@code java -cp <classpath> com.zhku.knowledge.publication.ReviewedKnowledgeScopeImportCli <策展JSON路径> <资料族> <知识范围>}</p>
 */
public final class ReviewedKnowledgeScopeImportCli {

    private ReviewedKnowledgeScopeImportCli() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "用法：ReviewedKnowledgeScopeImportCli <策展JSON路径> <资料族> <知识范围>");
        }
        Path curationFile = Path.of(arguments[0]);
        String documentFamily = requireText(arguments[1], "资料族不能为空");
        String knowledgeScope = requireText(arguments[2], "知识范围不能为空");

        // 读取器强制校验整份资料的审核状态、逐条片段的审核状态，以及
        // 「学生可见必须同时确认分享权限」——未审核文本无法借本入口绕过审核。
        ReviewedKnowledgePublication publication = new ReviewedKnowledgeJsonReader()
                .read(curationFile);
        ReviewedKnowledgePublisher publisher = LocalKnowledgePublisherFactory.create();
        publisher.publishReplacingScope(publication, documentFamily, knowledgeScope);

        System.out.println("已发布来源：" + publication.sourceId());
        System.out.println("标题：" + publication.sourceTitle());
        System.out.println("资料族：" + documentFamily + "，知识范围：" + knowledgeScope);
        System.out.println("学生可见：" + publication.studentVisible()
                + "，分享权利已确认：" + publication.sharingRightsConfirmed());
        System.out.println("知识片段数量：" + publication.chunks().size());
        for (ReviewedKnowledgeChunk chunk : publication.chunks()) {
            System.out.println("  - [" + chunk.chunkIndex() + "] " + chunk.sourceLocator()
                    + " · " + chunk.sectionTitle());
        }
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return stripped;
    }
}
