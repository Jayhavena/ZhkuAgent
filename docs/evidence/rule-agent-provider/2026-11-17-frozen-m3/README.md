# M3 全量开发集基线：准备与授权记录

当前状态：M3交付验收PASS，20题质量基线已建立；Agent回答质量FAIL。用户已批准最终R1，实际执行20任务/21run/79次调用，1 PASS、19 FAIL，全部适用维度可评估3/20。未用26次额度已关闭、剩余授权0，临时容器已清理，无重试/替换/重复试验。见[真实质量报告](live-5e4325f591f441b4a33d08add4151382/quality-report.md)及[逐题逐维报告](live-5e4325f591f441b4a33d08add4151382/report-reviewed.md)。

范围：冻结Gold v1.0全部RFE-001至020，原题不变，各执行一次。RFE-013只有实际courseType追问允许PUBLIC时，才在原task回复PUBLIC（公共选修课），不补跑其他分支。上限20任务、21run、105次DashScope qwen3.8-flash调用，单run5次、单次1024输出tokens/20秒、单run110秒、宿主2550秒（42.5分钟）。无自动重试、替换或重复试验。专用临时库，不访问共享演示数据。

分母固定20。未执行、失败、中断、缺回答/观察/终态都保留，未知不计通过。沿用M2六维Grader及独立标注准则；语义标注由独立审阅者完成，不能冒充用户人工签字。分类采用OBSERVED_LAYER_V1，原始证据摘要和回答片段绑定，缺观察非检索失败，预期缺证据边界非Agent缺陷，缺引用非伪造。分类和严重程度/影响题数共同支持修复优先级，不把无法证明的根因写成事实。

本批不调模型提示词、生产工具或发布策略；保存题集、模型配置、两份提示词、工具/发布协议及源码、资料版本、预算、评分实现与jar。与M2仅对共同10题逐题比较，单次试验无统计显著性主张。包内localExchangeRecording明确为禁用原始Provider报文，只保留调用元数据、动作、观察、发布问题及结果。资料/回答本地受控留存30天，不自动Git提交/CI上传。

源码位置：main Worktree；分支feat/zhkuagent-v3-frontend；起始HEAD338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a。现有用户/Agent变更保留，本任务不提交。M2已获批jar和必要源码的构建前校验副本位于 `D:\alldesk\Projects\ZhkuAgent\eval\baselines\gold-v1.0\m2-18143180\identity.json`，不是本批可复用授权。

最终R1：[request-package.json](request-package.json)，SHA-256：`998169056713f4c98f159301838db96b78c01c6b176e0faded48e4dad62159b4`。`request-package-pre-review.json`及`request-package-r0.json`均为未授权历史准备快照，不得执行；不带-r1的早期验证记录对应R0，不能当作最终包证据。

执行门槛：最终包三次独立零调用Check、20题真实临时库检索/读取Assembly、专用监督器长参数/超时回收测试、独立只读评审、用户明确批准最终SHA/105次额度/预算/24小时有效期。只有取得该批准后，才可创建一次执行回执并Execute；执行后关闭全部未用额度。

实施及验收步骤见 [M3计划](../../../superpowers/plans/2026-11-17-frozen-m3-full-baseline.md)，评测契约沿用 [现有批次说明](../../../agent/eval/chain/ten-case-batch.md)。下列“准备/授权门槛/未执行”段落保留为执行前过程记录，当前状态以上方实际报告和下方关闭证据为准，不重复申请或继承已关闭额度。

## 最终R1证据

- [准备验收与文件摘要清单](preparation-verification-r1.json)：最终包/jar/全部生产Java源文件一致，运行副本保留于既有eval/baselines/gold-v1.0/m3-99816905分区。
- [41项离线回归](offline-tests-r1.json)：含六项历史Trace回归、全量计划、错误保留/评分、宿主错误不混入Agent缺陷、授权副本防漂移、关闭失败仍回收执行器、预算矛盾拒绝。评测专用监督器另实际通过2550秒参数、UTF8管道、双流fixture脱敏、一秒超时回收检查；不是等待2550秒，也不是完整付费入口已执行。
- [三次独立进程包核对](package-checks-r1.json)：同一最终SHA。
- [20题临时库检索/读取验收](assembly-verification-r1.json)：全部SUCCEEDED，调用Provider为0；[启动记录与清理](assembly-2a009d9fc8a748f8a70933df10135f8e/launcher-result.json)、[容器清理回执](assembly-2a009d9fc8a748f8a70933df10135f8e/container-cleanup.json)，复查临时容器已移除。
- [M2兼容重放](m2-compatibility-verification-r1.json)：原10题所有整题/逐维判定及比例一致，仍1 PASS/9 FAIL、完整可评估5/10；仅离线处理旧证据，没有新增模型输出。理由中的集合展示顺序不保证逐字节一致，原M2产物未修改。
- [全缺证据输入的报告](offline-empty-replay-r1/report.json)：20题均UNJUDGEABLE、0 PASS、完整可评估0；仅缺证据验收夹具，不是实际全量执行。
- [版本比较](baseline-comparison-r1.json)：模型、两份提示词、预算、资料、生产业务/工具和六维Grader相同；selection、task/run数量、批次eval源码、jar不同。不得把“部分配置相同”解释成整个运行物相同。
- [独立评审核对与处理](review-disposition.md)：OpenCode/DeepSeek只读贡献由Codex逐项核对，必要修正后自行运行上述检查；没有声称第二次独立复评通过。

本任务源码变更：RuleFactBatchPlan.java、RuleFactBatchHost.java、RuleFactBatchReport.java、RuleFactBaselineIdentity.java、RuleFactFailureAnalysis.java；评测脚本start-rule-fact-eval.ps1、Invoke-ZhkuEvaluationProcess.ps1；测试RuleFactFullBaselineTest.java、rule-fact-evaluation-process.test.ps1；现有评测说明/任务计划/本目录证据文档。生产业务、提示词与六维Grader保持M2身份。不修改其他已有变更，不提交Git。

## 已执行命令与下一步

在仓库根执行：

```powershell
.\mvnw.cmd -q -pl knowledge -am '-Dtest=RuleFactFullBaselineTest,RuleFactBatchTest,RuleFactBoundEvaluationTest,RuleFactExecutionEvaluationTest,RuleFactFrozenDatasetTest,RuleFactOnlineHostTest,AgentEvalChainRegressionTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipTests=false' test
pwsh.exe -NoLogo -NoProfile -File knowledge/src/test/browser/rule-fact-evaluation-process.test.ps1
pwsh.exe -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -FullDataset -Operation Prepare -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m3/request-package.json
pwsh.exe -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -FullDataset -Operation Check -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m3/request-package.json -ApprovedPackageSha256 998169056713f4c98f159301838db96b78c01c6b176e0faded48e4dad62159b4
pwsh.exe -NoLogo -NoProfile -File knowledge/scripts/start-rule-fact-eval.ps1 -FullDataset -Operation Assembly -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-17-frozen-m3/request-package.json -ApprovedPackageSha256 998169056713f4c98f159301838db96b78c01c6b176e0faded48e4dad62159b4
```

Prepare含独立打包，不能把-DskipTests package视为测试。Check实际在三个新进程分别运行。完整Execute **未执行**，需用户对上述最终SHA、20task/21run/105次、全部单次/run/宿主预算、PUBLIC策略、专用临时库、无重试/重复及确认后24小时有效期明确批准；依据仓库AGENTS.md第112行的包身份/额度/预算/有效期门槛，不将本次开发指令或旧M2批准视为新批授权。批准后才创建回执，以同一命令将Operation换成Execute，并且只执行一次。

剩余风险/未验证：完整付费入口及新增10题的真实行为尚未验证；没有原始Provider请求/响应归档，部分根因可能仍不可判定；单次采样无统计显著性。下一交付为包含20题的真实逐维报告、失败分类与按证据排序的修复建议，不能在执行前宣告完成。

## 实际执行与交付（覆盖上方准备阶段状态）

[批准回执](execution-approval.json)明确绑定最终SHA、20/21/105范围与预算；有效期保守设为2026-11-17T15:29Z至2026-11-18T15:29Z，未延长用户确认后24小时窗口。[永久预留](execution-reserved.json)拒绝再次Execute；[授权关闭](execution-closeout.json)记录79次使用、26次关闭、剩余0。

本次执行的标准命令与上方Check相同，仅将Operation改为Execute；已实际成功完成，没有使用M2替代监督器。数据库为GUID临时容器的loopback55519专用库，清理后只读复查容器不存在。

真实结果：20任务、21run，HTTP200响应79/79、实际模型qwen3.8-flash，输入132869/output7266 tokens；宿主176.326秒、最长run12.589秒、最长单次5855毫秒、最长输出424 tokens，均在批准范围。003唯一整题PASS；010正文漏历史限定，013 ASK漏分支差异解释；其余17题FAILED/STOPPED。全量六维判定和缺失/失败边界见[JSON报告](live-5e4325f591f441b4a33d08add4151382/report-reviewed.json)、[实际预算/授权审计](live-5e4325f591f441b4a33d08add4151382/audit-verification.json)、[21轮错误明细](live-5e4325f591f441b4a33d08add4151382/execution-breakdown.json)。

必要定位召回19/19；17题范围/缺口语义仍未知，不作通过。修复优先级依据实际失败层：6题动作参数、4题发布核验、7题结束/无进展，以及2题必要说明遗漏；类别与引用缺失的归因边界均在[证据绑定优先级](live-5e4325f591f441b4a33d08add4151382/repair-priorities.json)解释。共同10题通过数仍1/10，可评估比例由5/10变2/10，只作单次描述，不推断显著性。

独立Codex离线复核21份输出及4份引用原文片段，不是用户人工终审。没有原始Provider报文，因此错误字段/核验误拒等细因仍需下一轮诊断；未为提高成绩修改提示词、生产工具或Grader。下一步按报告优先级修复后另包复验，本批不得再执行。
