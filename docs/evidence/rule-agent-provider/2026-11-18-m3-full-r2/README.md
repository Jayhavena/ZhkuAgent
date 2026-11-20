# M3 全量 20 题修复后基线（r2）

状态：**已按用户批准执行，因供应商连接中断未完成**。消耗 50 次调用，未用 55 次已关闭；执行到 RFE-013 ASK 时发生 `MODEL_CONNECTION_FAILURE`，014-020 未执行。

## 目的

2026-11-17 的 M3 全量基线已交付验收 PASS，但回答质量 FAIL（1 PASS / 19 FAIL）。随后修复了动作协议、结束提议重复校验、发布门禁、事实能力、答案边界与核验诊断。9 题代表复验（r4）和 RFE-008 单题复验（r7）已验证 5 个目标题的核心内容，但仍缺少一次覆盖全部 20 题的干净基线。

本包只做一次全量 20 题执行，用于判断当前修复后的真实质量，并确认：

- 动作参数拒绝（005/009/011/012/017）是否消失；
- 结束/预算停止（002/006/018/020）是否能在原有 5 次/110 秒内收敛为合法终态或可解释缺口；
- 发布核验拒绝（001/004/008/019）是否正确发布；
- 010/013 的说明遗漏是否补齐；
- 003/010/013 等上一轮通过项是否保持。

## 实际执行结果（未完成）

- 执行目录：[live-0dc663a9329f420a9e0c6f451ed477e4](live-0dc663a9329f420a9e0c6f451ed477e4)。
- 001-012 已执行；013 ASK 在模型连接阶段失败；014-020 未执行。`batch-error.json` 记录 `MODEL_CONNECTION_FAILURE`，`remainingCasesRetained=true`。
- 消耗 50 次调用，未用 55 次已关闭，无重试；容器清理成功。
- 已执行观察：001/003/007/010 COMPLETE；004 只发布32学时上限，漏原则值16；002 以通用 `UNSUPPORTED_DEMAND` 代替研究生范围缺口；005/006/009/011/012 发布前被 `INVALID_MODEL_RESPONSE` 拒绝；008 PARTIAL 且带保守缺口；013 ASK 连接失败。
- 这些缺陷已转为确定性修复：互补事实保留、S01/S04/S06/S08/S09 发布能力、受众/年份/当期/语料外范围缺口、个人成绩缺口。修复后的新包见 [r3](../2026-11-18-m3-full-r3/README.md)。

## 包身份

| 项 | 值 |
| --- | --- |
| 包路径 | `docs/evidence/rule-agent-provider/2026-11-18-m3-full-r2/request-package.json` |
| **包 SHA-256** | **`3aa65e7308d719b25f8ced554ab4d532b56068430979892572c27fb170bf1f29`** |
| 选择 | `FULL_20` |
| 任务/运行/调用上限 | 20 / 21 / 105 |
| 单 run 上限 | 5 次调用、110 秒、每次最多 1024 输出 tokens、20 秒 |
| 宿主上限 | 2550 秒 |
| 模型 | `qwen3.8-flash` |
| 重试/替换 | 0；配额不可复用 |
| 澄清续答 | RFE-013 仅按预声明 `PUBLIC` 续答 |
| 数据库 | 专用临时数据库，执行后删除 |

## 已执行预检

- 构建：`.\mvnw.cmd -q -pl knowledge -am -DskipTests package`，EXIT 0。
- 零调用包检查：`PASS ZERO_CALL_FULL_20_BATCH_CHECK 3aa65e73...`。
- 零调用数据库/工具装配：`PASS 20_CASE_DATABASE_TOOL_ASSEMBLY providerCalls=0`。
- 装配证据：[assembly-41f5e5b4eeef4a4d8173a73d184c96f9](assembly-41f5e5b4eeef4a4d8173a73d184c96f9)。
- 临时容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 聚焦回归：101 tests PASS；扩大回归 531 tests，仅既有 Windows 启动器与数据库环境测试失败。

详细输出见 [package-checks.txt](package-checks.txt)。

## 待批准

请求见 [execution-request.json](execution-request.json)：批准对象必须逐字匹配包 SHA `3aa65e73...`，上限 105 次调用/20 任务/21 run，建议批准后 24 小时内有效。批准前不创建 `execution-approval.json`，不执行真实运行。

## 解释边界

- 这是一次全量描述性基线，不是独立测试集或通用能力成绩；每题一次，不能推断稳定性。
- 若仍有动作参数、发布核验或结束流程失败，应先按同 run 证据修复，再决定是否另包复验；不能靠增加额度或重试掩盖。
- 20 题全部进入报告分母；任何缺失、失败或未知都不能计为通过。
