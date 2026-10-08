# R1 执行范围与门槛

日期：2026-10-07；按 P0 Task 2 顺序执行。R0 工程入口通过是本批前置条件；G0 资料和人工参考仍未通过。用户再次授权下一步后，独立推进此确定性缺陷，不扩大到 R2–R4。

业务改动仅 HybridRetrievalService.java 和 HybridRetrievalServiceTest.java。四参入口委托五参入口，参数顺序为 query/candidateLimit/resultLimit/lexical/vector；默认每路 max(20, resultLimit)，20 是待调优起点。每路取候选，RRF 融合后再截结果；约束 candidateLimit >= resultLimit > 0。

RED 用旧实现和遵守 limit 的替身，必须是实际排名断言失败。GREEN 验证兼容入口、显式预算与失败路径；相关检索/回答/资格回归仅选白名单，不启动数据库或真实供应商。

批次固定：r1-red-01、r1-green-01、r1-regression-01。每批新 XML、结果、源码哈希与资源检查由已有入口产生，不复制历史 XML。人工参考未锁定前不跑质量开发集10/20/40比较，更不触碰报告集。

交付：原字节与 SHA-256、增量 diff、RED/GREEN、指标、验收、回退清单；更新 RC-02 和任务状态，保留 R0 与历史规划记录。
