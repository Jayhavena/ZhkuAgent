package com.zhku.knowledge;

/**
 * 数据库测试统一从当前进程读取密码，避免把本机密钥放入命令参数或测试源码。
 */
public final class ZhkuDatabaseTestConfig {

    private ZhkuDatabaseTestConfig() {
    }

    public static String databasePassword() {
        String configured = System.getProperty("zhku.db.password");
        return configured == null || configured.isBlank()
                ? System.getenv("ZHKU_DB_PASSWORD")
                : configured;
    }
}
