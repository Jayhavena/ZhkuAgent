# 多会话同步状态板（SYNC-STATUS）

> **本文件是所有协作会话的唯一事实源。**
> 更新时机：开工 / 收工（提交）/ 合并前 / 合并后，四个时刻必更新。
> 更新方式：只改自己负责的行与公共区域；发现要改其他会话内容 → 写入"待协商事项"，不直接改。

---

## 零、会话登记与地盘划分（2026-09-16 起生效）

> 规则：任何会话**开工前必须先读本节并登记/确认自己的行**；任务与已登记
> 地盘冲突时，停止动手并报告用户。新会话由用户粘贴
> `docs/plans/agent-sync/会话启动提示词.md` 完成上岗；
> 连续开发（多轮自驱推进）使用 `docs/plans/agent-sync/持续开发指令.md`。
> 地盘按**目录**划分；改地盘外文件 → 写入"待协商事项"，不直接改。

| 会话 ID | 宿主 | 地盘（目录级） | 分支 / worktree | 状态 |
| --- | --- | --- | --- | --- |
| WB-1 | WorkBuddy 会话①（前端） | `agent/frontend/`、`docs/design-references/` | `campus-line-a` / 主 worktree | 🟢 活动中 |
| WB-2 | WorkBuddy 会话②（其他内容） | 待该会话开工时自填 | 主 worktree（与 WB-1 共享工作区，禁止同时 commit） | ⬜ 待登记 |
| WB-3 | WorkBuddy 会话③（线 A：检索+生成+评测） | 线 A：`knowledge/` 检索与生成侧（`routing/`、`retrieval/`、`rag/`、`eval/`）、Flyway `V9`–`V11`、`docs/evidence/`；与线 B 在 `knowledge/` 内的占用以「文件占用声明」为准 | `campus-line-a` / `ZhkuAgent-lineA` | 🟢 活动中（2026-09-17 已上岗；阶段 8 已完成十批提交，详见第二节线 A 行） |
| CX-1 | Codex 会话（线 B） | 线 B：工具 + 编排（`agent/` 工具侧、`knowledge/`，**前端体验职责已移交 WB-1**） | `campus-line-b` / `ZhkuAgent-lineB` | 🟢 活动中（首个交付已合并入主线 `7dce9e5`） |

> 同 worktree 多会话（WB-1 / WB-2）额外纪律：任一时刻只允许一个会话执行
> `git commit`；commit 前必须 `git status --porcelain` 核对暂存区只有自己
> 地盘的文件。

---

## 一、合并基线

| 项 | 值 |
| --- | --- |
| 主线分支 | `zhkuagent-modules`（只接收合并，不直接开发） |
| 当前基线提交 | `7414586`（2026-09-17 **第二次合并**：线 A 阶段 8 主体——五级路由 + 受控检索 + 生成守卫 + 评测 + HTTP 装配 + `V9`；首次合并为 `7dce9e5` 线 B 会话持久化与工具集） |
| 最近合并 | `campus-line-a` → 主线（`7414586`，纯快进，86 文件 +6136，检查清单逐条核对通过） |

> ✅ 2026-09-16 事故已闭环：本机 git 环境对"带斜杠的引用"（如 `codex/xxx`）写入存在缺陷，commit 触发的自动 gc 曾清空 `refs/heads/codex/`。**全部 6 个分支已迁移为顶层无斜杠命名**（`zhkuagent-modules` / `campus-line-a` / `campus-line-b` / `agent-run-context` / `zhkuagent-mvp` / `backup-fc7d153-before-amend`），`codex/*` 引用已删除，`gc.auto 0` 已设，并新增 `scripts/safeguard-refs.ps1` + post-commit 钩子自动守护。
>
> **纪律两条**：① 今后建分支一律用**不含斜杠**的名字；② 每次 commit 后跑 `git for-each-ref refs/heads` 核对（钩子已自动跑，发现丢失会自动恢复）。

## 二、各线状态

### 线 A：检索 + 生成 + 评测（Agent：WorkBuddy 会话，见第零节 WB-3）

| 项 | 值 |
| --- | --- |
| 分支 | `campus-line-a`（2026-09-17 已快进至主线 `7c94e20`） |
| worktree | `D:\alldesk\Projects\ZhkuAgent-lineA`（2026-09-17 已创建，专属本线） |
| Flyway 号段 | `V9`–`V11` |
| 当前任务 | 阶段 8（WB-3）十批已提交并**合并入主线 `7414586`**（①五级路由 ②V9 迁移 ③资格过滤/切分/profile 原子切换/RRF ④JDBC 索引 ⑤词法+向量召回与降级 ⑥作答编排+引用审计 ⑦provider 配置与合同测试 ⑧首份评测基线 ⑨三级处理器语义适配 ⑩HTTP 装配）。**剩余**：Spring AI 段——按最终决策走「精确化断言」（不建独立模块）：`knowledge/pom.xml` 引入版本锁定依赖 + 迁移 `springai-wip/` 适配器入 `knowledge/rag` + 改断言（加强 agent/工具不可见 + 类存在性换行为断言）；之后是真实 embedding profile 打开向量路与证据账本补充 |
| 状态 | 🟢 活动中（主体已合并；Spring AI 段进行中） |
| 最新提交 | `7414586`（与主线同点；下一轮开发请在快进后继续） |

### 线 B：工具 + 编排（Agent：Codex 会话，见第零节 CX-1）

| 项 | 值 |
| --- | --- |
| 分支 | `campus-line-b`（顶层命名，2026-09-16 已从 `codex/campus-line-b` 迁移，旧引用已删除；worktree `ZhkuAgent-lineB` 实际绑定的就是本分支） |
| worktree | `D:\alldesk\Projects\ZhkuAgent-lineB` |
| Flyway 号段 | `V12`–`V14`（已用 `V12`：会话持久化表） |
| 当前任务 | SSE 流式输出：采用方案 A，等待线 A 的统一 HTTP 装配合入后在稳定响应契约上接入；使用冻结的 `zhku.streaming.*`，之后为答案卡后端侧（前端 UI 归 WB-1） |
| 状态 | ⏸️ 等待线 A HTTP 装配依赖 |
| 最新提交 | `2ff857f`（已登记 SSE 边界，方案 A 获用户确认） |

## 三、文件占用声明（开工时登记，收工时清除）

| 文件 / 目录 | 占用方 | 任务 | 声明时间 |
| --- | --- | --- | --- |
| `knowledge/pom.xml` | WB-3（线 A） | 阶段 8 任务 3 引入版本锁定 Spring AI 依赖。**最终决策已定：走"精确化断言"，不在 pom 之外另建模块**（见第四节 Spring AI 条），故本占用继续有效；改动前后按高冲突文件规则处理 | 2026-09-17（16:45 曾因断言互斥回退，现按新决策继续） |
| `knowledge/src/main/java/com/zhku/knowledge/retrieval/`、`routing/`、`rag/`、`eval/` 及同名测试目录 | WB-3（线 A） | 阶段 8 检索与生成侧（`retrieval/`、`eval/` 经用户确认补入线 A 地盘，见第四节）；线 B 未占用这些路径 | 2026-09-17 16:41 |
| `docs/evidence/` | WB-3（线 A） | 阶段 8 证据账本 | 2026-09-17 16:41 |

> 说明：状态板更新由各线在自己分支进行，主线看到的版本是最近一次合并时的状态；
> 跨线判断冲突时，以双方分支上的最新登记为准。
>
> ⚠️ **合并陷阱（2026-09-17 实例）**：同一行被双方同时修改时（如第四节的决策条目），
> 后合并方的版本会覆盖先合并方——本次 `campus-line-a` 合并即覆盖了 WB-1 写入的
> "不走 B" 决策文字。**纪律**：合并状态板冲突时必须**逐段合并**（双方内容都保留），
> 尤其不得让"决策/根因"类内容丢失；合并后由合并方核对第四节是否含双方最新结论。

## 四、待协商事项

| 事项 | 提出方 | 说明 | 状态 |
| --- | --- | --- | --- |
| 前端归属移交 | WB-1 / CX-1 | 用户指定前端（`agent/frontend/`）由 WB-1 负责；原线 B 分工中"体验"部分相应剥离，CX-1 已确认不再改前端文件 | ✅ 已确认（2026-09-17） |
| Spring AI 依赖与 classpath 隔离断言 | 线 A 取证 / WB-1 决策 | **线 A 根因报告（2026-09-17 17:35，证据可复核）**：① 实际失败断言是 `ZhkuLocalPreviewApplicationTest:20` 的 `org.springframework.ai.chat.model.ChatModel` 不可见（另 `ZhkuLocalAdminApplicationTest:27`）；② 依赖树 `knowledge → spring-ai-openai:1.0.9 → spring-ai-model:1.0.9`，`ChatModel`/`EmbeddingModel` 同在 `spring-ai-model`、`VectorStore` 在 `spring-ai-vector-store`；③ `<exclusion>` 不可行（排除即排除掉要用的接口）；④ 测试层 `classpathDependencyExcludes` 只影响测试 JVM、产物仍带 `ChatModel` = **假绿**（线 A 已拒绝采用）。**最终决策：A 与 B 均不采用，改为「精确化断言」**——路线图明确"阶段 8 可在 knowledge 模块引入版本锁定的 Spring AI 依赖"、已批准计划亦要求改 `knowledge/pom.xml`，故失败那条属**被上层设计取代的过期断言**（其意图"预览应用无模型栈"已不成立），而 `DefaultAgentFactory` 那条才是真正的安全边界；建独立模块（B）属计划外变更且会在阶段 8 中途引入跨模块接口改动。执行要求：① 保留并**加强**安全断言（`com.zhku.agent.agent.DefaultAgentFactory` 及高权限工具包不可见）；② 把"`ChatModel` 类不存在"换成**行为断言**（未配置 `zhku.rag.*` 时不创建模型相关 bean、不发生模型调用）；③ 测试内注明改动理由；④ 本行即该守卫变更的透明登记；`springai-wip/` 适配器迁入 `knowledge/rag`，不建新模块 | ✅ 已决策（守卫变更已透明登记；文字曾于 `campus-line-a` 合并时被覆盖，2026-09-17 由 WB-1 恢复） |
| 线 A 地盘清单与实际交付包不一致 | WB-3（线 A） | 持续开发指令第一节把线 A 地盘列为 `routing/`、`rag/`、`eval/`、`answer/`，但阶段 8 计划要求的检索侧实现落在 `com.zhku.knowledge.retrieval/`。**2026-09-17 用户经 WB-1 确认：同意把 `retrieval/`、`docs/evidence/` 补入线 A 地盘**（线 B 未占用）；持续开发指令已就地更新 | ✅ 已确认（2026-09-17） |
| 「统一路由编排器」职责重叠 | WB-1 / CX-1 / 线 A | 分工表把"统一路由编排器"同时列在线 B 交付内容，而线 A 的拥有目录含 `knowledge/.../routing/`，阶段 8 计划也由 `KnowledgeAnswerOrchestrator` 承载五级路由测试。**确认边界**：路由判定规则与顺序（五级短路逻辑）归线 A 的 `routing/`；线 B 只提供工具与注册接口（`tools/`），由线 A 的编排器经接口调用，线 B 不改编排器文件；线 B 若需新增"工具分支"，先在状态板登记 | ✅ 双方已确认（CX-1 2026-09-17；线 A 2026-09-17 16:41） |
| 阶段 8/9/10 迁移号段过期 | WB-1 | 三份计划原写 `V7`/`V8`/`V9`，与既有迁移及已冻结号段冲突。**2026-09-17 用户确认处置**：阶段 8 改用 `V9`；阶段 9/10 号段待分配，由线 A 在阶段 8 交付后按"只追加"原则登记（预期 `V15`/`V16` 起）；三份计划文件已就地更正并标注"登记完成前不得创建迁移文件" | ✅ 已处置（待阶段 8 交付后分配 9/10 号段） |
| README 修正 | WorkBuddy | 根 README 与 knowledge README 仍写"未实现两轮追问"，与实际不符；待指派由谁修 | ⏳ 待指派 |
| agent/ 模块只读约定 | WorkBuddy | 首轮双方将 agent/ 视为只读资产；如需修改 ToolCallAgent 复用，先在此登记 | ⏳ 待确认 |
| 线 A / 线 B 归属 | WorkBuddy | 默认 WorkBuddy=线A、另一 Agent=线B；如互换，改本节与第二节两行即可 | ⏳ 待确认 |
| Spring AI 依赖与 classpath 隔离断言 | 线 A 取证 / WB-1 决策 | **线 A 根因证据（可复核）**：依赖树 `knowledge → spring-ai-openai:1.0.9 → spring-ai-model:1.0.9`；`jar tf` 证实 `ChatModel`/`EmbeddingModel` 同在 `spring-ai-model`（排除该 artifact = 把要用的接口一起排除）；仅 surefire 层掩掉只影响测试 JVM、产物 classpath 仍带 `ChatModel` = 假绿（线 A 已拒绝采用）。失败断言实为 `ZhkuLocalPreviewApplicationTest:20` 的 **ChatModel classpath 存在性**那条，而非 agent 类那条。**2026-09-17 最终决策：不走 B（独立模块）**——路线图明确"阶段 8 可在 knowledge 模块引入版本锁定的 Spring AI 依赖"，已批准计划亦要求改 `knowledge/pom.xml`；建独立模块属计划外变更且会在阶段 8 中途引入跨模块接口改动。**改为精确化断言**（下述守卫变更即本行登记）：① 保留并**加强**安全断言——`com.zhku.agent.agent.DefaultAgentFactory` 及高权限工具包不可见；② 把"`ChatModel` 类不存在"换成**行为断言**——未配置 `zhku.rag.*` 时不创建模型相关 bean、不发生模型调用（从"类不存在"升级为"未配置不启用"）；③ 测试内注明改动理由（按 AGENTS.md 注释约定） | ✅ 已决策（守卫变更已透明登记） |
| HTTP 装配是否现在做 | 线 A 提问 / WB-1 决策 | **2026-09-17 用户经 WB-1 批准：现在做**——属阶段 8 退出条件（跑通"…→ 引用校验 → 回答卡"）；只追加已冻结字段 `route`/`handoffRequired`，不改旧字段语义；占用已登记。注意：`ZhkuLocalPreviewConfiguration` 后续线 B 的 JDBC 配置切换也要动，合并时以"先合并方先落"处理，冲突时在状态板登记 | ✅ 已批准 |
| SSE HTTP 接入边界 | CX-1 | SSE 需基于本机预览会话门面输出冻结字段 `revision`、`conversationStatus`、`route`、`handoffRequired`，但 `ZhkuLocalPreviewConfiguration`、`LocalPreviewConversationFacade` 与会话 Controller 当前由线 A 占用。**2026-09-17 用户确认采用方案 A**：由线 A 完成 HTTP 统一装配并合入后，线 B 再接入 SSE；不新建并行会话端点。 | ✅ 已决策，等待线 A 依赖 |

## 五、冻结契约（开分前由双方共同确认，确认后只追加）

> 2026-09-17 状态：**五项全部冻结**。第 1、5 项按阶段 8 计划与建线现状确认；
> 第 2、3、4 项按线 B 草案、经用户 12:32 确认冻结。确认后只追加：后续任何
> 字段/配置键新增，在各条目下追加登记，不修改已冻结内容。

- [x] 统一路由判定顺序：澄清 → 当期通知 → 高风险规则 → 检索增强 → 拒答/人工
  （2026-09-17 用户确认按阶段 8 计划执行；依据：`KnowledgeAnswerOrchestratorTest` 明确"五级路由顺序、澄清优先、通知优先、规则优先、无证据拒答"，见 `docs/superpowers/plans/2026-09-16-stage-8-hybrid-retrieval-agent.md`。
  线 B 补充：严格短路执行，**后级不得覆盖已命中的前级结论**——此补充已被采纳为契约语义的一部分）
- [x] 引用白名单标识结构（生成层输出与服务端校验共用）
  （2026-09-17 用户确认按线 B 草案冻结：生成侧仅输出 `citationIds: List<String>`；每个 ID 是服务端为本轮获准证据分配的不透明标识；编排层与 HTTP 层只能透传经服务端校验后的 ID，不能自行构造）
- [x] HTTP 会话响应新增字段（只追加，不改旧字段语义）
  （2026-09-17 用户确认按线 B 草案冻结：保留既有 `sessionId` 语义；新增 `revision`、`conversationStatus`、`route`、`handoffRequired`；流式事件沿用这些字段。线 A 后续如需追加字段，按"只追加"原则另行登记）
- [x] 新配置键 / Profile 命名规范
  （2026-09-17 用户确认按线 B 草案冻结：线 B 侧使用 `zhku.conversation.*`（存储与 TTL）和 `zhku.streaming.*`（SSE）；Profile 沿用 `zhku-local-preview`，新增能力不创建泛化 `local` Profile；密钥继续仅从环境变量读取。线 A 启动后如需新增配置键，沿用 `zhku.` 前缀在此追加）
  - 线 A 追加（2026-09-17，只追加不改上文）：受控检索增强配置键 `zhku.rag.enabled`、`zhku.rag.active-profile`，以及按 profile 分组的 `zhku.rag.profiles.<name>.base-url` / `.api-key-env-var` / `.chat-model` / `.embedding-model` / `.embedding-dimensions` / `.timeout-seconds` / `.index-profile`。**一次只允许一个激活 profile**（`active-profile` 单值）；密钥只写环境变量名、值不落配置，配置文件与日志均不得出现密钥值。实现：`RagProviderProfileResolver` / `RagProviderProfile` / `ApiKeyResolver`（`knowledge/.../rag/`）。
- [x] Flyway 号段：线 A `V9`–`V11`、线 B `V12`–`V14`（2026-09-16 双方建线时已按此执行，无异议。
  2026-09-17 用户确认追加：阶段 8 用 `V9`；**阶段 9、10 号段待分配**，由线 A 在阶段 8 交付后追加登记（预期自 `V15`/`V16` 起），登记前不得创建迁移文件。既有 `V1`–`V8` 不可改写）

## 六、下次合并点

- 线 A 剩余增量（Spring AI 精确化断言 + `pom.xml` + 适配器迁入 + 真实 embedding profile 打开向量路 + 证据账本补充）完成后再次合并；
- 线 B `SSE 流式输出`：**前置已解除**（线 A 的 HTTP 装配已于 `7414586` 合入主线，响应契约稳定），可立即开工；独立可编译测试通过即可合并；
- 每次合并后由被合并方快进跟上新基线；合并状态板冲突时按第三节"合并陷阱"纪律逐段合并。

## 七、合并记录

| 时间 | 合并内容 | 来源分支 | 目标基线 | 检查清单通过 |
| --- | --- | --- | --- | --- |
| 2026-09-17 | 首次合并：会话持久化 JDBC（`V12`）+ 校园工具集 + Function Calling（33 文件 +3093） | `campus-line-b` | `7dce9e5` | ✅ 九项 |
| 2026-09-17 | 第二次合并：阶段 8 主体（五级路由 + 受控检索 + 生成守卫 + 引用审计 + 评测基线 + HTTP 装配 + `V9`，86 文件 +6136） | `campus-line-a` | `7414586` | ✅ 逐条核对（纯快进、零冲突；分支工作区另有未提交的 Spring AI 段，不在本次范围） |

## 八、引用载荷实际下发形态（WB-1 做前端引用卡 UI 的依据）

> 登记方：WB-3（线 A）2026-09-17 17:45。数据来源：阶段 8 HTTP 装配（提交见第二节线 A 行）。
> 原则同冻结契约：**只追加**；旧字段语义不变。

**接口**：`POST /api/zhku/local-preview/conversations/{sessionId}/messages`（body：`{"message":"…"}`）

**响应体（会话消息）实际形态**：

```json
{
  "type": "ANSWER",
  "message": "每学期公共选修课最高修读 4 学分。",
  "answer": null,
  "route": "RETRIEVAL_AUGMENTED",
  "handoffRequired": false,
  "citationIds": ["cit-3f9a…"]
}
```

| 字段 | 类型 | 语义 | 新旧 |
| --- | --- | --- | --- |
| `type` | 枚举字符串 | 既有语义不变：`ANSWER` / `NEED_CLARIFICATION` / `EVIDENCE_UNAVAILABLE` / `OUT_OF_SCOPE` / `SESSION_EXPIRED` / `SESSION_CANCELLED` / `SESSION_INVALID` | 旧（语义不变） |
| `message` | 字符串 | 面向学生的正文 | 旧 |
| `answer` | 对象或 null | 既有回答卡：`{conclusion, basis, sourceId, sourceTitle, sourceVersion, sourceLocator, applicableScope}`；**仅"当期通知/规则"级有卡**，检索增强级为 null | 旧（语义不变） |
| `route` | 枚举字符串 | 五级路由结论：`AWAITING_CLARIFICATION` / `CURRENT_NOTICE` / `HIGH_RISK_RULE` / `RETRIEVAL_AUGMENTED` / `INSUFFICIENT_EVIDENCE` | **新（只追加）** |
| `handoffRequired` | 布尔 | 是否需转人工渠道（拒答、会话异常、模型降级时为 true） | **新（只追加）** |
| `citationIds` | 字符串数组 | 服务端为本轮获准证据分配的**不透明标识**；前端只作锚点/透传，**不得解析其含义**；可用它到后端核对来源 | **新（只追加）** |

**关键约束（前端必须知道的）**：
1. `citationIds` 只在 `route=RETRIEVAL_AUGMENTED` 时非空；其他级别为空数组。标识形如 `cit-<24 位十六进制>`（`RandomCitationIdFactory`），不可预测、同轮不重复。
2. 全不命中时：`type=OUT_OF_SCOPE`（**旧值不变**）+ `route=INSUFFICIENT_EVIDENCE` + `handoffRequired=true`——请以 `route`/`handoffRequired` 判断"该转人工"，不要用 `type` 判断。
3. **当前缺口（下一轮补齐，字段名与结构预先登记，WB-1 可先按此预留 UI）**：本版响应只有不透明 `citationIds`，**不带每条引用的标题/定位/页码**。下一轮将追加 `citations` 明细数组（只追加，不影响旧字段）：
   ```json
   "citations": [
     {"citationId": "cit-3f9a…", "sourceTitle": "公共选修规则手册", "sourceLocator": "第 1 页", "pageNumber": 1}
   ]
   ```
   引用卡 UI 建议：先按 `citationIds.length` 渲染锚点，明细到位后填充标题/定位；两者长度恒等且顺序一致。
4. **流式（SSE）尚无载荷可登记**：本机预览当前是同步响应；SSE 属线 B 的 `zhku.streaming.*`，其事件内嵌字段将沿用 `route`/`handoffRequired`/`citationIds`（冻结契约已约定），待线 B 交付后由该线登记事件形态。
5. 另有一个单轮问答接口 `POST /api/zhku/local-preview/answers`（`mode/answer/citations/notice`），属阶段 5 既有路径，**不在本次引用卡链路内**，未被改动。
