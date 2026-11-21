# HTTP 400 单题复验准备记录

当前状态为单题复验已执行。HTTP 400 中断点已越过，发布核验仍失败。分支 `feat/zhkuagent-v3-frontend`，HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`。

上一批 RFE-001 在检索成功后的第二次决策收到 HTTP 400。`RuleActionBroker.actionState` 此时开始允许 `FINISH_CHECK`，修复版新增的嵌套 `anyOf`、`oneOf` 和唯一约束位于该分支中。该时序及版本差异支持优先验证 Schema 兼容性，供应商原错误正文缺失使具体原因无法确认。

`RuleAgentProtocol` 移除新增外发嵌套约束，回到 M3 原始外发形状，并保留提示词和宿主对空提议、重复事实、issue 配对的严格限制。`RuleAgentProvider` 将 HTTP 400/422 的诊断标为供应商响应拒绝，并对少量已知错误码给出固定参数分类。诊断字段不保存供应商错误消息。既有停批依据继续有效。相关测试覆盖非法结束项、本地假供应商 400 的分类与单次调用、发布行为、宿主及 Grader 重放。

[158项离线回归](offline-tests.json)通过。打包后的 `actionSchema` 与 M3 原始全量包字段深比较相等。[三次独立包核对](preflight-checks.txt)通过。[临时库检索和读取](assembly-2e05e11d49ff494b83efe31feed53d49/assembly.json)通过，[临时容器](assembly-2e05e11d49ff494b83efe31feed53d49/container-cleanup.json)经只读复查已移除。离线验证无法证明供应商接受实际动态请求。

[请求包](request-package.json) SHA-256 `0a2dfd9bee6e0adafb5f6d1bf15b93c161427dfa6b507b4802c7e2cd7fca8baa`。仅冻结 RFE-001 原题，1任务、1 run、最多5次 DashScope `qwen3.8-flash` 调用。每次最多1024输出 tokens、20秒，run 最长110秒，宿主130秒。使用专用临时数据库，无自动重试、替换和重复试验，授权确认后24小时内有效。此步验证第二次动态决策能否越过上次 HTTP 400，以及核验/终态是否产生可回溯证据。后续范围根据本次实际结果确定。

实际执行经过检索、原文读取、结束提议及独立核验，4次 HTTP 200。第二次动态请求成功且原文读取返回 `VERIFIED_FACT`。核验响应通过协议解码，门禁随后以 `FACT_NOT_RELEVANT` 拒绝，整题 FAIL。该码表示至少一项提议事实未与对应需求建立核验绑定。现有采集缺少提议和核验绑定细节，无法判断是选了多余事实还是核验漏绑。下一步应保存这两侧的最小结构化诊断后进行针对性验证。[调用证据](live-9eb7ef610af04f58a0c0b5515211097e/provider-traces.jsonl) · [逐维报告](live-9eb7ef610af04f58a0c0b5515211097e/report-reviewed.json)

批准包仅执行一次，未用1次额度已关闭，剩余额度0。执行后包核对通过，临时库容器已移除。[授权关闭记录](execution-closeout.json)
