package com.zhku.knowledge.eval;

import java.util.List;
import java.util.Objects;

/**
 * 评测集清单（manifest）：登记"有哪些数据集文件、什么版本、多少用例"。
 *
 * <p><b>为什么需要清单</b>：数据集文件可以被改而测试照样跑——清单把"改了数据"变成
 * 需要同步改清单的显式动作，加载时交叉核对不一致立即报错，基线才可信。</p>
 */
public record EvaluationManifest(
        String manifestVersion,
        String corpusFingerprint,
        List<Entry> datasets
) {

    public EvaluationManifest {
        manifestVersion = requireText(manifestVersion, "manifestVersion 不能为空");
        corpusFingerprint = requireText(corpusFingerprint, "语料指纹不能为空");
        if (!corpusFingerprint.startsWith(EvaluationDataset.CORPUS_FINGERPRINT_PREFIX)) {
            throw new IllegalArgumentException("语料指纹必须以 "
                    + EvaluationDataset.CORPUS_FINGERPRINT_PREFIX + " 开头");
        }
        Objects.requireNonNull(datasets, "数据集登记不能为空");
        if (datasets.isEmpty()) {
            throw new IllegalArgumentException("清单必须登记至少一个数据集");
        }
        datasets = List.copyOf(datasets);
    }

    /** 一条数据集登记：文件位置 + 版本 + 用例数（加载时与数据集本体交叉核对）。 */
    public record Entry(String datasetId, String file, String datasetVersion, int caseCount) {

        public Entry {
            datasetId = requireText(datasetId, "datasetId 不能为空");
            file = requireText(file, "数据集文件不能为空");
            datasetVersion = requireText(datasetVersion, "datasetVersion 不能为空");
            if (caseCount <= 0) {
                throw new IllegalArgumentException("caseCount 必须为正数：" + caseCount);
            }
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
