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
| 当前基线提交 | `7dce9e5`（2026-09-17 **首次合并**：线 B 会话持久化 JDBC + 校园工具集 + Function Calling；此前的 `0c9e449` 为阶段 5/6/7 收口与安全整改） |
| 最近合并 | `campus-line-b` → 主线（`7dce9e5`，纯快进，33 文件 +3093，检查清单九项全过） |

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
| 当前任务 | 阶段 8（WB-3）十批已提交：①五级路由编排器 ②V9 迁移 ③资格过滤/事实切分/profile 原子切换/RRF 融合 ④JDBC 索引任务 ⑤词法+向量召回与降级 ⑥检索增强作答编排+引用审计 ⑦provider 配置与合同测试 ⑧**首份评测基线**（`retrieval-golden-v1, K=3, N=4, Recall@3=1.0000, MRR=1.0000, 引用准确率=0.8750`，向量路不可用、降级运行）⑨三级处理器语义适配 ⑩**HTTP 装配已完成**（五级链路接进本机预览，只追加 `route`/`handoffRequired`/`citationIds`；引用载荷形态见第八节）。全量回归 248 通过/0 失败。**Spring AI 依赖引入仍受阻**：已按用户指示完成根因取证（`ChatModel`/`EmbeddingModel` 同属 `spring-ai-model`，`<exclusion>` 与装配层隔离均被证据否决），下一步按 B（独立模块）推进 |
| 状态 | 🟡 推进中，含 1 项阻塞待决策（Spring AI 走 B 或 C）（WB-3，2026-09-17） |
| 最新提交 | `625d795`（十批：HTTP 装配 + 引用载荷形态登记；随后快进合并主线 `7dce9e5`） |

### 线 B：工具 + 编排（Agent：Codex 会话，见第零节 CX-1）

| 项 | 值 |
| --- | --- |
| 分支 | `campus-line-b`（顶层命名，2026-09-16 已从 `codex/campus-line-b` 迁移，旧引用已删除；worktree `ZhkuAgent-lineB` 实际绑定的就是本分支） |
| worktree | `D:\alldesk\Projects\ZhkuAgent-lineB` |
| Flyway 号段 | `V12`–`V14`（已用 `V12`：会话持久化表） |
| 当前任务 | 会话持久化 JDBC ✅ 与校园工具集 + Function Calling ✅ 均已合并入主线 `7dce9e5`。**下一项：SSE 流式输出**（用冻结的 `zhku.streaming.*`），之后为答案卡后端侧（前端 UI 归 WB-1）；③ 工具接入只需提供 `tools/` 与注册表（已满足），不改编排器 |
| 状态 | 🟢 活动中（首个交付已合并） |
| 最新提交 | `7dce9e5`（已与主线同点；下一轮开发请在快进后继续） |

## 三、文件占用声明（开工时登记，收工时清除）

| 文件 / 目录 | 占用方 | 任务 | 声明时间 |
| --- | --- | --- | --- |
| `knowledge/pom.xml` | WB-3（线 A） | 阶段 8 任务 3 拟引入 Spring AI 依赖；**因与既有 classpath 隔离测试互斥，已于 16:45 回退，未提交**（详见第四节 Spring AI 条）。占用登记保留至该项落地（当前决策：不走 A，下一步评估 B） | 2026-09-17 16:41（16:45 回退） |
| `knowledge/src/main/resources/db/migration/V9*` | WB-3（线 A） | 阶段 8 元数据迁移（线 A 号段 `V9`–`V11`，本次首用 `V9`，已提交） | 2026-09-17 16:41 |
| `knowledge/src/main/java/com/zhku/knowledge/retrieval/`、`routing/`、`rag/`、`eval/` 及同名测试目录 | WB-3（线 A） | 阶段 8 检索与生成侧（资格过滤/切分/profile/索引/召回/路由/生成守卫/评测）；`retrieval/` 与 `eval/` 未列入持续开发指令的"只改你地盘"清单，按"检索侧"处理并在此登记；线 B 未占用这些路径 | 2026-09-17 16:41 |
| `docs/evidence/` | WB-3（线 A） | 阶段 8 证据账本（`2026-09-17-阶段8-受控检索增强-证据账本.md`） | 2026-09-17 16:41 |
| `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java`、`LocalPreviewConversationFacade.java`、`ZhkuLocalPreviewConversationController.java` 及对应测试 | WB-3（线 A） | 阶段 8 装配：把五级编排器（澄清/通知/规则三级经语义适配）+ 检索增强作答接进本机预览回答链路；**只追加**已冻结字段（`route`/`handoffRequired`/`citationIds`），不改旧字段语义。**已完成并提交 `625d795`**（全量回归 248 绿）；占用保留至合并回主线 | 2026-09-17 17:24（17:45 完成） |

> 说明：状态板更新由各线在自己分支进行，主线看到的版本是最近一次合并时的状态；
> 跨线判断冲突时，以双方分支上的最新登记为准。

## 四、待协商事项

| 事项 | 提出方 | 说明 | 状态 |
| --- | --- | --- | --- |
| 前端归属移交 | WB-1 / CX-1 | 用户指定前端（`agent/frontend/`）由 WB-1 负责；原线 B 分工中"体验"部分相应剥离，CX-1 已确认不再改前端文件 | ✅ 已确认（2026-09-17） |
| Spring AI 依赖与 classpath 隔离断言 | 线 A 提问 / WB-1 决策 | **2026-09-17 用户经 WB-1 决策：不选 A**——隔离断言（`com.zhku.agent.agent.DefaultAgentFactory` 不可见于 knowledge 预览 classpath）是"不暴露 agent 高权限工具"的机械保障，属项目核心资产。执行顺序：先定位可见性来源 → 优先 `exclusion`/装配层隔离（不改断言、不加模块）→ 结构上绕不开再建独立模块（B）→ C 仅在 B 也不可行时考虑。**线 A 根因报告（2026-09-17 17:35，证据见下）**：① 实际失败断言是同一测试里的 `org.springframework.ai.chat.model.ChatModel` 不可见（`ZhkuLocalPreviewApplicationTest:20`、`ZhkuLocalAdminApplicationTest:27`）；② 依赖树取证（`mvn dependency:tree -Dincludes=org.springframework.ai:*`）：`knowledge → spring-ai-openai:1.0.9 → spring-ai-model:1.0.9`，`ChatModel`/`EmbeddingModel` 与 `spring-ai-vector-store` 的 `VectorStore` 分别落在 `spring-ai-model` / `spring-ai-vector-store`（jar 内类清单已核）；③ `<exclusion>` 不可行——排除 `spring-ai-model` 即等于排除要用的 `ChatModel`/`EmbeddingModel` 接口，适配器与 PgVectorStore 全部不可用；④ 装配层隔离不可行——surefire `classpathDependencyExcludes` 只让测试 JVM 看不到该依赖，产物 classpath 仍含 `ChatModel`，属**假绿**，比红灯更糟。结论：**"断言不变 + Spring AI 进 knowledge 模块"在结构上不可同时成立** → 按用户既定顺序，下一步走 **B（独立模块承载 Spring AI，knowledge 保持零 Spring AI 依赖；预览用 provider-neutral 接口 + 自实现客户端）** | ✅ 已决策；**根因已报告，待确认走 B** |
| 线 A 地盘清单与实际交付包不一致 | WB-3（线 A） | 持续开发指令第一节把线 A 地盘列为 `routing/`、`rag/`、`eval/`、`answer/`，但阶段 8 计划要求的检索侧实现落在 `com.zhku.knowledge.retrieval/`。已在第三节登记占用并继续推进（线 B 未占用该路径）；建议把 `retrieval/`、`docs/evidence/` 补入线 A 地盘清单 | ⏳ 待用户确认 |
| 「统一路由编排器」职责重叠 | WB-1 / CX-1 / 线 A | 分工表把"统一路由编排器"同时列在线 B 交付内容，而线 A 的拥有目录含 `knowledge/.../routing/`，阶段 8 计划也由 `KnowledgeAnswerOrchestrator` 承载五级路由测试。**确认边界**：路由判定规则与顺序（五级短路逻辑）归线 A 的 `routing/`；线 B 只提供工具与注册接口（`tools/`），由线 A 的编排器经接口调用，线 B 不改编排器文件；线 B 若需新增"工具分支"，先在状态板登记 | ✅ 双方已确认（CX-1 2026-09-17；线 A 2026-09-17 16:41） |
| 阶段 8/9/10 迁移号段过期 | WB-1 | 三份计划原写 `V7`/`V8`/`V9`，与既有迁移及已冻结号段冲突。**2026-09-17 用户确认处置**：阶段 8 改用 `V9`；阶段 9/10 号段待分配，由线 A 在阶段 8 交付后按"只追加"原则登记（预期 `V15`/`V16` 起）；三份计划文件已就地更正并标注"登记完成前不得创建迁移文件" | ✅ 已处置（待阶段 8 交付后分配 9/10 号段） |
| README 修正 | WorkBuddy | 根 README 与 knowledge README 仍写"未实现两轮追问"，与实际不符；待指派由谁修 | ⏳ 待指派 |
| agent/ 模块只读约定 | WorkBuddy | 首轮双方将 agent/ 视为只读资产；如需修改 ToolCallAgent 复用，先在此登记 | ⏳ 待确认 |
| 线 A / 线 B 归属 | WorkBuddy | 默认 WorkBuddy=线A、另一 Agent=线B；如互换，改本节与第二节两行即可 | ⏳ 待确认 |
| Spring AI 依赖与 classpath 隔离断言 | 线 A 提问 / WB-1 决策 | 线 A 提出 A（授权改隔离断言）/ B（独立模块）/ C（自实现客户端）三选一。**2026-09-17 用户经 WB-1 决策：不选 A**——该断言（`com.zhku.agent.agent.DefaultAgentFactory` 不可见于 knowledge 预览 classpath）是"不暴露 agent 高权限工具"的机械保障，属项目核心资产。执行顺序：先定位可见性来源，**优先用 `exclusion`/装配层隔离解决（不改断言、不加模块）**；若结构上绕不开，则建独立模块（B）；C 仅在 B 也不可行时考虑 | ✅ 已决策（待线 A 报告根因） |
| HTTP 装配是否现在做 | 线 A 提问 / WB-1 决策 | **2026-09-17 用户经 WB-1 批准：现在做**——属阶段 8 退出条件（跑通"…→ 引用校验 → 回答卡"）；只追加已冻结字段 `route`/`handoffRequired`，不改旧字段语义；占用已登记。注意：`ZhkuLocalPreviewConfiguration` 后续线 B 的 JDBC 配置切换也要动，合并时以"先合并方先落"处理，冲突时在状态板登记 | ✅ 已批准 |

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

- 线 A 阶段 8 交付（R1 链路跑通 + 引用校验 + 回答卡 + 基线报告）后合并回主线；
- 线 B `SSE 流式输出` 独立可编译测试通过后即可再次合并（不等待线 A）；
- 每次合并后由被合并方快进跟上新基线。

## 七、合并记录

| 时间 | 合并内容 | 来源分支 | 目标基线 | 检查清单通过 |
| --- | --- | --- | --- | --- |
| 2026-09-17 | 首次合并：会话持久化 JDBC（`V12`）+ 校园工具集 + Function Calling（33 文件） | `campus-line-b` | `7dce9e5` | ✅ 九项 |
| （线 A 合并待阶段 8 交付后执行） | | | | |

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
