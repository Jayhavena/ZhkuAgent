package com.zhku.knowledge.telemetry;

import com.zhku.knowledge.privacy.PrivacyBoundary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 匿名遥测写入路径：**先过 {@link PrivacyBoundary#anonymousTelemetry} 白名单，再落库**。
 *
 * <p><b>为什么不自己定字段表</b>：隐私白名单是阶段 10 的既定边界（`PrivacyBoundary`），
 * 遥测只是它的消费方之一。若这里另建一套"允许字段"，两套规则必然会漂移，
 * 最终由谁更松决定泄露面——所以写入前一律调用既有白名单，白名单拒绝即不落库。</p>
 *
 * <p><b>为什么失败必须无副作用</b>：白名单与格式校验都在写库之前完成，被拒的调用不会留下半行数据；
 * 表上的 CHECK 是第二道闸（防绕过应用层直接写库）。</p>
 *
 * <p><b>这些维度能回答什么</b>：哪条链路＝`route`（五级结论）+ `retrievalProfile`/`generatorProfile`
 * （哪套检索/生成栈）；哪一级路由＝`route`；命中/降级/转人工＝`failureCategory`（`NONE` 为正常命中）
 * 与 `handoffRequired`；耗时＝`latencyBucket`（只落桶，不落精确值）。</p>
 */
public final class JdbcAnonymousTelemetryRepository {

    /** NOT NULL 维度：缺失即视为调用方 bug，直接拒绝而不是落一行不完整数据。 */
    private static final List<String> REQUIRED_FIELDS =
            List.of("route", "handoffRequired", "latencyBucket", "eventDate");

    private final DataSource dataSource;

    public JdbcAnonymousTelemetryRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    public void record(Map<String, Object> fields) {
        Objects.requireNonNull(fields, "遥测字段不能为空");
        // 白名单 + 取值形态校验：自由文本、原问答、身份信息、密钥、逐条散列在此被拒（无副作用）。
        Map<String, Object> bounded = PrivacyBoundary.anonymousTelemetry(fields);
        for (String required : REQUIRED_FIELDS) {
            if (!bounded.containsKey(required)) {
                throw new IllegalArgumentException("匿名遥测缺少必需维度：" + required);
            }
        }
        insert(bounded);
    }

    /** 按日计数（运维自检与测试用）。 */
    public long countByEventDate(LocalDate eventDate) {
        Objects.requireNonNull(eventDate, "事件日期不能为空");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM anonymous_telemetry_event WHERE event_date = ?")) {
            statement.setObject(1, eventDate);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return 0L;
                }
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法统计匿名遥测事件", exception);
        }
    }

    private void insert(Map<String, Object> bounded) {
        String sql = """
                INSERT INTO anonymous_telemetry_event (
                    id, route, retrieval_profile, generator_profile, handoff_required,
                    failure_category, latency_bucket, estimated_cost_bucket, event_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, text(bounded, "route"));
            statement.setString(3, text(bounded, "retrievalProfile"));
            statement.setString(4, text(bounded, "generatorProfile"));
            statement.setBoolean(5, (Boolean) bounded.get("handoffRequired"));
            statement.setString(6, text(bounded, "failureCategory"));
            statement.setString(7, text(bounded, "latencyBucket"));
            statement.setString(8, text(bounded, "estimatedCostBucket"));
            // 白名单已校验为 ISO 日期，这里的解析不会抛。
            statement.setObject(9, LocalDate.parse((String) bounded.get("eventDate")));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("无法写入匿名遥测事件", exception);
        }
    }

    private static String text(Map<String, Object> bounded, String field) {
        Object value = bounded.get(field);
        return value == null ? null : (String) value;
    }
}
