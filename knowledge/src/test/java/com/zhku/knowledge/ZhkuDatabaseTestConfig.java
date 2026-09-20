package com.zhku.knowledge;

/**
 * 数据库测试统一从当前进程读取密码，避免把本机密钥放入命令参数或测试源码。
 */
public final class ZhkuDatabaseTestConfig {

    private ZhkuDatabaseTestConfig() {
    }

    public static String databasePassword() {
        if (System.getProperty("zhku.db.password") != null) {
            // 测试也不能例外，否则命令行参数会绕过生产侧的密码来源约束。
            throw new IllegalArgumentException(
                    "密码不接受系统属性注入，请使用环境变量 ZHKU_DB_PASSWORD"
            );
        }
        return System.getenv("ZHKU_DB_PASSWORD");
    }
}
