package com.zhku.knowledge;

/**
 * 数据库测试统一从当前进程读取密码，避免把本机密钥放入命令参数或测试源码。
 */
public final class ZhkuDatabaseTestConfig {

    private ZhkuDatabaseTestConfig() {
    }
    private static final java.util.regex.Pattern TARGET=java.util.regex.Pattern.compile(
        "^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/(zhkuagent_rulecheck_e2e|zhkuagent_run_archive_e2e|zhkuagent_e2e)$");
    public static String databaseUrl() {
        String url=System.getProperty("zhku.db.url");verifyTarget(url);return url;
    }
    public static void verifyTarget(String url) {
        // 静态字段/数据源构造就拒绝，setup失败后cleanup也不能靠默认地址重新连接原库。
        if(url==null)throw new IllegalArgumentException("数据库测试必须显式配置专用临时库目标");
        var match=TARGET.matcher(url);
        if(!match.matches())throw new IllegalArgumentException("数据库测试仅允许精确回环专用临时库，拒绝原库与URL扩展");
        int port=Integer.parseInt(match.group(1));
        if(port<1024||port>65535||port==54329)throw new IllegalArgumentException("拒绝原演示库端口或非法测试端口");
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
