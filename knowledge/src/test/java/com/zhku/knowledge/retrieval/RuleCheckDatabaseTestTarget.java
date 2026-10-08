package com.zhku.knowledge.retrieval;

import java.util.regex.Pattern;

/** R4测试在建立连接前校验目标；不依赖外层启动器来保护原库。 */
final class RuleCheckDatabaseTestTarget {
    private static final Pattern TARGET = Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$");
    private RuleCheckDatabaseTestTarget() {}

    static void verify(String url) {
        if (url == null) { throw new IllegalArgumentException("必须显式配置专用临时数据库目标"); }
        var match = TARGET.matcher(url);
        if (!match.matches()) { throw new IllegalArgumentException("仅允许专用回环临时数据库，拒绝原库或URL扩展参数"); }
        int port = Integer.parseInt(match.group(1));
        if (port < 1024 || port > 65535 || port == 54329) {
            throw new IllegalArgumentException("拒绝原演示库端口或非法端口");
        }
    }
}
