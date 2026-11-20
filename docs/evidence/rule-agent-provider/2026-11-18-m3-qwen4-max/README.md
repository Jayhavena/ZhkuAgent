# M3 qwen4-max 全量复验

状态为 **20 题真实基线已交付，回答质量 FAIL**。4 题通过、16 题失败，全部适用维度可评估 4/20。

执行包使用百炼北京 Chat Completions 的 `qwen4-max`，沿用非思考模式和原动作 JSON Schema。冻结 Gold v1.0 的 20 题按原顺序各执行一次，RFE-013 只在符合原澄清契约时续答 `PUBLIC`。题集、提示词、工具资料和评分契约的摘要已与 r3 核对。模型、宿主源码及构建产物身份随本包重新冻结。见[百炼模型说明](https://help.aliyun.com/zh/model-studio/qwen4-max)和[北京接入地址](https://help.aliyun.com/zh/model-studio/base-url)。

- [执行包](request-package.json) SHA-256 为 `f3dae14844718c502c6111ee20e0837a6fccc965bc42e4dd38df5becdb72e198`。
- [批准请求](execution-request.json)包含 20 任务、21 run、105 次调用上限，有效期自批准起 24 小时。每 run 最多 5 次、110 秒，每次最多 1024 输出 token、20 秒，宿主 2550 秒，无自动重试。
- `RuleFactFullBaselineTest`、`RuleFactBatchTest`、`RuleAgentProviderTest`、`RuleFactOnlineHostTest`、`AgentEvalChainRegressionTest` 共 91 项聚焦回归通过。
- `Prepare -FullDataset -Model qwen4-max` 构建并冻结包，`Assembly -FullDataset` 通过 SHA 核验和 20 题资料装配。[运行记录](assembly-c4d268b6820e4f4f829287f044d5b255/launcher-result.json)显示 providerCalls=0，[清理回执](assembly-c4d268b6820e4f4f829287f044d5b255/container-cleanup.json)与 Docker 只读复查均确认临时容器已移除。
- 工作区为 main，分支 `feat/zhkuagent-v3-frontend`，HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`。[qwen3.8-flash r3 报告](../2026-11-18-m3-full-r3/README.md)保留为单独的中断批次。

## 实际执行

- 用户批准后，按本页 SHA 一次运行 `start-rule-fact-eval.ps1 -Operation Execute -FullDataset`。20 题均有执行记录，20 个任务共 20 个 run。RFE-013 的 ASK 在发布核验阶段失败，宿主依约跳过 `PUBLIC` 续答，记录为 `NO_WAITING_USER`。[批准回执](execution-approval.json) · [执行记录](live-2e14820612c34848a14a36db890ee063/launcher-result.json) · [续答跳过证据](live-2e14820612c34848a14a36db890ee063/RFE-013/RESOLVE_PUBLIC/turn-skipped.json)
- 实际调用 77 次，全部收到 HTTP 200，响应模型为 `qwen4-max`。28 次未用额度已关闭，剩余授权为 0。临时容器清理成功，并经 Docker 只读复查确认已移除。[额度关闭](live-2e14820612c34848a14a36db890ee063/batch-closeout.json) · [容器清理](live-2e14820612c34848a14a36db890ee063/container-cleanup.json) · [审计](live-2e14820612c34848a14a36db890ee063/audit-verification.json)
- Codex 离线复核实际发布文本、引用和适用边界，按回答及 capture 摘要绑定 20 份标注，用原有 Grader 生成逐题六维报告，并复核报告引用的 149 个证据文件摘要。RFE-001、002、004、008 通过。执行失败和续答缺失按现有规则保留在报告中。[逐题报告](live-2e14820612c34848a14a36db890ee063/report-reviewed.md) · [JSON 报告](live-2e14820612c34848a14a36db890ee063/report-reviewed.json)

## 回答质量

15 题在发布核验阶段触发 `REVIEW_SCHEMA_INVALID`，其中 14 题已确认召回所需条款，RFE-013 整题检索维度因续答缺失而不可判定。RFE-020 在检索动作因 `NO_PROGRESS` 停止，未给出题目要求的语料范围说明。现有 Provider 审计记录能定位发布核验失败阶段，具体违约字段不可从本批留存证据确定。

优先排查发布核验响应与本地 Schema 的兼容性，再复核 RFE-020 的语料外处理。后续真实复验需要新包和批准。
