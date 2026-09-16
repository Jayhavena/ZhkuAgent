package com.zhku.knowledge.governance;

import java.util.Objects;

/**
 * 本机受控资料文件的安全标识，不携带真实存储路径。
 */
public record StoredKnowledgeFile(
        String storageKey,
        String originalFilename,
        String sha256,
        long size
) {

    public StoredKnowledgeFile {
        storageKey = requireText(storageKey, "本机文件标识不能为空");
        if (storageKey.contains("/") || storageKey.contains("\\")) {
            throw new IllegalArgumentException("本机文件标识不能包含路径分隔符");
        }
        originalFilename = requireText(originalFilename, "原始文件名不能为空");
        sha256 = requireText(sha256, "文件摘要不能为空");
        if (!sha256.matches("^[A-F0-9]{64}$")) {
            throw new IllegalArgumentException("文件摘要必须是 64 位大写十六进制");
        }
        if (size < 1) {
            throw new IllegalArgumentException("PDF 文件不能为空");
        }
    }

    private static String requireText(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
