package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.feedback.FeedbackAggregate;
import com.zhku.knowledge.feedback.FeedbackAggregateQueryService;
import com.zhku.knowledge.feedback.FeedbackReason;
import com.zhku.knowledge.feedback.FeedbackRetentionService;
import com.zhku.knowledge.privacy.PrivacyBoundary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 本机管理员的匿名反馈审核入口。
 *
 * <p>管理员只能看到聚合结果；若把逐条事件或匿名散列接到这里，即使没有真实身份也会
 * 重新获得可关联轨迹，破坏反馈最小化收集的前提。</p>
 */
@RestController
@Profile("zhku-local-admin")
@ConditionalOnProperty(
        prefix = "zhku.local-admin",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-admin/feedback")
public final class FeedbackAdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedbackAdminController.class);
    private static final String ACTIVE_PROFILE_KEY = "zhku.rag.active-profile";
    private static final String RAG_ENABLED_KEY = "zhku.rag.enabled";

    private final FeedbackAggregateQueryService aggregateQueryService;
    private final FeedbackRetentionService retentionService;
    private final StandardEnvironment environment;

    public FeedbackAdminController(
            FeedbackAggregateQueryService aggregateQueryService,
            FeedbackRetentionService retentionService
    ) {
        this.aggregateQueryService = Objects.requireNonNull(
                aggregateQueryService, "反馈聚合查询服务不能为空"
        );
        this.retentionService = Objects.requireNonNull(retentionService, "反馈保留服务不能为空");
        this.environment = new StandardEnvironment();
    }

    @GetMapping(value = "/aggregates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FeedbackAggregateResponse>> listAggregates() {
        List<FeedbackAggregateResponse> response = aggregateQueryService.listAggregates().stream()
                .map(FeedbackAggregateResponse::from)
                .toList();
        recordAnonymousTelemetry();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }

    /** 管理端只查看启用状态，不读取或返回密钥值及其环境变量名。 */
    @GetMapping(value = "/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> configuration() {
        String profile = property(ACTIVE_PROFILE_KEY, "not-configured");
        String profilePrefix = "zhku.rag.profiles." + profile + ".";
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("provider", profile);
        configuration.put("model", property(profilePrefix + "chat-model", "not-configured"));
        configuration.put("profile", profile);
        configuration.put("status", enabled() ? "ENABLED" : "DISABLED");
        // 配置视图必须整体经过白名单，避免以后新增 provider 字段时被控制器默认透传。
        configuration.put("apiKeyEnvVar", environment.getProperty(profilePrefix + "api-key-env-var"));
        configuration.put("baseUrl", environment.getProperty(profilePrefix + "base-url"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(PrivacyBoundary.configurationEcho(configuration));
    }

    /** 由本机管理员显式触发，避免后台反馈静默影响其他业务链路。 */
    @PostMapping(value = "/retention", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RetentionResponse> aggregateExpired() {
        ResponseEntity<RetentionResponse> response = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RetentionResponse(retentionService.aggregateExpired()));
        recordAnonymousTelemetry();
        return response;
    }

    public record FeedbackAggregateResponse(
            String guideIdentifier,
            String guideVersion,
            FeedbackReason reason,
            String feedbackDate,
            long count
    ) {
        static FeedbackAggregateResponse from(FeedbackAggregate aggregate) {
            Map<String, Object> exported = PrivacyBoundary.aggregateExport(aggregate);
            return new FeedbackAggregateResponse(
                    (String) exported.get("guideIdentifier"),
                    (String) exported.get("guideVersion"),
                    FeedbackReason.valueOf((String) exported.get("reason")),
                    (String) exported.get("feedbackDate"),
                    ((Number) exported.get("count")).longValue()
            );
        }
    }

    public record RetentionResponse(long aggregatedEventCount) {
    }

    private boolean enabled() {
        return Boolean.parseBoolean(environment.getProperty(RAG_ENABLED_KEY, "false"));
    }

    private String property(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private void recordAnonymousTelemetry() {
        Map<String, Object> telemetry = PrivacyBoundary.anonymousTelemetry(Map.of(
                "route", "LOCAL_ADMIN_FEEDBACK",
                "failureCategory", "NONE",
                "eventDate", LocalDate.now(ZoneOffset.UTC).toString()
        ));
        LOGGER.info("zhku.anonymousTelemetry={}", telemetry);
    }
}
