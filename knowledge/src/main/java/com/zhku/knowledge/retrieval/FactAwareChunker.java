package com.zhku.knowledge.retrieval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 事实感知切分器：把已发布资料正文切成"可引用的最小事实单元"。
 *
 * <p>切分只认两种确定性边界：条款（第 X 条）与章节标题（一、二、……）。
 * 刻意不做语义切分——语义切分不可复现，同一资料两次索引产出不同 chunk
 * 会让内容哈希审计失去意义。通知办理事实（时间、入口）随条款行保留在同一
 * chunk 内，不被截断。</p>
 */
public final class FactAwareChunker {

    private static final Pattern CLAUSE_HEADING = Pattern.compile("^第[0-9一二三四五六七八九十百零]+条.*");
    private static final Pattern SECTION_HEADING = Pattern.compile("^[一二三四五六七八九十]+、.*");

    /**
     * 切分资料正文。
     *
     * @param content 已发布资料全文
     * @return 按出现顺序编号的 chunk 草稿列表
     */
    public List<ChunkDraft> chunk(String content) {
        Objects.requireNonNull(content, "资料正文不能为空");
        if (content.isBlank()) {
            throw new IllegalArgumentException("资料正文不能为空");
        }

        List<ChunkDraft> drafts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentTitle = null;

        for (String line : content.split("\\R")) {
            boolean boundary = isBoundary(line);
            if (boundary && !current.isEmpty()) {
                drafts.add(draft(drafts.size(), currentTitle, current.toString()));
                current.setLength(0);
            }
            if (boundary) {
                currentTitle = line.strip();
            }
            if (!line.isBlank()) {
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(line.strip());
            }
        }
        if (!current.isEmpty()) {
            drafts.add(draft(drafts.size(), currentTitle, current.toString()));
        }
        if (drafts.isEmpty()) {
            // 理论上不可达（非空正文至少产出一个 chunk），防御性兜底避免静默返回空索引。
            throw new IllegalStateException("非空资料未切分出任何 chunk");
        }
        return drafts;
    }

    private static boolean isBoundary(String line) {
        String trimmed = line.strip();
        return CLAUSE_HEADING.matcher(trimmed).matches()
                || SECTION_HEADING.matcher(trimmed).matches();
    }

    private static ChunkDraft draft(int index, String title, String content) {
        return new ChunkDraft(index, title, content, sha256(content));
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现的算法，缺失只意味着运行环境损坏。
            throw new IllegalStateException("运行环境缺少 SHA-256 实现", e);
        }
    }
}
