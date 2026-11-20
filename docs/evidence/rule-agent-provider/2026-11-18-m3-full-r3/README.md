# M3 全量 20 题修复后基线（r3）

状态：**全量复验 PARTIAL，回答质量 FAIL**。RFE-018 收到 HTTP 403 后停批，RFE-019、020 未执行。固定 20 题分母，12 PASS、6 FAIL、2 UNJUDGEABLE，全部适用维度可评估 14/20。

## 实际执行和复核

- 用户批准后，按本页 SHA 以 `start-rule-fact-eval.ps1 -Operation Execute -FullDataset` 执行一次，启动 18 个任务、19 个 run。RFE-013 完成 ASK 与同一任务的 `PUBLIC` 续答。运行位于 main Worktree，分支 `feat/zhkuagent-v3-frontend`，HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`。
- 75 次调用收到 74 个 HTTP 200 和 1 个 HTTP 403。宿主将 RFE-018 的 403 归为 `MODEL_AUTH_FAILURE` 并按规则停批；供应商拒绝的具体原因未由现有日志确定。剩余 30 次额度已关闭，剩余授权 0。Launcher 退出 1，临时容器清理成功，并经 `docker ps -a` 复查已移除。见 [批次停止](live-63f02c3db71e425aa1b5f9afdec18d62/batch-error.json)、[额度关闭](live-63f02c3db71e425aa1b5f9afdec18d62/batch-closeout.json)和[容器清理](live-63f02c3db71e425aa1b5f9afdec18d62/container-cleanup.json)。
- Codex 离线复核 19 轮发布文本、引用及适用边界，按原有 `RuleFactBatchReport` 和 Grader 生成 [逐题逐维报告](live-63f02c3db71e425aa1b5f9afdec18d62/report-reviewed.md)及 [JSON 报告](live-63f02c3db71e425aa1b5f9afdec18d62/report-reviewed.json)。复核绑定同轮回答和 capture 摘要，独立校验报告所列 138 个证据文件摘要。运行结束自动写出的 `report-unreviewed` 已纳入当时落盘的部分标注，结论以逐轮复核后的报告为准。
- RFE-002、005、006 已召回所需条款，随后以 `FACT_NOT_RELEVANT` 拒绝发布。RFE-009 漏了按教务处计划在规定时间内网上选课的规则，RFE-012 漏了核对教学计划和课程信息的路径。RFE-018 的 FAIL 反映执行未产出有效答复，无法评价本轮的当期截止说明。RFE-019、020 各维保留不可判定。
- M3 的 r3 全量复验仍未完成。后续真实复验须另立执行包并获得批准。

## 为什么有 r3

r2 全量包在 RFE-013 ASK 遇到 `MODEL_CONNECTION_FAILURE`，只执行到 001-012 就停止，014-020 未执行；50 次调用已消耗，剩余 55 次关闭。已执行部分还暴露了以下缺陷，r3 已修复：

- RFE-004：核验只识别上限需求，宿主把同条款的原则值事实当成未绑定额外事实删除。现在上限事实的 `auxiliaryDimensions` 若覆盖原则值维度，则保留并作为必要补充发布。
- RFE-005/006/009/011/012/017：S04 人数、S06 选课/未选课、S08 重复修读、S09 排课顺序事实没有发布能力，核验无法绑定。已登记 `ENROLLMENT`、`UNENROLLED_EXAM`、`REPEAT_RESTRICTION`、`SCHEDULE` 能力及对应规则变体。
- RFE-002/019：研究生受众/2026级年份问题被误报为通用资料不存在。现在发布明确限定本科对象的条文，并给出受众/年份范围缺口；无对象限定的合成规则仍硬拒绝。
- RFE-018：当期截止/补选问题现在发布一般选课规则并报告 `CURRENT_NOTICE_MISSING`，不编造时间、不代办。
- RFE-020：语料外奖学金问题返回 `MATERIAL_SCOPE_MISMATCH` 范围缺口，不编造评定条件。
- RFE-008：保留培养方案规则 + `PERSONAL_RECORD_NOT_AVAILABLE`，并清理已由个人缺口覆盖的通用 DETAIL 与“按这份手册”场景的当期噪声。
- 核验被拒时仍记录 `semanticReviews`；未读/未登记能力/结构错误保持硬拒绝。

## 包身份

| 项 | 值 |
| --- | --- |
| 包路径 | `docs/evidence/rule-agent-provider/2026-11-18-m3-full-r3/request-package.json` |
| **包 SHA-256** | **`f1d842acfb2eeeeeabc91bb9e71870c1741827faff0cafd535f77adde5dd80b1`** |
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
- 零调用包检查：`PASS ZERO_CALL_FULL_20_BATCH_CHECK f1d842ac...`。
- 零调用数据库/工具装配：`PASS 20_CASE_DATABASE_TOOL_ASSEMBLY providerCalls=0`。
- 装配证据：[assembly-ffbc2380f13d46a297779a3329ae0d5f](assembly-ffbc2380f13d46a297779a3329ae0d5f)。
- 临时容器清理：`cleanupSucceeded=true`、`resourcesClosed=true`、`timedOut=false`、退出码 0。
- 聚焦回归：109 tests PASS；扩大回归 539 tests，仅既有 Windows 启动器与数据库环境测试失败。

详细输出见 [package-checks.txt](package-checks.txt)。

## 批准记录

[执行请求](execution-request.json)及[用户批准回执](execution-approval.json)绑定包 SHA `f1d842ac...`、105 次调用/20 任务/21 run 上限和 24 小时有效期。

## 解释边界

- 这是一次全量描述性基线，不是独立测试集或通用能力成绩；每题一次，不能推断稳定性。
- 上次 `MODEL_CONNECTION_FAILURE` 是供应商传输中断，不是产品判定；r3 仍可能遇到同类外部故障，届时按批次规则停止并保留剩余题，不重试、不复用额度。
- 20 题全部进入报告分母；任何缺失、失败或未知都不能计为通过。
