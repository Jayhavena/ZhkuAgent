# M3 五题修复后 9 题定点复验包

状态：**已按用户批准执行一次**。9 题/10 run，消耗 40 次调用，未用 10 次已关闭；无鉴权/连接/限流失败。真实结果见下，未做人工终审标注，因此不把未复核报告当作通过率。

## 本包依据

上一轮代表复验为 4 PASS / 5 FAIL。随后按 014 → 004 → 007 → 008 → 015 完成离线修复；逐题处理、测试命令与边界见：

- [离线修复记录](../2026-11-18-m3-five-case-offline/README.md)

本包复用冻结 20 题中的固定 9 题名单、原题、既有 Grader 和独立语义复核，仅用于观察修复后的同题变化，不扩大为全量 20 题结论：

`RFE-001, RFE-004, RFE-007, RFE-008, RFE-010, RFE-013, RFE-014, RFE-015, RFE-016`

其中 014/004/007/008/015 是本轮修复目标；001/010/013/016 作为上一轮已通过项的回归对照。

## 包身份

| 项 | 值 |
| --- | --- |
| 包路径 | `docs/evidence/rule-agent-provider/2026-11-18-m3-repair-r2/request-package.json` |
| 包 SHA-256 | `f42b466b2216ee46c6d1fa2f3cabe179760a08cdb4908561d1a69a5e47189001` |
| 选择 | `REPAIR_9` |
| 任务/运行/调用上限 | 9 / 10 / 50 |
| 单 run 上限 | 5 次调用、110 秒、每次最多 1024 输出 tokens、20 秒 |
| 宿主上限 | 1250 秒 |
| 模型 | `qwen3.8-flash` |
| 重试/替换 | 0；不允许配额复用 |
| 澄清续答 | RFE-013 仅按预声明 `PUBLIC` 续答 |
| 数据库 | 专用临时数据库，执行后删除 |
| Jar SHA-256 | `f9a4af5ed1a1e498d0252fbf7a9529b87de6150acdee8470ff59cd51d7b175de` |

## 已执行预检

- 构建：`.\mvnw.cmd -q -pl knowledge -am -DskipTests package`，EXIT 0。
- 零调用包检查：`PASS ZERO_CALL_REPAIR_9_BATCH_CHECK f42b466b...`。
- 零调用数据库/工具装配：`PASS 9_CASE_DATABASE_TOOL_ASSEMBLY providerCalls=0`。
- 装配证据：[assembly-259e6ecdba93410983837e4210b4ed2f](assembly-259e6ecdba93410983837e4210b4ed2f)。
- 临时容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 未调用 DashScope；未读取或写入共享示范数据库。

详细输出见 [package-checks.txt](package-checks.txt)。

## 实际执行结果

- 执行目录：[live-92bf8b0e7a0943418dcb13ae8acf5667](live-92bf8b0e7a0943418dcb13ae8acf5667)。
- 调用审计：消耗 40 次，未用 10 次已关闭，0 重试；`executionCompleted=true`。
- 容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 未人工标注，`report-unreviewed` 中 7 题 UNJUDGEABLE、2 题 FAIL；下列是**实际回答观察**，不是评分结论。

| 题 | 观察 |
| --- | --- |
| RFE-001 | 类别与公选子类两条事实完整 |
| RFE-004 | 已发布16/32学时事实和24对比说明；另有一个 `UNSUPPORTED_DEMAND` 通用缺口，coverage PARTIAL |
| RFE-007 | 发布网络公选比例事实及个人计算边界，coverage COMPLETE |
| RFE-008 | **失败**：同时选入 `plan-selection-basis` 与 `plan-required-credits`，前者触发 `FACT_NOT_RELEVANT`，整题 INVALID_MODEL_RESPONSE |
| RFE-010 | 每学期4学分事实完整 |
| RFE-013 | ASK 正确追问；`RESOLVE_PUBLIC` 发布公共分支事实后仍被判 `ANSWER_COVERAGE_INCOMPLETE` |
| RFE-014 | **未满足**：同时发布“不办理缓考”和“不安排重修”，没有只保留重修事实 |
| RFE-015 | “可申请”事实及“不等于已获批准”边界完整 |
| RFE-016 | 公共选修不办理缓考事实完整 |

r2 暴露的 008/013/014 缺陷已转为确定性门禁修复：同对象不同维度的额外事实在保留真实缺口时可被过滤；未问缓考/延期考试时移除 `DEFERRED_EXAM` 需求；声明课程类别后移除另一类别分支；本地 sidecar 增加 `semanticReviews`。修复后的新包见 [r4](../2026-11-18-m3-repair-r4/README.md)，r3 为诊断增强前的作废包。

## 风险与解释边界

- 本轮为 S05 的 `online-general-half`、`plan-required-credits`、`plan-selection-basis` 新增了发布能力；007/008 是否改善取决于真实核验模型是否按能力维度拆分需求。
- 004/015 的数值比较和“申请不等于批准”边界由宿主生成；真实评分器是否接受仍需实测。
- 014 的自动排除只覆盖单题/单提议；多题仍严格拒绝。
- 即使本包通过，也只是同一固定 9 题的一次描述性复验；不能据此推断全量 20 题通过率或稳定性。
