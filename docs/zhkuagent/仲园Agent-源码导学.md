# 仲园 Agent 校园知识问答：源码导学

## 这次要学什么

这不是一个已经接入大模型的完整校园助手，而是一个已经跑通的“可信知识问答”最小系统。它先证明一件事：一条校园规则只有在经过审核、被发布、检索命中后，才能作为回答和出处展示；资料外的问题必须拒答。

本次学习目标是沿着两条真实链路阅读源码：

1. `PDF → 候选片段 → 审核通过 JSON → PostgreSQL` 的资料治理链路；
2. `开发者问题 → 本地问答入口 → 有依据问答服务 → 已发布资料 → 回答或拒答` 的问答链路。

## 源码版本与边界

- 当前分支：`codex/zhkuagent-mvp`。
- 基线提交：`41dd69a`。
- 本导学涉及的 `zhku` 源码、迁移脚本、审核资料和文档均是当前工作区的未提交开发成果；因此以本地文件为准，不能将其当作远程仓库已发布能力。
- 当前已覆盖：公共选修课程的 6 个已审核片段、8 道验收问题、本地数据库、关键词检索、有出处回答与拒答分支。
- 当前未覆盖：网页接口、学生端页面、登录授权、真实语义向量检索、大模型生成、成绩和学籍等敏感数据。

## 前置知识

| 知识点 | 为什么需要 | 在本项目的位置 |
| --- | --- | --- |
| Java 命令行程序 | 当前以命令行而非网页接口启动导入和问答 | `ReviewedKnowledgeImportCli`、`GroundedAnswerCli` |
| PostgreSQL 与事务 | 来源和多个片段必须一起发布，失败时不能留下半份资料 | `ReviewedKnowledgePublisher.publish` |
| 数据库迁移 | 新环境必须能从脚本恢复表结构 | `V1__create_zhku_knowledge_schema.sql` |
| 单元测试与验收测试 | 分别验证局部规则、真实数据库和真实问法 | `src/test/java/com/yupi/yuaiagent/zhku` |
| 资料审核 | 原始 PDF 文本不能绕过人工判断直接成为回答依据 | `ReviewedKnowledgeJsonReader` |

## 先建立总图

打开 [最小问答架构图](仲园Agent-最小问答架构.html)，按顶部“资料治理与入库”“有出处问答”“拒答边界”三个视图阅读。

先不要把这条链路理解成“用户提问后，模型从 PDF 中找答案”。当前的真实机制是：

```text
原始 PDF
  → 人工挑选、审核候选片段
  → 已审核 JSON
  → 事务发布到数据库
  → 针对已验收问题选择检索关键词
  → 仅从 PUBLISHED 资料检索
  → 命中：回答 + 页码出处；未命中：INSUFFICIENT_EVIDENCE
```

其中“人工审核”和“资料不足时拒答”是最重要的可信边界。

## 课程 01：一份资料为什么不能直接进知识库

**读完能回答：** 原始 PDF 为什么不能直接交给问答系统，审核清单又保存了哪些可追溯信息？

**主链：** `PDF 文件 → PdfPageTextExtractor → 候选 JSON → ReviewedKnowledgeJsonReader → ReviewedKnowledgePublication`

1. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/ingestion/PdfPageTextExtractor.java`。
   - 输入：PDF 路径和需要读取的物理页码。
   - 下一步：对页码去重、排序并校验范围，再使用 PDFBox 逐页提取。
   - 输出：带物理页码的 `ExtractedPdfPage`。
   - 要点：它只负责忠实提取，不判断规则是否仍然有效，也不写数据库。
2. 阅读 `data/curation/2025版学生手册2025.9.2-公共选修候选知识片段.json`。
   - 输入：审核后的公共选修资料。
   - 下一步：确认整份资料和每一个候选片段的 `review_status` 都是“审核通过”。
   - 输出：6 个可发布片段、页码定位、适用范围、风险提示和 8 道测试题。
   - 要点：这份资料明确标注为“本地验证”，且包含 2021 年管理办法；它不能直接作为面对学生的现行官方规则。
3. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/publication/ReviewedKnowledgeJsonReader.java`。
   - 输入：候选 JSON 文件路径。
   - 下一步：第 35—49 行拒绝未审核整份资料；第 54—72 行逐条拒绝未审核片段，并从定位文本提取 PDF 页码。
   - 输出：`ReviewedKnowledgePublication`。

**选读证据：** `src/test/java/com/yupi/yuaiagent/zhku/knowledge/publication/ReviewedKnowledgeJsonReaderTest.java`。

**暂缓阅读：** 如何自动从整份 PDF 生成候选片段尚未实现；当前由人工审核产出 JSON。

**状态：** 大纲。

## 课程 02：资料如何原子地发布到数据库

**读完能回答：** 为什么要区分“资料来源”和“知识片段”，以及为什么发布时必须使用事务？

**主链：** `ReviewedKnowledgeImportCli → LocalKnowledgePublisherFactory → ReviewedKnowledgePublisher.publish → knowledge_source / knowledge_chunk`

1. 阅读 `src/main/resources/db/migration/V1__create_zhku_knowledge_schema.sql`。
   - 输入：空数据库。
   - 下一步：创建 `knowledge_source` 和 `knowledge_chunk`。
   - 输出：带状态、版本、时间、校验和、页码定位和外键约束的结构。
   - 要点：只有 `PUBLISHED` 来源能被检索；来源与片段分表让答案能指向具体页码，而不是笼统指向整份手册。
2. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/publication/ReviewedKnowledgeImportCli.java`。
   - 输入：一个已审核 JSON 路径。
   - 下一步：读取审核资料、创建发布器、执行发布。
   - 输出：导入的来源标识和片段数量。
3. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/publication/LocalKnowledgePublisherFactory.java`。
   - 输入：Java 系统属性或环境变量中的数据库配置。
   - 下一步：创建统一的 `DataSource`。
   - 输出：供导入、检索、问答共用的发布器。
   - 要点：优先读取 `zhku.db.password`，其次读取 `ZHKU_DB_PASSWORD`；密码未提供就快速失败。
4. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/publication/ReviewedKnowledgePublisher.java`。
   - 输入：`ReviewedKnowledgePublication`。
   - 下一步：先调用 `requireApproved`，然后在一个事务中写入来源和所有排序后的片段。
   - 成功输出：来源状态为 `PUBLISHED`，片段附带稳定标识和内容校验和。
   - 失败输出：任一片段失败即回滚，避免“来源已发布、片段却不完整”。

**选读证据：**

- `src/test/java/com/yupi/yuaiagent/zhku/KnowledgeSchemaTest.java`：验证迁移后表、列和外键真实存在。
- `src/test/java/com/yupi/yuaiagent/zhku/knowledge/publication/ReviewedKnowledgePublisherTest.java`：验证未审核资料不能写入、归档资料不能被检索。

**暂缓阅读：** `pgvector` 扩展已在数据库迁移中安装，但当前没有写入向量，也没有使用向量相似度检索。

**状态：** 大纲。

## 课程 03：已发布资料如何被检索

**读完能回答：** 为什么检索 SQL 必须限定 `PUBLISHED`，以及当前关键词检索能做什么、不能做什么？

**主链：** `ReviewedKnowledgeSearchCli → ReviewedKnowledgePublisher.searchPublished → PostgreSQL → PublishedKnowledgeChunk`

1. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/publication/ReviewedKnowledgeSearchCli.java`。
   - 输入：一个关键词。
   - 下一步：调用 `searchPublished`，再由展示器输出结果。
   - 输出：供人工核验的资料片段和出处。
2. 回到 `ReviewedKnowledgePublisher.searchPublished`。
   - 输入：关键词。
   - 下一步：转义 `%`、`_`、反斜杠，使用参数化 SQL 查询。
   - 关键分支：SQL 固定使用 `source.publication_status = 'PUBLISHED'`。
   - 输出：`PublishedKnowledgeChunk` 列表。
3. 阅读 `src/test/java/com/yupi/yuaiagent/zhku/knowledge/publication/PublicElectiveKeywordSearchAcceptanceTest.java`。
   - 输入：审核 JSON 中的 8 个 `keyword_probe`。
   - 输出：每个关键词都必须命中预期页码；奖学金问题必须查不到结果。

**当前限制：** 这不是语义检索。它不能理解任意同义表达，也不负责给出自然语言结论；它的价值是以最小依赖先证明“已发布资料不会越权、出处不会丢失”。

**状态：** 大纲。

## 课程 04：一次问题如何变成有出处回答或拒答

**读完能回答：** 系统如何保证“有依据回答必须有出处”，又如何避免未知问题被错误回答？

**主链：** `GroundedAnswerCli → GroundedAnswerService.answer → resolvePublicElectiveKeyword → searchPublished → GroundedAnswer → GroundedAnswerPresenter`

1. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/answer/GroundedAnswerCli.java`。
   - 输入：一个校园问题。
   - 下一步：创建共用发布器，将其 `searchPublished` 方法作为检索器注入问答服务。
   - 输出：终端可读的回答文本。
2. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/answer/GroundedAnswerService.java`。
   - 输入：问题文本。
   - 下一步：去除空白、限制为 500 字；`resolvePublicElectiveKeyword` 只识别已人工验收的 8 类公共选修问题。
   - 命中路径：用确定性关键词检索已发布片段，取第一条证据，构造 `GROUNDED` 回答。
   - 拒答路径：问题不在当前范围，或关键词没有已发布证据，构造 `INSUFFICIENT_EVIDENCE` 回答。
   - 要点：它刻意不调用模型，也不把未知问法强行映射到“看起来相近”的规则。
3. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/answer/GroundedAnswer.java`。
   - 输入：回答模式、正文、引用列表。
   - 关键约束：`GROUNDED` 必须至少有一条出处；`INSUFFICIENT_EVIDENCE` 不能附带出处。
   - 这是一条数据结构级别的防线，不依赖展示层自觉遵守。
4. 阅读 `src/main/java/com/yupi/yuaiagent/zhku/knowledge/answer/GroundedAnswerPresenter.java`。
   - 输入：`GroundedAnswer`。
   - 输出：回答模式、正文、来源标题、定位和物理页码。

**选读证据：**

- `src/test/java/com/yupi/yuaiagent/zhku/knowledge/answer/GroundedAnswerServiceTest.java`：验证有证据、无证据、范围外三条分支。
- `src/test/java/com/yupi/yuaiagent/zhku/knowledge/answer/PublicElectiveGroundedAnswerAcceptanceTest.java`：真实数据库中的 8 道公共选修问题必须有出处；奖学金问题必须拒答。
- `src/test/java/com/yupi/yuaiagent/zhku/knowledge/answer/GroundedAnswerPresenterTest.java`：验证有出处和拒答时的输出格式。

**暂缓阅读：** 网页控制器、身份认证、对话记忆和大模型调用不属于当前链路；不要从原项目的通用 Agent 模块反向推断它们已被仲园 Agent 使用。

**状态：** 大纲。

## 一次手动验证的阅读顺序

用下面两个问题验证时，应按上述代码顺序在脑中追踪：

```text
一学期最多能选多少公共选修课学分？
→ 命中“每学期公共选修课最高”
→ 查到 PDF 第 76—77 页第 8、10 条
→ GROUNDED + 出处

奖学金申请截止日期是什么时候？
→ 不属于当前 8 类公共选修路由
→ 不访问数据库
→ INSUFFICIENT_EVIDENCE，且没有出处
```

## 自学提醒

若某个文件或原理看不懂，请继续追问 AI；本导学负责给学习路径与题目，不提供逐行讲解。
