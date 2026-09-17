package com.zhku.knowledge.eval;

import java.util.Locale;
import java.util.Objects;

/**
 * 一次评测运行的报告（可复核的最小集合）。
 *
 * <p>必须携带分母与**版本指纹**：没有分母的指标不可复核；没有 Prompt/模型/
 * 索引 profile 版本，指标变化就无法归因，历史基线也不可比。
 * 同时记录两路可用性：某路不可用时指标只反映幸存路径，不能当成
 * "模型/检索效果"来解读。</p>
 *
 * @param datasetVersion 评测集版本
 * @param k 指标计算的截断深度 K
 * @param caseCount 参与评测的问题数（分母）
 * @param recallAtK 各问题的 Recall@K 均值
 * @param mrr 各问题的 MRR 均值
 * @param citationAccuracy 各问题引用准确率均值（引用取前 K 条候选）
 * @param lexicalAvailable 词法路本轮是否可用
 * @param vectorAvailable 向量路本轮是否可用
 * @param generatorProfile 生成侧 profile（未接入模型时标注为未参与）
 * @param promptVersion 系统约束（Prompt）版本指纹
 * @param chunkingVersion 切分版本
 */
public record RetrievalEvalReport(
        String datasetVersion,
        int k,
        int caseCount,
        double recallAtK,
        double mrr,
        double citationAccuracy,
        boolean lexicalAvailable,
        boolean vectorAvailable,
        String generatorProfile,
        String promptVersion,
        String chunkingVersion
) {

    public RetrievalEvalReport {
        Objects.requireNonNull(datasetVersion, "评测集版本不能为空");
        Objects.requireNonNull(generatorProfile, "生成侧 profile 不能为空");
        Objects.requireNonNull(promptVersion, "Prompt 版本不能为空");
        Objects.requireNonNull(chunkingVersion, "切分版本不能为空");
        if (k <= 0 || caseCount <= 0) {
            throw new IllegalArgumentException("K 与评测问题数必须为正数");
        }
    }

    /** 渲染为可直接贴入证据账本的一行摘要。 */
    public String render() {
        return String.format(
                Locale.ROOT,
                "dataset=%s, K=%d, N=%d, Recall@K=%.4f, MRR=%.4f, 引用准确率=%.4f, "
                        + "词法路=%s, 向量路=%s, prompt=%s, 生成 profile=%s, chunking=%s",
                datasetVersion, k, caseCount, recallAtK, mrr, citationAccuracy,
                lexicalAvailable ? "可用" : "不可用", vectorAvailable ? "可用" : "不可用",
                promptVersion, generatorProfile, chunkingVersion);
    }
}
