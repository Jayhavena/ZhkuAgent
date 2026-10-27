# v2.0.0-rc2：三路检索对照报告

日期：2026-10-26

状态：SCORED_GATES_PASS；候选仍待独立复核。

## 1. 实验配置

- 固定语料指纹：4fd00ba1c8154fa7f4389cde6b64fe7276b5ce36ae05a6c87224ee9d465980f0
- 固定合格候选：8 个真实 chunk；不重切片、不改金标准。
- 主范围：ALL_ELIGIBLE_STUDENT_VISIBLE；15 条正向用例进入 Recall@5 / MRR 分母。
- 额外用例：1 条权限排除契约门禁；2 条 PUBLIC_ELECTIVE 范围门禁。
- 词法路：项目 JdbcLexicalCandidateRetriever 的中文 Bigram 旧基线排序，profile real-source-lexical-only-v1-20261026，chunking version real-source-lexical-baseline-v1。
- 向量路：项目 JdbcVectorCandidateRetriever，profile dashscope-text-embedding-v4-1024，使用已存储的 8 个真实 1024 维向量。
- 混合路：项目 HybridRetrievalService / HybridCandidateFuser；每路候选上限 8、最终 Top 5、RRF 平滑常数 k=60。
- 查询向量：18 个不同问题，text-embedding-v4、1024 维；生成阶段 3 次批量 API 请求（8、8、2），合计 306 输入 token。范围模式复用相同查询向量。
- 外发边界：仅发送评测问题文本；没有发送 chunk 正文、排除资料正文、密钥或用户对话内容。检索重放阶段 API 调用为 0。
- 执行隔离：目标数据库 zhkuagent_eval_v2_rc2；所有检索连接使用只读事务；检索阶段数据库写入为 0。向量 profile 保持 BUILDING，没有激活线上 profile。

## 2. 主评测指标

| 算法 | 正向用例数 | Macro Recall@5 | MRR | CompleteEvidenceSetRate@5 |
|---|---:|---:|---:|---:|
| LEXICAL | 15 | 1.000000 | 0.966667 | 1.000000 |
| VECTOR | 15 | 1.000000 | 0.966667 | 1.000000 |
| RRF_HYBRID | 15 | 1.000000 | 1.000000 | 1.000000 |

这些数值来自原有 score-v2-results.ps1，不是手工计算的模拟结果。

## 3. 资格与绑定门禁

本次计分器汇总六组运行（3 种算法 × 2 种范围模式）后得到：

| 门禁 | 计数 |
|---|---:|
| 禁止 chunk 泄漏 | 0 |
| 未知 chunk | 0 |
| 内容 checksum 不匹配 | 0 |
| source / scope / page / locator 绑定不匹配 | 0 |
| PUBLIC_ELECTIVE 范围违规 | 0 |
| 权限排除 deny-list 命中 | 0 |

范围门禁有实际区分度：PUBLIC_ELECTIVE 检索必须排除 PUBLIC_ELECTIVE_CREDIT_LIMIT 与 ELECTIVE_RETAKE 范围的合格 chunk。

**权限门禁的证据边界：** 9 个不合格 chunk 不在本轮索引中，且其正文未发送给 Embedding provider。因此 deny-list 计数为 0 只证明输出未包含这些禁止 ID，不能单凭这项结果证明“排除 chunk 已在候选集合中时检索过滤器仍会拒绝它们”。该穿透测试需要单独的合成资格夹具。

## 4. 观察到的排名差异

- RSV2-RET-005（第 13 条选课通知）：词法目标排名第 1，向量第 2，RRF 第 1。
- RSV2-RET-013（公共选修课与专业选修课重修）：三路都在 Top 5 覆盖全部必要证据，但两条证据的先后顺序不同。
- RSV2-RET-014（课程类别 + 两类重修分支）：词法必需证据排名为 2/3/4，向量为 3/1/2，RRF 为 2/1/4；三路均在 Top 5 命中全部三组必要证据。

本次 RRF 的 MRR 高于两条单路，但 Recall@5 与完整证据集率没有进一步提高，因为三路在这 15 条问题上都达到 1.0。不能据此宣称 RRF 对生产检索总体质量有已证实的提升。

## 5. 运行后完整性核验

- 原始隔离库 zhkuagent-eval-v1 的来源 URL 状态指纹仍为 27af38f18757ef76def4dcc28cc9caabfe9d7842c527817970da19bc5d40b7ca。
- rc2 数据库来源 URL 状态指纹仍为 cc99c9df75655ed90f7a203966ad21826443f25bd23320f4cdff6ceb170454f4。
- 原库与 rc2 的 chunk ID + source ID + content checksum 集合指纹均为 5d64a08ce27a59a4c84f8a89b316f56ed035b8e561679255b2e08649f9de54cb。
- 词法 profile 仍为 ACTIVE；向量 profile 仍为 BUILDING。检索没有改变索引运行或 profile 状态。
- 临时 JUnit harness 已从应用测试源目录移除；生产源代码未修改。

## 6. 结果文件

- query-embeddings.json：18 个问题的缓存查询向量，可离线复放。
- Rc2RetrievalEvaluationHarnessTest.java：使用项目真实检索器与 RRF 服务的受控运行器源码。
- retrieval-results-v2-rc2.json：计分器输入，含 6 组运行的 Top 5 排名及来源绑定。
- retrieval-trace-v2-rc2.json：每个问题的词法 Top 8、向量 Top 8、RRF Top 5 与融合分。
- scored-results-v2-rc2.json：正式计分输出与门禁汇总。
- run-retrieval-comparison.ps1：创建临时重放目录、执行运行器、调用计分器；使用缓存向量，不再调用 Embedding API。

## 7. 结论与限制

这轮对照在固定的 8 个合格 chunk 上完成，结果门禁全部通过。RRF 在当前 15 条开发问题上的 MRR 为 1.0，词法与向量均为 0.966667；三路的 Recall@5 与完整证据集率均为 1.0。

**这只是小语料、非盲测、AI 起草开发集上的诊断结果，不是生产质量估计，也不是正式算法选型结论。** 2025 版手册对当前学年的适用性仍为 UNKNOWN，官方线上 PDF 附件字节一致性仍为 NOT_VERIFIED，严格 locator accuracy 不计分，评测集仍待独立复核。