package com.zhku.knowledge.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import com.zhku.knowledge.publication.LocalKnowledgePublisherFactory;
import com.zhku.knowledge.campusagent.archive.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@Profile("zhku-local-preview")
@ConditionalOnProperty(name = { "zhku.campus-agent.enabled",
        "zhku.campus-agent.archive.enabled" }, havingValue = "true")
public class CampusRunArchiveConfiguration {
    @Bean
    CampusRunArchive.Limits zhkuCampusRunArchiveLimits(Environment environment) {
        var defaults = CampusRunArchive.Limits.defaults();
        try {
            int days = Integer.parseInt(environment.getProperty("zhku.campus-agent.archive.retention-days", "7"));
            int records = Integer.parseInt(environment.getProperty("zhku.campus-agent.archive.max-records", "10000"));
            return new CampusRunArchive.Limits(defaults.operation(), defaults.startup(), defaults.drain(),
                    Duration.ofDays(days), records);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("归档保留天数或容量超出预览边界");
        }
    }

    @Bean(destroyMethod = "")
    CampusRunArchiveOwner zhkuCampusRunArchiveOwner(Environment environment, CampusRunArchive.Limits limits) {
        // 构造不取锁；只有Store工厂成功收口资源时才启动，避免refresh失败留下无人销毁的owner。
        return new CampusRunArchiveOwner(archiveDataSource(environment), limits);
    }

    @Bean(name = "zhkuCampusRunArchiveStore", destroyMethod = "close")
    CampusRunArchiveStore zhkuCampusRunArchiveStore(Environment environment, CampusRunArchiveOwner owner,
            CampusRunArchive.Limits limits) {
        long deadline = System.nanoTime() + limits.startup().toNanos();
        try {
            var source = archiveDataSource(environment);
            owner.start();
            return new JdbcCampusRunArchiveStore(source, owner, limits, new CampusRunHistoryVisibility());
        } catch (RuntimeException e) {
            owner.close(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
            throw e;
        }
    }

    @Bean
    CampusRunHistoryService zhkuCampusRunHistoryService(CampusRunArchiveStore store) {
        return new CampusRunHistoryService(store);
    }

    private static DriverManagerDataSource archiveDataSource(Environment environment) {
        // 原预览配置可被关闭；归档必须独立检查回环边界，且在任何连接尝试之前拒绝外部绑定。
        new LocalPreviewBindingGuard(environment.getProperty("server.address"));
        String url = environment.getProperty("zhku.db.url");
        try {
            if (url == null || url.isBlank() || !url.startsWith("jdbc:postgresql://"))
                throw new IllegalArgumentException();
            URI target = URI.create(url.substring(5));
            // URL参数会覆盖驱动Properties中的时限；本阶段拒绝所有query，禁止凭据或无限预算绕过。
            if (target.getHost() == null || target.getPath() == null || target.getPath().length() < 2
                    || target.getUserInfo() != null
                    || target.getQuery() != null || target.getFragment() != null || target.getPort() == 0
                    || target.getPort() > 65535)
                throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("归档要求显式、无凭据和查询参数的PostgreSQL地址");
        }
        return LocalKnowledgePublisherFactory.createDataSource(environment);
    }
}
