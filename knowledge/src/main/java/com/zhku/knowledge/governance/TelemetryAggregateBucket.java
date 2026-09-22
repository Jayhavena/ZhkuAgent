package com.zhku.knowledge.governance;

import java.math.BigDecimal;

/** 单个匿名遥测桶的只读查询结果，尚未越过隐私输出白名单。 */
public record TelemetryAggregateBucket(Object value, long count, BigDecimal ratio) {
}
