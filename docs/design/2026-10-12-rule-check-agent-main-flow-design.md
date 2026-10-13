# ZhkuAgent：校园教务规则查证 Agent 主链路与分层技术方案

日期：2026-10-12。阶段：**设计与规划，未实施**。本文件为后续实施的主链路设计提案；项目定位沿用已确认约定，具体接口、预算和模块拆分供本次设计评审。没有修改产品代码、配置、依赖、审核材料或旧冻结记录，没有执行暂存、提交、标签、推送、数据库或供应商操作。

## 1. 项目定位、目标与本方案的约束

**项目定位：面向学生的校园教务规则查证 Agent，以 RAG 获取证据，在权限、资料与执行预算内动态推进查证任务，降低查阅、理解与条件判断成本。**

首期限定为本科对象、2025.9.2版本、目前已审核的《选修课管理办法》20条及允许展示的解释，受控本机试点。它不能替代个人资格认定或教务办理，不能承诺现行有效性，不能把整个手册或全部校园政策描述成已经覆盖。

最小能力闭环：自然提问 → 根据需求选择查证动作 → 检索/读取合格证据 → 必要时澄清 → 更新任务上下文 → 解释已知内容、条件与缺口 → 关联原文 → 有理由地结束。

核心优化目标：
- 能处理未预置问题和不同表达，而不是扩大固定题库。
- 根据工具反馈决定继续、追问或结束，而不是强制每题执行相同步骤。
- 有依据时完成所问任务，减少取到正确原文却拒答或答非所问。
- 缺证、歧义、处理失败和回放未覆盖各自可解释；独立答案与缺口可并存。
- 每个主张都对应相关原文，学生不用自己从候选片段中拼答案。
- 每个阶段可验收、可恢复、可定位到 Git 提交与资产版本。

**对前期方案的明确调整：** 之前的“固定意图解析一次 → 固定证据规划一次”属于工作流。本方案改为有界动态动作循环。旧文档保留为历史分析，不能继续将其中“两次固定调用/12秒”的建议当成本方案的执行合同。没有因此扩大业务、资料或工具权限，也不重新设计整个前端。

## 2. 当前工程结构与职责拆解

### 2.1 已核查的事实

- 根项目是 Java21、Spring Boot3.4.4 的 Maven 多模块项目：[pom.xml:4](D:/alldesk/Projects/ZhkuAgent/pom.xml:4)。
- 前端位于 agent/frontend，采用 Vue3/Vue Router/Vite；路径名称不表示它必须调用旧通用 Agent：[package.json:10](D:/alldesk/Projects/ZhkuAgent/agent/frontend/package.json:10)。
- knowledge 已依赖 agent-runtime，没有依赖 agent：[knowledge/pom.xml:24](D:/alldesk/Projects/ZhkuAgent/knowledge/pom.xml:24)。当前审核、检索、身份、原文服务及规则查证入口均在 knowledge。
- agent 是旧通用工具 Agent/实验模块，含不同供应商/工具依赖；DefaultAgent 当前默认20步及通用提示：[DefaultAgent.java:22](D:/alldesk/Projects/ZhkuAgent/agent/src/main/java/com/zhku/agent/agent/DefaultAgent.java:22)。
- agent-runtime 已提供每次运行独享上下文、工具事件、停止与清理；不负责政策判断：[AgentRunContext.java:34](D:/alldesk/Projects/ZhkuAgent/agent-runtime/src/main/java/com/zhku/agent/agent/runtime/AgentRunContext.java:34)、[AgentRunTask.java:14](D:/alldesk/Projects/ZhkuAgent/agent-runtime/src/main/java/com/zhku/agent/agent/runtime/AgentRunTask.java:14)。
- 当前学生查证路径用真实本地词法检索，但规划注入的是封存回放；向量是空实现：[RuleCheckRuntimeFactory.java:14](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckRuntimeFactory.java:14)、[RuleCheckPreviewConfiguration.java:149](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckPreviewConfiguration.java:149)。
- 老校园学分 Agent 虽有模型/工具循环，但合法路径被限制为“查询→计算”，并非本次查证 Agent 可直接复用的业务流：[CampusCreditAgentService.java:200](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/campusagent/CampusCreditAgentService.java:200)。
- 旧澄清存储是一个Session中的单个条件上下文，不是完整多轮任务：[RuleCheckClarificationStore.java:12](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/rulecheck/web/RuleCheckClarificationStore.java:12)。

这些是源码和配置事实。本轮未构建、未查看实际Maven依赖解析、未重新运行在线链路；不能据此声称所有组合可编译或已生效。

### 2.2 推荐物理部署与依赖

**首期采用一个知识后端进程，内部划分 Web/应用编排、领域 Agent、证据服务三个职责层。前端通过同源代理访问它。** Agent端是职责层，不新增独立微服务、第二套学生身份或第二个政策数据库。

~~~mermaid
flowchart LR
  UI["学生前端<br/>agent/frontend"] --> WEB["knowledge：Web与任务应用层"]
  WEB --> RA["knowledge：规则查证Agent职责层"]
  RA --> MP["受限模型适配器"]
  RA --> BROKER["服务端动作校验与工具调度"]
  BROKER --> EV["审核目录 / 检索 / 原文 / 决策"]
  EV --> DB["专用试点数据库"]
  RA --> RT["agent-runtime：运行、停止、事件"]
  WEB --> RT
  EV --> GATE["发布核验与结果投影"]
  GATE --> WEB
~~~

依赖方向：knowledge → agent-runtime；agent → agent-runtime；agent-runtime 不依赖 knowledge、Vue或校园政策。旧 agent 模块仍作为通用实验能力保留，不接入学生产品主链路。后续如确有多个领域Agent共用决策循环，再抽取通用能力，不为了名称现在新增模块或搬迁整个目录。

### 2.3 三端职责合同

**前端负责交互与真实状态展示。**
- 学生单入口、v2可信身份、自然提问、当前任务上下文、必要条件补充。
- 展示已确认内容、未完成项、下一步，以及相关且默认折叠的原文。
- 查询运行状态、发起取消、处理网络中断与迟到结果、账号变化清空。
- 不决定规则适用性，不执行工具，不拼造原文，不自己推断后端原因，不从固定样例生成正式答案。

**后端应用层负责所有执行边界。**
- 身份/CSRF/输入校验、任务归属、版本冲突、幂等、并发及预算准入。
- 调度一次运行、管理跨轮任务、验证工具动作、发布结果、签发原文凭据。
- 保证原文资格、审计、对象/时间/条件约束不被模型或前端绕过。
- 不用关键词固定规定每题的完整操作顺序，不把技术异常包装成资料缺失。

**领域 Agent 负责动态查证决策。**
- 理解实际要点与明确条件，决定下一动作、下一查询或是否读取上下文。
- 根据新证据、澄清回复、工具失败等更新决策，维护子问题覆盖。
- 提出相关的事实组合、缺口、条件性解释和停止理由。
- 不自行授予资料权限、添加工具、猜个人记录、生成未核验政策正文或声明教务办理完成。
- Agent的提议不等于执行许可，也不等于可发布结论；服务端领域守卫独立验证。

**证据/RAG服务负责工具结果真实。**
审核与学生可见性过滤、词法候选、按需上下文、版本/hash/跨度核验、结构化事实及其引用绑定。RAG是Agent可用能力，不等于Agent全部逻辑；工具观察中的候选、合格原文和可发布事实必须区分。

## 3. Main Flow：唯一正式学生主链路

### 3.1 关键节点

**M0 进入与身份。** /和品牌落到/rules；学生只看到查证。资料范围与2025现行性限制明确。保留现有v2身份能力，不合并旧v1登录。

**M1 建立任务。** 前端提交question和clientRequestId；后端生成taskId、turnId、runId，绑定可信主体与Session、资料版本及ONLINE模式。模型不接收账号凭证。超过输入/账户额度时在模型前拒绝。

**M2 准入并冻结本轮边界。** 记录父任务版本、工具白名单、资料范围、预算、已明确条件与原句跨度。条件只是用户声明，不是个人数据核验。创建每轮独享运行上下文，避免Singleton共享消息和可变决策。

**M3 模型提出下一动作。** 输入为任务要点、当前条件、最小化已核验观察和可用动作定义；输出一个结构化动作。动作可为SEARCH、READ、ASK或FINISH，不要求固定顺序。首次任务可携带至多4个子问题理解；这仍是待核对的模型理解，不是正确性证明。

**M4 服务端检查动作。** 校验名称、字段、类型、来源句段、工具参数、引用归属、版本、条件必要性及剩余预算。未知工具/越权资料/非法数值不执行；记录拒绝原因。不得让框架自动执行未经守卫的模型工具调用。

**M5 执行查证工具。** SEARCH/READ按批准范围执行，返回类型化观察。检索缺结果、来源撤回、工具故障分别返回；来源内容只作数据，不成为系统指令。记录已执行事实与调用关联。

**M6 反馈并选择继续。** 更新证据集合和子问题覆盖，把工具观察返回模型，回到M3。重复相同查询且没有新信息、预算耗尽、明确无可用资料时有界停止。可以补查，不能无限“再试一次”。

**M7 必要澄清。** ASK只允许当前任务会影响规则解释、且学生能回答的必要条件。服务端保存跨轮任务并签一次性继续凭据，结束当前run、释放线程和模型资源。等待不算运行成功，也不是新的检索失败。

**M8 补充后续答。** 前端提交本轮条件或自然补充、continuationToken、expectedVersion、clientRequestId。校验归属、过期、重复与改变目标情况；重新核对已保存的原文资格；生成新run，回M2/M3。不保持旧线程等待，不把用户改了目标当作给旧条件补值。

**M9 提议结果并独立核验。** FINISH返回子问题覆盖、factIds、允许的条件比较、issue与证据关系，不直接接受自由政策正文。后端读取当前事实和绑定，检验对象、时间、条件、数值来源、原文完整性、事实独立性及用途关系。结构可自动检查；需求是否完全解释等语义问题仍需独立评估，不能因ID合法就标任务完成。

**M10 发布与显示。** 身份/任务版本/资料再核验后，原子发布该任务结果版本及引用凭据。前端只接收匹配taskId/turnId/version/身份epoch的结果。先解释本次能确认什么、还缺什么、下一步，再给相关原文。

**M11 结束与可核对。** 显示ANSWERED/PARTIAL/WAITING_USER/无法完成的具体原因；点击原文再次fresh核验。收尾记录运行、预算与实际调用；取消请求与资源真正退出分别标注。

~~~mermaid
flowchart TD
  START["M0–M2：身份、任务、边界与预算"] --> NEXT["M3：模型提出下一动作"]
  NEXT --> GUARD["M4：服务端动作校验"]
  GUARD -->|SEARCH / READ| TOOL["M5：执行获准工具"]
  TOOL --> OBS["M6：记录真实观察"]
  OBS --> NEXT
  GUARD -->|ASK| WAIT["M7：保存上下文，结束本轮"]
  WAIT --> HUMAN["学生补充"]
  HUMAN --> RESUME["M8：归属与版本校验，创建新运行"]
  RESUME --> NEXT
  GUARD -->|FINISH| VERIFY["M9：主张、条件与原文核验"]
  VERIFY -->|通过| SHOW["M10–M11：发布解释和依据，收尾"]
  VERIFY -->|允许补查且有预算| OBS
  GUARD -->|无预算 / 不可恢复故障| STOP["明确原因，保留独立有效结果并停止"]
  TOOL -->|不可恢复故障| STOP
  STOP --> SAFE["M9：复核可独立保留的内容"]
  SAFE --> SHOW
~~~

这个循环的固定部分是权限、状态和核验。具体查证路径由模型根据需求和观察决定。即使模型认为任务完成，发布仍可能被拒绝。

### 3.2 三类典型执行轨迹

- 两类选修课区别：SEARCH类别与修读安排 → READ第4条必要事实 → FINISH对比。没有必要问个人课程类型，也不扩展到未问的重修/学分折算。
- “我这门选修没过能重修吗，什么时候报名”：可先SEARCH/READ第18条，也可根据已审核条件定义先ASK类别；补充后核验对应分支、检查已审核目录是否有当期通知，缺日期则保留规则并说明日期未知。模型不得推断已获批。
- 一周上几天课：按意图查询现有资料覆盖；没有课表来源时说明缺具体安排。问“哪个专业”无法补足未接入的课表，因此不能无限收集个人条件。

这些是合理轨迹示例，不是验收时要求每题复现相同工具顺序。验收看必要动作、证据与最终任务结果。

## 4. 技术实现路径与模块映射

### 4.1 前端

延用Vue3/Vite与已有品牌样式；单个任务内可连续补充，不增加完整历史工作台。建议职责组件为页面外壳、提问区、当前任务结果、条件补充、相关依据、出处对照层；实际文件拆分以现有组件规模为准，不以新增文件数为目标。

对应当前文件：
- router/index.js、Home.vue、RuleCheckView.vue：收敛学生入口、品牌与范围提示；实验构建独立保留旧路由。
- api/rule-check.js：消费统一任务/结果合同，分开业务issue与HTTP失败；不自行降级回放。
- state/rule-check-workflow.js：从“每次execute清掉所有结果”扩展为task/turn状态，保留本任务已核验结果及学生补充；仅新问题、身份变化或明确失效清理。
- RuleCheckFact.vue：就近对应引用，短原文默认折叠；没有相关性绑定不展示。
- rule-check-session.js：保留身份epoch、注销及迟到响应保护。

首期采用异步任务+短轮询，不流式展示模型政策正文。运行中可显示已执行的安全摘要，例如“已取得选修课类别条款”，不展示思维链、供应商载荷或假进度。轮询停止条件为run资源确认退出、WAITING_USER或发布完成；网络断开不自动重发创建任务。

UI版式继续以已有[页面设计稿](D:/alldesk/Projects/ZhkuAgent/docs/design/student-rule-check-ui-proposal.html)为参考。其固定样例仅演示，不进入正式能力实现。

### 4.2 后端应用层

建议在knowledge内部新增ruleagent职责包，并由独立在线配置显式装配，避免改坏旧封存入口。建议模块名只是设计边界，不代表文件已创建：
- RuleCheckTaskService：建任务、续答、查询、取消与任务版本发布。
- RuleCheckTaskStore：跨轮最小上下文、owner/version/TTL和幂等索引。
- RuleAgentRunner：每轮上下文、受限线程池、预算与收尾；调用领域Agent决策。
- RuleActionBroker：白名单、参数、来源与资料权限校验后执行工具。
- RuleResultPublisher：独立事实/范围/证据核验、引用凭据与HTTP投影。

复用当前v2可信身份、安全链、原文凭据、审计及证据reader，保留回环临时库保护；不直接打开旧rag.enabled或全量组件扫描。[RuleCheckPreviewSecurityConfiguration.java:48](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/security/RuleCheckPreviewSecurityConfiguration.java:48) 当前禁止其他入口混开，需新增独立、有限在线装配，而不是删除这些守卫。

首期每Session仅一个活跃任务；TaskStore为有容量上限的服务端内存存储，挂到可信会话归属。仅适用于单机临时试点：不提供跨重启恢复/多副本一致性/完整历史。等待条件TTL建议5分钟、任务最大存活15分钟，错误尝试不续期。完成结果与幂等记录保留至同一TTL结束；精确容量应在实现前作为配置锁定。未来要长期历史时再设计持久化表和迁移，不把旧任务历史冒充咨询历史。

模型网络及数据库读取在Session锁外执行；持锁仅作短时间的owner/version检查与提交。使用版本比较/CAS使旧run不能覆盖新任务、注销或取消。GET快照为不可变复制，防止前端读到半更新状态。

原子发布只覆盖任务结果版本、上下文与引用句柄的同一内存提交边界，不表示审核文件、数据库和HTTP发送存在全局事务。首期发布前及原文点击时fresh核验以缩小变化窗口；严格跨存储一致性若成为要求，须另行设计，不能把当前多次顺序复核描述成全链路原子。

### 4.3 Agent职责层与模型适配器

**建议首期采用结构化动作JSON+服务端显式调度。** 模型每次只提出下一动作，得到真实工具观察后再决定后续动作。它仍是动态Agent，不要求必须使用供应商原生function calling。

理由：现有OpenAI-compatible基础可参考，而回放规划器协议不含工具循环；结构化动作便于测试、计费、拒绝原因和工具权限核验。模型适配器只负责发送最小化消息、解析NextAction和类型化失败，不负责执行工具或授予事实资格。

建议抽象RuleAgentModelPort.nextAction(TaskView, ToolObservations, BudgetView) → ActionOutcome；Outcome保留有效动作、超时、限流、不可用、协议错误、预算拒绝。该签名是设计合同，不是本轮实现代码。保留raw响应摘要供受控验收，禁止普通日志打印完整请求/响应、私有输入和密钥。

备选为Spring AI原生工具调用，但必须由宿主掌握执行权，不让框架隐式循环或执行通用工具。仓库[ToolCallAgent.java:32](D:/alldesk/Projects/ZhkuAgent/agent/src/main/java/com/zhku/agent/agent/ToolCallAgent.java:32)已使用显式工具执行；Spring AI1.0.3官方[工具文档](https://github.com/spring-projects/spring-ai/blob/v1.0.3/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc)也支持手动控制。不能把最新2.0的ToolCallingAdvisor迁移示例复制进现有1.0.x项目。

当前agent-runtime/agent声明Spring AI1.0.0，knowledge声明1.0.9。这里只核查了POM，未解析最终依赖树；它是实施前的兼容性检查项，不是已经验证的不兼容。首期不同时升级框架/供应商，专业Agent不依赖旧agent模块。优先验证同进程knowledge+runtime的实际解析与现有合同测试。

Rag旧ModelChatClient接收answer/citationIds类型结果，不直接兼容NextAction；OpenAiCompatibleChatClient可参考超时/凭证边界，但需专门动作适配器，不能假称只改一个配置就接通。

### 4.4 共享运行模块

复用agent-runtime的runId、独享消息、工具提议/结果事件、STOP_REQUESTED、cleanupOnce及termination发布。尽量不修改通用生命周期枚举来容纳校园业务：
- TaskStatus描述用户任务；RunState描述一次执行。
- WAITING_USER属于TaskStatus，本轮Run以INCOMPLETE/DOMAIN_DECISION结束并真正释放。
- 澄清续答产生新runId，同一taskId；停止已终结run不能改写其结果。
- 健康查证后得出缺证属于业务部分/未完成结果，不是系统异常。清理成功也不证明查证任务成功。
- 内部工具与审计事件允许结构化记录，不把事件 payload 原样发送学生页面。

旧DefaultAgent、学分工具注册表及合成课表不加入新RuleActionBroker；当前[CampusToolRegistry.java:39](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolRegistry.java:39)的默认工具不适配规则查证任务。

### 4.5 知识治理、检索与原文

复用审核目录、学生可见与分享资格过滤、词法索引、EvidenceCapture、EvidenceVerifier和OriginalEvidenceService。对学生业务数据只读；必要的系统审计写入不等于Agent获得教务写权限。

检索预算分为每查询候选、核验片段、展示证据；按子问题保留必要依据。现有20候选/5结果作为工程基线保存，在线参数独立配置与测量，不修改旧封存请求身份。向量/RRF、重排序仅在必要证据召回不足的评估支持下再引入。

必须补足第4条原文已有但当前模板未完整表达的修读安排事实，并将整条原文事实拆为必要的原子解释。新增factId/条件/跨度须按现有语义审核边界核对；AI整理不自动算人工通过。候选原文有内容而可执行事实未覆盖时返回REVIEWED_FACT_COVERAGE_GAP，不冒充政策不存在。

## 5. 工具与动作合同

全部动作由模型提议、宿主执行；单次最多一个动作，READ可有界批量读取多个候选，以降低模型往返。首期不执行并行模型动作。

**SEARCH_REVIEWED_RULES**
- 输入：当前subquestionId、查询文本、允许的范围内筛选意图。
- 服务端强制注入知识范围/版本/权限，模型不能覆盖；查询不得包含凭据或新增个人档案。
- 输出：候选句段/元数据、task-scoped候选句柄、召回/通路健康信息、目录覆盖说明。
- 来源在学生许可范围内；候选片段还不能被当作可发布主张。健康零召回与检索失败分开。

**READ_VERIFIED_RULE**
- 输入：本任务此前SEARCH签发的候选句柄，至多3个；禁止任意sourceId、文件路径、URL或SQL。
- 输出：fresh核验的原文跨度、来源元数据、可执行审核factIds及条件/缺口定义。
- 返回数据仍要在结果发布及原文点击时复查。超出片段预算、资格变化和原文未支持可执行事实各有原因。

**ASK_NECESSARY_CONDITION**
- 输入：subquestionId、conditionId、影响判断的原因及已确认内容引用。
- 后端按审核条件定义检查确实相关、尚未明确、选项合法，不接受任意收集学号/成绩/身份信息。
- 返回WAITING_USER和一次性继续凭据，不调用外部教务接口。用户不清楚时保留未知、提示核对方法；相同条件不无限追问。

**FINISH_CHECK**
- 输入：逐子问题coverage、factIds、允许操作数及用户声明来源、issue原因和evidence关系。
- 宿主渲染审核解释/确定性比较，独立核验后才返回结果。模型不能提交任意规则正文、个人资格判断、资料许可或成功办理状态。
- 缺证停止只能说明当前允许资料无法支持该项；单次零召回不能断言整本手册不存在规定。

工具观察统一携带actionId、runId、step、status、reasonCode、data、dataLevel（CANDIDATE/VERIFIED_EVIDENCE/VERIFIED_FACT）和资料版本。异常只提供安全原因，不回显供应商异常原文。ASK/FINISH属于领域控制动作，不伪装成已执行教务业务工具。

## 6. 状态、结果与接口合同

### 6.1 三层状态分别表达

- TaskStatus：ACTIVE、WAITING_USER、RESOLVED、STOPPED、FAILED；描述一次查证的跨轮状态。
- RunState：复用CREATED、RUNNING、STOP_REQUESTED、SUCCEEDED、INCOMPLETE、FAILED、CANCELLED及实际terminated。
- Result：coverage=COMPLETE/PARTIAL/NONE；claims、issues、clarification、actions和evidenceRelations可并存。

RESOLVED意为查证流程已形成可解释的最终结果，不代表用户申请获批或每项问题都得到确定答案；coverage与issues仍须检查。完整任务成功按参考要求判断，不能仅统计RESOLVED次数。必要澄清是本轮正确行为，跨轮任务仍等待用户，不提前计作完整任务成功。

issues.kind：DATA_GAP、CLARIFICATION、PROCESSING_FAILURE、SCOPE_LIMIT、CONFLICT、REPLAY_MISS。
重要reasonCode：RETRIEVAL_NO_MATCH、RETRIEVAL_UNAVAILABLE、MATERIAL_SCOPE_MISMATCH、DETAIL_NOT_IN_REVIEWED_TEXT、CURRENT_NOTICE_MISSING、PERSONAL_RECORD_NOT_AVAILABLE、REVIEWED_FACT_COVERAGE_GAP、MODEL_TIMEOUT、MODEL_RATE_LIMIT、INVALID_MODEL_RESPONSE、UNKNOWN_TOOL、EVIDENCE_CHANGED、AUDIT_UNAVAILABLE、BUDGET_EXCEEDED、CLARIFICATION_EXPIRED、REPLAY_NOT_COVERED。

资料不足、系统错误和回放未覆盖不能经过同一个reject变成INSUFFICIENT_EVIDENCE。当前[RuleCheckEvidenceFirstFlow.java:195](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceFirstFlow.java:195)的原因折叠是后续明确修正点。

execution包括mode、modelAttemptCount、toolStepCount、providerResponseReceived、answerSource、replayCoverage；学生只看到必要解释，完整执行字段用于验收。在线语义前端消费schemaVersion=3；旧回放结果单独适配，不用旧转换器抹掉新原因。

每个claim关联subquestionId、条件及声明来源、审核factId和精确原文引用。每个issue有下一步与作用范围。每段证据必须支持claim、条件或冲突点，不能将全部retrievalTrace上屏。模型选择ID合法、hash正确和语义支持充分是不同检查。

### 6.2 首期建议接口

保留现有同源v2身份命名空间与cookie路径，新在线Controller使用/api/zhku/controlled-preview/v2/rule-check/tasks；旧POST rule-check留在工程回放配置中。新接口只是设计，当前未存在。

- POST /tasks：question、clientRequestId。返回202及taskId/turnId/runId/version，不提前给政策结论。
- GET /tasks/{taskId}：owner校验后返回任务与运行快照；RUNNING期间只有安全执行摘要，WAITING_USER/已发布时带核验结果。
- POST /tasks/{taskId}/messages：continuationToken、expectedVersion、clientRequestId、必要声明或自由补充。返回新run；不允许学生传规则、工具、模型或模式。改变任务目标时要求新任务，不能静默沿用旧引用。
- POST /tasks/{taskId}/cancel：expectedVersion及目标runId，返回停止请求状态；前端继续核对terminated，不立即显示资源已释放。
- GET /originals/{token}：沿用当前会话凭据、目录许可与fresh原文检查。
- GET /capabilities：可信配置给出实际ONLINE/ENGINEERING_REPLAY、资料范围、限制和接口schema；配置不得被客户端覆盖。

写接口带CSRF；任务与结果no-store；401/403是身份错误；409是版本/上下文/证据变化；429是应用准入限制。异步运行失败通过GET快照中的FAILED/reason表达；GET本身503表示查询服务不可用，不应删除已确认的任务身份或再次POST。

幂等键按actor+Session+动作作用域+clientRequestId存储，核对载荷指纹，先占位再调度；重复相同提交返回同一个run，不新增模型调用。相同键不同载荷409。未知POST结果先查询，不能让代理或前端重试POST来猜是否执行。GET轮询与模型调用配额分开限流。

### 6.3 失败与部分结果

- 模型超时/坏JSON：记录PROCESSING_FAILURE；没有独立核验答案则不展示候选原文作为答案。已有独立完成子问题可以保留PARTIAL。
- 单路检索故障：记录降级；其余路径有完整可靠证据时可以继续。禁用向量不计向量故障。
- 冲突：只阻断受影响项，保留其他独立结果。缺一方核验不叫已确认冲突。
- 原文404/409：撤销受影响内容并要求重新查证；原文503只表示核对服务失败，不能推断政策撤回。
- WAITING_USER过期、进程重启丢上下文：准确提示重新查证，不能伪造持久化恢复。
- 取消/超时：本地stop requested与远端计费/退出不同，记录未知调用成本，不声称取消必然不收费。

## 7. 在线、回放及执行预算

ONLINE与ENGINEERING_REPLAY由服务端启动配置选择，独立实例/会话cookie，客户端没有切换字段。在线进程不读取旧封存响应目录；回放缺记录绝不外发。没有获准凭证/合法配置时在线启动失败，不降为回放。

旧SealedRuleTaskReplay保留，不能用其固定任务协议伪装新Agent动作循环。若以后回放新Agent，需记录“动作请求+工具观察+模型响应”的版本化轨迹，以完整请求指纹匹配；未命中给REPLAY_NOT_COVERED。旧关键词兜底成功单列VERIFIED_RULE_ONLY，不计作模型决策或当次在线调用成功。

**首期预算建议，实施前锁定并用真实小批验证：**
- 一次run最多4次模型决策、6次领域动作，其中每个模型决定只执行一个有界动作；上限是防御，不要求用满。
- 每次模型请求最多5秒，排队、模型、工具及发布的整run最多25秒；剩余时间不足不再发起调用。
- 单task最多2轮必要澄清、3个run、12次累计模型尝试，等待用户不占运行线程/25秒，但受任务TTL约束。
- READ最多3句柄/次、task有效原文最多8个片段/32KiB；每次模型请求按实际序列化字节计量且不超过64KiB，输出每次最多1024token，均为建议预算。
- 每次模型输入从结构化TaskView重建，只保留当前需求、用户声明、有效证据和最小化最新观察；不无限追加旧原文与历史消息。完整执行事件留受控审计，不全部送模型。
- 不自动重试供应商；外发前原子占用本task及试点总额度，响应未知不退回尝试次数。没有外发许可/额度时本地stub可验证协议，但不宣称在线有效。
- 相同动作、参数及证据版本无新进展时不重复执行；记录NO_PROGRESS并结束或提示可补充项。自定义少量语义改写可继续，但也计预算，不允许无限换词。
- 线程池和等待队列有上限，BUSY/429明确；不是Singleton只有一个永久调用槽，也不把进程累计128次回放限制照搬长期服务。

数值尚未测量，不是性能承诺。总授权额度优先于每task预算；不能因为配置允许12次就认为已授权外发。实施前每次数据库/供应商操作依AGENTS检查环境凭证，数据库仅专用临时库。

## 8. 每个模块统一采用的执行流程

后续所有任务使用同一流程；本轮不执行：
1. **范围核对**：写明主链路节点M编号、要恢复的行为、起点分支/SHA及既有修改；排除未授权范围。
2. **合同先行**：固定输入/输出、状态转换、owner/version、错误、预算与依赖；用代表样例使上下游可核对。
3. **失败用例先建立**：覆盖真实缺陷和关键边界，不为简单文案镜像实现写大量测试。
4. **最小实现**：沿现有模块扩展，不同时做无关搬迁、全库格式化、依赖升级或资料扩容；关键约束中文注释说明原因。
5. **逐层验证**：领域/协议 → stub联调 → 临时库 → 必要的获准真实供应商 → 浏览器；按任务影响选择，不每轮重复所有检查。
6. **同版本证据**：记录输入、必要动作、实际状态、引用、失败、用量及源码/资料/prompt配置身份。部分暂存尤其验证提交内容完整。
7. **退出判断**：达到本任务退出标准才称完成；失败或时间预算耗尽留明确卡点，不自动增任务、降低参考或扩工具。
8. **Git保存与交接**：明确本次文件和差异，用户暂存提交或按明确授权执行；记录实际SHA与外部资产限制。push/PR/部署独立授权。

按任务持续更新同一记录，不每轮对话生成一个新报告。默认单个执行包45–60分钟；到时给结果/卡点/下一项，不用“持续开发”解释无限延长。遇到根本接口不成立先修改设计决定，不叠补丁凑通过。

## 9. 开发顺序、模块协作与成功退出标准

仍对应此前确认的五个工作包：D0=工程基线；D1–D3=最小Agent主链路；D4=学生页面接线；D5=能力与收益验收；D6=冻结交付。以下编号只是对同一工作包的技术依赖细拆，没有增加新的业务阶段。

### D0 / P0：恢复可定位的工程基线
覆盖当前已有源码、必要审核资料依赖及旧回放，分类可提交文件与本机受限资产，验证选定版本完整。当前HEAD为139194224d12、分支zhkuagent-modules，未等同于工作区全部成果。
退出：提交范围完整、恢复说明真实，有实际Git SHA；不把整个dirty工作区一次提交，也不把上传当成本地提交的后续自动步骤。

### D1 / P0：锁定Main Flow与共享合同
覆盖M1–M4、M7–M11；形成NextAction/ToolObservation/TaskSnapshot/ResultEnvelope、task/run区别、错误/幂等/预算示例。同步前端消费合同，但尚不制作新视觉。
退出：直接回答、澄清+部分内容、缺资料、工具故障、回放miss、取消竞态均可表达；签名/字段一致，非法组合fail closed。此步骤是D2/D3/D4的接口基线。

### D2 / P0：证据工具与必要事实覆盖
覆盖M4–M6、M9；适配已有词法/reader/核验，建立task句柄及原子事实，补第4条修读差异；新增语义依审核要求核对。
退出：未审核/跨范围/其他task句柄不执行；返回候选与核验事实分级；重复/变化/审计失败有原因；主张与相关跨度对应。无模型也能用stub证明工具真实契约。

### D3 / P0：最小动态Agent与任务续答
覆盖M2–M9；实现动作决策端口、broker、有限循环、每run生命周期、TaskStore、预算与追问后恢复。采用结构化动作适配器，保留框架升级备选。
退出：至少直接问法、需澄清问法、缺证问法三类轨迹合理且有分支；模型根据观察改变下一动作；没有强制“先检索再算”或固定答案；所有终止/取消真正收尾。先stub，再准备最小化真实请求包；没有许可不发起真实调用。

### D4 / P1：后端HTTP与学生前端垂直贯通
覆盖M0–M2、M7–M11；按已冻结合同接异步任务API、轮询、条件补充、结果、原文和取消；收敛导航，沿同一版式展示混合结果。
退出：实际HTTP及页面关联同task/turn/run；重复POST不重复外发；旧账号、旧版本和迟到响应不覆盖；默认展开原文0、实验导航0、无关证据0。此处验证真实链路，不用静态稿代替接口验收。

### D5 / P1：Agent能力、故障边界与用户价值验收
三组分开：旧40题工程回归、曝光开发题、候选冻结后形成的新报告题。新题必须包含不同表达、条件缺失、资料不足、多问题、否定/阈值/对象时间范围；参考证据与评分先定，运行后不为凑通过改参考。
退出：关键无依据/资格越界/伪造动作错误0；有依据错误拒答与任务完成单列；故障分类准确、调用/工具/预算真实；真正在线必要题有当次供应商证据。重复任务看行为稳定与最终结果，不要求固定轨迹。
随后小范围学生对照PDF体验，记录理解正确、原文核对和查证用时；没有学生数据不能声称已降低成本。

### D6 / P0：分别冻结与交付
旧回放工程基线保留；新Agent候选以源码、资料、schema、prompt、模型、工具合同及预算身份单独归档。提供普通终端启动/退出、受限资产恢复及实际Git提交/检查点。
退出：回放、真实Agent、学生收益三种证据各有结论，明确未验证项。不把上传成功、运行收尾或少量演示当上线达标。

依赖：D0→D1→D2→D3→D4→D5→D6。D1后可用合同stub提前检验前端，但不能提前宣称D4真实贯通。首次动态轨迹在D3解决，页面不再先行承诺未存在能力。

## 10. 验收矩阵与证据标准

每个测试记录question/补充、资料范围、期望要点、禁止结论、允许动作、不必要动作、终止要求、引用关系以及实际task/run身份。

- 类别区别：完整回答第4条修读安排，不追问个人类别；引用不要扩到排课/折算。
- 选修不合格+报名日期：必要类别追问，用户已明确就不重复；当期缺证不补日期，保留规则。
- 每周上课天数：不造统一天数或课表，不为未接入的个人数据收集无效条件。
- 平时成绩权重：组成可答、比例未知，不能把背景解释当权重问题完成。
- 本科历史规则+研究生/当前适用：可独立解释部分与范围不足分开，不越界确认。
- 阈值“超过30%”与“正好30%”：确定性比较正确，口述数值不证明其他个人考核资格。
- 混合问法：一个缺证不取消另一个有效项；冲突仅阻断对应项。
- 非法tool/fact/句柄、恶意原文指令、伪造声明：不执行、不发布、保留安全reason。
- 网络/模型/检索/审计失败、预算耗尽、重复POST、并发续答、取消后迟到完成：正确归因；不缓存兜底、不重复调用、不覆盖新状态、不报假终止。
- 回放MISS：0外发、准确模式；规则兜底不算在线模型能力。

能力指标分层：
1. 检索：可答子问题必要证据覆盖、漏召回、噪声；单次零召回不证明资料无规定。
2. 决策：是否选择必要动作/条件、是否正确停止、有依据错误拒答、多问题漏项。
3. 输出：任务完成、每条主张支持、条件例外、范围、未知表达、引用相关性。
4. 执行：实际调用次数、重复次数、状态一致性、资源退出、延迟分位与失败成本。
5. 用户：任务理解正确、可找到原文、查证耗时。小样本只用于试点，不外推整体质量。

原24题/22通过等前期建议不自动当成已批准新Agent门槛。D1/D5开始前锁定一次固定样本、评分、关键错误定义和预算；报告失败后只能在新候选/新样本身份下比较，不能反复用同一曝光集宣称泛化。模型裁判如采用须与人工评分校准，不能用另一个模型一句“通过”取代事实与任务验收。

## 11. 风险、取舍与防止返工的规则

- **Agent退化为固定工作流**：检查是否硬写每题动作顺序、靠问句模板回放、忽略工具观察。固定领域权限与停止条件可以保留，动态查证决策要有真实轨迹。
- **Agent自由生成扩大事实边界**：首期使用审核原子事实及允许操作渲染；灵活性来自理解、查询、补查与组合，不来自编造规则。动态事实抽取需另评估语义核验。
- **跨轮状态复杂**：task与run分离；等待即释放；短锁+版本发布；先单机有界内存，不急于消息队列和分布式会话。
- **依赖混杂**：不拉通旧agent所有供应商starter；P0核对实际依赖解析与runtime兼容，依赖升级单独任务。
- **时间和外发成本失控**：每run/task/试点总预算同时限制；授权批次不因反复运行自动增加；调用失败保留实际尝试和用量未知。
- **前后端持续返工**：先定任务/结果/动作合同，再做领域与真实垂直链路；新原因码改变映射，资料新增改变内容，不重新设计整页。
- **工程证据冒充产品效果**：stub、回放、真实调用、HTTP页面、学生收益各自单列。raw供应商响应曾经真实不代表当前输入实时执行。
- **Git保存不完整**：旧工作区分任务整理，选定文件和测试依赖一起验证；记录SHA及本机资产指纹，提交不保存数据库/凭证/原PDF。默认由用户暂存提交，明确授权后才由Codex操作Git写入；上传独立确认。

业务范围、资料权利、可用工具、结果语义或运行模式发生实质变化时，先记录设计影响与验收变化再实施。普通实现选择由执行者在合同内决定，不为每个文件重复请求方向确认。

## 12. 本轮交付和实施前仍需核对的事项

本文件统一后续Main Flow、分层责任、工具/API/任务状态合同和D0–D6开发依赖。它优先于前期固定两阶段规划建议；前期问题分析与静态UI保留作为证据/版式参考，不被悄悄改写成已实现能力。

实施前仅核对实质影响项：
- 实际依赖树及knowledge+agent-runtime兼容；本轮未运行Maven。
- 新增事实解释的语义审核；原文分享许可与新解释审核分开。
- ONLINE真实供应商适配、最小化载荷与本次具体外发额度；旧额度不自动延续。
- 本机Session内存与5/15分钟TTL是否满足受控试点；若要求长期历史或公网，属于范围变化。

本轮没有验证数据库、真实模型、端到端网页或Agent运行结果；未把设计称作已完成开发，未修改任何产品代码。

本轮文档校验：14处源码文件/行号引用均有效；457项产品源码、运行模块、脚本及关键配置的前后指纹一致。新增内容仅本设计文档，仍为未提交状态；这些检查证明设计交付范围受控，不证明新架构已经运行通过。

