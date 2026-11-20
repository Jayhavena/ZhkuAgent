# 里程碑二：首批10题执行准备

最新状态：**里程碑二报告交付验收 PASS；回答质量未达标**。本包经用户明确批准后已执行10题/11run，消耗42次调用，未用13次额度关闭、临时容器清理。首份真实质量报告为 [quality-report.md](live-13bba9842e834136acb1e37ff87009fb/quality-report.md)：1题PASS、9题FAIL，全部适用维度可评估5题。下文保留授权前准备记录；当前身份与实际调用见execution-approval.json、execution-reserved.json、authorization-closeout.json。

Repository / Worktree：`D:\alldesk\Projects\ZhkuAgent\main`；Branch / HEAD：`feat/zhkuagent-v3-frontend` / `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`。保留所有已有修改；未暂存、提交、推送或发布。

## 冻结身份与预算

请求包：[request-package.json](request-package.json)

SHA-256：`18143180bc208127846495b726e5f218a7f6140d9b4ed3e2b30f61c4cdbcff5c`

固定计划：[batch-plan-frozen.json](batch-plan-frozen.json)，来源为冻结v1.0已去重子集001/002/003/004/005/006/007/013/018/020。原题、数据集和选择文件摘要、逐轮契约、澄清回复、模型/协议/提示词/审核资产、源码、jar和启动器摘要均在包内。题卡预期不作为模型输入。

- 模型：DashScope / qwen3.8-flash。
- 最多10任务、11run、55调用；通常题各1run，013为ASK加预登记PUBLIC续答（同task）。
- 每run最多5调用，每次最多1024输出tokens/20秒，run最长110秒；宿主1350秒（22.5分钟）。调用/token预算不构成固定货币价格承诺。
- 无自动重试、替换题、第三轮澄清或剩余额度复用。鉴权/连接/供应商不可用、批次截止、资源无法关闭时停批并保留未执行项。
- 使用专用随机命名临时容器和既有安装器允许的临时库名，回环端口55519，拒绝共享端口54329。拟授权有效期为用户确认后24小时；批准前不生成APPROVED回执。

M1额度已经关闭，不覆盖本批。AGENTS.md第26行要求核对已批准的具体包身份/SHA、额度、预算、有效期，已向用户请求本批的明确确认。

## 实现与审查

新增RuleFactBatchPlan、RuleFactBatchHost、RuleFactBatchReport、RuleFactBatchTest；扩展已有启动器的显式-Batch模式；既有BoundEvaluation增加无事实/无主张轮次的已核验不适用处理，ExecutionEvaluation保留失败终态的具体issue原因码。没有修改题集、问题、模型提示词或回答生成逻辑。

复用现有OnlineFactory/TaskStore/Runner/审核工具/capture/scorer/Trace Grader。报告口径与复核步骤见 [批次说明](../../../agent/eval/chain/ten-case-batch.md)。

独立只读审查由OpenCode / opencode-go/deepseek-v4.1-flash完成（审查快照2026-11-17 21:20:53），没有修改文件、运行模型或数据库。Codex核对并修正：

- 清理等待覆盖20秒供应商超时加5秒余量，真泄漏仍停批。
- TaskStore错误保留具体枚举码，不保存可能含敏感数据的异常正文；缺Runner Outcome记录RUN_OUTCOME_MISSING及真实终态。
- create/message错误回填逐轮报告，不只在sources中留哈希。
- 初始化、closeout及报告异常保留恢复证据，报告失败不覆盖执行本身结论。
- 明确013两轮均须标注；检索继承严格绑定父轮来源。
- 停批条件明确包含鉴权、连接、供应商不可用；临时容器清理不再依赖数据库凭证导入。
- Assembly扩展为10题零模型端口检查；不强求“应当有答案/命中所有必需条款”才通过装配，以免把质量判断混入就绪检查。

修正后的源码由Codex通过测试/构建/多进程预检/真实数据库端口检查独立验证；没有把审查者的旧快照结论冒充修正后复审。

## 实际验证与证据

```powershell
mvn -q -pl knowledge -am "-Dtest=RuleFactBatchTest,RuleFactOnlineHostTest,RuleFactBoundEvaluationTest,RuleFactExecutionEvaluationTest,RuleFactFrozenDatasetTest,RuleAgentEvidenceToolsTest,AgentEvalChainRegressionTest" "-DskipTests=false" "-Dsurefire.failIfNoSpecifiedTests=false" test
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Batch -Operation Prepare -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m2/request-package.json
pwsh -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -Batch -Operation Assembly -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m2/request-package.json -ApprovedPackageSha256 18143180bc208127846495b726e5f218a7f6140d9b4ed3e2b30f61c4cdbcff5c
```

结果：44项测试（12+2+2+7+2+13+6），0失败/错误/跳过，副本位于tests/。Prepare构建及包生成退出0。三个独立pwsh/Java进程Check退出0，输出在preflight-1.txt至preflight-3.txt。启动器通过PowerShell语法解析。

首次Assembly因Docker Desktop启动未完成而就绪CLI超时，没有创建临时库、模型、授权或预留。按既有Docker恢复文档只读核对引擎已返回linux后继续零模型装配，没有改全局配置或终止无关进程，详见docker-readiness-note.json。

实际Assembly退出0，19个迁移成功应用到专用空临时库，10题检索/读取端口均正常，providerCalls=0。每题实际来源身份和端口状态见 [assembly目录](assembly-21af5f803d724c0d8cf0929d42535a1c/)，启动/退出日志为该目录launcher-result.json。该检查没有Agent决策、模型回答或语义质量判定；NO_MATCH等健康空结果也应保留。

## 待授权后的交付

执行一次批次，逐题保留原始capture、provider审计、开始/结束/跳过/失败日志和closeout；独立复核所有实际轮次，生成逐题逐维JSON/Markdown报告。分母10题，通过率、确定结论比例、全部适用维度可评估比例、失败原因分别列出；每个结论链接capture、annotation及其SHA。其余冻结10题独立注明不在本批分母。

真实执行、实际质量、故障触发的线上清理和多轮行为尚未验收，不能由离线测试代替。含原文/回答的后续产物限本地30天，不自动提交Git/CI或发布。
