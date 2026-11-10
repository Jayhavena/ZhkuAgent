# P02 真实执行评测：最小证据提交

范围：2026-11-10 已完成的单批 P02 执行与既有 Evaluation Pipeline 报告。用户批准从独立 Worktree 整理本地 evidence commit；不包含公开上传许可，不改产品源码，不追加模型调用，不重跑或改写既有评测结果。

## 身份与来源

- 原执行 HEAD 为 `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`，实际包含 dirty 增量；不能把本提交父版本冒称为完整执行源码。
- 批次：`live-5ecc89a52f364c38bf99bab6d6cd94ff`，实际执行日期 2026-11-10。原始报告中 2026-10-16 路径来自启动器固定布局。
- 包 SHA-256：`d2a8f81856e26dac3a1d85f1f399f83e49faf77aa8ea9cf0f609f232af5c5407`。
- jar SHA-256：`712a09b37338b73eced7db20774fd6ef8de6a2bf4476b71d6940f69e075ff846`；不包含 jar 或构建输出。
- 本地冻结清单 SHA-256：`6cd4667d230171eee7aa36210616661efd5359149eea348d75f6824a5924e853`。72 项的原路径、字节数、摘要、归属、决定及目标见 `source-review-72.json`。

72 项逐一核对原文件字节、摘要并读取文本：8 项 INCLUDE_EXACT，3 项 DERIVE_ALLOWLIST，9 项 DERIVE_IDENTITY，52 项 EXCLUDE。纳入原件为额度账本、固定 suite、两份白名单 Trace fixture、两份原报告、Probe 结果与资源回收记录。其他纳入文件是明确标记的摘要投影，不伪装为完整原件。

## 判定与账单边界

真实供应商模型 `qwen3.8-flash`；一次任务两轮，首轮必要澄清后续答。8 次调用（6 决策、2 独立发布核验），输入 14617 / 输出 1026 Tokens。原报告为 2 PASS / 0 FAIL / 0 NOT_EVALUABLE；采集 COMPLETE。8 次 HTTP 200 和响应 usage 均不代表账单结算成功。

授权上限为人民币 12 元；实际账单未知，是否低于上限也未知。`usage-summary.json` 保持 null / NOT_VERIFIED，未用价格估算替代账单。剩余 2 次调用额度已关闭，不复用；没有实时人民币账单熔断的限制已向用户披露。

G1 独立签核与 G2 浏览器扩验仍为单独事项，不纳入本提交实现或重开 G3/G4。上述 PASS 仅为 P02 可观察工程执行契约通过，不是全量规则语义、模型质量、学生页面在线 E2E 或发布验收通过。

## 本地保留与脱敏

不提交请求包正文、审核原文、原始请求响应、模型 proposed action、review input、outcome、终态原文、原始 provider trace、主机日志、凭据、数据库、完整 PDF 或构建输出。原文件留在本地冻结目录，本次无删除或修改。

两份 fixture 已由现有采集器剔除候选原文、文案和供应商正文，仅保留动作、状态、opaque 标识、关系和 factId 的 SHA-256。`collected/collection-summary.json` 去掉本机绝对目录；来源相对路径与摘要保留，允许本地核验，而原始资料不随提交提供。重复 collection 不纳入。

Grader 为现有 `RuleAgentTraceEvaluator`，源码身份及 collector/runner 身份见 `grader-identity.json`。原 evaluator-identity 漏列 ruleagent 下的 Grader，本次身份补录与批准包中的源码摘要一致；不修改原身份文件或评测结果。只提交身份，不复制生产源码。

本目录的 `report.json`、`report.md`、suite 和 fixture 为原字节副本，引用的本地原始 evidence 路径可能不在 Git 中，这是有意排除的边界。此包可以校验已保存证据与报告一致性；仅靠本提交无法独立重新验证被排除的原文、真实调用或供应商账单。没有新增评分框架。

## 校验与恢复

在仓库根目录运行：

```text
python docs/evidence/g3-g4-p02-20261110-reviewed/verify.py
```

该离线校验只读取本目录，验证文件清单、摘要、72 项分类、原样副本、白名单字段、报告与 fixture 对应及账单未知状态，不调用供应商、数据库或 Grader。可额外指定 `--source-root <原始本地仓库>` 检查全部 72 项冻结原件与本地审核原文未混入；不输出原文。Python 3 标准库即可。

本轮还核对主 Worktree 原 index 文件与 staged/unstaged 差异摘要，独立 Worktree 的提交不改变原暂存区。字节保全通过本目录 `.gitattributes` 限定，未修改全局 Git 配置。恢复本包使用本 evidence commit；原始敏感材料不从 Git 恢复。后续账单或新评测结果应单独新增补充记录，不改本轮报告。
