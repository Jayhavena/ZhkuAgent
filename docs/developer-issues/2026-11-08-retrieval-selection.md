# 检索选型：为什么是词法，不是向量/混合？效果如何？

- 日期：2026-11-08
- 状态：INVESTIGATING（已有初步理解，效果待验证）
- 来源：2026-11-08 会话；基于当前分支代码与已有评测证据的只读核对
- 相关主题：检索、RAG、评测

## 原始问题

> 我想了解一下本项目的检索是怎么选择的，为什么这么选，为什么不选其他的，选择这个的效果好吗？

## 背景

这个问题从“评测进展”讨论引出：评测链路刚打通，但检索质量还没有 Agent 级指标。仓库里同时存在词法、向量和 RRF 混合三套组件，不同入口的装配又不一样，所以需要先弄清楚“实际用的是哪条、为什么、替代方案如何、效果到什么程度”。

## 当前理解

> 以下是代码与现有证据的初步核对，不是正式选型结论。评测数字来自已有报告，推测部分仍需验证。

### 1. 两层“选择”

- **技术路线**：词法、向量、混合 RRF 选哪条。
- **候选筛选**：检索出一批候选后，哪些能真正交给模型。

### 2. 实际链路

| 入口 / 装配 | 实际检索实现 | 向量路 |
| --- | --- | --- |
| 学生规则 Agent（`ruleagent`） | `JdbcLexicalCandidateRetriever.forChineseBigramEvaluation`，中文二元组词法 | 没有 |
| 旧规则检查 `controlled` / `evidenceFirst` | `HybridRetrievalService` + 同一词法检索器 + `(q,l)->List.of()` | 空实现，融合后退化成词法 |
| 旧规则检查非受控预览 | `HybridRetrievalService` + 旧词法 + 一调用就抛异常的 `EmbeddingClient` | 不可用，降级为词法 |
| 通用 RAG（`zhku.rag.enabled=true`） | `HybridRetrievalService` + 词法 + 真实 `JdbcVectorCandidateRetriever` | 真实向量，RRF k=60 |
| 检索验收测试 | `RetrievalAcceptanceEvalTest`，词法为主，向量 opt-in | 视配置 |

结论：**仓库里有混合检索，不等于产品受控链路在用混合检索。** 真正跑真实向量的只有显式开启的通用 RAG 路径。

### 3. 词法检索的具体行为

- **检索单元**：一条条款一个 chunk。受控目录目前是 10 个 chunk、22 个 fact。
- **分词与排序**：中文按二元组切分，在已通过资格过滤的候选集上统计类 IDF 权重，再按得分排序。先过滤再统计 df，避免内部/撤回资料影响可见文档排名。
- **去重**：只折叠“同来源、同版本、正文完全相同”的记录，不用相似度阈值。一字否定或 4/5 学分差异都可能改变结论，不能当重复丢掉。
- **确定性**：同分按 contentChecksum → sourceLocator → chunkId 决胜，不依赖随机性，方便评测复核。
- **资格过滤**：SQL 预过滤（PUBLISHED、STUDENT_VISIBLE、共享权利确认、scope、checksum、profile ACTIVE、索引运行 COMPLETED、切片版本固定）→ `RetrievalEligibilityFilter` fresh 复核 → Agent 重新读原文并核对正文/revision/checksum，再要求 chunk 被当前目录中某个 APPROVED 事实的 evidenceBinding 引用。
- **预算**：旧规则路径每路候选 20、最终返回 5；Agent 单次 SEARCH 最多 20 条，摘要 ≤512 字符，句柄 TTL 5 分钟、上限 120 个，证据容量 8 片段 / 32KiB。
- **无命中语义**：Agent 里空结果是 `RETRIEVAL_NO_MATCH` 观察，不是“政策不存在”。

### 4. 为什么这么选

1. **语料小，召回不是主要矛盾。** 10 个 chunk、22 个 fact，且逐字审核过。真正难的是“只让合格、当前版本、已绑定事实的资料进来”，不是从百万文档里捞相关段落。
2. **要可复现、可验收。** 词法排序确定性，适合做基线对照；向量/重排会引入模型和近似最近邻的抖动，评测复核成本更高。
3. **不想引入新的外发和授权。** 向量需要 embedding 调用、新 profile、新索引、维度校验和回滚窗口；旧外发额度已经耗尽，向量 profile 还是 BUILDING。词法零外发、零索引重建。
4. **事实保真优先于语义泛化。** 规则问答里“原则上”“可申请”“低于 30 人”这些限定词不能丢；词法精确匹配对否定词和数字差异更保守。
5. **复杂度控制。** `QueryTerms` 的注释明确写了：刻意不用分词器或 BM25，先用版本化评测集测量；如果基线显示中文词法不足，再单独决策分析器或 BM25。不是“BM25 不好”，是“还没到引入它的时候”。

### 5. 为什么不选其他

| 方案 | 当前不选的原因 | 什么条件下才值得重新考虑 |
| --- | --- | --- |
| 纯向量 | 需要 embedding profile、外发、索引重建、维度/回滚；8–10 个 chunk 上没有收益证据；profile 仍是 BUILDING | 语料扩到上百 chunk，且盲测显示同义改写明显漏召回 |
| 混合 RRF | 组件存在，但受控链路向量是空实现；rc2 上只把 MRR 从 0.9667 提到 1.0，Recall@5 本来就 1.0，没有增量 | 词法在盲测集上 Recall 不足，融合能补回漏掉的候选 |
| BM25 | 中文分词/分析器未验证；10 个 chunk 不值得引入；当前二元组已能覆盖同形问法 | 语料变大、词法基线明显不够，且愿意维护分析器 |
| 重排模型 | 延迟、成本、排序不可解释；没有“召回够但排序不稳”的证据 | 召回已达标，但 Top 5 排序反复出错，且有评测预算 |
| LLM 查询改写 / 多查询 | 额外模型调用、失败模式、不可复现；当前问题不是“问法太口语” | 口语化漏召成为主要瓶颈，且能接受额外调用 |

### 6. 效果证据与边界

**评测数字（模块级）：**

- `real-source-retrieval-v2-rc2`：15 道开发题、8 个合格 chunk。三路 `Recall@5` 都是 1.000000；MRR 词法/向量 0.966667，RRF 1.000000；完整证据集率三路都是 1.0；六组资格/绑定门禁 0 违规。
- `RetrievalAcceptanceEvalTest` 早期基线：4 题、K=3，Recall@3=1.0、MRR=1.0、引用准确率 0.875（当时向量路不可用，只测词法+降级）。
- 向量第二份基线：Recall@3 和 MRR 仍是 1.0，没有提升；引用准确率从 0.875 降到 0.333，是候选变多后的分母效应，不能读成退化。

**这些数字不能直接回答“效果好”：**

- 语料只有 8–10 个 chunk，Recall 天然容易满分。
- 15 道题是 AI 起草、非盲测，数据集状态仍是 `CANDIDATE_PENDING_INDEPENDENT_REVIEW`。
- 2025 版手册对当前学年的适用性仍是 UNKNOWN，严格 locator accuracy 没有计分。
- 这 8 个 chunk 没有自动接入 Agent 的 10 个可发布 factId，检索命中不等于 Agent 能正确回答。
- Agent 评测链路那 6/6 是行为判定，不是检索 Recall 指标，不能混用。

**初步判断**：在固定小语料、同形问法上，这套词法检索够用，而且资格护栏很扎实；但“泛化到学生口语化提问”没有被证明，最大风险就是同义/改写漏召回。二元组在 8–10 个 chunk 上统计 IDF 也不稳定，分数只能当粗排。

### 7. 待验证

- [ ] 构造一组盲测的同义/口语改写题，在 Agent 实际链路上同时跑词法、向量、RRF。
- [ ] 记录必要证据的 `Recall@5` / `MRR` / 完整证据集率、`RETRIEVAL_NO_MATCH` 比例和最终回答正确率。
- [ ] 验证 8–10 个 chunk 上 IDF 权重的稳定性，确认小语料是否让分数退化成“命中几个词”。
- [ ] 评估 `forChineseBigramEvaluation` 作为生产装配名称是否合适，是否需要改名或显式标注“当前默认”。
- [ ] 明确向量/RRF 的引入门槛：只有盲测 Recall 明显不足时，才作为新 profile、新评测、可回滚变更引入。

### 8. 相关文件

- [T08 检索方案证据](../preparation/t08/retrieval-scheme.md)
- [rc2 三路检索对照报告](../evidence/rule-check-refactor/2026-10-26/real-source-retrieval-v2-rc2/retrieval-comparison-report.md)
- [阶段 8 受控检索增强证据账本](../evidence/2026-09-17-阶段8-受控检索增强-证据账本.md)
- [ChineseBigramLexicalRanker.java](../../knowledge/src/main/java/com/zhku/knowledge/retrieval/ChineseBigramLexicalRanker.java)
- [JdbcLexicalCandidateRetriever.java](../../knowledge/src/main/java/com/zhku/knowledge/retrieval/JdbcLexicalCandidateRetriever.java)
- [RuleAgentEvidenceTools.java](../../knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentEvidenceTools.java)
- [RuleAgentReviewedToolsConfiguration.java](../../knowledge/src/main/java/com/zhku/knowledge/ruleagent/web/RuleAgentReviewedToolsConfiguration.java)
- [RuleCheckRuntimeFactory.java](../../knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckRuntimeFactory.java)
- [RagProviderConfiguration.java](../../knowledge/src/main/java/com/zhku/knowledge/rag/RagProviderConfiguration.java)

## 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-11-08 | 首次创建，记录检索选型问题与初步核对结果 |
