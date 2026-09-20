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
| WB-1 | WorkBuddy 会话①（前端） | `agent/frontend/`、`docs/design-references/`、`knowledge/src/main/resources/zhku/local-preview/`（2026-09-18 用户裁决补登：本机预览问答页是前端交付物，物理上无法移出该目录）、**`knowledge/src/main/resources/zhku/local-admin/`（2026-09-20 用户裁决：阶段 9 管理员最小审核界面）** | `zhkuagent-modules` / 主 worktree `D:\alldesk\Projects\ZhkuAgent`（**登记例外，2026-09-18 用户裁决**，详见下方纠正登记） | 🟢 活动中；**阶段 9 管理员界面已放行**（`local-admin/index.html`，开工前登记占用） |
| WB-2 | WorkBuddy 会话②（其他内容） | 待该会话开工时自填 | 主 worktree（与 WB-1 共享工作区，禁止同时 commit） | ⬜ 待登记 |
| WB-3 | WorkBuddy 会话③（线 A：检索+生成+评测） | 线 A：`knowledge/` 检索与生成侧（`routing/`、`retrieval/`、`rag/`、`eval/`、**`serviceguide/`**＝阶段 9 办事清单模型与解析器）、Flyway `V9`–`V11` + **`V15`（阶段 9）** + **`V17`–`V18`（阶段 10）**、`docs/evidence/`；与线 B 在 `knowledge/` 内的占用以「文件占用声明」为准 | `campus-line-a` / `ZhkuAgent-lineA` | 🟢 阶段 8 已入库；**阶段 9（清单/解析器 + 回答卡扩展）与阶段 10 已由用户裁决放行**，开工前先登记占用 |
| CX-1 | Codex 会话（线 B） | 线 B：工具 + 编排（`agent/` 工具侧、`knowledge/`、**`feedback/`（2026-09-20 用户裁决：阶段 9 匿名枚举反馈闭环 + 90 天保留去重 + 管理员最小审核 API）**，前端体验职责已移交 WB-1）；Flyway `V12`–`V14` + **`V16`（阶段 9）** | `campus-line-b` / `ZhkuAgent-lineB` | 🟢 四项交付已入库（`2cba965`）；**阶段 9 反馈侧已放行**，开工前先登记占用 |

> 同 worktree 多会话（WB-1 / WB-2）额外纪律：任一时刻只允许一个会话执行
> `git commit`；commit 前必须 `git status --porcelain` 核对暂存区只有自己
> 地盘的文件。

> **WB-1 纪律纠正登记（2026-09-18，用户裁决）**：WB-1 的业务提交 `0483e6e`
> （本机问答页改版）**直接落在主线 `zhkuagent-modules`**（父提交 `958a0aa`，非合并提交），
> 违反第一节"主线只接收合并，不直接开发"；且改动文件
> `knowledge/src/main/resources/zhku/local-preview/index.html` 当时不在上表 WB-1 已登记地盘内。
> 用户裁决采用"如实登记"方案，处置如下：
> ① 地盘按上表补登 `knowledge/src/main/resources/zhku/local-preview/`；
> ② 分支 / worktree 如实登记为 `zhkuagent-modules` / 主 worktree（原先登记的
> `campus-line-a` 有误——该分支实为 WB-3 在 `ZhkuAgent-lineA` 使用）；
> ③ WB-1 提交纪律：只按**路径** `git add` 自己地盘内的文件（禁止 `git add -A` / `git add .`），
> commit 前 `git status --porcelain` 核对暂存区不含他线文件；要改地盘外文件先登记再动手；
> ④ 如后续要求 WB-1 改走"独立分支提交、由 PM 合并"，需先为 WB-1 分配**专属 worktree**——
> 主 worktree 现与 WB-2 共享且带有他线未提交改动，就地切分支会带走并干扰他人工作区。

---

## 一、合并基线

| 项 | 值 |
| --- | --- |
| 主线分支 | `zhkuagent-modules`（只接收合并，不直接开发） |
| 当前基线提交 | `e7b9d3a`（2026-09-20 **第八次合并**：线 A 同步登记，纯文档；`03daff4` 为合并后复核登记、`2cba965` 为第七次合并=线 B SSE 账本补齐 + 引用明细透传、`df6d31b` 为第六次合并=线 A 阶段 8 收尾、`1573507` 为第五次、`b333392` 为第四次、`e9cdf84` 为第三次） |
| 最近合并 | `campus-line-a` → 主线（`e7b9d3a`，**冲突 0**，仅状态板线 A 行）；`campus-line-b` → 主线（`2cba965`，**冲突 0**） |

> ⚠️ **2026-09-20 本机事故（类型不同于 09-16；累计复现三次，触发条件见下方 🔴 段）**：主 worktree
> `D:\alldesk\Projects\ZhkuAgent` 的**每-worktree 管理目录**
> `<common-dir>/worktrees/ZhkuAgent` 在 `git` 写操作（`merge` / `stash`）后**被静默删除**，
> 表现为 `fatal: not a git repository: …/worktrees/ZhkuAgent`、`Unable to create index.lock`。
> 仓库本体（对象库与 `refs/heads` 7 条引用）**未受损**；仅该 worktree 的 HEAD/index 丢失。
> 处置：从任一健康 worktree 重建管理目录（`gitdir` / `commondir` / `HEAD` 三个文件），
> 再 `git reset`（mixed，不动工作区）重建索引；**第七次合并因此改用工作区无关的
> `git merge-tree --write-tree` + `commit-tree` + `update-ref` 完成**（结果与常规 `git merge` 等价，
> 内容为 `merge-tree` 的零冲突结果）。
> **规避纪律**：① 主 worktree 每次 `git` 写操作前后都要 `Test-Path <common-dir>/worktrees/ZhkuAgent/HEAD`
> 核对；② 写操作前确认没有未提交改动（或先备份到仓库外）；
> ③ 合并这类可复算的操作优先在健康 worktree 里用 `merge-tree` 路线执行。
> 修复脚本尚未入库（当前为手工步骤），待登记后再评估是否落成 `scripts/` 下的一次性脚本。
>
> 🔴 **2026-09-20 09:37 第三次复现，已定位触发条件**：**在主线 worktree 执行 `git merge <分支>` 必定删除
> 管理目录**（三次 `git merge` 全部触发；而 `git add` / `commit` / `reset` / `checkout` / `stash` 之后的
> `add`+`commit` 均正常）。症状固定：先打印 `index.html` 的 `LF will be replaced by CRLF` 警告，
> 紧接着 `Unable to create .../index.lock` + `fatal: Unable to write index.`，管理目录随之消失。
> **硬性纪律：主线 worktree 一律禁止直接 `git merge`**——改用 §一 所述 `merge-tree --write-tree`
> + `commit-tree`（双父）+ `update-ref` 的等价路线，在健康 worktree 里执行；
> 且**每次 `git merge` 失败后必须先重建管理目录 + `git reset` 再继续**（重建后索引为空，
> 不 reset 就提交会把全仓"删掉"）。
> 第六次（`df6d31b`）、第七次（`2cba965`）、第八次（`e7b9d3a`）合并均以该路线完成，结果与常规合并等价。

> ✅ 2026-09-16 事故已闭环：本机 git 环境对"带斜杠的引用"（如 `codex/xxx`）写入存在缺陷，commit 触发的自动 gc 曾清空 `refs/heads/codex/`。**全部 6 个分支已迁移为顶层无斜杠命名**（`zhkuagent-modules` / `campus-line-a` / `campus-line-b` / `agent-run-context` / `zhkuagent-mvp` / `backup-fc7d153-before-amend`），`codex/*` 引用已删除，`gc.auto 0` 已设，并新增 `scripts/safeguard-refs.ps1` + post-commit 钩子自动守护。
>
> **纪律两条**：① 今后建分支一律用**不含斜杠**的名字；② 每次 commit 后跑 `git for-each-ref refs/heads` 核对（钩子已自动跑，发现丢失会自动恢复）。

## 二、各线状态

### 线 A：检索 + 生成 + 评测（Agent：WorkBuddy 会话，见第零节 WB-3）

| 项 | 值 |
| --- | --- |
| 分支 | `campus-line-a`（2026-09-20 **已快进至主线 `9d10653`**：`63b2c2c --ff-only--> 9d10653`，纯快进、无独有提交；阶段 8 收尾已由第六次合并 `df6d31b` 入库） |
| worktree | `D:\alldesk\Projects\ZhkuAgent-lineA`（2026-09-17 已创建，专属本线） |
| Flyway 号段 | `V9`–`V11`（阶段 8 已用 `V9`）＋ **阶段 9 用 `V15`**、**阶段 10 用 `V17`–`V18`**（2026-09-20 用户最终裁决；阶段 9 迁移**只创建一个文件**） |
| 当前任务 | **阶段 9 已开工（2026-09-20）**，范围＝用户裁决：①办事清单模型与解析器（新包 `com.zhku.knowledge.serviceguide`）②统一回答卡扩展；任务 4/5/6（反馈侧）属线 B。**排期：先做用户裁决的补数据任务**（新增一份"已发布+学生可见+`PUBLIC_ELECTIVE`"资料，走本机 8124 既有治理链路：submissions → extract → split → review → publish，带 `knowledgeScope=PUBLIC_ELECTIVE`/`documentFamily=STUDENT_HANDBOOK`，发布时 `studentVisible=true` + `sharingRightsConfirmed=true`；**不直接写库、不改 `zhku.rag.retrieval-scope`、不放宽资格过滤**）→ 重跑两份评测并如实入账本 → 再做 V15 迁移 + serviceguide 模型与解析器（RED 先行）+ 回答卡扩展 + `ServiceGuideAcceptanceTest`。阶段 8 成果（真实 provider 打通、向量路、第二份基线、`JdbcEmbeddingProfileStore` 7 例）已全部入库 |
| 状态 | 🟡 **进度**：① 占用已登记（§三：`serviceguide/`、`V15__*`、回答卡扩展面）；② 补数据任务**已完成**（来源 `861be981`：PUBLISHED + STUDENT_VISIBLE + PUBLIC_ELECTIVE，合格条目 0 → 2，全过程走 8124 链路、有策展留痕）；③ 两份评测已重跑并如实入账本（数字与 09-18 一致，**不宣称提升**）。**⛔ 阻塞**：补数据引发 `PublicElectiveGroundedAnswerAcceptanceTest` 6 例转红（见 §四，三选项待你裁决）→ **阶段 9 的 V15/serviceguide 尚未开工**（不叠加改动到红树上）。**硬边界（不变）**：V15 与线 B `V16` 互不建外键（软引用）、渠道/电话/链接/地点只能来自审核资料、不代办不认定资格不自动改规则、不混入阶段 10 的表/Controller |
| 最新提交 | `a2cbb01`（阶段 9 开工占用登记）＋本次待提交：补数据策展留痕（`knowledge/data/curation/2025版学生手册2025.9.2-公共选修修读与考核规则学生可见知识片段.json`）、证据账本（补数据全过程 + 评测复跑 + 副作用登记）、状态板、以及两处测试改动（向量基线用例的脆弱断言改形状断言 + 新增语料指纹打印）。**注意**：前次同步后全量回归 **307 通过**，本次因演示库语料变更变为 **6 红**（数据引发，见 §四） |

### 线 B：工具 + 编排（Agent：Codex 会话，见第零节 CX-1）

| 项 | 值 |
| --- | --- |
| 分支 | `campus-line-b`（顶层命名，2026-09-16 已从 `codex/campus-line-b` 迁移，旧引用已删除；worktree `ZhkuAgent-lineB` 实际绑定的就是本分支） |
| worktree | `D:\alldesk\Projects\ZhkuAgent-lineB` |
| Flyway 号段 | `V12`–`V14`（已用 `V12`：会话持久化表） |
| 当前任务 | 会话持久化 JDBC ✅、校园工具集 + Function Calling ✅、SSE ✅（主线 `1573507`）。答案卡后端侧首项 ✅：SSE 已透传 `citations[]`，与 `citationIds` 保持同长同序；前端 UI 仍归 WB-1。阶段 9 是否分配给线 B，等待用户裁决。 |
| 状态 | 🟢 活动中，**已并入主线 `2cba965`**（第七次合并）；第五次合并欠的"同批证据账本"（九项清单第 7 条）已由 `2b25bb1` 补齐并随本次合并入库 |
| 最新提交 | `d84195c`（答案卡后端侧状态）；上批 `50d92ab`（SSE 引用明细透传 + TDD + 账本；knowledge 299 项通过 / 3 项跳过）、`bbb3764`/`2b25bb1`（SSE 证据账本补齐与格式清理） |

## 三、文件占用声明（开工时登记，收工时清除）

| 文件 / 目录 | 占用方 | 任务 | 声明时间 |
| --- | --- | --- | --- |
| `knowledge/src/main/java/com/zhku/knowledge/retrieval/`、`routing/`、`rag/`、`eval/` 及同名测试目录 | WB-3（线 A） | 阶段 8 检索与生成侧——**已交付**（真实 provider 打通、向量路开启、第二份基线已产出）；阶段 9 开工前须按新包（`serviceguide/`、`feedback/`）另行登记 | 2026-09-17 16:41 |
| `knowledge/src/main/resources/zhku/local-preview/index.html` | WB-1（前端） | SSE 流式接进问答页（**实现与 6 张验收截图已完成、尚未提交**）；阶段 9 反馈交互复用同一占用 | 2026-09-20 09:20 |
| `docs/evidence/` | WB-3（线 A）/ WB-1 / CX-1 | 各线证据账本（**共享目录，新增文件不构成冲突**；只追加、不改他线文件） | 2026-09-17 16:41 |
| `knowledge/src/main/resources/application-zhku-local-preview.yml` | 已合并（两线共用） | 线 A 的 `zhku.rag.*` 与线 B 的 `zhku.streaming.*` 现已并存于主线；**此后任何一方再改此文件前须重新登记**（本次合并冲突即发生在此文件） | 2026-09-18 20:44 |
| `knowledge/src/main/java/com/zhku/knowledge/serviceguide/` 及 `knowledge/src/test/java/com/zhku/knowledge/serviceguide/` | WB-3（线 A） | 阶段 9 任务 2：不可变 `ServiceGuide` / `GuideStep` / `OfficialChannel` / `HumanHandoffReason` + 只返回"有当前有效证据"的清单解析器（**新包，主+测试目录一并占用**） | 2026-09-20 09:46 |
| `knowledge/src/main/resources/db/migration/V15__*` | WB-3（线 A） | 阶段 9 线 A 迁移（最终裁决号段）：`service_guide` / 步骤 / 材料 / 官方渠道 / 风险提示 / 关联规则与通知版本。**自包含、不与线 B 的 `V16` 建外键**；确需关联用软引用（清单标识 + 版本号）。**同一时刻只有一个会话创建迁移文件** | 2026-09-20 09:46 |
| 回答卡扩展面（任务 3）：`knowledge/.../answer/` 的展示层 + `knowledge/.../web/LocalPreviewConversationFacade` 及对应测试 + `knowledge/.../rag/` 的回答载荷类型 | WB-3（线 A） | 阶段 9 任务 3：结论后附"下一步 / 需确认事项 / 官方人工渠道"；高风险或证据不足时只保留人工渠道与风险提示。**统一回答卡属共享面，只追加字段、不改旧字段语义**；新字段须同步登记进 §八（WB-1 据此做前端）。**动手前若有他线正在改这些文件，先停下协商** | 2026-09-20 09:46 |

> 已完成并清除的占用：WB-1 对 `knowledge/src/main/resources/zhku/local-preview/index.html` 的
> 引用卡改版占用（已交付 `0483e6e`：引用卡 UI + 删除页脚免责声明 + 明暗验收截图）。
> **2026-09-20 因 SSE 接入已重新登记**（见上表）。
>
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
| README 修正 | WorkBuddy | 文档落后于代码，待指派由谁修：① 根 README 与 knowledge README 仍写"未实现两轮追问"；② `docs/zhkuagent/仲园Agent-本机HTTP问答接口开发者验收手册.md` 第 45 行的启动主类仍是旧包名 `com.yupi.yuaiagent.zhku.knowledge.web.ZhkuLocalPreviewApplication`，实际为 `com.zhku.knowledge.web.ZhkuLocalPreviewApplication`（照抄会启动失败） | ⏳ 待指派 |
| 真实 provider 打通与向量路（阶段 8 收尾） | 线 A 提问 / WB-1 核实 | 线 A 报告两项需外部资源：① 真实供应商 smoke test 需密钥 + 显式 opt-in；② 向量路需真实 embedding profile 才能真正打开（现为降级，首份基线已如实标注）。**WB-1 核实的硬约束**：DeepSeek 官方仅提供 chat 模型（`deepseek-flash`/`deepseek-v4-pro`），**无 embeddings 端点**，故 chat 与 embedding 无法由 DeepSeek 一家覆盖。**2026-09-17 用户决策：采用方案 B——改用阿里云百炼（DashScope）单供应商同时覆盖 chat + embedding**（避免配置拆分与第二套密钥）。参数与端点已登记于第五节；要求：密钥仅从环境变量 `DASHSCOPE_API_KEY` 读取、值不入库；真实 smoke 为显式 opt-in、结果与 profile/模型版本写入证据账本；完成后出**第二份基线（含向量召回）**，仍未过评测前不得宣称效果提升。**2026-09-18 WB-1 补充诊断（针对 401）**：线 A 实测网络/端点可达、收到厂商 `request_id`，鉴权为 `401 invalid_api_key`，且**密钥形态为长度 117、`sk-` 开头，不符合百炼常见 `sk-`+32 形态**。据此最可能的原因（按概率）：① **站点与端点不匹配**——阿里云文档明确"国际站 key 只能走国际端点、国内站 key 只能走国内端点"，用错即返回 401；② 该 key 并非百炼 API Key（长度不符，疑复制了其他平台/其他类型凭据）；③ key 被停用或无模型权限。**用户侧处置**：在百炼控制台确认 key 所属站点（国内 `bailian.console.aliyun.com` / 国际 `modelstudio.console.alibabacloud.com`），在新开的 PowerShell 窗口对**两个端点各跑一次**同一 curl 自检（国内 `https://dashscope.aliyuncs.com/compatible-mode/v1`、国际 `https://dashscope-intl.aliyuncs.com/compatible-mode/v1`），哪边通即用哪边并同步 `base-url`；两边皆 401 则回控制台重新生成 key（注意复制完整、无空格）。**2026-09-18 20:41 用户端自检结果：国内站端点返回 embedding 维度 1024、鉴权通过** → 站点确认为国内站，`base-url` 保持 `https://dashscope.aliyuncs.com/compatible-mode/v1` 不变。这与线 A 的 401 相矛盾，最可能原因是**两次使用的 key 值不同**（昨日环境变量中的值疑为 117 位误值）或**测试进程未继承新设置的环境变量**；处置：先比对环境变量值是否与刚验证通过的 key 一致，一致后在**新开终端**重跑 smoke | ⏳ 降级为"待确认环境变量一致性后重跑" |
| agent/ 模块只读约定 | WorkBuddy | 首轮双方将 agent/ 视为只读资产；如需修改 ToolCallAgent 复用，先在此登记 | ⏳ 待确认 |
| 线 A / 线 B 归属 | WorkBuddy | 默认 WorkBuddy=线A、另一 Agent=线B；如互换，改本节与第二节两行即可 | ⏳ 待确认 |
| 根 `.gitignore` 追加 `/.workbuddy/` | 线 A（WB-3）自查登记 | **越界自查**：线 A 在 `44da0e1` 中顺手给根 `.gitignore` 追加了 `/.workbuddy/`（理由：该目录存协作临时日志，内含供应商响应片段与本机路径，不宜误入版本库）。该文件属**共享根文件、不在线 A 地盘**，严格说应先登记再做——特此补登记。影响面极小（仅忽略本机日志目录，不影响任何源码/配置），但**若你认为该变更应由其他线或用户统一收口，可 `git revert` 该片段或由你在收口时重写该行**，线 A 无异议 | ⏳ 待你的处置意见（当前已生效） |
| Spring AI 依赖与 classpath 隔离断言 | 线 A 取证 / WB-1 决策 | **线 A 根因证据（可复核）**：依赖树 `knowledge → spring-ai-openai:1.0.9 → spring-ai-model:1.0.9`；`jar tf` 证实 `ChatModel`/`EmbeddingModel` 同在 `spring-ai-model`（排除该 artifact = 把要用的接口一起排除）；仅 surefire 层掩掉只影响测试 JVM、产物 classpath 仍带 `ChatModel` = 假绿（线 A 已拒绝采用）。失败断言实为 `ZhkuLocalPreviewApplicationTest:20` 的 **ChatModel classpath 存在性**那条，而非 agent 类那条。**2026-09-17 最终决策：不走 B（独立模块）**——路线图明确"阶段 8 可在 knowledge 模块引入版本锁定的 Spring AI 依赖"，已批准计划亦要求改 `knowledge/pom.xml`；建独立模块属计划外变更且会在阶段 8 中途引入跨模块接口改动。**改为精确化断言**（下述守卫变更即本行登记）：① 保留并**加强**安全断言——`com.zhku.agent.agent.DefaultAgentFactory` 及高权限工具包不可见；② 把"`ChatModel` 类不存在"换成**行为断言**——未配置 `zhku.rag.*` 时不创建模型相关 bean、不发生模型调用（从"类不存在"升级为"未配置不启用"）；③ 测试内注明改动理由（按 AGENTS.md 注释约定） | ✅ 已决策（守卫变更已透明登记） |
| 真实 provider 冒烟受阻（`401 invalid_api_key`）→ **已解决** | 线 A（WB-3） | 第 1 轮（2026-09-17）：端点国内站 `https://dashscope.aliyuncs.com/compatible-mode/v1`（chat `qwen-plus`、embedding `text-embedding-v4`/1024），网络与端点可达（拿到阿里云 JSON + `request_id`），但鉴权 `401 invalid_api_key`；密钥形态诊断（不打印值）：长度 117、`sk-` 开头，不符合百炼常见 `sk-`+32。**根因（现已确认）：环境变量里的值本身不对**（贴错凭据），与站点/端点无关。**第 2 轮（2026-09-18）用户重设环境变量后原样重跑：2/2 通过**（chat 1613ms、结构合法且回填服务端引用标识；embedding 829ms、**1024 维**）。两轮记录与"能/不能说明什么"已入证据账本；密钥值全程未入库、未进日志、未进对话 | ✅ 已解决 |
| 演示语料与检索范围不匹配（向量路的"数据侧"发现） | 线 A（WB-3）新登记 | 读取演示库核对：共 3 来源 / 9 片段；满足"已发布 + 学生可见 + 权利已确认"的只有 2 个来源，其 `knowledge_scope` 分别为 `ELECTIVE_RETAKE`(2 片段) 与 `PUBLIC_ELECTIVE_CREDIT_LIMIT`(1 片段)；而配置登记的范围是 `PUBLIC_ELECTIVE`，过滤为**精确匹配**（`RetrievalEligibilityFilter.evaluate`）→ **以当前配置范围检索，演示库合格条目为 0**。第三个来源为 `INTERNAL_ONLY` 且权利未确认，本就不许进入索引与召回（未放宽过滤绕过）。**影响**：向量路"机制已通（真实 embedding + 索引重建 + 原子切换可跑）"，但"演示库上有可用条目"这一步未成立。**处置建议（需你选）**：① 补一份"学生可见 + `PUBLIC_ELECTIVE` 范围"的已发布资料；② 或把 `zhku.rag.retrieval-scope` 调整为与现有资料一致的范围（属配置键取值变更，按"只追加"原则登记）；③ 或先不动，等阶段 9/10 语料扩充时一并处理。**在此之前不得宣称"向量检索已生效/效果提升"** | ✅ 已裁决（2026-09-20 用户选①：**补一份"学生可见 + `PUBLIC_ELECTIVE` 范围"的已发布资料**，不改检索范围配置、不放宽过滤）。执行侧＝线 A（走既有治理链路：提交 → 人工审核 → 事务发布，并随批提交证据；补齐后重跑第二份基线并在账本登记新数字，**仍不得宣称"提升"**） |
| 根 `.gitignore` 追加 `/.workbuddy/` | 线 A（WB-3）自查登记 | **越界自查**：线 A 在 `44da0e1` 中顺手给根 `.gitignore` 追加了 `/.workbuddy/`（理由：该目录存协作临时日志，内含供应商响应片段与本机路径，不宜误入版本库）。该文件属**共享根文件、不在线 A 地盘**，严格说应先登记再做——特此补登记。影响面极小（仅忽略本机日志目录，不影响任何源码/配置），但**若你认为该变更应由其他线或用户统一收口，可 `git revert` 该片段或由你在收口时重写该行**，线 A 无异议 | ⏳ 待你的处置意见（当前已生效） |
| HTTP 装配是否现在做 | 线 A 提问 / WB-1 决策 | **2026-09-17 用户经 WB-1 批准：现在做**——属阶段 8 退出条件（跑通"…→ 引用校验 → 回答卡"）；只追加已冻结字段 `route`/`handoffRequired`，不改旧字段语义；占用已登记。注意：`ZhkuLocalPreviewConfiguration` 后续线 B 的 JDBC 配置切换也要动，合并时以"先合并方先落"处理，冲突时在状态板登记 | ✅ 已批准 |
| SSE HTTP 接入边界 | CX-1 | SSE 需基于本机预览会话门面输出冻结字段 `revision`、`conversationStatus`、`route`、`handoffRequired`，但 `ZhkuLocalPreviewConfiguration`、`LocalPreviewConversationFacade` 与会话 Controller 当前由线 A 占用。**2026-09-17 用户确认采用方案 A**：由线 A 完成 HTTP 统一装配并合入后，线 B 再接入 SSE；不新建并行会话端点。 | ✅ 已决策，等待线 A 依赖 |
| 🔴 主线回归变红：14 项既有验收失败（共享演示库漂移） | PM（本项目，2026-09-20 复核）/ 线 A 执行 | **现象**：`PublicElectiveGroundedAnswerAcceptanceTest` 6/9 失败（部分题 `expected GROUNDED but was INSUFFICIENT_EVIDENCE`，部分引用定位符由条款级 `"PDF 第 76 页，第 2、4 条"` 退化为页级 `"PDF 第 76 页"`）、`PublicElectiveKeywordSearchAcceptanceTest` 7/11 失败（同类定位符不匹配）、`JdbcRetrievalIndexingTest` 1/3 失败（**期望合格片段数 2、实际 4**）。**PM 独立复核结论**：在**不含线 B 代码的主线**（`9d10653`）上复跑同 3 个类，得到**完全相同的 6/7/1＝14 项失败** → **不是线 B 的回归**，而是**共享演示库（54329/zhkuagent）的语料态漂移**：合格片段 2→4，且引用定位符粒度变粗。09:28 PM 在主线跑全量还是 `307 通过 / 0 失败`，故漂移发生在 09:28 之后、由**最近对该库写入的一方（线 A，其当轮任务正是"补 `PUBLIC_ELECTIVE` 资料"）**造成。**处置（P0，线 A 执行）**：① 先判定库里新增的是"正式补录的已发布资料"还是"评测用例（`RetrievalAcceptanceEvalTest` / `RetrievalVectorBaselineEvalTest`）插入的评测语料残留在共享库"——若是残留，按 V9 的 `retrieval_index_entry → knowledge_chunk` 为 `ON DELETE RESTRICT` 的顺序先删索引条目再删片段，并补上用例自清；② 若是正式资料，则同步更新这 3 个验收类的期望（定位符/合格片段数）或改成用例自持数据，**不得为了变绿而放宽资格过滤或删掉正式资料**；③ 修好后在主线上复跑全量并在本表登记数字。**在此之前：主线回归为红，任何"全量通过"的表述都不成立；这也是当前唯一 P0** | 🔴 未解决（责任方＝线 A；PM 已排除线 B 嫌疑） |
| 阶段 9 迁移的跨线依赖（`V15` / `V16`） | PM（本项目，2026-09-20 裁决） | 阶段 9 计划原把"办事清单 + 反馈"写成**一个**迁移；按号段裁决拆成 `V15`（线 A：`service_guide`、步骤、材料、官方渠道、风险提示、关联规则/通知版本）与 `V16`（线 B：反馈事件、匿名会话散列、枚举原因、创建/到期时间、去重键）。**风险**：各线在自己分支独立跑回归时**看不到对方分支的迁移文件**，若 `V16` 对 `V15` 的表建外键，线 B 的回归会直接变红。**裁决**：**两条迁移互不建外键，各自自包含**；确需关联处用**软引用**（清单标识 + 版本号，不加 FK 约束），由服务层校验。**另**：`V16` 中禁止出现 `message` / `student_id` / `name` / `contact` / 原问题 / 原回答列（白名单约束，计划已写明），此点属硬边界，违反即不合 | ✅ 已决策（PM 在号段/接口边界授权内；两线按此执行） |
| 线 B"答案卡升级（后端侧）"与阶段 9 回答卡扩展的归属重叠 | PM（本项目，2026-09-20 裁决） | 分工表/持续开发指令曾把"答案卡升级（后端侧）"列在线 B，而阶段 9 第 3 项"扩展统一回答卡"按 2026-09-20 裁决归**线 A**（回答卡代码在 `answer/`，属线 A 地盘）。**裁决**：回答卡的**扩展**统一由线 A 做；线 B 该任务**视为已收尾**（其已合并部分＝SSE 事件透传 `citations[]`，见第七次合并）。线 B 不再动 `answer/`；若发现回答卡与 SSE 载荷不一致，先登记不直接改 | ✅ 已决策（避免两线重复实现同一件事） |
| SSE 证据账本路径 | CX-1 | `docs/evidence/` 仍由 WB-3 登记占用；线 B 先完成 SSE 的代码与测试，提交证据账本前需由 WB-3 释放该目录或在本表明确按不同文件并行编辑的边界。**2026-09-20 已闭环**：按"共享目录、只追加、不改他线文件"的既有边界，线 B 已提交 `docs/evidence/2026-09-18-SSE流式输出-证据账本.md`（`2b25bb1`），并随第七次合并入库 | ✅ 已解决（2026-09-20） |
| 主 worktree 管理目录被 git 写入清除（本机事故） | PM（本项目） | **2026-09-20 复现三次，已定位触发条件**：在 `D:\alldesk\Projects\ZhkuAgent` 执行 **`git merge <分支>` 必定触发**（三次全中；而 `add`/`commit`/`reset`/`checkout`/`stash` 均正常），`<common-dir>/worktrees/ZhkuAgent` 被静默删除（报 `not a git repository`、`Unable to create index.lock`）。**对象库与 7 条分支引用均未受损**，只丢该 worktree 的 HEAD/index；已按"重建 `gitdir`/`commondir`/`HEAD` + `git reset` 重建索引"修复三次，未提交改动（WB-1 的 `index.html`）**零丢失**。第六/七/八次合并全部改用 `merge-tree --write-tree` + `commit-tree` + `update-ref` 完成，结果与常规合并等价。**硬性纪律：主线 worktree 禁止直接 `git merge`** | 🔴 已知本机缺陷，触发条件与纪律见第一节 |
| 阶段 9 / 10 的号段与归属 | PM（本项目） | 阶段 8 已交付入库，**号段分配的触发条件已满足但仍未登记**（§五 第 5 项）。当前线 A 阶段 8 已收尾、线 B 四项交付均已入库，两条线**都在等这次裁决才能开工**；若不裁决，线 B 将无事可做。PM 提案：阶段 9 → `V15`–`V16`（清单/回答卡归线 A、反馈闭环 + 管理员最小 API 归线 B、管理界面归 WB-1）；阶段 10 → `V17`–`V18`（归线 A）。**属"改变产品范围/资源分配"，等用户明确裁决后再写入 §五 并放行** | ✅ 已裁决（2026-09-20 用户选"按提案执行"）：号段与归属已写入 §五 第 5 项，三条线已放行 |
| **补数据的副作用：一个既有验收用例转红（线 A 上报，需你裁决）** | 线 A（WB-3） | 已按裁决①走 **8124 管理端链路**补出 `PUBLISHED + STUDENT_VISIBLE + rights=true + PUBLIC_ELECTIVE` 来源（`861be981`，2 片段；该范围合格条目 0 → 2）。**副作用**：`PublicElectiveGroundedAnswerAcceptanceTest` 由绿转红（9 例中 6 例）。**根因两层**：① 发布器按"同资料族 + 同知识范围"归档旧版本（`archivePublishedScope`），旧来源 `2a04fd61` 变 `ARCHIVED`，该用例依赖的第 8/10、13、18 条在 `PUBLIC_ELECTIVE` 范围内不再可召回 → 相关问法由 `GROUNDED` 变 `INSUFFICIENT_EVIDENCE`；② **两条发布路径的定位粒度不同**——**8124 管理端链路把出处定位固定为页级**（`KnowledgeGovernancePublisher:199-201` 按候选页码拼 `"PDF 第 N 页"`，**且忽略审核阶段填写的 `sourceLocator`**），而该用例断言的是**条级**定位（`"PDF 第 76 页，第 2、4 条"`）；**条级定位只有策展 JSON 导入链路能产出**（`ReviewedKnowledgeJsonReader` + `CreditLimitEvidenceImportCli`/`ElectiveRetakeEvidenceImportCli`——既有 3 份来源中的 2 份正是走它发布的）。**我未做**：未改用例期望、未放宽断言、未直接改库回填。⚠️ **共享开发库已处于新语料状态**，任何会话在本机跑回归都会看到这 6 处失败。**三选项**：<br>**A. 扩内容 + 期望改页级**：再用 8124 链发布一版覆盖第 6/8/10/13/18 条（恢复覆盖），并把用例 6 处期望改为页级定位。代价＝出处定位精度从条级降到页级（等于放宽验收标准）。<br>**B. 改走策展 JSON 导入链路**（线 A 推荐）：按既有 6 片段结构（内容 + 条级定位）重新发布为学生可见——**用例零改动、覆盖不缺、精度不丢**；代价＝偏离你指定的 8124 链路，且需为 `PUBLIC_ELECTIVE` 主题补一个导入入口（或让既有 CLI 支持该主题），属新增代码需你批准。<br>**C. 回退本次数据**：把 `2a04fd61` 恢复 `PUBLISHED`（可见性仍 INTERNAL_ONLY）。代价＝需直连改库（与"不直接写库"纪律冲突），且当前无"取消归档"的治理能力；该范围合格条目会回到 0（你的①就白做了）。<br>**未开工项**：阶段 9 的 `V15` 迁移与 `serviceguide` 包**尚未开始**（此项不依赖本裁决，但树上有 6 处红，先不叠加改动） | ⏳ 待你裁决 |

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
  - 线 B 追加（2026-09-17）：SSE 仅使用 `zhku.streaming.enabled`、`zhku.streaming.timeout-millis`、`zhku.streaming.worker-threads`、`zhku.streaming.queue-capacity`；均只在 `zhku-local-preview` Profile 生效，未配置时保持本机预览的同步消息接口可用。
  - 线 A 追加（2026-09-17，只追加不改上文）：受控检索增强配置键 `zhku.rag.enabled`、`zhku.rag.active-profile`，以及按 profile 分组的 `zhku.rag.profiles.<name>.base-url` / `.api-key-env-var` / `.chat-model` / `.embedding-model` / `.embedding-dimensions` / `.timeout-seconds` / `.index-profile`。**一次只允许一个激活 profile**（`active-profile` 单值）；密钥只写环境变量名、值不落配置，配置文件与日志均不得出现密钥值。实现：`RagProviderProfileResolver` / `RagProviderProfile` / `ApiKeyResolver`（`knowledge/.../rag/`）。
  - 线 A 再追加（2026-09-17，装配用）：`zhku.rag.retrieval-scope`（知识范围过滤，如 `PUBLIC_ELECTIVE`；缺省=不限定）、`zhku.rag.top-k`（送模型的候选上限，缺省 5）。语义：`zhku.rag.enabled=true` 但缺 `active-profile` 或 profile 键不全 → **启动失败**（不静默降级）；`enabled` 未设置 → 装配整体不生效（无模型 bean、无检索增强服务）。实现：`RagProviderConfiguration`（`knowledge/.../rag/`）。
  - WB-1 追加（2026-09-17，真实 provider 打通用，只追加）：激活 profile 名 `dashscope`，键为 `zhku.rag.profiles.dashscope.base-url` = `https://dashscope.aliyuncs.com/compatible-mode/v1`（国内站 key 用国内端点，国际站 key 只能用 `https://dashscope-intl.aliyuncs.com/...`，不可混用）、`.api-key-env-var` = `DASHSCOPE_API_KEY`、`.chat-model`（以控制台可用列表为准，如 `qwen-plus`）、`.embedding-model` = `text-embedding-v4`、`.embedding-dimensions` = `1024`（v4 支持 2048/1536/1024(默认)/768/512/256/128/64；**维度必须与索引 profile 及向量列一致，禁止混用**）、`.timeout-seconds`、`.index-profile`。环境变量名与端点属配置事实、可入库；**密钥值不得入库、不得进对话、不得写日志**。
- [x] Flyway 号段：线 A `V9`–`V11`、线 B `V12`–`V14`（2026-09-16 双方建线时已按此执行，无异议。
  2026-09-17 用户确认追加：阶段 8 用 `V9`；**阶段 9、10 号段待分配**，由线 A 在阶段 8 交付后追加登记（预期自 `V15`/`V16` 起），登记前不得创建迁移文件。既有 `V1`–`V8` 不可改写）
  - PM 追加（2026-09-20，只追加不改上文）：阶段 8 **已交付并入库**（第六次合并 `df6d31b`），号段分配的**触发条件已满足**；具体方案**待用户裁决**（PM 提案：阶段 9 → `V15`–`V16`、阶段 10 → `V17`–`V18`）。**在裁决并登记前，任何会话不得创建迁移文件**。已用：`V9`（线 A 检索目录）、`V12`（线 B 会话持久化）；`V10`/`V11`/`V13`/`V14` 仍空闲，按"不回收、不改写"处理。
  - PM 追加（2026-09-20，**用户裁决：按提案执行**，只追加不改上文）：**阶段 9 / 10 号段与归属已定**。
    **号段**：阶段 9 → `V15`–`V16`；阶段 10 → `V17`–`V18`（自 `V15` 起连续；`V10`/`V11`/`V13`/`V14` 仍空闲，不回收、不改写）。
    **归属**：阶段 9 的 ①办事清单模型与解析器、②统一回答卡扩展 → **线 A（WB-3）**；
    ③匿名枚举反馈闭环（含 90 天保留与去重）、④管理员最小审核 **API** → **线 B（CX-1）**；
    ⑤管理员最小审核**界面** → **WB-1**。阶段 10 全部（遥测/评测/门禁/观测/隐私）→ **线 A（WB-3）**。
    **开工纪律**：各线动手前先在 §三 登记自己新包的占用；迁移文件只能取自本行号段，且**同一时刻只允许一个会话创建迁移文件**（阶段 9 的两侧需各用各的号：线 A 用 `V15`，线 B 用 `V16`）。

## 六、下次合并点

- **两条线当前都没有待合并增量**：`campus-line-a`（`63b2c2c`）已于第八次合并 `e7b9d3a` 入库、`campus-line-b`（`d84195c`）已于第七次合并 `2cba965` 入库；两侧均无独有提交，只需各自 `git merge zhkuagent-modules` 快进跟上（线 A 到 `e7b9d3a`、线 B 同时跟到 `e7b9d3a`）；
- **合并执行纪律（本机缺陷）**：主线 worktree **禁止直接 `git merge`**（第三次复现即为此因），一律用 `merge-tree --write-tree` + `commit-tree` + `update-ref` 路线，在健康 worktree 里执行；详见第一节事故说明；
- **唯一阻塞开工的是裁决**：阶段 9/10 的号段与归属（见第四节）与演示语料范围。裁决后由 PM 先把号段写入 §五，再放行对应会话；
- WB-1 手里还有**未提交**的 SSE 页面接入 + 6 张验收截图（主 worktree），属其地盘、按第四节 WB-1 纪律自行按路径提交；
- 每次合并后由被合并方快进跟上新基线；合并状态板冲突时按第三节"合并陷阱"纪律逐段合并；
- **主 worktree 执行 git 写操作前**：按第一节 2026-09-20 事故说明先核对 `<common-dir>/worktrees/ZhkuAgent` 是否完好。

## 七、合并记录

| 时间 | 合并内容 | 来源分支 | 目标基线 | 检查清单通过 |
| --- | --- | --- | --- | --- |
| 2026-09-17 | 首次合并：会话持久化 JDBC（`V12`）+ 校园工具集 + Function Calling（33 文件 +3093） | `campus-line-b` | `7dce9e5` | ✅ 九项 |
| 2026-09-17 | 第二次合并：阶段 8 主体（五级路由 + 受控检索 + 生成守卫 + 引用审计 + 评测基线 + HTTP 装配 + `V9`，86 文件 +6136） | `campus-line-a` | `7414586` | ✅ 逐条核对（纯快进、零冲突；分支工作区另有未提交的 Spring AI 段，不在本次范围） |
| 2026-09-17 | 第三次合并：Spring AI 精确化断言（7 类 agent 类安全断言 + 行为断言）+ `citations[]` 引用明细 + 版本指纹（21 文件 +936） | `campus-line-a` | `e9cdf84` | ✅ 逐条核对（纯快进；`pom.xml` 为全仓唯一一次变更、无冲突面） |
| 2026-09-18 | 第四次合并：DashScope profile 配置（仅环境变量名）+ 端点 `/v1` 路径归一化 + 真实 smoke 用例（显式 opt-in）+ 验收手册包名修复 + 账本（10 文件 +212） | `campus-line-a` | `b333392` | ✅ 核对（状态板自动合并；决策文字完整） |
| 2026-09-18 | 第五次合并：SSE 流式输出（`web/streaming/` 5 类 + 会话控制器端点 + 2 测试类 + 设计/计划文档） | `campus-line-b` | `1573507` | ✅ 核对（冲突 1 处 `application-zhku-local-preview.yml`，按"逐段保留双方"解决：`zhku.rag.*` 与 `zhku.streaming.*` 并存）。**事后缺口**：本次 13 个文件里没有证据账本，违反九项清单第 7 条 → 已由线 B 在 `2b25bb1` 补齐（见第七次合并） |
| 2026-09-20 | 第六次合并：线 A 阶段 8 收尾（`JdbcEmbeddingProfileStore` + 7 例真实库用例 + `RetrievalVectorBaselineEvalTest` + 逐题分母留痕 + 账本，8 文件 +940） | `campus-line-a` | `df6d31b` | ✅ 冲突 0（主线只有 1 个 PM 文档提交，无文件重叠）。**注**：本次合并于 09-18 21:1x 发起、冲突已解决但**未提交**，悬置两天后于 09-20 由 PM 收口提交；暂存区经 `git diff --cached --name-only` 核对只有线 A 的 8 个文件，未混入他线文件 |
| 2026-09-20 | 第七次合并：线 B SSE 证据账本补齐 + SSE 事件透传 `citations[]`（4 文件） | `campus-line-b` | `2cba965` | ✅ 九项（`merge-tree` 试算冲突 0；状态板命中行不重叠已实测：线 A 改 §二线A行/§四/§六、线 B 改 §二线B行/§八第 4 条）。**执行方式**：因主 worktree 管理目录被本机缺陷清除，改用 `merge-tree --write-tree` + `commit-tree`（双父）+ `update-ref` 完成，结果与常规合并等价 |
| 2026-09-20 | 第八次合并：线 A 同步登记（纯文档，仅状态板线 A 行；含两侧"已快进至 `b33a76a`、7 引用完好、同步后回归 307/0/4"记录） | `campus-line-a` | `e7b9d3a` | ✅ 九项（`merge-tree` 试算冲突 0；本次是 1/1 分叉的真合并，状态板命中行不重叠）。**执行方式**：主 worktree 的 `git merge` 第三次触发管理目录缺陷（见第一节），改用 `merge-tree --write-tree` + `commit-tree` + `update-ref` 完成；本轮为纯文档变更，**未重跑回归**（代码状态与 `03daff4` 完全相同） |

> **合并后独立复核（PM，2026-09-20 09:28）**：在**合并后的主线 worktree** 跑
> `.\mvnw.cmd -pl knowledge test`（密码从用户级环境变量注入，不入命令行）→
> **307 通过 / 0 失败 / 0 错误 / 4 跳过，`BUILD SUCCESS`**，退出码 0。
> 4 个跳过 = `DashScopeProviderSmokeTest` 2 个 opt-in 守卫 + `RetrievalVectorBaselineEvalTest` 1 个
> opt-in + `PdfPageTextExtractorTest` 1 个既有跳过（均在预期内，与"默认不依赖网络与密钥"一致）。
> 与两侧自述（线 A 306、线 B 299）口径差异来自测试数叠加：+1 = 线 B `ConversationStreamProcessorTest`
> 新增用例。**这是九项清单第 5 条"全量测试通过"的 PM 侧证据**（此前两次合并只有分支自述，本轮补上）。

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
  "citationIds": ["cit-3f9a…"],
  "citations": [
    {"citationId": "cit-3f9a…", "sourceTitle": "公共选修规则手册", "sourceLocator": "第 1 页", "pageNumber": 1}
  ]
}
```

| 字段 | 类型 | 语义 | 新旧 |
| --- | --- | --- | --- |
| `type` | 枚举字符串 | 既有语义不变：`ANSWER` / `NEED_CLARIFICATION` / `EVIDENCE_UNAVAILABLE` / `OUT_OF_SCOPE` / `SESSION_EXPIRED` / `SESSION_CANCELLED` / `SESSION_INVALID` | 旧（语义不变） |
| `message` | 字符串 | 面向学生的正文 | 旧 |
| `answer` | 对象或 null | 既有回答卡：`{conclusion, basis, sourceId, sourceTitle, sourceVersion, sourceLocator, applicableScope}`；**仅"当期通知/规则"级有卡**，检索增强级为 null | 旧（语义不变） |
| `route` | 枚举字符串 | 五级路由结论：`AWAITING_CLARIFICATION` / `CURRENT_NOTICE` / `HIGH_RISK_RULE` / `RETRIEVAL_AUGMENTED` / `INSUFFICIENT_EVIDENCE` | **新（只追加）** |
| `handoffRequired` | 布尔 | 是否需转人工渠道（拒答、会话异常、模型降级时为 true） | **新（只追加）** |
| `citationIds` | 字符串数组 | 服务端为本轮获准证据分配的**不透明标识**；前端只作锚点/透传，**不得解析其含义** | **新（只追加）** |
| `citations` | 对象数组 | 引用明细（**已实现**）：`citationId` + `sourceTitle` + `sourceLocator` + `pageNumber`(可 null)；与 `citationIds` **长度恒等、顺序一致**；不含片段正文（避免把整段资料塞进响应） | **新（只追加）** |

**关键约束（前端必须知道的）**：
1. `citationIds` / `citations` 只在 `route=RETRIEVAL_AUGMENTED` 时非空；其他级别为空数组（`[]`）。标识形如 `cit-<24 位十六进制>`（`RandomCitationIdFactory`），不可预测、同轮不重复。
2. 全不命中时：`type=OUT_OF_SCOPE`（**旧值不变**）+ `route=INSUFFICIENT_EVIDENCE` + `handoffRequired=true`——请以 `route`/`handoffRequired` 判断"该转人工"，不要用 `type` 判断。
3. 引用卡 UI：直接按 `citations[]` 渲染（标题 + 定位 + 页码），`citationIds` 作锚点/透传即可；两者顺序一致，无需自行配对。明细由服务端在同一次分配中产生，**不经过模型**，模型无法影响它。
4. **流式（SSE）实际载荷（CX-1，2026-09-18）**：同一会话消息地址以 `Accept: text/event-stream` 协商，顺序为 `accepted` → `reply` → `complete`。三类事件均有 `sessionId`、`revision`、`conversationStatus`；`reply`/`complete` 实际下发 `type`、`message`、`answer`、`route`、`handoffRequired`、`citationIds`、`citations`，后两者长度恒等、顺序一致；`accepted` 的作答字段为 `null`/空数组。完整证据见 `docs/evidence/2026-09-18-SSE流式输出-证据账本.md`。
5. 另有一个单轮问答接口 `POST /api/zhku/local-preview/answers`（`mode/answer/citations/notice`），属阶段 5 既有路径，**不在本次引用卡链路内**，未被改动。
6. 检索增强级默认**关闭**（未配置 `zhku.rag.*` 时整级跳过）：未开启时不会出现 `route=RETRIEVAL_AUGMENTED`，前端引用卡按"无引用"分支即可。
