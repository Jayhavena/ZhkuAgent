# 冻结题集里程碑一：在线采集与样本验收准备

当前状态：**CLOSED_NOT_EXECUTED_BUILD_INVALIDATED**。用户已明确批准本目录的具体 SHA；执行尝试在 Java 宿主的包检查处被拒绝，发生于授权预留、迁移和模型构造之前。providerCalls=0、databaseMigrations=0；本次临时容器已移除。回执保留于 `execution-approval.json`，失败证据为 `failed-attempt-before-provider.json`，旧额度关闭于 `authorization-closeout.json`。修正版与零模型调用的真实数据库验收见 [r2](../2026-11-17-frozen-m1-r2/README.md)。下文保留首轮准备记录，不再作为当前执行授权。

仓库/Worktree：`D:\alldesk\Projects\ZhkuAgent\main`，分支 `feat/zhkuagent-v3-frontend`，HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`。保留所有已有暂存与未暂存修改；没有提交、推送或发布。

## 实现

- `RuleAgentEvidenceTools` 在通过来源资格、当前原文、版本和 checksum 校验后，将实际候选 handle/sourceId/chunkId/revision/checksum 与 observationId/runId/step 交给宿主观察接口；模型候选 Schema 未变。
- `RuleFactExecutionContract` 从通过 manifest 校验的冻结题集生成执行前契约，保存题目 SHA、轮次、必答事实与终态/动作预期。`execution-contracts.json` 保存20题、22种轮次契约的独立快照。RFE-013 的 ASK 首轮不要求续答分支事实；PUBLIC/PROFESSIONAL 续答分别只核对本分支，不替代 ASK 首轮验收。续答契约已定义，本次在线包仅执行各题首轮，不自动续跑。
- `RuleFactBoundEvaluation` 只从机器采集的实际检索身份和已发布引用生成清单，使用版本+checksum 映射冻结 locator。检索 sidecar 须同时匹配 task/run/step/observationId/handle 与对应 Trace 观察。缺失、不健康或无法映射时不能判检索通过。引用版本/来源无法映射则失败；引用对主张的支持关系仍须独立核验。
- capture.json 保存题集身份、题目/轮次、turnId/version、发布回答/引用、Trace、检索来源。annotation 文件单独保存核验人、理由、回答片段、事实核验和引用支持判断，通过 captureSha256 与 answerSha256 绑定，过期标注拒绝。采集文件不得编辑；修订标注不改采集文件。
- `RuleFactOnlineHost` 复用现有 OnlineFactory、TaskStore、Runner、证据工具和 Trace Grader，增加单题独立入口。冻结预期仅留在本地包中；模型只接收实际用户问题及既有运行合同/审核资料。采集的是 TaskStore 接受的已发布终态，拒绝结果/run/version 不一致。
- `RuleFactBoundEvaluation.report` 保留20题分母及每维理由；未执行题 UNJUDGEABLE。默认 annotation-pending.json 不含支持标注，不能使整题 PASS。事实、引用、适用性、澄清和未授权操作声明仍依赖独立语义核验，不宣称已实现自动语义评分。

## 本轮文件范围

新增：三个生产 Java 类 `RuleFactExecutionContract`、`RuleFactBoundEvaluation`、`RuleFactOnlineHost`；`knowledge/scripts/start-rule-fact-eval.ps1`；`RuleFactBoundEvaluationTest`；本目录包、契约快照、预检及测试证据。

修改：`RuleAgentEvidenceTools`、对应工具测试、`docs/agent/eval/chain/frozen-execution-input.md`。保留上一轮新增的 ExecutionCapture/ExecutionEvaluation 及测试，未改动既有 `RuleFactOfflineScorer` 的用户修改、协议、Runner 或前端修改。

## 验证

命令：

```powershell
mvn -q -pl knowledge -am "-Dtest=RuleFactBoundEvaluationTest,RuleFactExecutionEvaluationTest,RuleFactFrozenDatasetTest,RuleAgentEvidenceToolsTest,AgentEvalChainRegressionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Operation Prepare -CaseId RFE-001
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Operation Check -ApprovedPackageSha256 ca4e42571adbcca8b237cafe0cb934248d2031e0f99080c84876269f935ee0e7
```

结果：30 tests / 0 failures / 0 errors / 0 skipped（2+7+2+13+6），报告副本在 `tests/`；构建与 Prepare 退出0；包核对退出0，原始输出在 `preflight.txt`。PowerShell 启动器通过语法解析，并在独立 pwsh 子进程验证 Prepare/Check。未验证 Execute、Docker、数据库迁移或供应商调用；这些状态不计 PASS。

覆盖：冻结20题及 RFE-013 轮次拆分、引用历史版本映射拒绝、真实工具候选与观察身份绑定、未授权 task 不生成 sidecar、缺 sidecar 不可判定、capture JSON 往返读取、标注摘要过期拒绝，以及原有缺回答/观察/终态、独立终态偏离、失败执行、20题分母与 ERROR 隔离回归。

## 待批准的具体执行

- 包：`request-package.json`
- SHA-256：`ca4e42571adbcca8b237cafe0cb934248d2031e0f99080c84876269f935ee0e7`
- 题集 SHA：`36e2827d7855a8b1ddcacbde013aa60dd59e4b99f215ec35b3b9c7470f86f280`
- 题目：RFE-001「学校选修课分哪几类？公共选修课又包括哪些？」
- 模型：包内固定的 DashScope / `qwen3.8-flash`；配置身份绑定当前构建、生产源码、启动脚本、协议、提示词、审核资产和预算。
- 额度：1 task / 1 run，最多5次调用（最多4次决策+1次独立核验），单次输出最多1024 tokens，单次最长20秒，run最长110秒，无自动重试、续跑或额度复用。此为调用/token预算，不是固定货币价格承诺。
- 有效期：请求用户确认后24小时。用户确认具体包前不得生成 APPROVED 回执；旧 P02/g3 授权剩余额度为0，不复用。
- 数据库：执行时通过现有 Docker 就绪入口，仅创建专用随机命名临时容器/数据库，绑定回环地址，拒绝共享库端口54329；结束只移除本次临时容器。凭证每次操作按 Process→User→Machine 读取，不写入包或回执。
- 输出：新建 `live-<uuid>/` 下的 capture.json、annotation-pending.json、report-unreviewed.json、provider-traces.jsonl、calls.json。敏感能力令牌不保存。含受控原文的采集文件只在本地保存，保留30天，不提交Git/CI。

## 授权后动作与验收

记录用户批准的包身份/额度/有效期为 execution-approval.json；零调用复核；专用临时库执行一次。宿主原子创建 execution-reserved.json，即使失败也不自动重试。独立核验同一 capture 的事实、引用、适用性、澄清/缺口与未授权操作声明，再运行 BoundEvaluation 生成报告。题目回答失败也保留真实失败依据；里程碑验收要求可逐维解释且缺输入不会通过，不要求把真实失败伪装为答对。

尚未证明：线上执行、资源清理的实际结果、真实模型行为、人工语义核验质量。首个包仅验证RFE-001；不能外推为全部20题通过或产品验收完成。

本轮补充的只读契约研究委派已请求取消，没有采用其未交付结果，也没有创建替代委派。前轮已交付的 OpenCode/DeepSeek 审查建议由 Codex 核对后用于实现；所有上述编译/测试/预检由 Codex 独立执行。
