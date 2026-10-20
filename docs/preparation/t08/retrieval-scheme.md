# T08 检索方案证据：当前链路实际使用什么

日期：2026-10-14（第二轮准备）。性质：**只读核对源码后的事实记录**，用于 T08 正式验收前解释技术方案；不修改任何生产检索代码。核对基线 `88e57d6`（worktree `codex/t08-acceptance-prep`）。

## 0. 一句话结论

当前受控查证链路（`ruleagent`）**实际使用单一中文二元组词法检索**（`JdbcLexicalCandidateRetriever.forChineseBigramEvaluation`），
配多层资格过滤、句柄化读取与事实—证据绑定；**向量路注入为空实现**，受控目录索引条目 `embedding=NULL`。
仓库里完整的向量/混合检索组件（`JdbcVectorCandidateRetriever`、`HybridRetrievalService`、`EmbeddingProfile*`）**存在但未接入本链路**，
仅服务旧通用 RAG 路径。**本报告不主张“检索质量已达标”**——检索是否足以支持未预置问题，由正式 T08 测定。

## 1. 实际链路（新 Agent，逐环有源码依据）

```
模型 SEARCH_REVIEWED_RULES(query)
  → RuleActionBroker → RuleAgentEvidenceTools.search()
     ① directory()：catalogue Supplier → RuleArticle4Overlay.apply(reviewedCatalogue(root))
        - 过滤 reviewState=APPROVED 且 referenceRevision=2025.9.2，按 ruleId 排序
        - 版本号 = "cat-" + SHA256(规范化JSON) —— 目录身份随内容变化，不靠启动时快照
     ② lexical.retrieve(query, min(settings.candidateLimit()=20, 20))
        - JdbcLexicalCandidateRetriever.forChineseBigramEvaluation（diversity=true）
     ③ 逐 chunk 复核：reader.read → eligible / contentIsIntact / 正文相等 / revision 相等 / checksum 相等
     ④ allowed()：chunk 必须被当前目录版本中某 APPROVED 事实的 evidenceBinding 引用
     ⑤ 候选投影：≤20 条；摘要 ≤512 字符（代理对安全截断）；不透明 UUID 句柄；TTL=waitingTtl(5min)
     ⑥ 句柄预算：handles+additions >120 → BUDGET_EXCEEDED；空结果 → RETRIEVAL_NO_MATCH（观察，非"政策不存在"）
模型 READ_VERIFIED_RULE(c1..c3 + candidateSetId)
  → RuleCandidateReferences 编号解析（c1..cn 绑定本 task 当前候选集，防跨任务/跨集合借用）
  → RuleAgentEvidenceTools.read()
     ① 句柄在位/未过期/同目录版本/allowed
     ② RuleCheckEvidenceCapture + CitationAuditRepository（引用审计）
     ③ 事实绑定核验：目录内每个 fact 的全部 evidenceBindings 必须被捕获且跨度核验通过
        → VerifiedFact(id = ruleId + ":" + factId)
     ④ dataLevel：解析出事实=VERIFIED_FACT，否则=VERIFIED_EVIDENCE（仅有合格原文）
     ⑤ 证据容量：≤8 片段 / ≤32KiB（任务级 retainedProofs）
```

依据文件：`RuleAgentEvidenceTools.java`（search/read/directory/allowed）、`RuleCandidateReferences.java`、
`RuleAgentReviewedToolsConfiguration.java`（装配与临时库约束）、`RuleCheckRuntimeFactory.java`（新旧路径装配对照）。

## 2. 检索单元与资格过滤

| 环节 | 事实 | 依据 |
| --- | --- | --- |
| 检索单元 | `knowledge_chunk`：一条条款的逐字正文为一个 chunk（受控目录共 10 个 chunk：第 2/4/5/6/8/9/10/11/12/18 条） | `Reviewed2025ControlledCatalogue.importChunksAndBaseRules` |
| 事实单元 | fact 绑定到 chunk 内的 UTF16 跨度（start/end/quote），factId = 规则号+事实号 | `RuleCheckRuleTemplate.Fact/EvidenceBinding` |
| 资格过滤第一层（SQL 预过滤） | profile=ACTIVE（或离线评测 BUILDING）、index_run=COMPLETED、chunking_version 固定、source PUBLISHED + STUDENT_VISIBLE + sharing_rights_confirmed、knowledge_scope 匹配、checksum 一致 | `JdbcLexicalCandidateRetriever.loadRows` |
| 资格过滤第二层（fresh 复核） | `RetrievalEligibilityFilter.evaluate(source, requiredScope)` 对每个候选重算 | 同上 |
| 资格过滤第三层（ruleagent 自核） | reader 复读 + 正文/revision/checksum 相等 + `allowed()` 绑定检查 + `directory()` 版本哈希；**每次取目录都重新核对，不用启动时永久许可** | `RuleAgentEvidenceTools` search/read/directory |
| 资料根约束 | 只用专用回环临时库 `jdbc:postgresql://127.0.0.1:<port>/zhkuagent_rulecheck_e2e`（端口≠54329，拒绝原演示库）；外部资产仅作只读字节 | `RuleAgentReviewedToolsConfiguration.requireIsolated` |

## 3. 候选与读取预算（来自代码与 v3 合同第 7 节）

| 预算 | 值 | 说明 |
| --- | --- | --- |
| 单次 SEARCH 候选上限 | 20（`min(settings.candidateLimit()=20, 20)`，代码另有 `values.size()==20` 硬闸） | 工程预算，注释明确“不是已验证的最优检索参数” |
| 旧规则路径参数 | candidateLimit=20 / resultLimit=5（`settings()`） | 旧 `HybridRetrievalService` 融合后返回 5 |
| 候选摘要 | ≤512 字符 | 防止把整篇原文塞进模型上下文 |
| 句柄 TTL / 句柄上限 | waitingTtl 5 分钟 / 120 个 | 跨轮过期即 EVIDENCE_CHANGED |
| READ 句柄数 | 1–3（合同与 `checkReadCount`） | 模型编号 c1..cn，内部 UUID 不暴露 |
| 任务证据容量 | 8 片段 / 32KiB | 超出 BUDGET_EXCEEDED，不悄悄交付部分事实 |
| 排序 | 汉字二元组 + 类 IDF 权重（仅在合格集合内统计 df）；tie-break：contentChecksum→sourceLocator→chunkId，**确定可复现**；diversity 只折叠“同来源同版本且正文完全相同”，**不用相似度阈值**（注释：一字否定或学分差异都可能改变结论） | `ChineseBigramLexicalRanker` |

## 4. 仓库“存在的能力” vs 当前链路“实际使用”

| 组件 | 仓库状态 | 当前查证链路 |
| --- | --- | --- |
| `JdbcLexicalCandidateRetriever` | 存在（基础词法 + 中文二元组两个变体） | **实际使用**：`forChineseBigramEvaluation`（diversity=true） |
| `JdbcVectorCandidateRetriever` | 存在，需 `EmbeddingClient` | **未使用** |
| `HybridRetrievalService` + `HybridCandidateFuser`（RRF） | 存在 | 新 `ruleagent` 证据工具**不经过它**（直接拿词法检索器）；旧 `evidenceFirst/controlled` 装配虽经它，但 **vector 注入 `(question,limit)->List.of()` 空实现**（`RuleCheckRuntimeFactory:21/32`） |
| `EmbeddingProfileStore/Switcher` 等 | 存在（旧 RAG 运维设施） | 未接入；受控目录 profile 登记为 `provider='NONE', model='LEXICAL_ONLY_NO_EMBEDDINGS', dimensions=1, embedding=NULL` |
| 真实向量外发（embedding 调用） | 仅旧通用路径 `create(...)` 构造 | 当前链路 **0 次向量外发** |

**取舍说明（为什么当前不引入向量/重排）**：
1. 资料范围极小且已逐字审核（10 chunk/22 fact），词法+资格过滤已能全覆盖；引入向量需要新索引、新审核边界与新外发（embedding 调用），超出当前授权。
2. 当前排序**确定可复现**（tie-break 不依赖随机性），有利于验收对照；重排模型会引入不可解释的排序抖动。
3. diversity 明确不用相似度阈值——否定词/学分差异场景下相似折叠会丢关键候选，这是已记录的设计约束，不是疏漏。
4. **这不是“向量检索无用”的通用结论**；若正式 T08 发现词法召回不足（须以 G3 真实样本的 NO_MATCH/RETRIEVAL_NO_MATCH 观察为证），应作为有界改进提议交用户决定，含新授权与新评测，不在准备阶段预设结论。

## 5. 验证方式与未验证范围

- 验证方式：对 `88e57d6` 基线源码只读核对（文件与行号见上文）；未运行数据库、未执行检索、未调用供应商。
- 未验证范围：
  - 词法检索在真实问题上的召回率/命中率——**未测**，属正式 T08 G3 指标；
  - 中文二元组排序与 candidateLimit=20 是否最优——未测（代码注释自述“不是已验证的最优检索参数”）；
  - Article4 增量与主题目录草案（C05，`DEVELOPER_PROPOSED_LOCAL_ENGINEERING_OVERLAY_NOT_HUMAN_POLICY_APPROVAL`）在真实装配中的行为——未测；
  - 向量路若接入的表现——未测且未授权。
- 本文件只解释“当前方案是什么、为什么是这样”，**不构成检索质量或 Agent 准确率证据**。
