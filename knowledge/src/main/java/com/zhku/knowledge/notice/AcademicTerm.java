package com.zhku.knowledge.notice;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 受审核通知所属学期。
 *
 * <p>动态通知不能只写“本学期”：缺少可比较学期会让历史通知在后续学期误命中。</p>
 */
public record AcademicTerm(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^\\d{4}-\\d{4}-[123]$");

    public AcademicTerm {
        value = Objects.requireNonNull(value, "学期不能为空").strip();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("学期格式必须为 YYYY-YYYY-N");
        }
        int startYear = Integer.parseInt(value.substring(0, 4));
        int endYear = Integer.parseInt(value.substring(5, 9));
        if (endYear != startYear + 1) {
            throw new IllegalArgumentException("学年结束年份必须紧接开始年份");
        }
    }
}
