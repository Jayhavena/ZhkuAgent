# Effect Eval v1 阶段报告

日期：2026-11-17。P0-1 总体状态 **PARTIAL**：20 题冻结预期及离线兼容验证完成，真实质量评测未执行；本报告不提供 Agent 质量成绩。

## 范围与身份

- 仓库/Worktree：`D:/alldesk/Projects/ZhkuAgent/main`；分支 `feat/zhkuagent-v3-frontend`；HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a` 加既有 dirty，本次未改既有 index。
- 数据集：`zhku-rule-fact-check-effect / v1.0`；`datasetStatus=FROZEN`；20/20 `humanReviewStatus=APPROVED`。审核人用户；冻结记录时间 `2026-11-17T09:18:52+08:00`。
- 数据集 SHA-256：`36e2827d7855a8b1ddcacbde013aa60dd59e4b99f215ec35b3b9c7470f86f280`。manifest 固定数据、决定表、卡及来源；不是模型执行包。
- 18 题承接历史审核，RFE-017/020 根据本会话明确接收决定纳入。APPROVED 是预期契约接收状态，不是模型通过状态。
- 本次修改：新增本证据包、新冻结加载测试及实施计划；修改既有 `RuleFactOfflineScorer` 版本门禁。不改历史数据、审核卡、B02/B03 或生产运行配置。

## 分母、得分与执行覆盖

全量计划分母 **20 题**；首批去重计划 **10 题 × 1 次**（见 smoke-subset.json）；实际真实 Agent 输出 **0**，实际真实评分 **0**，跳过或已评分样本 **0**。未执行的 20 题全部保留为 NOT_RUN，不把 0 个已知失败当作成功，也不计算 0/0 或将缺成绩填为 0 分。

| 维度 | 冻结预期中的分母口径 | 本轮真实已评数量 | 得分 | 缺口 |
|---|---|---:|---|---|
| retrievalRecall | 19 题有必需证据；RFE-020 无必需证据，仍须检查错误来源使用 | 0 | NOT_EVALUABLE | 无本批检索观察及用例关联 |
| factSupport | 19 题有必答事实；RFE-020 必答事实覆盖不适用，额外无依据主张仍须检查 | 0 | NOT_EVALUABLE | 现有函数依赖外部核验观察，未接真实回答 |
| citationIntegrity | 19 题必须引用；RFE-020 无引用时 N/A，有引用则加入适用分母 | 0 | NOT_EVALUABLE | 未产生真实引用和核验观察 |
| scopeAndApplicability | 保留20题逐例审核，适用范围与历史版本不得省略 | 0 | NOT_EVALUABLE | 无本批完整维度判定 |
| clarificationAndGap | 保留20题逐例审核，须结合题型判定必要澄清和缺口 | 0 | NOT_EVALUABLE | 无本批交互与维度判定 |
| executionSafety | 20题逐例检查，需实际Trace/工具/终态 | 0 | NOT_EVALUABLE | 既有Trace判定器未绑定本批RFE用例 |

`requiredEvidenceRecall@K`、`claimSupportRate`、`citationLocatorAccuracy`、`scopeErrorRate`、`clarificationDecisionAccuracy`、`unsupportedClaimRate`、`casePassRate` 均 **NOT_EVALUABLE**。整题得分和质量门禁未执行，不能写“20题通过”或“准确率100%”。N/A是按实际输出决定的维度适用性，不自动让整题通过。

## 本轮实际验证

1. Python 冻结验证 5 tests PASS：审核决定卡/JSON 同步、manifest/来源完整性、可复现生成及拒绝覆盖、B02/B03 事实引用绑定、相对 v3 仅审核元数据变化。日志 freeze-tests.log。
2. 首次新加载测试按预期暴露旧版本门禁：2 tests 中 1 error，`dataset or manifest mismatch`；保留 loader-red.log。这是有意的红灯验证，不是 Agent 失败。
3. 完成门禁兼容后，定向 Maven 验证 17 tests，0 failures/errors/skips：冻结加载2、既有合成流程2、历史v2结构7、评测链负例6。新加载测试还覆盖重新计算hash后仍不得放行PENDING/DRAFT/缺审核人/非法时间，以及错误摘要和未知版本。实际命令见 README，日志 targeted-tests.log。
4. 通过现有 `RuleFactReviewFlow` CLI 实际加载本冻结版本，执行原6条合成标注样例，退出码0。只覆盖RFE-017/020两个题号：事实2 PASS/1 FAIL/3 N/A；引用3 PASS/1 FAIL/1 UNJUDGEABLE/1 N/A。FAIL和UNKNOWN为预设测试输入，不能计算成产品质量率。输出 synthetic-flow-output.json。

## 失败、未知及未执行清单

- 真实产品失败清单：**未评测，不能认定为空**。
- 未执行：RFE-001、002、003、004、005、006、007、008、009、010、011、012、013、014、015、016、017、018、019、020；共同原因 NO_REAL_AGENT_OUTPUT。
- 合成负例：`17-missing-order`事实FAIL；`20-bad-citation`引用FAIL；`20-unknown-citation`引用UNJUDGEABLE。均符合预设，不是待修产品缺陷。
- 现有事实函数仅检查必答事实覆盖，不完整检测额外无依据主张；现有引用函数消费人工核验结果，不自动验证语义。四个剩余维度及整题聚合未接入本20题；不能直接把合成入口用于真实评分。

## 环境、证据及边界

本次在 Windows 本地 PowerShell 执行 Python / Maven / Java，具体版本、测试汇总与源码身份见 verification.json。Maven离线运行；数据库操作0、Docker操作0、供应商调用0，无新账单。测试证据只保存必要汇总，不复制含系统环境的完整Surefire XML。

语料为2025.9.2归档条款，原始PDF字节本工作树不可用；当期适用性UNKNOWN，运行时未激活，资料发布仍DRAFT/INTERNAL_ONLY。开发集非盲测，冻结不提升为留出集。旧P02两轮行为检查和历史6例Trace回归不计入本20题质量分母。

OpenCode / `opencode-go/deepseek-v4.1-flash` 独立审查已完成，提出来源锚点、版本门禁和评分缺口。Codex独立通过本轮Python重新校验所有事实与引用锚点，并通过实际加载与负例验证版本兼容。其“RFE-020仍待接收”的意见以本会话后续明确接受为准，决定保存在 acceptance.json。

## 下一步

本轮冻结交付完成。接下来需在既有评测体系中把真实回答/检索/Trace与RFE用例绑定，保留本地文本供人工核验而不扩大Trace白名单外发，并补齐六维及整题判定的观察契约。先对 smoke-subset.json 的10题准备具体请求包与授权，再实际执行并另出质量报告。当前样本选择计划没有模型、调用/token/时长/费用额度或有效期，不构成授权包；不得用本次“冻结”授权或历史P02额度启动真实批次。

现有大量 staged/unstaged 改动保留，本轮不作整体提交或推送；真实质量验收仍未完成。
