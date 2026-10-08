### Strengths

- 四个 JDBC 组件统一使用同一份 profile、切片版本与 scope；两路切片过滤均位于排序、截断之前，旧构造器及离线 BUILDING 路径保留原行为。
- 目录按 APPROVED 与精确引用版本筛选；选项、条件及模板均有不可变快照，越权选择、未知条件键和目录变化会受控拒绝。
- 复用 Hybrid → Capture → Audit → Decision；来源撤回会在决策阶段再次核验，异常正文不泄漏。
- 已核对13条审查指纹及受保护文件；保存的23份 XML 共262项，失败、异常、跳过均为0，XML 哈希一致。29项 runner 合同记录通过。本次未运行测试、数据库或模型。

### Critical

无。

### Important

**I1：实际 embedding 故障绕过单路降级，健康词法结果被丢弃。**

- **位置：**RuleCheckRuntimeFactory.java:23
- **触发：**通过新工厂传入现有 `SpringAiEmbeddingModelClient`，词法路取得有效证据，但 embedding 调用失败或返回非法向量。
- **原因：**现有适配器抛出 `ModelCallFailedException`、`ModelOutputFormatException` 等运行时异常；`JdbcVectorCandidateRetriever` 原样传播，`HybridRetrievalService` 仅捕获 `RetrieverUnavailableException`。最终异常抵达应用层总兜底，整轮返回 `INSUFFICIENT_EVIDENCE`。
- **影响：**新工厂的实际接入路径不满足单路降级合同；即使本地证据足够，供应商暂时故障也会使查证不可用。目前尚无学生入口，因此不构成已上线事故。
- **最小修复：**在本批装配的 embedding/向量检索边界，将供应商调用及输出失败转换为 `RetrieverUnavailableException`，保留健康词法路继续核验、审计、决策。补工厂级反例：embedding 替身抛出上述实际异常，词法证据仍应得到 `ANSWERED`；不需要真实供应商调用。
- 现有降级测试直接让检索器抛目标异常，因此未覆盖这个适配边界。

### Minor

**M1：切片测试没有形成“错误版本挤占截断预算”的反例。**

- **位置：**JdbcRuleCheckCitationAuditTest.java:173
- **触发：**未来有人把切片过滤移到排序、LIMIT 之后。
- **影响：**当前夹具使用同一 chunk、相同向量、候选上限20；仅有两个版本时，错误的“先截断再过滤”实现仍可能通过。现有 SQL 的顺序正确，此项是回归保护缺口。
- **最小修复：**使用不同 chunk，让错误切片排名更靠前，并将候选上限设为1；断言仍返回目标切片。词法与向量分别构造可确定排序的夹具。

### Declined to judge

- 合法 ID 是否真正匹配自然语言问题：本批只有选择接口与替身，语义路由评估明确未完成。
- 真实2025规则的条件、例外、审核责任及展示资格：草稿与资料未激活，工程夹具不能提供这类证明。
- v2 身份、CSRF、页面、最终下发及历史再展示：没有相应入口，需独立验收。
- 跨目录、数据库、多个条款的原子一致性及最终撤回窗口：当前采用顺序复核，未提供并发或原子提交保证。
- 完整有效期、发布清单、原召回 entry 追踪及历史正文重放：属于已记录的后续边界。
- 真实模型表现、费用、报告集质量及用户收益：没有实际执行或参与者证据。
- 历史 dirty 树、旧自由 RAG 风险、原库升级及全仓测试：均不属于本次 before 字节增量审查。

### Assessment

**Ready to merge? With fixes。**

内部编排和固定配置边界整体成立，但应先修复 I1，使工厂实际接入路径兑现单路降级合同。修复后可作为未开放学生入口的工程组件收口；不能据此关闭 R5/G2 或宣称产品效果已验证。
