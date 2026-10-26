# 真实来源检索 v1：资格与运行路径核查

状态：**部分完成（DIRECT_PATH_PROBES_COMPLETE / HYBRID_INDEX_BLOCKED）**。本报告不把专用关键词查询结果冒充为混合 RAG Recall@K / MRR。

## 1. 执行方式与安全边界

- 数据来源：原有 ZhkuAgent PostgreSQL 持久化卷 `zhkuagent_zhkuagent_postgres_data` 的隔离副本。
- 原始数据库容器 `zhkuagent-postgres` 保持停止；启动失败的原因是其 Docker bind mount 指向已不存在的旧路径 `D:\\alldesk\\Projects\\ZhkuAgent\\knowledge\\infra\\init\\001-enable-pgvector.sql`。
- 没有修复或重建原容器。将原卷只读挂载并复制到临时卷；文件数量 1,422 对 1,422，PostgreSQL `global/pg_control` SHA-256 相同。目录统计字节数相差 4,096，因此不声称整卷逐字节相同。
- 临时副本使用相同 PostgreSQL 镜像启动在回环端口 55441。查询连接强制 `readOnly=true`、关闭自动提交并执行 `SET TRANSACTION READ ONLY`；没有迁移、发布、索引写入或应用数据写入。
- 没有调用 Chat/Embedding 供应商；模型调用 0，应用数据写入 0。
- 临时探针调用项目现有 `ReviewedKnowledgePublisher.searchPublished/searchStudentVisiblePublished`、`JdbcCreditLimitEvidenceFinder` 和 `JdbcElectiveRetakeEvidenceFinder`。检索词是从金标准证据提取的关键词短语，不是将自然语言问题交给已建索引的排序检索器。
- Maven 结果：`RealSourceRetrievalProbeTest` 1 个测试通过，0 failures / 0 errors。它验证的是下面的直接检索路径与资格边界，不代表 RAG 评测通过。

## 2. 被测库前置条件

只读快照中的 `knowledge_source` 与 `knowledge_chunk` 有真实策展资料，但：

- `embedding_profile`：0 条
- `retrieval_index_entry`：0 条
- 当前本机 RAG 配置未显式开启（代码约定为默认关闭）。

因此，当前**无法运行 v1 规定的通用混合检索评测，也不能计算 Recall@K 或 MRR**。缺少索引不应记作 0 分；它是评测前置条件未满足。此次仅对已存在的直接关键词检索和专用证据查询做诊断。

## 3. 五条用例结果

| 用例 | 结果 | 数据库绑定 / 资格 | 解释 |
| --- | --- | --- | --- |
| RSR-01 | 直接关键词命中 | source `6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05`，chunk `55815778-3038-33cb-9a23-7b1ed5efed4f`；PUBLISHED、STUDENT_VISIBLE、分享权已确认；范围 `PUBLIC_ELECTIVE` | 命中 32 学时规则；定位是第 5—6 条，且数据库正文是策展摘要，不是冻结金标准的逐字第 5 条原文 |
| RSR-02 | 内部命中、学生端正确排除 | source `4a7c1e93-2f6b-4d18-9b05-3c8e7a1f5d42`，chunk `39af6bf7-0ce5-3ffa-8d35-f0ee1c88ea26`；PUBLISHED 但 INTERNAL_ONLY、分享权未确认 | 内部关键词查询命中 1 条；学生可见查询命中 0 条。不能作为学生端可回答用例计分 |
| RSR-03 | 内容命中、定位不充分 | source `f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5`，chunk `e2dc2c7a-ce97-32db-9506-b7c29d2a15bf`；PUBLISHED、STUDENT_VISIBLE、分享权已确认；范围 `PUBLIC_ELECTIVE_CREDIT_LIMIT` | 专用查询命中 4 学分上限；定位合并第 8、10 条，page_number=76，无法单独绑定第 10 条的精确原文位置 |
| RSR-04 | 直接关键词命中 | source `6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05`，chunk `b2213ca7-c40c-3054-ad75-fd2a058c7ade`；PUBLISHED、STUDENT_VISIBLE、分享权已确认；范围 `PUBLIC_ELECTIVE` | 定位到 PDF 第 77 页第 11 条；内容覆盖相近课程和已合格课程不得重复两项限制，但仍是策展正文，不是逐字金标准 |
| RSR-05 | 证据存在，跨范围组合与澄清流程未验证 | 第 4 条：source `6b2f...` / chunk `ac78ed6c-5147-32a6-8297-c4c7967a9d1e`，范围 `PUBLIC_ELECTIVE`；第 18 条：source `72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14` / chunks `06766d1e-2f83-3f8d-9a1d-1b66b2700a06` 与 `b3cd386d-49fc-3224-8b3e-533f89dea106`，范围 `ELECTIVE_RETAKE`；均已发布、学生可见且分享权已确认 | 类别证据和两种重修分支分别可查询；它们分属不同知识范围。此次未验证 Agent 会否先澄清课程类别，也未证明单次通用检索会同时召回全部必要证据 |

上述每个目标 chunk 的 `retrieval_index_entry` 数量均为 0。

## 4. 影响 v1 解释的发现

1. v1 是 5 条开发样本，涉及 6 个不同条款编号（第 4、5、8、10、11、18 条）；它们覆盖的知识范围并不相同。
2. RSR-02 的预期状态是 `ANSWERED`，但它的必要证据目前明确属于内部资料。若评估学生可见能力，该题应作为权限/范围排除案例，而不能作为普通答案召回题。
3. RSR-03 的学生可见证据能够支持“每学期 4 学分”这一内容，但其定位把第 8、10 条合并，不能满足第 10 条独立精确定位的金标准。
4. RSR-05 需要跨 `PUBLIC_ELECTIVE` 与 `ELECTIVE_RETAKE` 两个范围。用单一范围的通用检索器不能假设会自动召回两者；澄清行为与多范围证据收集应作为独立验收点。
5. 当前资料片段主要是经策展的摘要或按业务分支拆分的文本，不总是金标准中的逐字条文。因此“找到语义相关摘要”与“逐字原文、条款、页码准确绑定”必须分别评分。

## 5. 结论与下一步

- 真实数据库的来源/片段身份及学生可见资格已完成只读核对。
- 当前直接查询路径的五条诊断已执行；2 条直接命中、1 条内部命中但学生端正确排除、2 条部分完成。
- 通用混合检索评测仍被 **0 个 profile / 0 条 index entry** 阻断；没有产生可报告的 Recall@K / MRR。
- 下一步应先在隔离环境中明确目标检索范围、建立可追溯的真实来源到 chunk 的逐条绑定，并准备专用评测索引；不要把内部参考资料放入学生可见索引来凑齐五题。
- v1 保持不变。后续扩充应另建 v2，并把学生可见召回、内部资料排除、多范围证据与澄清、逐字引用定位分成不同评测维度。
