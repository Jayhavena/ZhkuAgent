package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.governance.AnonymousTelemetryAggregateQueryService;
import com.zhku.knowledge.governance.TelemetryAggregateDimension;
import com.zhku.knowledge.privacy.PrivacyBoundary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 本机管理员的匿名遥测趋势接口；只返回经白名单过滤的桶。 */
@RestController
@Profile("zhku-local-admin")
@ConditionalOnProperty(prefix = "zhku.local-admin", name = "enabled", havingValue = "true")
@RequestMapping("/zhku/local-admin/telemetry")
public final class TelemetryAdminController {

    private final AnonymousTelemetryAggregateQueryService queryService;
    private final Clock clock;

    @Autowired
    public TelemetryAdminController(AnonymousTelemetryAggregateQueryService queryService) {
        this(queryService, Clock.systemUTC());
    }

    TelemetryAdminController(AnonymousTelemetryAggregateQueryService queryService, Clock clock) {
        this.queryService = Objects.requireNonNull(queryService, "聚合查询服务不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @GetMapping("/aggregates")
    public ResponseEntity<List<Map<String, Object>>> aggregates(
            @RequestParam(defaultValue = "route") String dimension,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        TelemetryAggregateDimension selected = parseDimension(dimension);
        LocalDate today = LocalDate.now(clock);
        // 含首尾共 30 天；缺省窗口不能意外扩大为整表，避免历史桶成为比例分母。
        LocalDate start = from == null ? today.minusDays(29) : from;
        LocalDate end = to == null ? today : to;
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "起始日期不能晚于结束日期");
        }
        List<Map<String, Object>> response = queryService.aggregate(selected, start, end).stream()
                .map(bucket -> PrivacyBoundary.telemetryAggregateExport(
                        selected.requestValue(), bucket.value(), bucket.count(), bucket.ratio()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    private static TelemetryAggregateDimension parseDimension(String dimension) {
        try {
            return TelemetryAggregateDimension.fromRequest(dimension);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的遥测聚合维度");
        }
    }
}
