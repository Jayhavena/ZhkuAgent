# 受控校园网页展示与真实模型有限验收开发计划

**最终状态（2026-10-06）：用户确认P4人工功能验收通过，q退出及资源/租约已独立核对，当前版本收口。** [最终验收](../../evidence/controlled-web-demo/2026-10-06/p4/final-closeout/manual-acceptance.md)是执行结果来源；下方原计划细项及早期进展保留为历史，不作为继续实施或重新授权指令。未逐项验证的浏览器质量项目仍保留，不自动启动新阶段。

> **For agentic workers:** 使用 superpowers:executing-plans 在本会话逐批执行。用户另交一个会话负责独立视觉设计/原型，按下方白名单分工；不自动派生Agent或恢复旧ABC协作。步骤用复选框跟踪。

**Goal:** 在一个本机Nginx地址完成可信登录、本人学分任务与历史、重修澄清补答，并以当前真实模型与可控测试分别形成可核验展示证据。

**Architecture:** 复用现有Vue、Nginx代理和knowledge受控JSON接口。校园与通用Agent循环继续独立；Runtime不加入身份/Session。账号及合成资料只由独立本机准备工具写入专用库，浏览器不获得治理能力。

**Tech Stack:** 沿用仓库现有Vue3、Vite4、Java21、Boot3.4.4、Spring AI1.0.9、PostgreSQL和Nginx；不升级依赖，不引入新的状态管理、UI或评测框架。

**Spec:** 本计划承接用户本轮明确的求职展示目标与前序第一阶段设计，结合 [Nginx使用指南](../../manual/nginx-local-preview.md)、[4A＋3B事实核验](../../evidence/milestone-4a/2026-10-05/A/20-4a-3b-integration-review.md)。本文件是完整实施草案；Nginx小批已独立获授权并完成，其余批次尚未开始，不把计划当验收。

2026-10-06补充用户要求：页面统一美化、密码默认隐藏且可切换显示。推荐方案及交互验收见 [校园网页视觉设计](../specs/2026-10-06-controlled-campus-web-ui-design.md)，作为P2/P3实现和P4浏览器验收依据；具体视觉效果先提供首屏预览，不新增业务批次。

用户拟将视觉布局交给其他Agent：[设计交接包](../specs/2026-10-06-frontend-design-agent-handoff.md)。对方只写`docs/design/controlled-campus-web/`独立原型，主会话拥有API/身份/后端与集成文件；设计完成后再明确生产Vue布局writer，当前不允许双方并行修改页面。交接准备不等于P1开发授权或设计Agent已开工。

## 当前状态与版本结束点

建议版本名：**受控校园网页演示版**。尚无正式Git标签或发布版本号；Maven仍为0.0.1-SNAPSHOT。

| 批次 | 状态 | 交付 |
| --- | --- | --- |
| P0 Nginx基础接线 | 已完成，历史证据 | 容器到Windows回环连通、静态托管、受控前缀代理、旧目标清理；25项代理＋5项启动器检查 |
| P1 专用环境、账号与真实模型有限验证 | 已收口 | 12/12真实模型、171项相关回归有独立证据；见[P1验收](../../evidence/controlled-web-demo/2026-10-06/p1-closeout/acceptance.md)，人工启动确认另见最终P4 |
| P2 登录与学分网页闭环 | 实施完成，人工功能验收由P4确认 | [P2记录](../../evidence/controlled-web-demo/2026-10-06/p2/acceptance.md)，登录、提交、取消、出处、历史；17项是该批历史前端结果，夹具不是可信认证或真实模型 |
| P3 重修恢复补充展示 | 实施与独立验证完成，人工确认由P4记录 | [P3记录](../../evidence/controlled-web-demo/2026-10-06/p3/acceptance.md)：该批32项前端与5项HTTP/不同PID，不重复累计 |
| P4 统一验收与展示材料 | 用户确认通过，版本收口 | [最终记录](../../evidence/controlled-web-demo/2026-10-06/p4/final-closeout/manual-acceptance.md)：227相关、11校园真库、最新36前端分别引用；人工报告与实际q退出区分，lease已释放 |

P1～P4是一版四批。范围获授权后连续完成，不逐文件索要确认。达到P4结束标准即收口，不自动开启新里程碑。

## Global Constraints

- 保留全部既有dirty；先保存HEAD、状态、目标文件before与哈希，不reset/clean、不覆盖其他人的改动，不提交或推送。
- 只用合成账号、资料与课程和专用临时库，拒绝原54329演示库；不新增迁移、不改Flyway历史。
- 每次DB/供应商操作重新检查进程→用户→系统环境，按AGENTS继承；密钥、密码、Cookie、CSRF、完整Prompt不得进入源码、命令参数、日志或证据。
- 回环监听和默认拒绝保留；维护者不继承USER；无注册、找回密码、JWT、SSO或治理页面。
- 两循环、Runtime、政策证据规则、课程匹配、工具白名单和转人工规则原则上不改。遇到新增测试复现生产缺陷，先列失败与最小范围，重大扩展暂停报告。
- 同一仓库不并行Maven；执行时保存源码并暂停writer，登记lease、选类和源码hash，结束保存新XML/日志和实际资源退出后释放。
- 按当前安装版本查询Context7后使用框架API，不为对齐最新文档升级项目。
- 原终端替身演示保留；无生产假模型开关。真实模型失败不阻碍独立网页实现，但未完成验收不能宣称本版真实模型闭环通过。

## Review Focus

1. 503带accepted=true/runId必须保留已受理事实；纯网络断开不能被解释为未提交，更不能自动POST重试。
2. 退出/切换账号后迟到的查询响应不得在新账号页面出现；清理缓存、请求和轮询。
3. 取消请求成功不等于terminated；前端轮询到期不等于后端RUN_TIMEOUT。
4. 重启后在线404和本人历史可读可能同时成立；404也可能为他人/不存在，不能暴露归属推断。
5. Session只有控制状态持久化；刷新或重新登录需保留路由中的ID提示，但不得把它当许可或宣称恢复完整聊天。

## 固定接口与页面行为

所有浏览器请求使用同源前缀 `/api/zhku/controlled-preview/v1`。不使用旧通用API适配器，不删/api路径，不从body/header构造身份。

| API | 页面行为 |
| --- | --- |
| GET /auth/csrf | 登录前获取；登录/退出后重新获取；token仅在内存 |
| POST /auth/login | username/password与CSRF按现有表单协议；200后GET /auth/me |
| GET /auth/me、POST /auth/logout | 身份恢复与退出；401清正文、缓存和请求，跳登录 |
| POST /runs | 发送现有question/courses；202保存runId；503 accepted=true同样保存ID并查，不重提 |
| GET /runs/{id}、POST /runs/{id}/cancel | 保持ControlledRunReply的run与resultVisibility；取消后继续查实际终结 |
| GET /history?limit=20&cursor=...、GET /history/{id} | 使用现有分页与详情，不把在线Snapshot当历史DTO |
| POST /conversations、GET /conversations/{id} | 创建/读取控制状态；ID写路由，权限始终由后端决定 |
| POST /conversations/{id}/messages、/cancel | 显式expectedRevision；冲突后读取状态，用户确认重发，不自动覆盖 |

结果分别显示执行状态、归档确认/历史状态、正文许可。SUCCEEDED且HIDDEN展示“执行已完成，当前资料不允许展示”；UNCONFIRMED不展示缓存正文。steps只展示现有允许字段，不展示自由细节或伪造工具实时事件。

轮询固定为1秒一次、单页面最多60秒；离页/退出取消。60秒到期显示“暂未确认，可继续查询”，不制造Run超时。按钮防连点只改善页面交互，不宣称实现后端幂等。

## Task P1：可登录专用演示环境与真实模型验证

**新增候选文件（只在实施时创建）：**
- `knowledge/src/main/java/com/zhku/knowledge/controlled/demo/ControlledWebDemoSetupCli.java`：显式本机准备入口，不是Controller或自动启动Bean。
- `knowledge/src/main/java/com/zhku/knowledge/controlled/demo/ControlledWebDemoHost.java`：本机STOP/EOF关闭已有产品Context，无新HTTP接口或替身装配。
- `knowledge/scripts/start-controlled-web-demo.ps1`与`.cmd`：专用库、隐藏密码输入、真实受控jar、Nginx接线、实际退出与资源清理。
- `knowledge/src/test/java/com/zhku/knowledge/controlled/demo/ControlledWebDemoSetupTest.java`：真库初始化约束。
- `knowledge/src/test/java/com/zhku/knowledge/controlled/demo/ControlledLiveModelAcceptance.java`：显式手动启用的有限供应商验收，不进入默认Maven测试。
- `knowledge/src/test/java/com/zhku/knowledge/controlled/demo/ControlledModelSerialCallContractTest.java`：本轮真实失败触发的SDK串行选项协议测试，替身不证明模型收益。
- 本轮证据根 `docs/evidence/controlled-web-demo/2026-10-06/`：before、命令、hash、独立结果和验收。

**复用/只读：** ControlledPreviewApplication、PreviewIdentityRepository、ControlledCampusAgentConfiguration、CourseCreditChatModelFactory.open、归档/Conversation装配与V19/V20。

2026-10-06后端实施校准：用户授权除前端之外的内容。[本轮记录](../../evidence/controlled-web-demo/2026-10-06/README.md)保存全部独立结果。CourseCreditChatModelFactory仅经本轮TOOL_CALL_LIMIT失败与协议RED扩展一处默认`parallelToolCalls(false)`；open资源契约不变。此句为首批状态。后续用户授权收口P1，仅追加合格政策后的已执行工具反馈；新固定12/12、171项相关回归通过，完整人工启动器交互待确认。后续用户将人工交互移至P4；P2生产Vue已接线并通过有限夹具验证，P3代码与独立复验已完成，真实网页联测待P4；原型整改由设计会话进行。

- [ ] 保存目标文件/HEAD/状态与源码hash，记录测试lease；先跑既有相关基线。前序591去重及Nginx25/5只算历史，不能冒充本轮结果。
- [ ] 准备独立临时库并按现有迁移到V20，显示安全的数据库目标和本轮资源ID；禁止创建/修改54329。服务重启保留本次库，整个演示结束后清理本次库。
- [ ] 初始化user-a、user-b、maintainer。首次使用随机UUID，后续对同一库复用ID；密码由本机隐藏输入，经子进程短期环境传递，只保存BCrypt10摘要，操作后清理。账号名/角色列表固定，库内已有不符合预期的同名账号则拒绝，不重置、不认领。
- [ ] 初始化仅有本轮合成出处的学分/重修资料；重跑不覆盖既有来源。撤回/恢复只作本机准备动作，不新增维护者治理API。
- [ ] 在限定真库中验证首次初始化、重复初始化ID稳定、非专用目标拒绝、账号冲突拒绝、密码/hash不输出、失败事务不留下半套数据。
- [ ] 从真实受控产品jar启动，不注册test ChatModel；供应商凭据只来自环境。启动后实际HTTP登录A/B/M，核对匿名GET401、USER许可和维护者拒绝；Nginx地址下校验完整路径和Cookie。
- [ ] 验证关闭/重启：先关Nginx展示入口，再对服务有界排空与关闭，检查实际PID/监听/连接退出，再清理本次临时库。保留本次库的重启不重置账号/资料；未实际退出则保留失败证据，停止后续供应商调用，不把强杀当优雅关闭。

**真实模型样本和先验限制：**

固定6例×2次=12次任务，分别为正常1.5+2、第二组正常课程、缺输入、非法课程输入、撤回合成政策、提示跳过政策或调用未批准工具。问题与独立预期在第一笔真实请求前存档，不按结果换题。

- 正常4次要求合法查询→计算、总数正确、同一合成来源、本人确认归档与结果可读。
- 缺输入/非法输入不返回虚构计算，非法输入按既有守卫不调用模型或计算工具。
- 撤回政策不计算、无答案正文；提示攻击允许被忽略后合规完成或安全拒绝，但绝不执行未批准工具/绕过证据。
- 保留现有maxSteps=3、Run30秒、传输零自动重试。12任务最多36次模型逻辑调用，样本批总时长15分钟；连续2次供应商不可用或资源不收敛则停止，未执行样本记NOT_RUN，固定分母12不变。
- 每例记录标签、模型名、调用次数、耗时、运行/停止/归档/可见性、安全的出处标识和供应商可取得的用量。不记录完整Prompt/凭据。费用无法可靠取得时标未知；次数/时长上限不等于货币硬上限，不新增复杂费用平台。
- 不自动重放、不盲目重新POST。失败作为本轮结果，修复后另开明确复验记录，不覆盖首次结果。
- HTTP只展示现有允许的Step轨迹，不能把Step条数当模型请求数。实际调用/Token只有传输侧或供应商提供可靠计数时才记录；否则标不可获取并分别列代码上限，不增加事件查询API或为计数泄露自由详情。
- 新演示进程就绪门槛固定20秒，正常退出10秒；超限后最多5秒强制回收仅作失败诊断，不能转为通过。并发/取消使用CountDownLatch等明确进入屏障，不靠睡眠碰运气，不按结果扩大门槛。

P1验收：真实产品入口、真实登录、专用真库和真实模型正常闭环有证据；负例无越权或非法执行；退出实际收敛。供应商样本失败时P1不标完成，允许先推进不依赖供应商的P2/P3。

## Task P2：登录与学分网页闭环

**文件候选：**
- 新增 `agent/frontend/src/api/controlled.js`：请求与CSRF、错误分类；不复用通用SSE客户端。
- 新增 `agent/frontend/src/state/controlled-session.js`：内存身份、请求generation和退出清理；不引入状态框架。
- 新增 `agent/frontend/src/views/ControlledLogin.vue`、`CampusRun.vue`、`CampusHistory.vue`。
- 新增 `agent/frontend/src/components/ControlledLayout.vue`、`PasswordInput.vue`和`src/styles/controlled.css`：统一布局、基础视觉和可切换密码；不新增UI依赖。
- 修改 `agent/frontend/src/router/index.js`、`views/Home.vue`：明确校园入口；旧通用演示保留说明，Nginx不放开其API。
- 新增 `agent/frontend/tests/controlled-api.test.mjs`、`controlled-session.test.mjs`：使用Node内建测试和有限HTTP夹具，无新依赖。
- `vite.config.js`仅开发确有需要时增加同一受控前缀的回环代理；默认展示走已验证Nginx，不追加第二套部署。

- [ ] 先写行为测试：登录前后重新取CSRF，401清身份，403保留精确分类，503 accepted=true保留runId，网络未知确认不自动重提，退出后迟到响应被丢弃。
- [ ] 按视觉设计建立统一浅色校园工作台；先预览登录和学分主屏，集中确认一次视觉方向，再复用到历史与重修。主界面突出课程、结果和出处，技术诊断折叠，不伪造统计或进度。
- [ ] 实现登录表单/当前账号/退出。密码默认隐藏，用眼睛按钮切换且不修改值、不触发表单提交；支持键盘与可访问名称。密码提交后清空并恢复隐藏，不存浏览器Storage；返回页面路径只允许本站校园路由，禁止外部跳转。
- [ ] 学分页提供合成课程表单与固定Golden填充按钮；沿用question/courses，不把“虚构课程”表述伪装成真实教务数据。
- [ ] 展示已受理、运行、取消请求、终态和清理完成；只用实际状态，不提供伪百分比、不把刷新误当重新提交。
- [ ] 成功展示总学分、适用范围、出处与人工核实边界。隐藏/未确认不读缓存；页面解码及组件渲染使用文本，不渲染模型HTML。
- [ ] 历史页使用现有SQL过滤后的分页，在线404时提供“尝试查询本人历史”；历史仍无记录则明确不存在或不可访问，不判定他人归属。
- [ ] 增补有限夹具测试：身份切换时未完成GET迟到、取消响应但terminated=false、SUCCEEDED/HIDDEN、503已受理、在线404/历史存在、缺失历史、JSON异常和502。
- [ ] 执行内建前端测试与 `npm run build`；启动最终Nginx，用浏览器验证登录表单、刷新、路径与错误显示。前端fixture不算真实账号/模型验收。

P2验收：用户从Nginx页面手动登录，提交合成学分、看到出处和本人历史；B看不到A，维护者不能执行USER动作；断网/503无自动POST重试；离页、退出与轮询期限清理完成。

## Task P3：重修澄清与重启恢复补充展示

**文件候选：**
- 新增 `agent/frontend/src/views/CampusConversation.vue`、`tests/controlled-conversation.test.mjs`。
- 修改本批 `api/controlled.js`、`state/controlled-session.js`和router，加入受控四个Conversation方法。
- 复用ControlledConversationFacade/Controller、JdbcConversationStore、ConversationHttpRestartTest；不改领域服务。

- [ ] 创建会话后进入含sessionId的校园路由；刷新重新GET状态，不把ID当许可。退出立即清正文；重新登录可以返回同一路由再由服务授权。
- [ ] 问“选修课挂科了可以重修吗？”显示追问；读取最新revision再提交“专业选修课”，显示现有重修答案和出处。
- [ ] 409重新读取状态并提示确认，不自动重发；410显示过期并允许显式新建；取消后不能复活；倒计时仅辅助显示，不修改服务端expiresAt。
- [ ] 仅内存显示本次问答；刷新/重启恢复的是澄清状态，不伪造或存储完整聊天历史。收到答复后按现有接口读取新的revision。
- [ ] 前端有限测试覆盖同ID重新读取、过期/取消/冲突、来源撤回、退出清内容和身份变化迟到响应。
- [ ] 专用库实际重启服务，记录不同PID、旧Cookie401、同userId重新登录、原pending/revision/expiry不变、补答后revision推进。浏览器保持路由提示而非维持登录。

P3验收：有效期内重启后补答成功，控制状态与出处正确；不宣称Run续跑、完整聊天恢复或Session绑定Run。

## Task P4：最终复验与求职展示收口

**修改/新增候选：** 根README、knowledge/README、agent/frontend/README；`docs/manual/controlled-campus-web-demo.md`；本轮证据中的baseline、live-results、regression、browser-checklist、acceptance、limitations。不新增独立宣传平台或视频制作依赖。

- [ ] 新增/修改测试先定向执行；复用既有Runtime、校园Service、来源、归档、身份、HTTP和Session组。只有失败/新改动才拓展测试，不反复跑整仓。
- [ ] 相关组包括AgentRuntimeLifecycleContractTest、SharedRuntimeContractTest、CampusCreditAgentServiceTest、CampusRunAccessServiceTest、ControlledPreviewConfigurationTest、LegacyPreviewSecurityCompatibilityTest、ControlledCampusHttpAcceptanceTest、ControlledCampusIdentityProcessTest、ControlledConversationFacadeTest、ControlledConversationControllerTest、ConversationHttpRestartTest，以及已有归档、Golden/临时库组。
- [ ] 使用既有专用临时库runner；3B选类从最终交付command.json取实际名单，不手抄历史91。4A核验指定ExpectedMigration20。旧runner写入历史根中的新phase时保留原记录，本阶段证据链接新结果；真实供应商只走独立显式runner，不能放进禁止供应商的旧脚本。
- [ ] 真实浏览器通过Nginx验收A/B隔离；使用独立浏览器配置或普通＋隐私窗口，不将两个普通标签当两套Cookie。
- [ ] 验收1440/768/360px与200%缩放、可见焦点、空态/错误/加载一致性；密码默认隐藏、反复切换值不变、点击不提交、键盘可用、提交/退出清空且重置隐藏。视觉验收与真实认证分别记录。
- [ ] 用可控阻塞复现运行中取消、超时与实际收敛；受控合成夹具放src/test，不用生产假模型开关。来源撤回与身份失效由专用准备动作复现，用户页面无治理权限。
- [ ] 故障检查正文为空/无他人信息，并核对模型/工具调用0或原计数不变；不能只有HTTP状态断言。503已受理、未知确认与重启在线/历史衔接分别验证。
- [ ] 打包Java模块并构建Vue，跑最终Nginx路径检查。发布入口是本机Vue构建＋固定Nginx镜像Compose；原Dockerfile完整多阶段构建不是本版默认路径，不混称已验证。
- [ ] 人工记录三条短演示：A学分成功+B隔离；取消/来源隐藏；重修追问→不同PID重启→重新登录补答。页面证据无密码、Cookie、Key或真实个人资料。
- [ ] 整理一张链路图、一份约5～8分钟演示顺序、样本耗时表、关键设计取舍与局限。解释模型只能提出工具调用；请求取消不等于释放；执行、归档、可见性分开。
- [ ] 最终检查diff、保护文件hash、文档链接和资源退出，释放lease；标明历史/本轮/替身/真实模型/实际浏览器各证据类别。未提交、未推送，不开启自动下一阶段。

## 最终测试矩阵

| 场景 | 证明方式 | 必须观察 |
| --- | --- | --- |
| 合成账号初始化 | 专用真库 | 同库ID稳定、摘要保存、冲突拒绝、半套事务不保留 |
| 登录/CSRF/退出 | 实际Java HTTP＋浏览器/Nginx | Cookie轮换、写请求校验、401/403准确、退出清内容 |
| A/B/维护者 | 独立Cookie | 全Run/历史/Session隔离，无他人正文 |
| 正常学分 | 真实模型固定样本 | 合法两次决策、数值、出处、确认归档与本人查询 |
| 模型偏离/非法课程 | 替身精确复现＋真实攻击样本 | 未批准工具不执行，输入不改写，错误正确分类 |
| 取消/超时 | 固定阻塞屏障 | 请求与终结分开、不同停止原因、实际线程/PID收敛 |
| 来源撤回 | 专用合成资料准备 | 在线/历史隐藏，澄清不返回旧证据 |
| 503已受理/断网未知 | 可控响应窗口＋前端测试 | 保留runId，不自动重新POST，不假称幂等 |
| 服务重启 | 实际不同PID＋浏览器 | 旧Cookie无效、账号稳定、Run查历史、澄清TTL不延长 |
| 409/410/取消Session | 真库＋页面 | 显式revision，过期不续命，取消不复活 |
| Nginx/启动清理 | 真实容器 | 回环发布、旧路径拒绝、完整URI、502不回退、容器网络退出 |

## 不做与后续版本

本版不做：后端提交幂等、自动归档修复、Run在线数据库透明回退、新政策抽象、不可变证据、模型调度替换、多宿主/多机、非本机发布、自动续跑、完整聊天历史、注册/SSO/治理。

这些候选留在原路线，展示不通过时不自动把它们加进来。防连点不算幂等，历史可查询不算活动恢复，样本成功不算真实学生效果或生产稳定性。

## 回退、停止条件与完成标准

每批保存新增名单与before/diff。回退只撤本批已知改动、停止本批容器，不清理仓库、不修改旧迁移；不把私有owner改NULL，不让旧二进制读取受控库。关闭新展示入口不重新启用旧外部代理。

重大停止条件：需要开放非回环/匿名/治理才能通过；需改两循环/Runtime身份；账号归属被后补或泄露；模型执行非法工具；实际资源不退出；需要真实学生或非合成资料；必须加入未授权新依赖/迁移。报告实际文件与失败，其他独立范围可继续。

版本完成必须同时具备：

- [ ] 单一本机Nginx地址可登录并完成学分、取消、结果出处、本人历史。
- [ ] 页面采用统一校园工作台视觉，在桌面和窄屏可用；密码显隐、键盘操作、自动填充和清理行为符合设计，未新增UI框架。
- [ ] 固定真实模型样本有完整记录，正常4次通过，安全负例不非法执行；未执行或正常失败不能宣称真实模型验收完成。
- [ ] A/B隔离、资料撤回、身份失效、取消/超时与已受理读取失败页面正确；源规则保持。
- [ ] 重修控制状态跨PID重新登录后继续补答，TTL/revision/取消语义准确。
- [ ] 本轮相关回归、构建、实际浏览器检查与退出通过，证据区分明确。
- [ ] 文档可复现，限制和未验证项列明，lease释放，未提交未推送。

本轮计划编写没有运行测试、DB或供应商，也没有新增产品代码。P0仅引用前一轮证据。下一步是授权后的P1，从相关基线和专用演示准备开始；不重复整仓设计审查。



