# RFE-008 单题定点复验包（r6）

状态：**已按用户批准执行一次**。消耗 4 次调用，未用 1 次已关闭。结果为 `REVIEW_BINDING_INVALID`，未发布答案。

## 目的

r4 九题复验中 RFE-008 仍失败：提议同时选入 `plan-selection-basis`、`plan-required-credits` 与 `online-general-half`，其中 `online-general-half` 未绑定并触发 `FACT_NOT_RELEVANT`。旧过滤只要求单需求、单额外事实，无法处理多需求/多额外事实。

本包只复验 RFE-008，验证以下确定性修复：

- 一次移除所有“未被独立核验绑定到本题需求”的额外事实，而不是逐个拒绝。
- 保留事实必须覆盖全部已识别需求；剩余真实缺口以 PARTIAL 保留。
- 未读或伪造事实 ID 先以 `FACT_NOT_READ` 拒绝，不能被过滤吞掉。
- 修复后的原题应发布培养方案/规定学分规则，并报告个人成绩、已修学分与适用培养方案缺口。

## 包身份

| 项 | 值 |
| --- | --- |
| 包路径 | `docs/evidence/rule-agent-provider/2026-11-18-rfe008-r6/request-package.json` |
| **包 SHA-256** | **`ac90da5883bd38de79968e268c133e52c4589bea1e1e7e975d0a3abe485b14c5`** |
| 选择 | `RFE-008` 单题 |
| 任务/运行/调用上限 | 1 / 1 / 5 |
| 模型 | `qwen3.8-flash` |
| 重试/替换 | 0；配额不可复用 |
| 数据库 | 专用临时数据库，执行后删除 |

## 已执行预检

- 构建：`.\mvnw.cmd -q -pl knowledge -am -DskipTests package`，EXIT 0。
- 零调用包检查：`PASS ZERO_CALL_PACKAGE_CHECK ac90da58...`。
- 零调用数据库/工具装配：`PASS DEDICATED_DATABASE_SEARCH_READ_ASSEMBLY providerCalls=0`。
- 装配证据：[assembly-d98bd60bab954ff68487e09b8fd32809](assembly-d98bd60bab954ff68487e09b8fd32809)。
- 临时容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 聚焦回归：100 tests PASS；扩大回归中仅既有 Windows 启动器与数据库环境测试失败。

详细输出见 [package-checks.txt](package-checks.txt)。

## 实际执行结果

- 执行目录：[live-a7ac896983ff4f29a420dfe9b4470d3b](live-a7ac896983ff4f29a420dfe9b4470d3b)。
- 消耗 4 次调用，未用 1 次已关闭；容器清理成功。
- 结果：`REVIEW_BINDING_INVALID`，无发布答案。核验器把某条事实绑定到了维度/对象不匹配的需求，门禁在覆盖检查前硬拒绝；本地 sidecar 因拒绝发生在返回 assessment 之前而为空。
- 已修复：新增绑定规范化，只移除“维度/对象/角色不匹配”的绑定；未登记能力、未读事实和结构错误仍硬拒绝；核验被拒时也会把 assessment 写入本地 sidecar。
- 修复后的单题包见 [r7](../2026-11-18-rfe008-r7/README.md)。

## 解释边界

- 单题通过只证明 RFE-008 这一次真实回答按预期发布，不证明 9 题或全量 20 题稳定。
- 绑定规范化只处理能力匹配的语义维度漂移；不会放宽未登记事实、原文漂移或未读ID。
