# G2 → G3 → G4 本轮交付

2026-11-10：用户明确接受限定准入并批准具体包后，完成一次真实 P02 Probe，并接入既有 Evaluation Pipeline。G2 限定收束，G3 执行证据与 G4 报告交付完成；不代表产品整体质量或发布通过。

仓库/Worktree：D:/alldesk/Projects/ZhkuAgent/main；分支 feat/zhkuagent-v3-frontend；HEAD 338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a。原 dirty 改动与 index 保留。本轮只写验收及授权/执行/评测证据，未改产品源码、未 commit/push/deploy。

授权包 SHA：d2a8f81856e26dac3a1d85f1f399f83e49faf77aa8ea9cf0f609f232af5c5407；jar SHA：712a09b37338b73eced7db20774fd6ef8de6a2bf4476b71d6940f69e075ff846。启动前重查候选源码无漂移、包校验通过、端口空闲、Docker 就绪及所需凭据。authorization.json 记录用户原文和限定范围；approval-state.json 记录一次预留/启动/关闭，不复用历史授权。

真实执行使用现有 start-rule-agent-probe.ps1 -Probe -OnlyP02，ApprovedPackageSha256 为上述 SHA，PackageRelativePath 为本目录 request-package.json，DatabasePort 55518。launch-result.json 退出码 0。新执行目录为 docs/evidence/rule-agent-provider/2026-10-16/t05-69-review-condition-request/live-5ecc89a52f364c38bf99bab6d6cd94ff；路径中的历史日期是现有启动器固定输出布局，本次实际执行时间为 2026-11-10，授权归本批。

实测结果：qwen3.8-flash，P02 同一任务两轮；首轮 WAITING_USER / INCOMPLETE，PUBLIC 续答后 RESOLVED / SUCCEEDED。8 次供应商调用（6 决策 + 2 发布核验），全部 HTTP 200。输入 14617 tokens、输出 1026 tokens；只统计每 runId+attempt 的 FINISHED 记录，避免 BEGIN/RECEIVED 重复计数。实际账单 UNKNOWN_UNTIL_RECONCILED，不声称已核验人民币费用；批准上限为 12 元且无实时人民币熔断的限制已披露。未自动重试；剩余 2 次额度关闭，remainingAuthorizedCalls=0。

本轮复用 AgentEvalChainMain collect / run 与 RuleAgentTraceEvaluator/既有 Grader：collect 状态 COMPLETE，2 runs；预先冻结的首轮必要澄清与续轮完整证据期望未改变，仅填入真实目录/runId。结果 PASS 2 / FAIL 0 / NOT_EVALUABLE 0，regressionPassed=true。collection.json、白名单 Trace、来源摘要、固定套件、report.json/report.md 均已保存，原始请求/响应留在本地执行目录。

评测命令使用 Java classpath `knowledge/target/classes;` 加 knowledge/target/cp.txt 内容：

```
com.zhku.knowledge.eval.chain.AgentEvalChainMain collect --evidence <上述实际目录> --out <本目录>/collected --repo-root .
com.zhku.knowledge.eval.chain.AgentEvalChainMain run --suite <本目录>/eval-suite.json --out <本目录>/evaluation --repo-root .
```

完整运行记录见 eval-collect.log、eval-run.log、eval-exit.json；执行前已有评测负例回归 6 项通过。execution-artifact-manifest.json 保存原始产物 SHA；evaluator-identity.json 保存当前评测器源码摘要，便于复跑溯源。

资源：宿主 resourcesClosed=true；端口 55518 无监听、无本次 Probe 容器、无 Java 残留（resource-check.json）。共享演示库未参与本次写验收；只使用随机无卷临时容器。没有新开浏览器，也没有把此次宿主 Probe 当作在线学生页面 E2E。

保留限制：G1 独立签核仍未补齐，G2 运行时完整 asset 摘要及账号全操作浏览器矩阵仍未验；本次仅 P02 工程诊断，不是新金标准/全量模型质量评测。后续如需扩大样本或在线学生页面验收，应另立范围与包授权。当前唯一待对账项为实际供应商账单，不复用本批未消耗额度。
