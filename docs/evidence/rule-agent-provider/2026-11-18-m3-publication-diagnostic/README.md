# RFE-001 发布核验诊断包

状态：批准后已真实执行一次，RFE-001 六维 PASS。分支 `feat/zhkuagent-v3-frontend`，HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`。[上次RFE-001真实结果](../2026-11-18-m3-http400-probe/README.md)

`RulePublicationGate` 在 `FACT_NOT_RELEVANT` 拒绝时保留被拒事实的目录能力、同一子问题的已读提议事实、需求序号/维度/对象/识别状态及已校验事实绑定。`RuleAgentRunner` 将诊断附于内部核验事件，`RuleFactOnlineHost` 按身份和 capture SHA-256 写本地 `publication-diagnostics.json`。共用轨迹与供应商请求沿用原字段，诊断不保存需求原文、模型填写的需求ID或原始报文。该记录可以区分未识别对应需求、识别需求后绑定缺失，以及提议事实和已识别需求不相符等观察结果，具体语义仍需独立复核。

[88项离线验证](offline-verification.json)通过，涵盖拒绝仍生效、记录两侧绑定、诊断仅落本地以及历史重放。新旧单题包的模型、端点、预算、双提示词、动作Schema、核验合同和冻结轮次均相同。三次 [包核对](preflight-checks.txt) 与[临时库检索读取](assembly-4b9bb17391504180b05461c3865a6a7e/assembly.json)通过，临时容器经只读复查已移除。

[请求包](request-package.json) SHA-256 `f89fec63b643478af2bb236a1c8f27881a2ee93a330328fcebaa83bd3d7c3262`。RFE-001 原题、1任务/1 run、最多5次 DashScope `qwen3.8-flash` 调用。每次最多1024输出 tokens/20秒，run 最长110秒，宿主最长130秒。专用临时库，无自动重试、无重复试验，确认后24小时内有效。执行后核对第二次决策、发布终态，并在复现时按诊断定位缺失绑定。

实际4次调用均HTTP 200，依次完成检索、读取、结束提议和独立核验，终态 RESOLVED/SUCCEEDED。发布正文包含两项必答事实及2025.9.2历史限定，原文片段与第4条SHA及两条主张引用关系均核对通过。按冻结Grader及独立离线复核，六维均PASS。[逐维报告](live-a2891e8ddbfb4943a534345809824976/report-reviewed.json) · [引用核对](live-a2891e8ddbfb4943a534345809824976/citation-checks.json)

本轮诊断文件成功生成并与 capture SHA 匹配，拒绝列表为空。上轮 `FACT_NOT_RELEVANT` 没有复现，因此此前究竟是提议多选还是核验漏绑仍未知。单题通过仅覆盖此次执行，其余19题无新结果。[诊断记录](live-a2891e8ddbfb4943a534345809824976/publication-diagnostics.json)

只执行一次，未用1次额度已关闭，剩余授权0，执行后包核对通过，临时容器已移除。[关闭记录](execution-closeout.json)
