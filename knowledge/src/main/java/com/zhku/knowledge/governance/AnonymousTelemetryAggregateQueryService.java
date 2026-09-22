package com.zhku.knowledge.governance;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 匿名遥测表的只读桶聚合。
 *
 * <p>这里故意不提供按事件标识读取的方法：该表的用途是运营趋势，而非回看任何一条
 * 事件。比例的分母由同一条 SQL 的窗口函数给出，保证它只计算请求日期窗口内的数据。</p>
 */
public final class AnonymousTelemetryAggregateQueryService {

    private final DataSource dataSource;

    public AnonymousTelemetryAggregateQueryService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "数据源不能为空");
    }

    public List<TelemetryAggregateBucket> aggregate(
            TelemetryAggregateDimension dimension,
            LocalDate from,
            LocalDate to
    ) {
        Objects.requireNonNull(dimension, "聚合维度不能为空");
        Objects.requireNonNull(from, "起始日期不能为空");
        Objects.requireNonNull(to, "结束日期不能为空");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("起始日期不能晚于结束日期");
        }

        String column = dimension.columnName();
        String sql = """
                SELECT %s AS bucket_value, COUNT(*) AS bucket_count,
                       SUM(COUNT(*)) OVER () AS window_count
                FROM anonymous_telemetry_event
                WHERE event_date BETWEEN ? AND ?
                GROUP BY %s
                ORDER BY bucket_count DESC, %s ASC NULLS LAST
                """.formatted(column, column, column);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, from);
            statement.setObject(2, to);
            try (ResultSet result = statement.executeQuery()) {
                List<TelemetryAggregateBucket> buckets = new ArrayList<>();
                while (result.next()) {
                    long count = result.getLong("bucket_count");
                    long total = result.getLong("window_count");
                    buckets.add(new TelemetryAggregateBucket(
                            bucketValue(result, dimension),
                            count,
                            BigDecimal.valueOf(count).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    ));
                }
                return List.copyOf(buckets);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法读取匿名遥测聚合", exception);
        }
    }

    private static Object bucketValue(ResultSet result, TelemetryAggregateDimension dimension) throws SQLException {
        if (dimension == TelemetryAggregateDimension.EVENT_DATE) {
            Date eventDate = result.getDate("bucket_value");
            return eventDate == null ? null : eventDate.toLocalDate();
        }
        return result.getObject("bucket_value");
    }
}
