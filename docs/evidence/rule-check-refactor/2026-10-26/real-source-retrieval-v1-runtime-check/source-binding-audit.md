# 真实来源绑定、资格与词法基线核查

日期：2026-10-26

状态：**SOURCE_BINDING_AUDIT_COMPLETE / LEXICAL_DIAGNOSTIC_COMPLETE / HYBRID_EVALUATION_BLOCKED**

本报告不修改冻结的 v1 金标准，不把词法诊断分数冒充混合 RAG 指标。

## 1. 环境恢复与安全边界

- 原容器 `zhkuagent-postgres` 保持停止，原卷 `zhkuagent_zhkuagent_postgres_data` 保留。
- 原容器挂载配置指向 `D:\\alldesk\\Projects\\ZhkuAgent\\knowledge\\infra\\init\\001-enable-pgvector.sql`；该路径当前是一个目录，不是普通文件。实际 SQL 文件存在于 mainline 工作树的 `knowledge/infra/init/001-enable-pgvector.sql`。这属于文件/目录类型不匹配和旧根路径问题。
- 没有修复旧容器，也没有启动原数据库。将原卷只读复制到独立卷 `zhkuagent_eval_v1_data_20261026`，并启动容器 `zhkuagent-eval-v1`，仅绑定到 `127.0.0.1:55441`，没有挂载有问题的初始化脚本路径。
- 副本与原卷文件数均为 1,422，`global/pg_control` SHA-256 均为 `9e66b6c7f3dbe4cd5ecca2899bd80a08209a8dc1fe867fd8e591630aca1cd6b1`。不声称整个卷逐字节完全相同。
- 副本 PostgreSQL 16、pgvector 扩展、主机端口连通性及只读 SQL 均验证通过。
- 原始 PDF 未复制、移动或修改；没有数据库迁移、真实模型调用或 Embedding API 调用。

## 2. 真实来源与片段资格

冻结金标准指向《2025版学生手册2025.9.2.pdf》中的《仲恺农业工程学院选修课程管理办法》（仲字〔2021〕65号），PDF SHA-256 为：

`dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653`

既有来源抽取记录覆盖物理第 76—77 页（印刷第 64—65 页）。但数据库当前无法独立证明每条 source 记录来自这份具体 PDF：

- `knowledge_source` 共 6 条，6 条的 `source_url` 都为空。
- `knowledge_source` schema 没有 `source_sha256`、`document_sha256`、`source_file_sha256` 或 `original_file_sha256` 字段。
- 数据库的 `content_checksum` 不是独立存储的原始 PDF 指纹。
- 因此，文件指纹只能从项目外部证据清单核验；当前数据库的来源—文件绑定尚未达到机器可验证的完整闭环。
- 2026 版学生手册已出现在学校资源目录；2025 版对当前学年的适用性仍为 `UNKNOWN`。本评测仅针对冻结的 2025 文本，不构成学生发布授权。

### 当前片段盘点

| 来源 ID（缩写） | 发布状态 | 可见性 / 分享权 | 范围 | 片段数 | 资格结论 |
| --- | --- | --- | --- | ---: | --- |
| `6b2f4c18…` | PUBLISHED | STUDENT_VISIBLE / true | PUBLIC_ELECTIVE | 5 | 可进入学生可见检索 |
| `72e1a0a5…` | PUBLISHED | STUDENT_VISIBLE / true | ELECTIVE_RETAKE | 2 | 可进入学生可见检索 |
| `f4d0b0a0…` | PUBLISHED | STUDENT_VISIBLE / true | PUBLIC_ELECTIVE_CREDIT_LIMIT | 1 | 可进入学生可见检索，但不是 PUBLIC_ELECTIVE 范围 |
| `4a7c1e93…` | PUBLISHED | INTERNAL_ONLY / false | PUBLIC_ELECTIVE | 1 | 必须排除学生检索 |
| `2a04fd61…` | ARCHIVED | INTERNAL_ONLY / false | PUBLIC_ELECTIVE | 6 | 必须排除学生检索 |
| `861be981…` | ARCHIVED | INTERNAL_ONLY / true | PUBLIC_ELECTIVE | 2 | 必须排除学生检索 |

共 17 个片段，按当前资格标记只有 8 个可进入学生可见检索：PUBLIC_ELECTIVE 5 个、PUBLIC_ELECTIVE_CREDIT_LIMIT 1 个、ELECTIVE_RETAKE 2 个。当前应用预览配置指定 `retrieval-scope: PUBLIC_ELECTIVE`，因此同一次范围受限检索只会看到该范围的 5 个合格片段。预览配置的 RAG 仍默认关闭。

## 3. 五条 v1 的真实绑定审查

### RSR-01：第 5 条，16 / 32 学时

- 候选片段：`55815778-3038-33cb-9a23-7b1ed5efed4f`
- 来源：`6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05`，PUBLISHED、STUDENT_VISIBLE、分享权已确认。
- 实际定位：PDF 物理第 76 页、第 5—6 条。
- 结论：正文包含 16 学时“原则上”和 32 学时上限，内容可用；但片段定位比金标准的第 5 条更宽，不能通过严格条款定位检查。

### RSR-02：第 8 条，网络公选课比例

- 唯一匹配该比例的当前片段：`39af6bf7-0ce5-3ffa-8d35-f0ee1c88ea26`，来源 `4a7c1e93-2f6b-4d18-9b05-3c8e7a1f5d42`。
- 它虽然状态为 PUBLISHED，但明确是 INTERNAL_ONLY，且分享权未确认；另一个含第 8 条的来源已 ARCHIVED。
- 学生可见的合格索引中没有第 8 条比例证据。
- 结论：v1 将此题标为 `ANSWERED`，与当前被测学生可见语料资格冲突。它不能作为学生端正向召回题计分；在新版本中应改为权限排除测试，或者先有正式审核通过、可学生可见的证据。

### RSR-03：第 10 条，每学期 4 学分

- 候选片段：`e2dc2c7a-ce97-32db-9506-b7c29d2a15bf`，来源 `f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5)。
- 资格标记本身合格，但范围为 `PUBLIC_ELECTIVE_CREDIT_LIMIT`，与当前配置的 `PUBLIC_ELECTIVE` 不同。
- 数据库页码为 76，定位为“PDF 第 76—77 页，第 8、10 条”；原文第 10 条实际位于 PDF 物理第 77 页、印刷第 65 页。
- 结论：跨全部合格范围的词法检索能找到 4 学分内容，但严格出处定位不合格；按当前 PUBLIC_ELECTIVE 范围检索则根本看不到该片段。

### RSR-04：第 11 条，相近课程与重复修读

- 候选片段：`b2213ca7-c40c-3054-ad75-fd2a058c7ade`，来源 `6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05)。
- 资格标记合格；定位为 PDF 物理第 77 页、第 11 条。
- 正文覆盖“与专业教学计划相同或相近”及“已考核合格不可重复修读”两项限制。
- 结论：五条中绑定最完整的一条；仍需注意数据库没有直接存储原始 PDF 指纹。

### RSR-05：第 4 条 + 第 18 条，类别澄清与两类重修分支

需要的证据实际是一组多片段绑定：

- 课程类别：`ac78ed6c-5147-32a6-8297-c4c7967a9d1e`（PUBLIC_ELECTIVE，第 2、4 条）。
- 公共选修课分支：`06766d1e-2f83-3f8d-9a1d-1b66b2700a06`（ELECTIVE_RETAKE，第 18 条）；同义候选还有 `8760f9a1-27c4-3fd3-9871-9209e2a398f0`，但它同时合并第 14—15 条。
- 专业选修课分支：`b3cd386d-49fc-3224-8b3e-533f89dea106`（ELECTIVE_RETAKE，第 18 条）。
- 结论：v1 只把第 18 条表示为一个必要 locator，但预期事实同时要求公共与专业两个分支。严格完整证据检查必须检查三组事实：类别、公共分支、专业分支。
- 当前 `PUBLIC_ELECTIVE` 范围无法召回 `ELECTIVE_RETAKE` 中的专业分支。跨所有合格范围的词法检索能找到两个第 18 条分支，却在 Top 5 内漏掉第 4 条。两种模式都未满足完整证据集。

## 4. 实际运行的词法诊断

为避免未经同意调用付费 Embedding API，本轮在隔离副本上创建了一个明确标记为 `NONE / LEXICAL_ONLY_NO_EMBEDDINGS` 的临时诊断 profile，索引 8 个合格片段，向量均为 NULL；运行项目的显式 `ChineseBigramLexicalRanker` 评测路径，比较：

1. `ALL_ELIGIBLE_SCOPES`：不限制 knowledge_scope，但仍严格排除内部、归档或分享权未确认来源。
2. `CONFIGURED_PUBLIC_ELECTIVE`：模拟当前配置的 PUBLIC_ELECTIVE 范围。

测试结果：1 个 JUnit 测试通过，0 failures / 0 errors；5 个问题各运行两种范围模式，共 10 次检索。检索测试只读数据库；模型调用 0、Embedding API 调用 0。详细排名和 chunk ID 见同目录 `lexical-baseline-results.json`。

| 用例 | 全部合格范围 Top 5 | PUBLIC_ELECTIVE Top 5 | 诊断 |
| --- | --- | --- | --- |
| RSR-01 | 目标片段第 2 名 | 目标片段第 2 名 | 内容命中；第 1 名是合并第 14—15、18 条的公共选修片段，目标定位仍偏宽 |
| RSR-02 | 无合格第 8 条证据 | 无合格第 8 条证据 | 正确排除内部资料；不能把该题计为正向召回失败 |
| RSR-03 | 目标内容第 1 名 | 目标片段不在该范围 | 内容命中，但出处定位错误；当前默认范围排除该来源 |
| RSR-04 | 目标片段第 1 名 | 目标片段第 1 名 | 通过本轮词法召回诊断 |
| RSR-05 | 第 18 条公共分支第 1、专业分支第 3；第 4 条未进 Top 5 | 第 4 条第 3；公共分支第 1；专业分支被范围排除 | 两种模式都缺少完整证据集 |

**没有计算正式 Recall@5 或 MRR。** 这是有意的：RSR-02 的正向金标准没有合格学生可见证据，RSR-03 的目标定位不准确，RSR-05 的 locator 级金标准不足以表达两种重修分支的完整性。现在给出一个整体数字会把数据资格错误掩盖成检索器质量。

临时 JUnit 源码将在本轮结束时移除。词法 profile 保持为隔离副本中的唯一 ACTIVE profile，名称明确标记为 `LEXICAL_ONLY_NO_EMBEDDINGS`，8 条 embedding 均为 NULL；它只用于词法诊断，不是向量索引。测试容器随后以 exit 0 正常停止（触发来源尚未确定），持久化评测卷仍保留；进入下一阶段前应先确认容器状态并使用明确的 profile ID。

## 5. 阶段结论与下一步门槛

1. **隔离环境已恢复**，原数据库容器与原卷没有被修改。
2. **真实资料资格已核实**：8 个合格片段、9 个排除片段；内部资料没有进入学生可见诊断索引。
3. **五条问题已跑完两种词法范围诊断**，但没有正式 v1 综合指标。
4. 下一阶段不能直接把所有片段灌入向量索引。应先建立新的 v2 绑定清单，保持 v1 不变，并处理：
   - RSR-02：转成权限排除测试，或等待正式学生可见证据；
   - RSR-03：修正独立的第 10 条 locator/page binding；
   - RSR-05：把 Article 18 的公共与专业分支都纳入完整证据检查，并明确跨范围检索/专用工具策略；
   - 来源元数据：为来源记录补上可追溯的原始文件指纹和来源 URL/证据引用；在适用性确认前不作学生发布批准。
5. 真正的向量/混合检索索引会调用配置的 DashScope `text-embedding-v4`，并向隔离数据库写入 profile、运行记录和向量条目。本轮尚未调用该 API。正式建立前需确认允许使用该 Embedding API；不应把词法诊断冒充混合检索结果。
