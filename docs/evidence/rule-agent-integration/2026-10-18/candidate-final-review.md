# 统一候选与新审包：最终元数据窄审阅

审阅源码检查点 `dabd499a5a6cad65b28ed10226b7ecf3ba94e07d`，新包 `2026-10-19/online-http-20261019-unified-01/request-package.json`，以及 request-review、candidate-manifest、项目状态、实施计划和进度顶部。结论：未发现 Critical、Important 或 Minor 不一致，可继续元数据提交及最终零外发 Check；不构成真实外发许可。

- 新包实际 SHA256 为 `c3616b1d01ed3f27835e1f825318e969647114c37eeda983f6ddcfd946781460`，batchId 为 `6aab157c-d3b7-4640-811f-6cd949857594`。源码提交、树 `f9b7c401646be20c4bf201cf4c020ba3b0471645`、JAR 与能力权威指纹均和候选清单/执行代码一致。旧包实际 SHA 以 `9478ae0a` 开头，保留原字节并被明确标为已替代，没有恢复旧额度。
- 八类 Java XML 摘要合计 165 项，失败/错误/跳过均 0；所指源码清单的 662 项与当前字节漂移 0。前端日志为 178 pass、fail/skipped 均 0；三种构建退出码均 0。新包学生构建的 27 个文件与既有实际浏览器构建记录逐项相同。文档没有将 136 与 165 相加。
- 210519 和 055801 的真实 UI 观察与原租约中断明确分层，原 SQL 失败也保留。082036 回执仅证明同测试文件 SHA 的真实临时库资格撤销、显式 STOP、worker/监听/容器正常退出，activeJobs=0、browserOperations=0；三文档和清单未把它冒充 UI 重跑或一批整体全绿。
- 普通终端 Prepare/Check 的 XML 各为 1 项、失败/错误/跳过均 0；回执记录数据库/Provider 为 0。新包的固定题、PUBLIC、1任务/2run/10次、20秒单次/110秒单run、900秒租约/1000秒看门狗和10元新提案与 request-review 一致。实际账单保留 UNKNOWN。
- 包状态 NOT_AUTHORIZED、候选状态 NOT_APPROVED、当前可调用次数 0、无本 SHA reservation，与三文档的待审批边界一致。任务一/T06/T07整体退出及 T08—T10正式验收仍未关闭。

本审阅只读既有文件、Git 身份与摘要，未运行 Maven、数据库、Provider 或 CUA，未修改源码、请求包或三份状态文档；唯一新增文件为本记录。不独立判定官方单价的实时有效性、未执行的真实模型质量、最终 Check 结果或元数据提交之后的状态。
