# 阶段 7：当期选课通知治理实施计划

> 状态：待执行。前置：阶段 6 已验收。后置：阶段 8。  
> 范围：仅本地人工接入的“选课通知”；覆盖选课、退改选、补选、改选。

## 目标与非目标

实现通知的审核、发布、替代、失效和学生端时态过滤，使系统只从当前学期的有效官方通知中回答动态办理事实。首期不自动爬取官网、不接模型、不做向量索引、不做前端大改造、不处理真实身份或校务办理。

## 任务清单

1. [ ] 新增 `V6__add_official_notice_lifecycle.sql`：扩展提交资料族以允许 `OFFICIAL_NOTICE`；新增通知族、学期、通知类型、生命周期、有效起止、字段确认标志、替代来源 ID、官方 URL、不可变快照存储键/哈希/抓取时间/媒体类型，以及 `notice_fact`（动作、开始、结束、官方入口、确认状态）。为“当前通知”查询创建组合索引。迁移仅追加，保留旧资料的默认行为。
2. [ ] 在 `com.zhku.knowledge.notice` 建立不可变模型：`NoticeType`、`NoticeLifecycleStatus`、`AcademicTerm`、`OfficialWebSnapshot`、`NoticeFactType`、`NoticeFact`、`PublishedNotice`。构造约束：学生可用的动态事实必须有确认学期、确认开始/结束、已审核官方 URL 和快照；任何缺项均不能标为 `PUBLISHED`。
3. [ ] 以现有 `KnowledgeSubmissionService`/`CandidateReviewService` 为基础，增加通知专用审核服务和仓储。发布同一“通知族 + 学期 + 类型”新版本时，在一个数据库事务中发布新版、记录替代关系、使旧版为 `SUPERSEDED`；失败时保持旧版仍可用。
4. [ ] 增加 PDF 与官方网页快照的本地文件持久化策略。保存 URL、获取时间、SHA-256、媒体类型和不可变副本；网页实际变更不覆盖审核时的快照。扫描件文本/页码不可靠时进入 `NEEDS_OCR_REVIEW`，而不是直接发布。
5. [ ] 实现 `CurrentNoticeFinder`，仅用注入的 `Clock` 查找：已发布、学生可见、权利确认、未替代、学期匹配、`validFrom <= now < validTo` 的通知。`now == validTo` 必须视为无效。不存在合规通知时返回结构化 `EVIDENCE_UNAVAILABLE` 和审核过的官方查询链接，禁止输出日期或资格。
6. [ ] 将动态通知分支置于现有静态公共选修规则和未来检索分支之前。仅增加最小本机 HTTP/页面展示以验证“当前通知 / 无有效通知”两种结果，不将模型、回答卡重构或阶段 8 路由目录带入。
7. [ ] 增加管理员审核操作的本地验收手册和本阶段证据账本，明确审核人标签是本机流程记录而非真实身份审计。

## 先写的测试

- `NoticeLifecycleModelTest`：合法/非法元数据组合；PDF/网页快照的哈希不可变；扫描件不能绕过 OCR 审核。
- `JdbcNoticeRepositoryTest`（PostgreSQL/Testcontainers）：新通知发布、同范围替代、发布中异常回滚、旧版不可再作为当前通知。
- `CurrentNoticeFinderTest`：学期不匹配、未发布、内部可见、无分享权利、已替代、未开始、`now == validTo`、已过期、完整有效通知。
- `CourseSelectionNoticeRouterTest`：动态问题优先于静态规则；缺元数据/无当前通知拒答并给官方渠道；不从通知推导资格。
- `NoticeGovernanceAcceptanceTest`：管理员审核发布后学生端能引用来源版本/快照定位；新版发布后旧证据不出现。

## 验收与退出条件

聚焦测试、`knowledge` 全模块测试及 PostgreSQL 集成测试均通过；人工上传/审核一份选课通知和一份替代通知，验证过期/替代结果。证据账本必须标出测试数、环境、时间和未覆盖内容。只有完成这些，才能开始阶段 8。

## 不得提前做

- 不接入 ChatModel、Embedding、pgvector、SSE 模型流或“RAG”宣传；
- 不自动抓取或定时爬取官网；
- 不从缺失的学期/时段推测当期安排；
- 不把通知当作静态手册，也不删除旧快照（旧快照保留审计，学生检索不可见）。
