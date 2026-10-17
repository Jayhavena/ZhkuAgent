# 3A 之后的后端工程开发计划

> **历史路线说明（2026-10-18 更新）：** 以下为 10 月 3 日起点，不能继续按“尚未实施”概括全项目。4A 身份/归属、3B 澄清控制状态与 3C 提交/预算已有阶段验收；真实模型也已有受控学分及 T05 分支证据。3D 受限恢复、4B 非本机入口仍未在当前目标内启动。现阶段先按[统一集成与收口计划](2026-10-18-project-integration-and-closeout-plan.md)完成规则查证主链，状态见[项目总览](../../zhkuagent/2026-10-18-project-status.md)。下文“保持未提交”是旧约束，现行本地保存遵循 [AGENTS.md](../../../AGENTS.md)。

日期：2026-10-03。状态：**基于真实源码的开发路线草案，尚未实施**。用户已选择“优先后端工程：权限、持久化与恢复”。本文件不是一次性执行五项能力的授权；各阶段独立设计、基线、实施、验收，不将路线设计当代码完成。

**目标：** 从单人进程内原型，逐步走向具有可信身份、资源归属、持久会话和受限恢复的小范围单实例后端，最后用真实模型与实际网络入口验证。

**架构：** 两条 Agent 主循环继续独立；共享 Runtime 只表达执行生命周期。身份、用户资源归属、会话、恢复检查点及校园证据/转人工在 knowledge 应用与领域层。仍使用现有 PostgreSQL，不建设分布式调度、通用工作流、多 Agent、MCP 或长期记忆。

**技术基线：** JDK21、Spring Boot3.4.4、Spring AI1.0.9、现有 JDBC/Flyway/JUnit。权限阶段建议新增 Boot 管理版本的 Spring Security；这是后续单独审阅的依赖增量，本轮和3A未改依赖。不要以升级整个技术栈作为实现条件。

## 1. 当前事实与源码依据

| 事实 | 真实文件、类与方法 |
| --- | --- |
| 校园Agent实际使用供应商模型的工厂已存在 | [CampusAgentPreviewConfiguration](../../../knowledge/src/main/java/com/zhku/knowledge/web/CampusAgentPreviewConfiguration.java)，zhkuCampusAgentChatModel；[CourseCreditChatModelFactory](../../../knowledge/src/main/java/com/zhku/knowledge/tools/modelcall/CourseCreditChatModelFactory.java)，create；它不是“模型接入从零开始” |
| 校园流程由服务端控制工具，不能交给框架自动执行 | [CampusCreditAgentService](../../../knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusCreditAgentService.java)，构造器internalToolExecutionEnabled(false)、run/decide；固定政策查询和纯计算 |
| Run当前请求没有用户或业务Session字段 | [CampusAgentPreviewController](../../../knowledge/src/main/java/com/zhku/knowledge/web/CampusAgentPreviewController.java)，start；[CampusCreditAgentManager](../../../knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusCreditAgentManager.java)，start/find/cancel |
| 每次政策查询创建独立临时会话，不等于Run拥有可续用Session | CampusAgentPreviewConfiguration.zhkuCampusCreditAgentManager 中的policyGateway，createConversation/sendMessage |
| 默认会话明确是内存实现 | [ZhkuLocalPreviewConfiguration](../../../knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java)，zhkuLocalPreviewConversationStore |
| 已有JDBC实现与revision原子更新，可复用而非重写 | [ConversationStore](../../../knowledge/src/main/java/com/zhku/knowledge/conversation/ConversationStore.java)，create/find/savePending/clearPending/cancel/clearExpired；[JdbcConversationStore](../../../knowledge/src/main/java/com/zhku/knowledge/conversation/JdbcConversationStore.java)，updateActive；[ConversationStoreFactory](../../../knowledge/src/main/java/com/zhku/knowledge/conversation/ConversationStoreFactory.java)，create，尚未配置接线 |
| Session仅保存控制状态，不保存消息 | [ConversationState](../../../knowledge/src/main/java/com/zhku/knowledge/conversation/ConversationState.java)，sessionId/status/pendingIntent/pendingField/expiresAt/revision；[V12](../../../knowledge/src/main/resources/db/migration/V12__create_conversation_session_store.sql) |
| 身份与资源权限尚不存在，UUID不是授权凭据 | [ZhkuLocalPreviewConversationController](../../../knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationController.java)，create/sendMessage/streamMessage/cancel；Run/history入口亦未接受可信Principal |
| 回环是当前部署硬边界 | [LocalPreviewBindingGuard](../../../knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewBindingGuard.java)，构造器；不能简单删掉它开放网络 |
| 3A只保存准入/结局，没有续跑输入与检查点 | [CampusRunArchive](../../../knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunArchive.java)；[JdbcCampusRunArchiveStore](../../../knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/JdbcCampusRunArchiveStore.java)，admit/finalizeRun；[V18](../../../knowledge/src/main/resources/db/migration/V18__create_campus_agent_run_archive.sql) |
| 锁owner是进程执行所有权，不是用户身份 | [CampusRunArchiveOwner](../../../knowledge/src/main/java/com/zhku/knowledge/campusagent/archive/CampusRunArchiveOwner.java)，start/claim/probe；instanceId/epoch不能拿作userId |
| 真实供应商已有单例验收候选，但未在3A执行 | [CampusCreditAgentLiveProviderTest](../../../knowledge/src/test/java/com/zhku/knowledge/campusagent/CampusCreditAgentLiveProviderTest.java)，liveModelSelectsApprovedToolsAndAnswersFromPublishedRule；读取既有审核资料且使用MockMvc，不等于合成资料矩阵或真实监听 |
| 供应商日志可能带Prompt，已有CLI专门防护 | [CourseCreditModelCallCli](../../../knowledge/src/main/java/com/zhku/knowledge/tools/modelcall/CourseCreditModelCallCli.java)，main；[PrivacyBoundary](../../../knowledge/src/main/java/com/zhku/knowledge/privacy/PrivacyBoundary.java)，anonymousTelemetry/configurationEcho |

3A修正后本轮272通过、真实JVM7包含其中；见[验收](../../evidence/milestone-3a/05-验收记录.md)。这只能作为后续基线，不证明本文能力已实现。

## 2. 路线选择与依赖

推荐每次一个阶段，顺序为：

```text
3A收尾
 → 4A 可信身份与资源归属（含本机真实HTTP权限测试）
 → 3B 业务Session持久化与Run关联
 → 3C 提交幂等、持久请求元数据、预算与有限重试
 → 3D1 受限重新执行 → 3D2 指定检查点续跑
 → 5 真实模型受控接入验收
 → 4B 非本机入口、TLS与小范围多人试用验收
```

这是工程优先顺序。真实HTTP监听不是最后才开始：4A即用模型替身监听真实回环端口测鉴权，3B/3D再用外部HTTP客户端验重启，5换真实供应商，4B最后才开放指定非本机入口。

备选一是展示优先：先本机HTTP/真实模型，再权限与持久化，能更早展示但会后改资源契约，不符合本次选择。备选二是一次统一身份/会话/恢复/模型，故障窗口和回滚互相耦合，不推荐。本文保留两个循环，不用架构统一制造改动。

## 3. 全局边界与数据规则

- 新受控模式采用独立profile及API命名空间；原匿名本机模式只可单独回环运行。受控模式禁止同时开放匿名v1、旧conversations/SSE和管理入口作为绕过路径；不要改旧LocalPreviewBindingGuard。
- 初期可信身份推荐预置的本地测试账户，采用Spring Security、密码哈希和服务器登录Session；不自制JWT、不相信X-User-Id或请求body中的角色。只做两个普通用户和一个资料维护角色，不做注册、邮件、密码找回或校园SSO。需要机构身份时再选择OIDC，不能假设已拥有学校身份服务。
- 登录HttpSession与业务Conversation Session是两种对象。登录可在重启后要求重新登录；同一个持久userId重新登录后才能恢复自己的业务Session。客户端Cookie/sessionId都不是资源所有权依据。
- 初期只存身份ID/密码哈希/角色、归属、业务澄清控制状态、必要请求摘要及受控恢复输入。完整聊天消息、模型上下文/推理、Prompt、密码原文、APIKey、工具自由文本不默认落库。恢复输入是新的数据留存范围，单独明确允许字段、TTL与删除；不偷偷扩充3A归档。
- 旧匿名Session/Run保持无主，受控接口禁止读取；不自动归属首个登录用户。用户不能转移资源归属，维护者不默认读取所有个人Run。
- 同库增量迁移，禁止改V1–V18。候选V19/V20等在实施前重新核对编号；若其他工作已占用则重新编号，不覆盖。
- 默认开关关闭、单实例、专用临时库、合成资料/虚构课程。每次DB/模型操作按AGENTS重新检查环境凭据；只有模型阶段获得明确调用额度后才发供应商请求。
- 保持未提交、未推送；各阶段先保存范围哈希与测试基线，再取该阶段真实增量，保留旧证据。

## 4. Milestone 4A：身份与资源权限

目标：登录身份可信；A/B互相不能查、取消、发消息、查看历史或用SSE访问对方资源；维护角色只获得明确资料权限。初期仍回环，不开放非本机试用。

**候选修改：** knowledge/pom.xml；CampusCreditAgentManager、CampusRunArchiveStore/JdbcCampusRunArchiveStore/CampusRunHistoryService（可信用户与按归属查询）；JdbcConversationStore；原web装配的受控模式边界。共享Runtime不加用户字段。

**候选新增：** knowledge内security/TrustedActor.java、PreviewSecurityConfiguration.java、PreviewIdentityRepository.java；web/ControlledCampusAgentController.java、ControlledConversationController.java、ControlledPreviewConfiguration.java；新SQL V19__create_preview_identity_and_ownership.sql；对应Security/Ownership/真实HTTP测试。以上为候选新文件，当前不存在。

**接口与资源约束：** TrustedActor包含服务器确认的稳定UUID userId和角色；由认证层构造，业务body不接受。Run的requesterUserId与ownerInstanceId/epoch分开。Manager增加只用于受控入口的可信调用上下文，归属与ADMITTED在同一准入事务绑定，绑定失败不投递。Session创建绑定稳定用户后才返回；初期Session状态可以仍内存，但归属不能成为用户可伪造的字段。

后续安全API建议前缀`/api/zhku/controlled-preview/v1`，runs/history/conversations语义显式定义；旧九字段成功响应在旧入口保持。安全API未登录401、角色无权403、他人ID与不存在同为404、数据库授权查询失败503且无正文。history列表在SQL按requester过滤后分页，不能先取全量再过滤。取消和会话更新同时检查归属与revision；异步执行显式传可信主体，不依赖工作线程继承SecurityContext的ThreadLocal。

**步骤：**

1. 先固定权限矩阵及全部入口清单（含admin、feedback、telemetry、SSE、管理端口），写A/B交叉访问RED。临时库创建合成账户，密码通过环境注入，数据库只存哈希。
2. 实现认证默认拒绝、有限公开登录/静态资源、CSRF、注销、会话固定攻击防护；受控profile缺配置/有匿名旁路则启动失败。新Security依赖对旧应用自动配置影响必须测试，不能声称“增加依赖不影响默认行为”。
3. 原子绑定Run归属、受限Session创建，按归属SQL读取/更新；验证绑定commit未知不调度、A/B同UUID尝试、分页不泄露数量/正文、撤回仍需领域门禁。
4. 真实回环随机端口启动，外部HTTP客户端分开Cookie罐登录A/B，验证CSRF和401/403/404/503；替身只在src/test专用配置，不能加入生产假模型开关。旧默认回归、真库与网络测试全通过后验收。

**成功标准：** 未授权业务/模型/工具调用均为0；A/B在所有路径隔离；登录注销和资源隔离经真实HTTP验证；匿名旁路关闭；已有校园证据规则未移出knowledge。

## 5. Milestone 3B：业务Session持久化

前提：4A稳定身份与资源归属通过。目标是重启后恢复澄清控制状态，不承诺完整聊天历史或LLM消息记忆。

**候选修改：** ConversationStoreFactory、JdbcConversationStore、ZhkuLocalPreviewConfiguration的受控装配、LocalPreviewConversationFacade、CampusAgentPreviewConfiguration及受控Run DTO。候选新配置ControlledConversationStoreConfiguration与新SQL V20__scope_conversation_session.sql，新增OwnedJdbcConversationStoreContractTest、ConversationHttpRestartTest。

默认旧模式仍内存；受控模式明确选择jdbc，不可在DB异常时静默回退内存。可采用JdbcConversationStore的可信主体构造入口，或已有接口的限定用户适配；详细阶段设计二选一，必须让每个find/save/cancel SQL带userId与revision，不能只Controller查一次后继续全局裸UUID写。

**步骤：**

1. 重跑已有ConversationStoreContractTest/JdbcConversationStoreContractTest并审计所有调用方。字段是sessionId、稳定归属、status、pendingIntent/pendingField、expiresAt、revision；不加原文消息表。
2. 固定过期/取消/并发语义：到期隐藏且不复活；取消清空pending；两请求同revision只有一个更新；不存在ID不能发消息隐式创建。确认来源证据仍在每次回答时重查。
3. 受控Run允许关联本用户业务sessionId，但政策工具原先新建的内部会话不得自动暴露或混成用户会话。先实现创建、重启后追问恢复，再另测Run关联；Session持久化不让旧运行自动继续。
4. 启动JVM1通过HTTP建会话并进入追问，退出后JVM2重新登录同用户完成第二轮；B仍404。真实JDBC并发取消/迟到写、过期、数据库不可用/恢复、状态保留与有限清理均验证。

**成功标准：** 非过期业务会话状态跨PID可读可续问；登录Session不复用旧凭据；用户隔离、revision、取消及过期不变；没有完整对话长期留存。回滚只切回旧单人模式，不能把用户会话降级成匿名可读。

## 6. Milestone 3C：持久提交、幂等与预算

2026-10-07实施校准：以[3C实施计划](2026-10-07-milestone-3c-submission-resource-plan.md)为本批契约，学分键按`(userId,submissionKey)`隔离，不强制关联重修Session；后文sessionId/attempt等是早期候选，不代表本批实施。3C三批及12项门禁已完成，V21/3B装配有限兼容修复后最终55类371项、前端46项及普通网页启动通过；[本轮验收](../../evidence/milestone-3c/2026-10-07/3c-3/acceptance.md)保留失败和未验证项。阶段结束，不启动3D。

前提：4A、3B。这是自动恢复的必要前置，不能只做“给请求加一个key”。

**候选修改：** CampusCreditAgentManager、新受控Controller、JdbcCampusRunArchiveStore的必要边界、CourseCreditChatModelFactory及受控模型配置；候选新增CampusRunSubmissionStore、CampusExecutionBudget、受控提交/预算SQL、SubmitIdempotencyIntegrationTest和ModelCallBudgetTest。不统一通用Agent循环。

**接口：** 提交键按(userId,sessionId,idempotencyKey)隔离，规范化请求摘要匹配才返回原runId；同键不同内容409；跨用户相同键互不影响。摘要不是匿名化证明，同样受访问/保留治理。并发claim在同一事务中，与用户归属/准入绑定；已受理但投递状态未知不能由重复HTTP直接再投递。

**步骤：**

1. 真库RED：相同key并发20次只产生一条逻辑提交/一次正常调度，内容不同409；准入ACK丢失后查询原ID，不自动再执行；A/B同key不同资源。
2. 持久保存最小提交元数据、输入摘要、派发确认、取消标记、attempt关系与计数；停止原因继续用真实执行事实，不能把元数据写入成功当执行成功。
3. 初始建议上限：每用户在途2、实例在途2、队列16、模型决策最多3次/attempt、任务30秒。实际provider请求另计，SDK隐式重试不得绕过额度。默认不重试；仅明确的可重试供应商失败允许最多1次，指数退避且共用原时长/调用预算；commit未知、解析失败和工具副作用不自动重试。
4. 供应商调用前原子预留预算，用量缺失/ACK未知不乐观退还；输出token上限与费用额度由专用profile明确提供。测试空内容、拒答、max_tokens截断、上下文过长、429、超时、预算耗尽/用户取消和I/O资源收敛；不把本地中断当远端停止计费。

**成功标准：** 重复提交与并发条件可核验，未知投递无盲目重放，故障期间模型请求计数/时长有界；这不是exactly-once。真实价格/模型额度需5阶段核对供应商官方资料，本路线不预填费用数字。

## 7. Milestone 3D：受限自动恢复，拆两批

前提：4A/3B/3C、纯工具白名单、持久取消与预算。3A原Run的INTERRUPTED保留，不回写成正在执行。新attempt使用新runId、parentRunId、attemptNo与新owner epoch。

### 3D1：受限重新执行

这是从头的新尝试，必须明确命名，不能宣传为断点续跑。只允许结构化校园学分任务、合成/用户同意保存的虚构课程、政策只读查询及纯计算；未知或有副作用任务保持INTERRUPTED，需人工决定。

**候选新增：** knowledge/campusagent/recovery/CampusRunRecoveryInput.java、JdbcCampusRunRecoveryStore.java、CampusRecoveryCoordinator.java；新SQL保存逻辑任务/attempt/输入；新增RecoveryReplayIntegrationTest、RecoveryCancellationTest及真实进程测试。候选修改Manager受控准入，不改共享Runtime，不创建Planner。

恢复输入白名单是场景代码、规范化courseId/category/credits、userId/sessionId、schemaVersion、expiresAt、取消标记；不保存自由提问、Prompt或工具原始JSON。默认仅合成资料验收；启用真实用户输入留存前独立确认数据范围与删除。建议最多20门、序列化4KiB、保留24小时且不超过Session有效期；值是计划限制，不是当前代码能力。

**步骤：** 定义eligible/不eligible的具体任务 → 真库claim与attempt唯一约束RED → 获新owner、重新检查身份/取消/Session/输入版本/预算后创建新attempt → 实际子JVM杀进程并自动发起最多1个恢复attempt → 再次崩溃、用户取消、失锁、过期/撤回等不再循环恢复。不能只读取3A archive就重跑，它没有输入。

### 3D2：指定检查点续跑

仅支持一个真正的续跑窗口：**已取得合格政策证据，但尚未完成学分纯计算**。其他窗口先保持手动或3D1策略。候选checkpoint字段：stage=POLICY_VERIFIED、原sourceId/version/scope/locator、输入摘要、revision、ownerEpoch、时间；不能把完整Runtime事件流当检查点。

**候选修改：** CampusCreditAgentService在合格政策边界写领域检查点，新增专用resume入口；复用原纯计算与答案组成规则，不改decide主循环、不合并通用循环。若必须把循环改成通用状态机或保存完整模型上下文才可续跑，停止该批并报告，不能扩大到复杂编排。

**步骤：** 检查点提交前/后分别杀JVM → 新owner先检查取消、用户启用状态、Session/输入TTL，再按原ID/version重核证据 → 新attempt接续calculate/final，不再次执行已确认政策查询的模型选择 → 所有终态、checkpoint ACK未知、版本不兼容/证据撤回返回明确不可恢复或转人工，不强行计算。

**成功标准：** 3D1证明最多一个新尝试；3D2另外证明从指定检查点继续。报告分开统计模型/工具调用次数、PID/epoch/父子Run和重复纯计算；不承诺任意工具、任意断点、exactly-once或全Agent续跑。供应商续跑另需5验证，不能把替身恢复等同于真实模型恢复。

## 8. Milestone 5：真实模型受控接入与验收

目标：验证已经存在的模型工厂及校园工具选择在真实供应商下的行为。先本机，不引入新RAG检索能力，也不直接运行旧LiveProviderTest当作新阶段的完整合成验收。

**候选修改：** CourseCreditChatModelFactory、RagProviderProfile/Resolver及受控校园装配、供应商调用保护和日志配置；必要时单独拆出校园model profile以避免启用整条RAG，仅复用现有库，不升级框架。候选新增SyntheticCampusLiveModelAcceptanceTest、CampusLiveModelAcceptanceReport与显式受控live runner。

**步骤：**

1. 先用本机供应商协议替身验证端点路径（避免重复/v1）、连接/读取timeout、maxTokens、SDK重试、空响应/拒答/截断/超大工具参数、unknown tool和日志哨兵不泄露。现有profile.timeout不等于工厂已正确配置HTTP超时，实施前读取项目1.0.9实际源码核对。
2. 明确指定provider/model、可外发合成问题、次数/token/费用上限和日志策略，确认已有环境凭据，每次独立检查；默认不开启供应商。预算不足/未批准或未知schema则fail closed，不发请求。
3. 真库只发布合成政策+虚构课程；不要复用旧测试中实际手册资料外发。保持internalToolExecutionEnabled(false)，工具白名单/调用顺序/资料可见性和转人工仍由服务端执行。
4. 两批证据分开：真实ChatModel+真实Manager/工具/临时库；随后同一受控profile真实回环HTTP端到端。现有MockMvc结果不能计第二批。建议固定10个场景重复2轮，正常、缺输入、证据不足/撤回、注入、越权工具、超限都列期望；记录成功率、模型/tool次数、usage（缺失标缺失）、延迟与固定失败分类，不只保存成功样例。

**验收门槛建议：** 正常任务目标至少90%正确完成；所有危险工具/越权/不可见证据泄露为0，所有任务受预算收敛。未达门槛只交付失败分析与受控模型原型，不能宣传稳定模型服务。供应商随机性不作为普通回归测试通过条件，普通CI保持无密钥替身。

## 9. Milestone 4B：非本机真实HTTP与有限多人试用

前提：4A完整权限矩阵、3B持久会话、3C预算；启用恢复/真实模型则分别要求3D/5验收。非本机部署独立设计，不删除原回环guard。未完成本阶段之前禁止代理/隧道绕开本机限制。

**候选新增/修改：** 独立ControlledPreviewApplication与受控profile配置、TLS/可信代理部署说明、受控启动器；候选NetworkHttpAcceptanceTest、TwoUserHttpRestartAcceptanceTest。不部署通用agent应用、不开放管理入口、不对公网发布。

**步骤：**

1. 从实际应用启动随机回环端口，以JDK HttpClient外部请求；再以独立JVM/实际监听端口验证登录、提交、查询、取消、历史、追问、重启及服务退出。所有网络调用设置deadline，退出后验证端口/子进程/临时容器收敛。
2. 选定部署主机、受限网络、TLS终止点与可信代理集合，明确cookie Secure/HttpOnly/SameSite、CSRF/CORS规则；不能只改server.address=0.0.0.0，也不能信任任意Forwarded头。
3. 从第二台主机或已明确的独立网络客户端实际请求：A/B独立登录、互查互取消404、登录过期/注销401、来源撤回隐藏、越权SSE关闭、并发队列/重启恢复有界、管理端口不可访问。模型可先替身网络验收再真实供应商；报告分开。
4. 只向明确的小范围测试用户开放，告知输入/保留边界，保存撤回与反馈记录。真实学生效果仍需后续用户研究，不能因HTTP200宣称产品有效。

**成功标准：** 有实际网络地址、客户端来源、状态码、PID、端口及资源清理证据；权限与持久化在同一网络路径有效；默认匿名入口不能暴露。没有第二客户端/网络环境时本阶段标未验证，不用MockMvc或localhost冒充非本机。

## 10. 测试、故障窗口与阶段完成门禁

| 风险输入/窗口 | 必须在所属阶段验证 |
| --- | --- |
| 身份来自伪造header/body、A/B同资源ID、旁路SSE/admin | 4A真实HTTP + 真库授权条件；未授权模型/工具0 |
| 登录重启、Session取消后旧revision、跨用户迟到写 | 3B双JVM追问与真库并发，所有权稳定且取消不复活 |
| 准入/归属commit ACK丢失、重复提交内容不同、派发未知 | 4A/3C原子准入与幂等，不盲目投递或重放 |
| checkpoint commit前后退出、旧owner迟到、取消已保存但未派发 | 3D真实PID/epoch/实际SQL行与动作计数；不以context重建代替 |
| 供应商默认重试、max_tokens、空响应、提示注入、日志带Prompt | 3C协议替身及5真实模型；预算守卫服务端强制 |
| 并发资料撤回、用户停用、恢复输入过期/新schema | 3D/5每次重新授权，不用缓存成功当许可 |
| TLS/代理头/Cookie/CSRF/管理端口、服务退出残留 | 4A回环网络与4B非本机外部客户端，两者证据分开 |

每阶段必须保存问题→设计→增量diff/hash→本轮RED/GREEN与回归→完成/未验证验收。可复用当前命令作为基线：

```powershell
& .\docs\evidence\milestone-2\verify.ps1
& .\knowledge\scripts\verify-campus-run-archive-temp-db.ps1 -Port 55433 -Tests 'CampusRunArchiveSchemaIntegrationTest,CampusRunArchiveOwnerIntegrationTest,CampusRunHistoryVisibilityIntegrationTest,CampusRunArchiveStoreIntegrationTest,CampusRunArchiveBudgetTest,CampusRunArchiveConfigurationIntegrationTest,CampusRunArchiveProcessRestartTest'
& .\scripts\Import-ZhkuCredentials.ps1 -UseDatabase
& .\knowledge\scripts\verify-campus-temp-db.ps1 -Port 55432
```

新候选测试类不存在，本文不提供伪称可运行的未来-Dtest命令；实施详细计划时先创建测试，再记录实际命令和结果。新迁移出现后，原归档临时库测试中最高版本断言要在对应阶段显式更新，不能修改3A历史报告。每个新runner须显式白名单，禁止借普通脚本误跑供应商。

## 11. 回滚、停止与工作量

- 4A回滚：关闭受控入口，保留用户/归属表；回到单人本机模式，禁止匿名访问已有私有资源。不回滚数据为“人人可读”。
- 3B回滚：停止会话写入，保留表；内存预览仅新匿名会话，既有私有Session不可挂入匿名入口。
- 3C回滚：停止准入，保留提交键/预算/未知派发记录，不能将未确认任务再投递。
- 3D回滚：停恢复扫描/claim并有界关闭，保留检查点/attempt；旧Run不覆写，遗留新attempt下次中断对账。关闭自动恢复不删除证据。
- 5回滚：关供应商开关、禁外发，测试替身只在测试，不在生产静默替换供应商。保留费用/用量的脱敏记录。
- 4B回滚：关闭网络入口及代理路由，验证端口退出，再回到回环；既有授权数据保留。所有阶段不改Flyway历史、不DROP、不整体reset/clean。

停止条件：必须合并两个循环、扩张为任意工具自动续跑、绕过领域证据、无法定位commit未知、无法保证取消先于恢复、需要存原文/秘密才能实现、权限旁路仍开放、真实模型预算未确认或资源不收敛。报告差距，不能为填满路线擅自扩大范围。

规划工作量（不是交付承诺）：4A约18–30小时、3B10–18、3C12–20、3D两批24–40、5约12–20、4B6–12；合计约82–140小时，不含机构SSO、部署运维、等待供应商/网络和真实用户研究。先评审4A最小任务清单，不同时展开其余阶段。

## 12. 文档依据、自审与下一步

本轮按AGENTS用Context7核对官方资料：[Security请求授权](https://github.com/spring-projects/spring-security/blob/6.4.4/docs/modules/ROOT/pages/servlet/authorization/authorize-http-requests.adoc)、[CSRF](https://github.com/spring-projects/spring-security/blob/6.4.4/docs/modules/ROOT/pages/servlet/exploits/csrf.adoc)、[Session管理](https://github.com/spring-projects/spring-security/blob/6.4.4/docs/modules/ROOT/pages/servlet/authentication/session-management.adoc)、[Boot3.4真实Web测试](https://docs.spring.io/spring-boot/3.4/reference/testing/spring-boot-applications.html)、[Spring AI手动工具执行](https://github.com/spring-projects/spring-ai/blob/v1.0.3/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc)。AI资料为可取的1.0.3参考，项目实际锁1.0.9；不复制未核对的API，不把main文档当项目版本证明。

自审：五项目标均对应阶段；真实回环HTTP与非本机分开，Session状态与聊天历史分开，3D1重新执行与3D2断点续跑分开。所有现有类/方法来自本轮源码读取；新文件/接口都是候选。依赖/存储/身份策略变化只在后续阶段提出，本轮没有实现，没有调用真实模型或开放监听。

推荐下一步只做 **Milestone 4A 只读详细设计与测试清单**：确定账号来源、完整入口矩阵、事务归属与受控profile接线，完成可审阅设计后再实施。保持此前当前会话顺序实施偏好，详细计划不自动切换为逐任务多Agent。
