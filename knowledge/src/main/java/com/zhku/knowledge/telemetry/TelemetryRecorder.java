package com.zhku.knowledge.telemetry;

import java.util.Map;

/**
 * 遥测记录端口（**旁路**语义）。
 *
 * <p><b>为什么是端口而不是直接依赖仓储</b>：链路侧只该知道"有个地方收遥测"，实现可以是
 * 写库、也可以什么都不做（默认关闭）。这样测试能注入一个"必定失败"的实现来验证
 * "遥测写不进去也必须正常作答"这条硬约束。</p>
 *
 * <p><b>调用方约定</b>：字段必须能通过 {@code PrivacyBoundary.anonymousTelemetry} 白名单；
 * 实现抛出的任何异常都由调用方吞掉并降级为"本次不记录"，绝不向上冒泡影响作答。</p>
 */
@FunctionalInterface
public interface TelemetryRecorder {

    void record(Map<String, Object> fields);
}
