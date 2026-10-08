package com.zhku.knowledge;

import org.flywaydb.core.Flyway;

import java.util.Objects;

/**
 * 测试侧 Flyway 迁移的统一入口。
 *
 * <p>只允许显式专用临时库；校验须先于Flyway配置，直接运行旧测试也不能迁移原库。
 * outOfOrder保留既有测试合同，用临时schema验证历史迁移号乱序场景；不修改已有迁移内容，
 * 也不授予共享或生产库补迁移权限。</p>
 */
public final class ZhkuFlywayTestSupport {

    private static final String DEFAULT_LOCATIONS = "classpath:db/migration";

    private ZhkuFlywayTestSupport() {
    }

    /** 迁移默认位置；所有入口均先拒绝原库，乱序只沿用既有临时库合同。 */
    public static void migrate(String url, String user, String password) {
        migrate(url, user, password, DEFAULT_LOCATIONS);
    }

    /**
     * 迁移指定位置。
     *
     * @param locations 迁移脚本位置（如 `classpath:db/migration` 或 `filesystem:/tmp/xxx`）；
     *                  参数化是为了让乱序用例能用临时目录里的脚本，而不必污染正式迁移目录。
     */
    public static void migrate(String url, String user, String password, String locations) {
        flyway(url, user, password, locations).migrate();
    }

    /** 直接拿到已配置好的 Flyway 实例（需要读取 schema history 等场景）。 */
    public static Flyway flyway(String url, String user, String password, String locations) {
        return configured(url, user, password, locations, null);
    }

    /**
     * 在指定 schema 内迁移（供"乱序可迁移"用例使用）：真实迁移历史在 `public`，
     * 用例需要自己的 schema 才能用临时脚本模拟"高版本先入库、低版本后补"而不污染正式历史。
     *
     * @param schema 目标 schema（自动创建并设为默认）
     */
    public static Flyway flywayIn(
            String url, String user, String password, String locations, String schema) {
        return configured(url, user, password, locations, schema);
    }

    private static Flyway configured(
            String url, String user, String password, String locations, String schema) {
        ZhkuDatabaseTestConfig.verifyTarget(url);
        Objects.requireNonNull(user, "数据库用户不能为空");
        Objects.requireNonNull(password, "数据库密码不能为空");
        Objects.requireNonNull(locations, "迁移位置不能为空");
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations(locations)
                // 乱序容忍：见类注释——这不是放宽校验，而是让已发生的乱序能被安全补应用。
                .outOfOrder(true);
        if (schema != null) {
            configuration = configuration.schemas(schema).defaultSchema(schema);
        }
        return configuration.load();
    }
}
