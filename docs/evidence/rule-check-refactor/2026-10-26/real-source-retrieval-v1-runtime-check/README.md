# 真实来源检索 v1：资格、词法、向量与 RRF 诊断

状态：**REAL_SOURCE_RETRIEVAL_DIAGNOSTIC_COMPLETE / FORMAL_METRICS_BLOCKED**。真实向量与混合检索已运行；由于 v1 金标准的来源资格、条款定位和多证据绑定尚不完整，不汇总正式 Recall@K / MRR。

## 1. 冻结数据集

- 数据集：`docs/evidence/rule-check-refactor/2026-10-26/real-source-retrieval-v1/cases.json`
- SHA-256：`bc9e5a8088540577b0deb6c8aaab984497144028e91bf72cb9b99dcc402ec223`
- 5 条开发集用例，覆盖第 4、5、8、10、11、18 条；未修改 v1 问题或金标准。
- 来源快照：《2025版学生手册2025.9.2.pdf》，SHA-256 为 `dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653`。当前学年适用性仍为 UNKNOWN，官方线上附件字节匹配尚未验证。
- 用例为项目负责人自审、非盲测、非留出集；结果用于开发诊断，不代表生产质量估计。

## 2. 隔离环境与安全边界

- 原数据库容器 `zhkuagent-postgres` 保持停止，原卷 `zhkuagent_zhkuagent_postgres_data` 未修改。
- 原容器启动问题来自旧 bind mount：`D:\\alldesk\\Projects\\ZhkuAgent\\knowledge\\infra\\init\\001-enable-pgvector.sql` 当前是目录而非普通文件；实际 SQL 文件位于 mainline 工作树。没有修复或重建原容器。
- 原卷只读复制到隔离卷 `zhkuagent_eval_v1_data_20261026`，容器 `zhkuagent-eval-v1` 使用 PostgreSQL 16 / pgvector，端口绑定 `127.0.0.1:55441)。复制前后各 1,422 个文件，`global/pg_control` SHA-256 相同；未声称整个卷逐字节完全相同。
- 向量索引的所有写入仅发生于隔离副本。真实检索评测期间，数据库连接强制只读事务，数据库写入 0。
- API key 只从已有进程环境读取，没有输出密钥值，也未写入命令参数、文件或日志。没有调用聊天模型；本轮 8 次片段向量化调用 + 5 次查询向量化调用。

## 3. 真实语料资格与索引

原数据库快照共 6 个 source rows、17 个 chunks。当前资格标记下，8 个片段合格、9 个排除：

| knowledge_scope | 合格片段数 |
| --- | ---: |
| PUBLIC_ELECTIVE | 5 |
| PUBLIC_ELECTIVE_CREDIT_LIMIT | 1 |
| ELECTIVE_RETAKE | 2 |

新向量 profile 为 `dashscope-text-embedding-v4-1024`，配置为 `text-embedding-v4`、1024 维，切分版本 `real-source-embedding-v1-20261026`。3 个索引运行均 COMPLETED，分别索引 5、1、2 个片段；8 条 embedding 均非空，不合格片段进入索引数为 0。该 profile 保持 BUILDING，仅供离线评测读取，没有激活线上 RAG。

词法对照使用隔离库中既有的 `real-source-lexical-only-v1-20261026` profile / `real-source-lexical-baseline-v1`。两路引用同一批 chunk ID，但各自使用独立 profile 和索引运行。

## 4. 检索诊断

每条冻结问题分别运行两种范围模式：

- `ALL_ELIGIBLE_SCOPES`：不限制知识范围，但仍严格执行发布、学生可见和分享权过滤；
- `PUBLIC_ELECTIVE`：模拟当前预览配置的范围。

每种模式对照三条路径：中文 Bigram 词法、余弦向量、RRF 混合。RRF 使用 (k=60)，每路候选上限 8，最终 Top 5。五个问题各生成一次查询向量，并在两种范围模式之间复用缓存。

| 用例 | 全部范围结果 | PUBLIC_ELECTIVE 结果 | 主要发现 |
| --- | --- | --- | --- |
| RSR-01 | 目标 chunk：词法第 2、向量第 1、RRF 第 1 | 同左 | 向量/RRF 改善目标排名，但 chunk locator 覆盖第 5—6 条，不能作为精确第 5 条绑定 |
| RSR-02 | 内部目标不返回 | 内部目标不返回 | 当前没有合格的学生可见第 8 条证据；作为权限排除检查，不计正向召回分 |
| RSR-03 | 目标内容三路均第 1 | 目标不可见 | 当前范围与目标 chunk 的 knowledge_scope 不一致；locator/page 仍不准确 |
| RSR-04 | 目标 chunk 三路均第 1 | 同左 | 目标条款命中稳定，locator 较精确；正文仍是策展摘要 |
| RSR-05 | 词法证据不完整、向量完整、RRF 不完整 | 三路均不完整 | 向量 Top 5 可包含三组必要证据；RRF Top 5 漏掉第 4 条类别证据；单一范围无法包含专业重修分支 |

完整排名、chunk/source ID、scope、页码、locator 和派生检查见：

- `embedding-hybrid-results.json`：真实向量与 RRF 对照结果。
- `embedding-hybrid-analysis.md`：本阶段结论和逐例分析。
- `lexical-baseline-results.json`：之前执行的纯词法基线。
- `source-binding-audit.md`：来源、片段资格和原文定位核查。
- `results.json`：更早的直接关键词/专用证据路径探针；该文件记录的是历史阶段，不是本轮混合检索结果。

Maven 验收：`RealSourceHybridEmbeddingBaselineTest` 1 passed / 0 failures / 0 errors。临时测试源码及构建报告在本轮结束时清理，冻结数据集保持原样。

## 5. 为什么没有汇总 Recall@5 / MRR

1. RSR-02 的正向金标准指向 INTERNAL_ONLY、分享权未确认的证据；
2. RSR-03 的第 10 条 locator/page 与目标绑定不准确，且默认范围不包含该 chunk；
3. RSR-01 的目标 chunk 覆盖多个条款；
4. RSR-05 的金标准需要“课程类别 + 公共重修分支 + 专业重修分支”三组必要证据，不能只检查一个第 18 条 locator；
5. 5 条样本是开发集、非盲测、自审样本。

把这些问题直接折算为整体召回率，会混淆检索质量与金标准/资格错误。当前只报告逐例排名、权限排除和证据集合完整性。

## 6. 下一阶段门槛

优先建立新版本 v2 金标准，保持 v1 冻结：

- 修正 RSR-01、RSR-03 的条款级 locator 与页码绑定；
- 将 RSR-02 变为权限排除测试，或补充经正式审核的学生可见证据；
- 明确 RSR-05 三组必要证据与先澄清课程类别的验收条件；
- 建立原始文件指纹到 source/chunk 的机器可核验绑定；
- 扩充覆盖多知识范围、同义问法、边界问题、跨范围证据、过期/归档和权限排除的真实用例，再由独立复核确认后计算正式指标。

当前结论：向量检索在部分问题上改善了目标排名，但 RSR-05 证明 RRF Top 5 不保证保留完整证据集合。先修复金标准与来源绑定，再评估整体算法优劣。
