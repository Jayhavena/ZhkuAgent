# M3 修复复验中断记录

2026-11-18，`feat/zhkuagent-v3-frontend`，HEAD `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a`。用户批准包 SHA `5b6c9b64d6623dc8bbc847e41201227d95ae39c2b10c151091eb2719cbccd662` 后，通过标准 `-FullDataset -Operation Execute` 启动一次。状态为 **PARTIAL**，修复效果仍待判定。[批准回执](execution-approval.json) · [关闭核对](execution-closeout.json)

RFE-001 第一次决策返回 HTTP 200，检索观察已覆盖必要定位。第二次决策返回 HTTP 400，供应商端返回了响应。当前 Provider 将该状态映射为 `MODEL_UNAVAILABLE`，触发已批准的停批规则。此时尚未进入发布核验。M3 的 RFE-001 则执行到了发布核验，失败码为 `FACT_NOT_RELEVANT`。两次失败发生在不同阶段，无法比较发布核验修复效果。[调用证据](live-0f72babf4d0a4a3698a05586219b36be/RFE-001/provider-traces.jsonl) · [停批证据](live-0f72babf4d0a4a3698a05586219b36be/batch-error.json)

供应商请求 ID 为 `3b7c2707-ff62-99ca-a7e4-478a41474dc6`。日志保存了第二次请求与响应摘要、状态码和长度，原始错误正文不可用。修订后的动作 Schema 是应优先排查的变化之一，当前证据无法确定具体拒绝原因。离线覆盖宿主协议及本地假服务，供应商对动态 Schema 的接受情况仍待验证。

保留20题分母，0 PASS、1 FAIL、19 UNJUDGEABLE、0 ERROR，全部适用维度可评估0/20。后19题均未启动，不能计为模型质量失败。首题只有失败提示，语义复核保留范围及缺口未知。四组修复均未完成真实复验。[逐题逐维报告](live-0f72babf4d0a4a3698a05586219b36be/report-reviewed.md) · [机器可读报告](live-0f72babf4d0a4a3698a05586219b36be/report-reviewed.json)

已核对两次调用、103次未用额度关闭、剩余授权0。执行后包身份核对通过，专用临时容器已移除。原 M3 全量报告作为历史基线保留。下一轮需先处理 HTTP 400 的可诊断性和动态 Schema 兼容性，再冻结新包确认授权。[修复前离线验证](offline-verification.json)
