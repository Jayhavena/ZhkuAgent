package com.zhku.knowledge.privacy;

import com.zhku.knowledge.feedback.FeedbackAggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 阶段 10 的最小隐私输出边界。
 *
 * <p>遥测、配置回显和管理员导出都必须先经过显式白名单。白名单之外即使是未来新增的
 * 字段也不会因默认透传而把原文、身份信息或密钥带出服务边界。</p>
 */
public final class PrivacyBoundary {

    private static final Set<String> TELEMETRY_FIELDS = Set.of(
            "route", "retrievalProfile", "generatorProfile", "handoffRequired",
            "failureCategory", "latencyBucket", "estimatedCostBucket", "eventDate"
    );
    private static final Set<String> CONFIGURATION_FIELDS = Set.of(
            "provider", "model", "profile", "status"
    );
    private static final Set<String> TELEMETRY_AGGREGATE_DIMENSIONS = Set.of(
            "route", "failureCategory", "handoffRequired", "latencyBucket", "eventDate"
    );
    private static final Pattern CODE_DIMENSION = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern PROFILE_DIMENSION = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private PrivacyBoundary() {
    }

    /** 为结构化日志保留有限诊断维度，拒绝任何自由文本或敏感字段。 */
    public static Map<String, Object> anonymousTelemetry(Map<String, ?> fields) {
        Objects.requireNonNull(fields, "遥测字段不能为空");
        rejectFieldsOutside(fields, TELEMETRY_FIELDS, "匿名遥测");
        fields.forEach(PrivacyBoundary::validateTelemetryValue);
        return Map.copyOf(fields);
    }

    /** 配置回显采用允许列表复制，密钥或未来未审核字段不会被意外暴露。 */
    public static Map<String, Object> configurationEcho(Map<String, ?> configuration) {
        Objects.requireNonNull(configuration, "配置不能为空");
        Map<String, Object> echo = new LinkedHashMap<>();
        for (String field : CONFIGURATION_FIELDS) {
            Object value = configuration.get(field);
            if (value != null) {
                echo.put(field, value);
            }
        }
        return Map.copyOf(echo);
    }

    /** 管理员导出只能含已审核的聚合维度，绝不携带逐条散列或去重键。 */
    public static Map<String, Object> aggregateExport(FeedbackAggregate aggregate) {
        Objects.requireNonNull(aggregate, "反馈聚合不能为空");
        return Map.of(
                "guideIdentifier", aggregate.guideIdentifier(),
                "guideVersion", aggregate.guideVersion(),
                "reason", aggregate.reason().name(),
                "feedbackDate", aggregate.feedbackDate().toString(),
                "count", aggregate.count()
        );
    }

    /** 遥测聚合只放行一个已审核维度、计数和比例，不能借管理端导出逐条事件或散列键。 */
    public static Map<String, Object> telemetryAggregateExport(
            String dimension,
            Object value,
            long count,
            BigDecimal ratio
    ) {
        if (!TELEMETRY_AGGREGATE_DIMENSIONS.contains(dimension)) {
            throw new IllegalArgumentException("遥测聚合不允许维度：" + dimension);
        }
        if (count < 0) {
            throw new IllegalArgumentException("遥测聚合计数不能为负数");
        }
        Objects.requireNonNull(ratio, "遥测聚合比例不能为空");
        if (ratio.compareTo(BigDecimal.ZERO) < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("遥测聚合比例必须在零到一之间");
        }
        Map<String, Object> export = new LinkedHashMap<>();
        export.put(dimension, normalizeTelemetryAggregateValue(dimension, value));
        export.put("count", count);
        export.put("ratio", ratio.setScale(4, RoundingMode.HALF_UP));
        return Collections.unmodifiableMap(export);
    }

    /** 到期时刻等于当前时刻即应清理，避免匿名明细多保留一个调度周期。 */
    public static boolean hasReachedExpiry(Instant expiresAt, Instant now) {
        return !Objects.requireNonNull(expiresAt, "到期时刻不能为空")
                .isAfter(Objects.requireNonNull(now, "当前时刻不能为空"));
    }

    private static void rejectFieldsOutside(
            Map<String, ?> fields,
            Set<String> allowedFields,
            String boundaryName
    ) {
        fields.keySet().stream()
                .filter(field -> !allowedFields.contains(field))
                .findFirst()
                .ifPresent(field -> {
                    throw new IllegalArgumentException(boundaryName + "不允许字段：" + field);
                });
    }

    private static void validateTelemetryValue(String field, Object value) {
        boolean valid = switch (field) {
            case "route", "failureCategory", "latencyBucket", "estimatedCostBucket" ->
                    value instanceof String text && CODE_DIMENSION.matcher(text).matches();
            case "retrievalProfile", "generatorProfile" ->
                    value instanceof String text && PROFILE_DIMENSION.matcher(text).matches();
            case "handoffRequired" -> value instanceof Boolean;
            case "eventDate" -> isIsoDate(value);
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("匿名遥测字段格式不合法：" + field);
        }
    }

    private static boolean isIsoDate(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        try {
            LocalDate.parse(text);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Object normalizeTelemetryAggregateValue(String dimension, Object value) {
        if (value == null && "failureCategory".equals(dimension)) {
            // 数据库允许失败分类缺省，保留空桶比伪造“正常”标签更诚实。
            return null;
        }
        Object normalized = value instanceof LocalDate date ? date.toString() : value;
        validateTelemetryValue(dimension, normalized);
        return normalized;
    }
}
