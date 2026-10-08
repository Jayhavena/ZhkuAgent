package com.zhku.knowledge.campusagent.archive;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.net.URI;

/** 验收只能写入脚本创建的专用库，缺配置不能回退演示库。 */
public final class ArchiveTestDatabase {
    private ArchiveTestDatabase() { }

    public static DataSource dataSource() {
        String url = System.getProperty("zhku.db.url", System.getenv("ZHKU_DB_URL"));
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("缺少专用临时库地址");
        }
        URI target = URI.create(url.substring("jdbc:".length()));
        if (!"127.0.0.1".equals(target.getHost()) || target.getPort() < 1024
                || target.getPort() > 65535 || target.getPort() == 54329
                || !"/zhkuagent_run_archive_e2e".equals(target.getPath())
                || target.getUserInfo() != null || target.getQuery() != null || target.getFragment() != null) {
            throw new IllegalArgumentException("拒绝非专用临时库");
        }
        String password = ZhkuDatabaseTestConfig.databasePassword();
        if (password == null || password.isBlank()) throw new IllegalArgumentException("缺少本次数据库凭据");
        return new DriverManagerDataSource(url, System.getProperty("zhku.db.user", "zhkuagent"), password);
    }

    public static void migrate() {
        var validated = (DriverManagerDataSource) dataSource();
        ZhkuFlywayTestSupport.migrate(validated.getUrl(), validated.getUsername(),
                ZhkuDatabaseTestConfig.databasePassword());
    }
}
