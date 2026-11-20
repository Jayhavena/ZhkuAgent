# M3 修复后 9 题复验包（r4）

状态：**已按用户批准执行一次**。9 题/10 run，消耗 40 次调用，未用 10 次已关闭；无鉴权/连接/限流失败。实际回答见下；未做人工终审标注，因此不把未复核报告当作通过率。

## 为什么有 r4

r2 包（SHA `f42b466b...`）已在用户批准后执行一次：9 题/10 run，消耗 40 次调用，未用 10 次已关闭。真实回答暴露了三个仍需修复的问题：

- RFE-008：提议同时选入 `plan-selection-basis` 与 `plan-required-credits`，前者被核验判为无关，整题以 `FACT_NOT_RELEVANT` 失败。
- RFE-013 `RESOLVE_PUBLIC`：已发布正确的公共分支事实，但核验保留了另一分支/额外需求，发布被判 `ANSWER_COVERAGE_INCOMPLETE`。
- RFE-014：核验把缓考规则扩展成额外需求，最终同时发布“不办理缓考”和“不安排重修”，没有满足“只保留重修事实”。

r3 包在最终诊断增强前生成，已作废，不用于执行。r4 包含以下修复：

- 发布门禁在过滤“同对象、不同维度、未绑定”的额外事实时，保留真实缺口 issue；过滤后只要求已识别需求被覆盖，不再强制整题 COMPLETE。
- 核验结果规范化：问题未明确问及缓考/延期考试时，移除 `DEFERRED_EXAM` 需求及绑定；用户已声明 `courseType` 时，移除另一课程类别的分支需求。
- 发布诊断 sidecar 新增 `semanticReviews`，保留本地核验需求/绑定摘要，便于下一轮定位 `ANSWER_COVERAGE_INCOMPLETE` 或额外需求。
- `UNSUPPORTED_DEMAND` 文案补充“具体课程实际安排”，避免把可见事实问题误写成当期通知/代办。

## 包身份

| 项 | 值 |
| --- | --- |
| 包路径 | `docs/evidence/rule-agent-provider/2026-11-18-m3-repair-r4/request-package.json` |
| **包 SHA-256** | **`10c489086697042f029304c29550f7e2f35a8eabb4c9a49a776f5006bc5a7574`** |
| 名单 | RFE-001、004、007、008、010、013、014、015、016 |
| 上限 | 9 任务 / 10 run / 50 次调用；单 run 5 次、110 秒、每次最多 1024 输出 tokens |
| 模型 | `qwen3.8-flash` |
| 重试/替换 | 0；配额不可复用 |
| 澄清续答 | RFE-013 仅按预声明 `PUBLIC` 续答 |
| 数据库 | 专用临时数据库，执行后删除 |

## 已执行预检

- 构建：`.\mvnw.cmd -q -pl knowledge -am -DskipTests package`，EXIT 0。
- 零调用包检查：`PASS ZERO_CALL_REPAIR_9_BATCH_CHECK 10c48908...`。
- 零调用数据库/工具装配：`PASS 9_CASE_DATABASE_TOOL_ASSEMBLY providerCalls=0`。
- 装配证据：[assembly-83c1558c6fda424e8b697f1ff13aa0a4](assembly-83c1558c6fda424e8b697f1ff13aa0a4)。
- 临时容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 聚焦回归：99 tests PASS；扩大回归 529 tests，2 个 Windows 启动器测试和 4 个未提供专用数据库的测试仍为环境失败，其余通过。

详细输出见 [package-checks.txt](package-checks.txt)。

## 实际执行结果

- 执行目录：[live-d71f7d08f7424ddbbbb3aa3776fb61e5](live-d71f7d08f7424ddbbbb3aa3776fb61e5)。
- 调用审计：消耗 40 次，未用 10 次已关闭，0 重试；`executionCompleted=true`。
- 容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 未做人工标注；下列是实际回答观察。

| 题 | 观察 |
| --- | --- |
| RFE-001 | **失败**：同时选入两条未绑定的 T02 修读安排事实；旧单事实过滤只移除一条，后续仍 `FACT_NOT_RELEVANT` |
| RFE-004 | 16/32学时 + 24对比说明，coverage COMPLETE，无额外缺口 |
| RFE-007 | 比例规则 + 个人计算边界，coverage COMPLETE |
| RFE-008 | **仍失败**：提议含两条未绑定事实（`plan-selection-basis` 已绑定、`online-general-half` 未绑定）；多需求时旧过滤未触发 |
| RFE-010 | 每学期4学分完整 |
| RFE-013 | ASK 正确追问；`RESOLVE_PUBLIC` 只发布公共分支，coverage COMPLETE |
| RFE-014 | **已修复**：只发布重修事实；缓考事实进入 `relevanceExclusions`，未再发布 |
| RFE-015 | “可申请”及“不等于已获批准”边界完整 |
| RFE-016 | 公共选修不办理缓考完整 |

r4 暴露的是同一类缺陷：提议混入多个未绑定事实时，旧过滤一次只移除一个，且要求单需求。已改为一次保留“独立核验明确绑定到本题需求的全部事实”，移除所有未绑定额外事实；若剩余事实不能覆盖全部已识别需求则仍拒绝，未读/伪造 ID 先以 `FACT_NOT_READ` 拒绝。修复后的单题 008 包见 [r6](../2026-11-18-rfe008-r6/README.md)。

## 解释边界

- 本包仍只对同一固定 9 题做一次描述性复验，不能推断全量 20 题通过率或稳定性。
- r4 的门禁规范化是确定性宿主规则；它只能约束“未问缓考/另一类别分支”的额外需求，不能保证真实核验模型一定按预期识别主需求。
- 008/001 的修复效果需要下一次实测；r6 只定点复验 008，001 由同一过滤路径的离线测试覆盖。
