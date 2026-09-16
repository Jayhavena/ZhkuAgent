# 阶段 8：统一规则路由与受控检索增强实施计划

> 状态：待执行；不得在阶段 7 验收前开始。  
> 目标：把已有规则、当期通知和获准资料组织为一条可审计回答链路，并首次真实接入模型表达与 pgvector 检索。

## 目标与非目标

该阶段交付的是 R1“链路接入”：受控检索、引用校验、模型故障降级和基线评测，不是生产 RAG。首期不使用 LangGraph、Temporal、n8n，不启用 `agent` 模块高权限工具，不承诺所有模型服务商兼容，不承诺中文 BM25 或重排已有效。

## 任务清单

1. [ ] 新增 `V7__add_retrieval_catalog_and_index_audit.sql`：创建规则目录、检索 chunk 版本映射、embedding profile、索引运行、索引条目与引用审计所需元数据。每条条目关联原 `knowledge_chunk`、来源版本、切分版本、内容哈希和 profile；不把模型输出当作原始知识。
2. [ ] 在 `com.zhku.knowledge.routing` 定义 `RuleCatalogEntry` 与确定性 `AnswerRoute`，并实现顺序固定的 `KnowledgeAnswerOrchestrator`：待澄清 → 当前通知 → 高风险规则 → 检索增强 → 证据不足/人工渠道。目录项必须声明所需事实、证据范围、风险和转人工条件。
3. [ ] 在 `knowledge/pom.xml` 引入版本锁定的 Spring AI Chat/Embedding/PGVector 依赖，并在 `com.zhku.knowledge.rag` 定义 provider-neutral 配置：`baseUrl`、API key 环境变量名、chat model、embedding model、维度、超时、index profile。一次只有一个启用 profile；密钥不得进入日志、异常或管理页面。
4. [ ] 实现审核后异步/命令式索引任务：按标题/章节/条款/通知办理事实切分，写入含权限、范围、时态、版本的元数据。只有已发布、学生可见、分享权利确认、未替代且满足通知时态的资料可索引和召回。embedding 变更走“新 profile → 重建 → 评测 → 原子切换 → 保留旧索引回滚窗口”，禁止混用维度。
5. [ ] 实现受控词法候选检索和向量候选检索；先输出各自 ranked candidates，再在两路均可用时用 RRF 融合。中文词法效果先用版本化评测集测量；若基线不足，再单独决策分析器/BM25 方案。重排仅在基线显示收益后作为 feature flag 增加。
6. [ ] 实现 `GroundedAnswerComposer`。模型只能接收当轮获准片段和不可变系统约束，并输出 `answer`、`uncertainty`、`citationIds`、`handoffRequired`。`AnswerEvidenceGuard` 校验 citation ID 白名单、原子结论的支持范围和拒答条件；模型超时、限流、格式非法、无证据/低分一律降级，不返回无出处结论。
7. [ ] 为经过合同测试的 provider profile 增加 mock 合同测试；真实供应商 smoke test 必须显式 profile 启用、密钥来自环境变量、结果不写入 Git。未通过合同测试的 DeepSeek、Kimi、GLM、Qwen、混元、OpenAI 端点不能写入“已支持”。
8. [ ] 新增受控检索证据账本，记录 Prompt/模型/profile/index/dataset 版本、基线、分母、运行时间、环境与失败样例。

## 先写的测试

- `KnowledgeAnswerOrchestratorTest`：五级路由顺序、澄清优先、通知优先、规则优先、无证据拒答。
- `RetrievalEligibilityFilterTest`：草稿、内部资料、权利未确认、已替代、过期、范围不符资料均不能进入候选/上下文。
- `ChunkingAndIndexAuditTest`：条款/通知事实定位、哈希、profile 与维度变化、旧索引切换回滚。
- `HybridCandidateFusionTest`：单路降级、双路 RRF 的可重复排名；测试名不得称“BM25”除非专用 BM25 真实实现。
- `GroundedAnswerComposerTest`：有证据正常回答、伪造 citation、越界资格结论、提示注入文本、低分、超时、限流和结构非法均安全降级。
- `ProviderProfileContractTest`：配置隔离、密钥遮蔽、mock OpenAI-compatible 协议；真实 smoke test 为显式 opt-in。
- `RetrievalAcceptanceEvalTest`：运行最小版本化黄金集，输出 Recall@K/MRR/引用准确率；未有结果时只记录“待评测”。

## 验收与退出条件

R1 链路必须实际跑通“已发布证据 → 索引 → 过滤检索 → 模型/降级 → 服务器引用校验 → 回答卡”。全部关键拒答、权限、时态、伪造引用和注入测试通过；产生首份可复核基线报告。通过后才能把“已接入受控检索增强链路”用于演示；仍不得说生产上线或性能提升。

## 不得提前做

- 不把旧 `agent` 模块的终端/文件/网页/下载工具暴露给学生；
- 不将模型回答、用户对话或未审核网页直接写回知识库；
- 不因“OpenAI-compatible”就跳过各供应商合同验证；
- 不以现有 pgvector 依赖或一个成功请求宣称 RAG 已落地或效果提升。
