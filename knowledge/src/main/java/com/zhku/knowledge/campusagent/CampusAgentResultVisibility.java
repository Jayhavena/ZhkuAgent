package com.zhku.knowledge.campusagent;

import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.campusagent.archive.CampusRunHistoryVisibility;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/** 展示时重新核验原来源；只形成响应副本，不能把权限变化改写成执行失败。 */
public final class CampusAgentResultVisibility {
    private static final String HIDDEN = "执行状态已记录；当前政策资料不可展示，请重新查询或向教务部门人工核实。";
    private final Function<CampusRunArchive.PayloadCandidate, CampusRunArchive.Visibility> permission;

    public CampusAgentResultVisibility(DataSource source) {
        this(checker(Objects.requireNonNull(source)));
    }

    public CampusAgentResultVisibility(
            Function<CampusRunArchive.PayloadCandidate, CampusRunArchive.Visibility> permission) {
        this.permission = Objects.requireNonNull(permission);
    }

    public CampusCreditAgentManager.Snapshot filter(CampusCreditAgentManager.Snapshot result) {
        Objects.requireNonNull(result);
        boolean protectedResult = result.source() != null || !result.steps().isEmpty()
                || result.status() == CampusCreditAgentService.Status.ANSWERED;
        if (!protectedResult) return result;
        if (result.source() != null && result.answer() != null) {
            try {
                var gate = permission.apply(new CampusRunArchive.PayloadCandidate(result.answer(), result.source()));
                if (gate != null && gate.status() == CampusRunArchive.VisibilityStatus.VISIBLE) return result;
            } catch (RuntimeException unavailable) {
                // 未能取得许可不等于可见；不记录可能包含凭据/缓存正文的异常原文。
            }
        }
        // LOOKUP.detail 中也有来源定位；隐藏正文时不能留下步骤泄漏，业务元数据仍然真实。
        return new CampusCreditAgentManager.Snapshot(result.runId(), result.state(), result.stopReason(),
                result.terminated(), result.status(), HIDDEN, result.failureCategory(), List.of(), null);
    }

    private static Function<CampusRunArchive.PayloadCandidate, CampusRunArchive.Visibility> checker(DataSource original) {
        DataSource source = original;
        if (original instanceof DriverManagerDataSource driver) {
            if (driver.getUrl() == null || driver.getUrl().contains("?")) {
                throw new IllegalArgumentException("资料许可查询不接受覆盖时限的数据库URL参数");
            }
            var bounded = new DriverManagerDataSource(driver.getUrl(), driver.getUsername(), driver.getPassword());
            var properties = new Properties();
            properties.setProperty("connectTimeout", "1");
            properties.setProperty("socketTimeout", "2");
            properties.setProperty("cancelSignalTimeout", "1");
            bounded.setConnectionProperties(properties);
            source = bounded;
        }
        DataSource bounded = source;
        var visibility = new CampusRunHistoryVisibility();
        return candidate -> {
            try (var connection = bounded.getConnection()) {
                // 与历史使用同一原来源检查，不能拿最新政策内容替换旧引用。
                return visibility.evaluate(connection, candidate);
            } catch (SQLException | RuntimeException unavailable) {
                return new CampusRunArchive.Visibility(CampusRunArchive.VisibilityStatus.HIDDEN,
                        CampusRunArchive.Reason.SOURCE_UNAVAILABLE);
            }
        };
    }
}
