package com.zhku.knowledge.retrieval;

import java.util.Objects;
import java.util.UUID;

/**
 * 一次索引运行的结果摘要（供证据账本记录分母：候选数与实际入索引数）。
 *
 * @param runId 运行标识
 * @param candidateChunks 通过资格过滤的候选片段数
 * @param indexedEntries 实际写入索引的条目数
 */
public record IndexRunResult(UUID runId, int candidateChunks, int indexedEntries) {

    public IndexRunResult {
        Objects.requireNonNull(runId, "运行标识不能为空");
        if (candidateChunks < 0 || indexedEntries < 0) {
            throw new IllegalArgumentException("索引计数不能为负");
        }
    }
}
