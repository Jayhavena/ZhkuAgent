# 真实语料 Embedding / 向量 / RRF 混合检索诊断

日期：2026-10-26

状态：**DIAGNOSTIC_COMPLETE / FORMAL_RECALL_AND_MRR_BLOCKED**

## 1. 本阶段范围

- 使用冻结的 `real-source-retrieval-v1/cases.json`，SHA-256：`bc9e5a8088540577b0deb6c8aaab984497144028e91bf72cb9b99dcc402ec223`；未修改问题、金标准或预期状态。
- 仅对 8 个已经核验为 PUBLISHED、STUDENT_VISIBLE 且 sharing rights confirmed 的真实语料片段建立向量索引：PUBLIC_ELECTIVE 5 个、PUBLIC_ELECTIVE_CREDIT_LIMIT 1 个、ELECTIVE_RETAKE 2 个。
- 使用项目配置的国内站兼容端点、`text-embedding-v4`、1024 维；API key 仅从既有进程环境读取，未写入文件、命令参数或日志。
- 对 5 个冻结问题各生成一次查询向量，并在两种范围模式下复用缓存。索引阶段有 8 次 embedding-client 调用，查询阶段有 5 次，共 13 次客户端调用；未调用聊天模型。
- 向量 profile 保持 `BUILDING`，仅通过离线评测入口读取；未激活线上 profile、未启用应用 RAG，也未修改原数据库。
- 评测期间所有数据库连接均设置为只读事务。索引创建写入仅发生在隔离副本；检索评测本身数据库写入为 0。

## 2. 隔离索引与执行合同

| 项目 | 结果 |
| --- | --- |
| 隔离容器 / 端口 | `zhkuagent-eval-v1` / `127.0.0.1:55441` |
| 隔离卷 | `zhkuagent_eval_v1_data_20261026` |
| 原容器 / 原卷 | `zhkuagent-postgres` 保持停止；`zhkuagent_zhkuagent_postgres_data` 保留且未修改 |
| 向量 profile | `dashscope-text-embedding-v4-1024` |
| provider / model / dimensions | `dashscope / text-embedding-v4 / 1024` |
| 固定切分版本 | `real-source-embedding-v1-20261026` |
| 索引任务 | 3 个 COMPLETED，0 个 FAILED；分别索引 5、1、2 个片段 |
| 向量条目 | 8 条，其中 embedding 非空 8 条、空向量 0 条 |
| 不合格片段进入索引 | 0 |
| 查询向量 | 5 个不同问题各调用一次；两种范围模式共用缓存 |
| 对照路径 | 中文 Bigram 词法、余弦向量、RRF 混合 |
| RRF 配置 | 平滑常数 (k=60)，每路候选上限 8，最终 Top 5 |
| JUnit | 1 passed / 0 failures / 0 errors |

词法路使用隔离库中既有的 `real-source-lexical-only-v1-20261026` profile 和 `real-source-lexical-baseline-v1` 切分版本；向量路使用新 profile 和新切分版本。两路映射到同一批真实 chunk ID，并分别通过资格过滤，但它们是两个独立索引运行，而不是同一个 profile 下的同一批 index entries。

完整排名、chunk ID、source ID、知识范围、页码、locator 和派生检查保存在同目录 `embedding-hybrid-results.json`。

## 3. 五条问题的排名诊断

“全部合格范围”表示检索不限制 knowledge_scope，但仍排除未发布、非学生可见或分享权未确认的资料；“PUBLIC_ELECTIVE”模拟当前预览配置。

| 用例 | 全部范围：词法 / 向量 / RRF | PUBLIC_ELECTIVE：词法 / 向量 / RRF | 解释 |
| --- | --- | --- | --- |
| RSR-01：第 5 条学时上限 | 目标 chunk：2 / 1 / 1 | 目标 chunk：2 / 1 / 1 | 向量与 RRF 把目标片段从第 2 提升到第 1；但 chunk locator 覆盖第 5—6 条，仍不满足精确第 5 条绑定 |
| RSR-02：第 8 条网络公选比例 | 无合格正向金标准 | 无合格正向金标准 | 所有路径均未返回内部 chunk；这是权限排除，不应计作普通正向召回题 |
| RSR-03：第 10 条每学期 4 学分 | 目标内容：1 / 1 / 1 | 目标不可见：— / — / — | 三路均能在跨范围模式排第一；当前范围排除该 chunk，且数据库 locator/page 与第 10 条的金标准不一致 |
| RSR-04：第 11 条两项限制 | 目标 chunk：1 / 1 / 1 | 目标 chunk：1 / 1 / 1 | 三路在两种范围模式下均排第 1；locator 较精确，但 chunk 正文仍是策展摘要，不是逐字原文 |
| RSR-05：第 4 条澄清 + 第 18 条两类分支 | 完整证据：词法否 / 向量是 / RRF 否 | 完整证据：词法否 / 向量否 / RRF 否 | 向量路跨范围 Top 5 同时含类别、公共重修、专业重修三组；RRF Top 5 漏掉类别 chunk；单一 PUBLIC_ELECTIVE 范围无法包含专业重修分支 |

### RSR-05 的关键反例

全部合格范围的向量 Top 5 包含：

1. `06766d1e…`：第 18 条公共选修课分支；
2. `b3cd386d…`：第 18 条专业选修课分支；
3. `ac78ed6c…`：第 4 条课程类别。

因此，按当前诊断定义，向量路的三组必要证据都在 Top 5 内。RRF Top 5 包含前两组，但没有 `ac78ed6c…`，完整证据集检查失败。**RRF 并不保证比最佳单路更完整。** 这不是足以宣布 RRF 整体更差的证据，而是说明必须把多证据集合完整性作为单独验收项，不能只看单个目标 chunk 的排名。

## 4. 权限与范围边界

- RSR-02 的第 8 条比例证据只存在于 INTERNAL_ONLY、sharing rights 未确认的片段。六种检索结果（3 路 × 2 范围）均未返回该内部 source/chunk。
- PUBLIC_ELECTIVE 范围只有 5 个合格片段，不含 `ELECTIVE_RETAKE` 的专业重修分支，也不含 `PUBLIC_ELECTIVE_CREDIT_LIMIT` 的第 10 条片段。
- 跨范围模式在评测中用于诊断召回上限，不意味着生产 Agent 应无条件跨范围检索。生产工具需要明确允许访问哪些范围，并保留每条证据的资格判定。
- 当前资料库的数据库 source rows 没有 source URL，也没有直接保存原始 PDF SHA-256 字段；虽然冻结数据集记录了本地 PDF 的 SHA-256，运行时 source-to-document 绑定仍未形成完整机器可验证闭环。
- 2025 版文本对当前学年的适用性仍是 UNKNOWN。本阶段只评估检索，不评估法规时效、生成答案质量或学生发布授权。

## 5. 指标状态

没有汇总 Recall@5 或 MRR。原因不是检索没运行，而是 v1 金标准仍存在结构性问题：

1. RSR-02 的正向证据没有学生可见资格，应改为权限排除测试或等待审核通过的学生可见证据；
2. RSR-03 的目标 locator/page binding 不准确，且 scope 与当前默认范围不同；
3. RSR-01 的目标 chunk 仍覆盖多个条款，不能把 chunk 命中等同于精确条款命中；
4. RSR-05 的金标准只列第 18 条一个 locator，但验收事实需要类别、公共分支、专业分支三组证据；
5. 5 条样本是开发集、非盲测且由项目负责人自审，不能作为可靠的总体效果估计。

本轮只报告可复核的逐例排名与证据集合检查。没有将这些诊断排名包装成正式综合分数。

## 6. 下一阶段建议

先冻结一个新的 v2 评测集，而不是调整 RRF 常数或继续细分检索参数：

- 修正 RSR-01、RSR-03 的条款级 locator 与页码绑定；
- 将 RSR-02 明确为内部资料排除测试，或补齐经过正式审核的学生可见证据；
- 将 RSR-05 的三组必要证据及“必须先澄清课程类别”分别写入可执行验收条件；
- 对每个 source/chunk 绑定原始文件指纹、文档版本、页码、条款定位和审核状态；
- 扩充覆盖不同知识范围、同义问法、边界问题、跨范围证据、过期/归档资料和权限排除的真实用例；待金标准经独立复核后，再计算正式 Recall@K / MRR。

当前可得结论是：向量检索在部分问题上改善了目标排名，RRF 在 RSR-01 上提升了目标位置，但 RSR-05 证明 RRF Top 5 仍可能漏掉必要证据。下一阶段优先处理金标准和证据绑定，而不是宣称某一种检索算法已经胜出。
