# 多会话同步状态板（SYNC-STATUS）

> **本文件是所有协作会话的唯一事实源。**
> 更新时机：开工 / 收工（提交）/ 合并前 / 合并后，四个时刻必更新。
> 更新方式：只改自己负责的行与公共区域；发现要改其他会话内容 → 写入"待协商事项"，不直接改。

---

## 零、会话登记与地盘划分（2026-09-16 起生效）

> 规则：任何会话**开工前必须先读本节并登记/确认自己的行**；任务与已登记
> 地盘冲突时，停止动手并报告用户。新会话由用户粘贴
> `docs/plans/agent-sync/会话启动提示词.md` 完成上岗。
> 地盘按**目录**划分；改地盘外文件 → 写入"待协商事项"，不直接改。

| 会话 ID | 宿主 | 地盘（目录级） | 分支 / worktree | 状态 |
| --- | --- | --- | --- | --- |
| WB-1 | WorkBuddy 会话①（前端） | `agent/frontend/`、`docs/design-references/` | `campus-line-a` / 主 worktree | 🟢 活动中 |
| WB-2 | WorkBuddy 会话②（其他内容） | 待该会话开工时自填 | 主 worktree（与 WB-1 共享工作区，禁止同时 commit） | ⬜ 待登记 |
| WB-3 | WorkBuddy 会话③（线 A：检索+生成+评测） | 线 A：`knowledge/` 检索与生成侧、Flyway `V9`–`V11`、评测；与线 B 在 `knowledge/` 内的会话持久化路径以「文件占用声明」为准 | `campus-line-a` / `ZhkuAgent-lineA` | 🟢 活动中（2026-09-17 已上岗：基线已快进至 `8b1e1d8`，按阶段 8 计划 TDD 开工；已知悉线 B 占用 `conversation/` 包、`V12*` 迁移及对应测试，避让） |
| CX-1 | Codex 会话（线 B） | 线 B：工具 + 编排（`agent/` 工具侧、`knowledge/`，**前端体验职责已移交 WB-1**） | `campus-line-b` / `ZhkuAgent-lineB` | 🟢 活动中（已登记，占用会话持久化路径） |

> 同 worktree 多会话（WB-1 / WB-2）额外纪律：任一时刻只允许一个会话执行
> `git commit`；commit 前必须 `git status --porcelain` 核对暂存区只有自己
> 地盘的文件。

---

## 一、合并基线

| 项 | 值 |
| --- | --- |
| 主线分支 | `zhkuagent-modules`（只接收合并，不直接开发） |
| 当前基线提交 | `0c9e449`（2026-09-16 收口：阶段 5/6 `4042392` + 阶段 7 `695eb48` + 协作文档 `05420d1` + 安全整改 `0c9e449`（safeguard/bundle 脚本 + 协作文档更新），测试由用户自跑全绿） |
| 最近合并 | 无 |

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
| 当前任务 | 阶段 8：统一规则路由与受控检索增强（WB-3 已上岗，按 `docs/superpowers/plans/2026-09-16-stage-8-hybrid-retrieval-agent.md` TDD 执行；Flyway 用 `V9`–`V11`，配置键用 `zhku.` 前缀并在第五节追加登记） |
| 状态 | 🟢 开工中（WB-3，2026-09-17） |
| 最新提交 | `8b1e1d8`（与主线同点，快进无新提交） |

### 线 B：工具 + 编排（Agent：Codex 会话，见第零节 CX-1）

| 项 | 值 |
| --- | --- |
| 分支 | `campus-line-b`（顶层命名，2026-09-16 已从 `codex/campus-line-b` 迁移，旧引用已删除；worktree `ZhkuAgent-lineB` 实际绑定的就是本分支） |
| worktree | `D:\alldesk\Projects\ZhkuAgent-lineB` |
| Flyway 号段 | `V12`–`V14` |
| 当前任务 | 基线快进已完成（`205205a` → `0c9e449`，无冲突，未推送）；**下一步：再次快进获取本次状态板/启动提示词提交，在第五节对四项草案完成线 B 侧确认，并在第零节完成 CX-1 登记** |
| 状态 | ⏳ 待上岗登记 |
| 最新提交 | `0c9e449`（与主线同点，快进无新提交） |

## 三、文件占用声明（开工时登记，收工时清除）

| 文件 / 目录 | 占用方 | 任务 | 声明时间 |
| --- | --- | --- | --- |
| （暂无） | | | |

## 四、待协商事项

| 事项 | 提出方 | 说明 | 状态 |
| --- | --- | --- | --- |
| 前端归属移交 | WB-1 | 用户指定前端（`agent/frontend/`）由 WB-1 负责；原线 B 分工中"体验"部分相应剥离，CX-1 不再改前端文件 | ⏳ 待 CX-1 确认 |
| README 修正 | WorkBuddy | 根 README 与 knowledge README 仍写"未实现两轮追问"，与实际不符；待指派由谁修 | ⏳ 待指派 |
| agent/ 模块只读约定 | WorkBuddy | 首轮双方将 agent/ 视为只读资产；如需修改 ToolCallAgent 复用，先在此登记 | ⏳ 待确认 |
| 线 A / 线 B 归属 | WorkBuddy | 默认 WorkBuddy=线A、另一 Agent=线B；如互换，改本节与第二节两行即可 | ⏳ 待确认 |

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
- [x] Flyway 号段：线 A `V9`–`V11`、线 B `V12`–`V14`（2026-09-16 双方建线时已按此执行，无异议）

## 六、下次合并点

- 基线收口提交完成 → 已创建 `campus-line-a` / `campus-line-b`（顶层命名）→ 冻结契约提交并合并回主线（第一次合并）。

## 七、合并记录

| 时间 | 合并内容 | 来源分支 | 目标基线 | 检查清单通过 |
| --- | --- | --- | --- | --- |
| （暂无） | | | | |
