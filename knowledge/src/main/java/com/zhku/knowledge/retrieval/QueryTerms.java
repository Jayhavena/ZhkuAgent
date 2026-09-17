package com.zhku.knowledge.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 受控词法检索的问题分词（确定性、无状态）。
 *
 * <p>刻意不用分词器或 BM25：首期只做"按标点/空白切分 + 长度≥2 的去重词面匹配"，
 * 其效果由版本化评测集测量；如果基线显示中文词法不足，再单独决策分析器或
 * BM25 方案，而不是先把未经验证的复杂度引入链路。</p>
 */
public final class QueryTerms {

    private static final int MIN_TERM_LENGTH = 2;

    private QueryTerms() {
    }

    /** 抽取可检索词面，保持出现顺序并去重。 */
    public static List<String> extract(String query) {
        Objects.requireNonNull(query, "查询不能为空");
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : query.split("[^\\p{IsHan}A-Za-z0-9]+")) {
            if (token.length() >= MIN_TERM_LENGTH) {
                terms.add(token);
            }
        }
        return List.copyOf(new ArrayList<>(terms));
    }
}
