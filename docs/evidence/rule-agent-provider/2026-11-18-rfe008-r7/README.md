# RFE-008 单题定点复验包（r7）

状态：**已按用户批准执行一次**。消耗 4 次调用，未用 1 次已关闭。RFE-008 已发布培养方案/规定学分规则，并报告个人记录缺口；另有两个保守性缺口。

## 目的

r6 单题执行在独立核验后以 `REVIEW_BINDING_INVALID` 失败：核验器把某条事实绑定到了维度/对象不匹配的需求，门禁在覆盖检查前硬拒绝。r7 验证以下修复：

- 新增绑定规范化：只移除“需求维度/对象/角色与已登记能力不匹配”的绑定；
- 未登记能力、原文指纹漂移、未读事实和结构错误仍硬拒绝，不放宽；
- 核验被拒时把 assessment 写入 `publication-diagnostics.json` 的 `semanticReviews`，便于定位；
- 保留事实仍需覆盖全部已识别需求，真实缺口以 PARTIAL 保留。

## 包身份

| 项 | 值 |
| --- | --- |
| 包路径 | `docs/evidence/rule-agent-provider/2026-11-18-rfe008-r7/request-package.json` |
| **包 SHA-256** | **`ec7c3b5b4baac7061a404eb848b50fc7782fef4893a350ce31ef45d6365430b5`** |
| 选择 | `RFE-008` 单题 |
| 任务/运行/调用上限 | 1 / 1 / 5 |
| 模型 | `qwen3.8-flash` |
| 重试/替换 | 0；配额不可复用 |
| 数据库 | 专用临时数据库，执行后删除 |

## 已执行预检

- 构建：`.\mvnw.cmd -q -pl knowledge -am -DskipTests package`，EXIT 0。
- 零调用包检查：`PASS ZERO_CALL_PACKAGE_CHECK ec7c3b5b...`。
- 零调用数据库/工具装配：`PASS DEDICATED_DATABASE_SEARCH_READ_ASSEMBLY providerCalls=0`。
- 装配证据：[assembly-062fd6c3e1ca40fdbc92f80e1e20f768](assembly-062fd6c3e1ca40fdbc92f80e1e20f768)。
- 临时容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 聚焦回归：101 tests PASS。

详细输出见 [package-checks.txt](package-checks.txt)。

## 实际执行结果

- 执行目录：[live-2f43db4fa7fc47e2bc7314f7d7c5dc09](live-2f43db4fa7fc47e2bc7314f7d7c5dc09)。
- 消耗 4 次调用，未用 1 次已关闭；容器清理成功。
- 核验输出：一个 `GRADUATION_CREDITS / GENERAL_UNDERGRADUATE` 需求，绑定 `plan-required-credits`；个人部分以 `UNSUPPORTED_DIMENSION` 保留。`plan-selection-basis` 作为未绑定额外事实被 `relevanceExclusion` 移除。
- 发布结果：PARTIAL。
  - 主张：`学生应按人才培养方案要求，在规定学习年限内修读...取得规定学分。`
  - 缺口：`PERSONAL_RECORD_NOT_AVAILABLE`，明确没有个人成绩、已修学分或适用培养方案，并要求核对成绩单/培养方案或咨询教务。
  - 额外保守缺口：`CURRENT_APPLICABILITY_UNKNOWN`（问题含“现在”）和 `DETAIL_NOT_IN_REVIEWED_TEXT`（S05个人缺口模板的全局缺口）。
- 对照 Case 要求：培养方案依据、个人记录缺口、核对建议均已覆盖；没有编造剩余学分。正式评分仍需人工标注。

## 解释边界

- 单题通过只证明 RFE-008 这一次真实回答按预期发布，不证明 9 题或全量 20 题稳定。
- 额外两个保守缺口未被移除：它们不矛盾，但可能影响严格评分器的 issue 匹配；若需要更干净的 008 答案，可后续抑制“明确按这份手册”时的当期缺口和已由个人缺口覆盖的通用 DETAIL。
- 001 在 r4 的多未绑定事实失败已由同一过滤路径修复并有离线测试，但没有再做 001 的真实复验。
