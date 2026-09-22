package com.zhku.knowledge;

import org.flywaydb.core.Flyway;

import java.util.Objects;

/**
 * 测试侧 Flyway 迁移的统一入口。
 *
 * <p><b>为什么要统一收口并开启 outOfOrder</b>：本项目的迁移号段按阶段分配，跨线并行开发时
 * 会出现"高版本先入共享库、低版本后补"的真实情形（线上已发生：`V16` 先应用，随后补的 `V15`
 * 被判为乱序迁移，所有直接 `Flyway.configure()` 的入口在校验阶段全部失败）。
 * 把开关收敛到这一个入口，既避免"在十几个测试类里各写一遍"的散落写法，也让这条约定有唯一出处。</p>
 *
 * <p><b>这不是放宽校验</b>：`outOfOrder` 只影响"已存在的历史迁移能否补应用"，不改校验强度、
 * 不改已有迁移内容。**创建新迁移前仍需先确认编号大于共享库当前最大版本**；本入口只是让
 * 已发生的乱序能被安全补上（配合 `ZhkuFlywayTestSupportTest` 的乱序用例锁定行为）。</p>
 */
public final class ZhkuFlywayTestSupport {

    private static final String DEFAULT_LOCATIONS = "classpath:db/migration";

    private ZhkuFlywayTestSupport() {
    }

    /** 迁移默认位置（`classpath:db/migration`），行为与收口前一致，只多了乱序容忍。 */
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
        Objects.requireNonNull(url, "数据库地址不能为空");
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
