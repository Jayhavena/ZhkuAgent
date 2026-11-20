# G3 执行前授权审查

审查时状态：**PRECHECK_PASS / NOT_AUTHORIZED / providerCalls=0**。后续用户已明确批准，本批执行与评测现已结束；最终状态见 execution-receipt.json、DELIVERY.md。以下保留执行前审查内容，不覆盖历史边界。历史 t05-69 授权已 CLOSED_PASSED，remainingAuthorizedCalls=0，不可复用。

## 冻结身份与零调用检查

- 包：request-package.json
- SHA-256：`d2a8f81856e26dac3a1d85f1f399f83e49faf77aa8ea9cf0f609f232af5c5407`
- jar SHA-256：`712a09b37338b73eced7db20774fd6ef8de6a2bf4476b71d6940f69e075ff846`
- 当前 Worktree / branch / HEAD / dirty 身份见 candidate-identity.json；源码与 dist 清单独立保存。
- PrepareProbe 构建成功；CheckPackage 校验通过（package-check.log），数据库操作 0、供应商调用 0。
- CheckEnvironment 实测 Java / Docker / keyAvailable 均 true；没有回显凭据。执行前仍须重新按 Process→User→Machine 检查。
- G2 限定范围接收待确认，见 ../../../student-zero-model-20261110/G2-closeout.md；没有擅自填补 G1 独立签核。

## 本次申请的确切范围

供应商：阿里云 DashScope 北京兼容接口 https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions；模型 `qwen3.8-flash`。

仅 1 批 P02、同一 task 最多 2 runs：问题“我这门选修课没过，能不能重修？”，必要澄清后使用预登记回复 PUBLIC。最多 10 次供应商调用（每 run 至多 4 次决策及 1 次独立核验），每次 20 秒、输出最多 1024 tokens，每 run 110 秒、Java 宿主总计 235 秒。默认领域 Budget 内更宽的 maxTaskRuns=3/maxModelAttemptsPerTask=12 不授权扩大本批，包内批次限制仍为 2 runs/10 calls。无自动重试、无第三轮、无下一批。

申请费用上限人民币 12 元；实际账单在执行后核对，不能把 usage 估算当作实际扣费。现有宿主硬限制调用数/token/时长，**未实现实时人民币账单熔断**，授权前须明确接受这个限制；超时或结果未知不重跑。

建议授权有效期：用户批准起至 2026-11-10 23:59:59 +08:00，且批准后 20 分钟内开始；任一条件过期重新授权。执行过程中发现包/源码/jar/资产漂移、凭据不可达、预算异常、首次必要追问失败、非法响应、超时、审计缺失或资源退出不明即停止，不追加调用。

临时资源：仅本次随机命名无卷 PostgreSQL 容器，专用库 zhkuagent_rulecheck_e2e，回环端口 55518（先检查空闲）；不写共享演示库。退出只清理自有容器与进程。

外发：包中的问题/回复、system/action/review 协议、最多 10 份审核条款摘录及相应语义、2 项第4条批准事实、真实检索候选摘要、工具观察和动态预算/状态、独立发布核验输入。动态 c 编号与候选枚举同源。包列出最大外发范围；不发送凭据、owner/session/token、参考答案或完整 PDF。

本地证据保存：请求/响应 JSON、proposed action、review input snapshot、Trace、task-results、probe-result；不保存 HTTP headers，密钥脱敏，畸形 JSON 省略并记摘要。证据含审核资料文字，只本地留存，不自动上传。

## 执行回执与 G4 交接

当前脚本虽然接收新 PackageRelativePath，live 及 launcher 输出仍固定落在历史 t05-69 根下的全新 live-GUID/launcher-GUID 文件。不会覆盖历史文件；执行后按实际目录登记到本批回执，不能误归属为历史授权执行。

获得明确授权后先写 authorization.json / approval-state.json，记录原文、包 SHA、有效期、上限、一次批次预留；零调用复核再执行现有 start-rule-agent-probe.ps1 -Probe -OnlyP02。失败/未知时也关闭本批剩余额度，不因未用完而复用。

G4 复用 AgentEvalChainMain collect → RuleAgentTraceEvaluator/现有 Grader → AgentEvalChainMain run。eval-suite-template.json 在真实执行前固定首轮 WAITING_USER/INCOMPLETE/NONE、续轮 RESOLVED/SUCCEEDED/COMPLETE 与必要动作/证据链期望；执行后只填实际 evidenceDir/runId，不根据结果修改期望。缺观察/终态按既有规则 NOT_EVALUABLE。

既有离线评测回归本次 6 tests，0 failures/errors/skips（eval-regression-retry.log）；首次命令行参数分词失败保留在 eval-regression.log。模板含占位符，尚未执行 collect/run，不是新真实报告。G4 保持 WAITING_FOR_G3_EVIDENCE。

申请决定：接受 G2-closeout.md 的限定准入及残余限制，并批准本 SHA 的上述一次 P02 外发、预算和本地证据记录。未取得此决定前不调用模型。

