# P2 Worktree 生命周期：轻量治理与检查

> 2026-11-19；适用于 ZhkuAgent；阶段状态：本地实施与验证完成；提交身份与后续保留情况以 Git 日志和实际工作树为准。
> 本文是现有协作约定的操作补充，提交检查仍遵循 [Git 本地提交流程](../git-commit-workflow.md)。本机另有 `docs/collaboration/parallel-development-and-integration-v1.md`，当前尚未进入 Git 追踪，本文件不依赖其历史 Worktree 数量快照，也不声明任何工作区可以被删除。

## 目标与原则

避免“新 Agent 就新建 Worktree”造成重复目录、任务无归属、索引误操作和无法安全收尾。**事实从 Git 实时读取；任务归属从负责人/任务交接记录确认。** 不维护一份会自动过时的手工 Git Worktree 注册数量清单。

- 默认复用当前任务已有的工作区。只有**并行开发隔离、保护当前未提交改动、独立实验**确实需要隔离时，才提出新 Worktree。
- **单任务一个明确写入负责人**；同一实际目录/文件不可被两个写入会话同时占有。
- 手动 Worktree 只能建在项目既有的 `D:/alldesk/Projects/ZhkuAgent/worktrees/<task-id>` 直接子目录。T3 等工具管理的 Worktree 由其工具维护，不另行复制或代管。
- 工作树 clean 只表示未检出 Git 文件差异，**不等于**任务已经完成、分支安全、证据已归档或可以删除。
- 未注册目录（例如辅助工具、备份、历史文件夹）不按 Git Worktree 计数，也不自动清理。
- 检查工具是**只读预检器**，不创建、不删除、不切换工作区，不更新索引、分支、Hooks、Tag 或数据库。

## 操作入口

入口：`scripts/check-worktree-lifecycle.ps1`，支持 Windows PowerShell 5.1 / PowerShell 7。

| 操作 | 命令 | 判定 |
|---|---|---|
| 盘点全部注册 Worktree | `powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode Audit` | 逐一报告路径、分支、HEAD、Git 差异计数、锁定信息；Owner/Task 不推断 |
| 工具自身自测 | `powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode SelfTest` | 不创建文件；解析、任务标识与目录边界检查 |
| 创建前预检 | `powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode Create -TaskId issue-123 -Owner team-a -TargetPath D:/alldesk/Projects/ZhkuAgent/worktrees/issue-123 -Branch agent/issue-123 -BaseSha <40-char-commit-SHA> -IsolationReason Parallel -Scope backend-task -Acceptance regression-green` | 只核验；即使返回 PRECHECK_READY，也**不意味着已授权创建** |
| 收尾预检 | `powershell -NoProfile -File scripts/check-worktree-lifecycle.ps1 -Mode Close -TargetPath <registered-path> -Owner <owner> -HandoffRef <task-record> -OwnerAcknowledged -DispositionRecorded` | 检查注册身份和 staged/unstaged/untracked；缺负责人或任务交接记录时 BLOCKED；通过后仍须另行批准 |

命令中的 `<...>` 为占位符，执行时必须替换为实际值。不能对未核实的仓库基线自动 Fetch/Pull。脚本退出码：0＝盘点完成或通过技术预检（**仍需授权**），1＝BLOCKED，2＝输入或检查错误。

## 生命周期和责任

| 阶段 | 准入/离开条件 | 责任 |
|---|---|---|
| REQUESTED | 明确 Task ID、Owner、必要性、修改范围、目标分支/基础 SHA、验收条件、资源清单 | 提出任务的会话；负责人确认 |
| PREFLIGHT | 实时检查 `git worktree list --porcelain`、是否已有任务/分支/目录、路径限制、基础提交存在性 | Agent 运行 Create 预检；失败不创建 |
| ACTIVE | 获得单独明确授权后才可创建；记录真实 Worktree 路径、分支、HEAD、Owner、允许修改文件 | 拥有写权限的 Agent |
| DELIVERED | 验收证据、变更归属、Commit/PR/证据/临时产物去向均有记录 | 实施会话交付，负责人复核 |
| CLOSEOUT_REVIEW | 检查 staged/unstaged/untracked、未提交产物、分支历史、外部引用、锁、工具归属与任务负责人回执 | 原任务负责人及项目负责人 |
| REMOVED / RETAINED | **项目负责人另行批准**后才执行有范围的移除；或明确说明保留原因；T3 目录走其工具的收尾入口 | 项目负责人；禁止脚本自行删除 |

登记采用已有 Issue/任务卡或交接记录，**不为每个 Worktree 新建一份独立管理文档**。首次登记至少包含 `Task ID | Owner | 实际 Worktree Path | Branch | Base SHA | 文件范围 | 运行资源 | 验收标准`；收尾再补 `最终 SHA | 验收证据 | 已提交/未提交资产去向 | Owner 回执 | 清理或保留决定`。历史任务找不到负责人时标记 UNKNOWN，绝不根据目录名伪造 Owner。

## 首次现场基线（2026-11-19；不是实时清单）

实时检查发现 **16 个已注册 Worktree**（主工作区 1、T3 托管 5、项目级 10）。其中 5 个存在 staged、unstaged 或 untracked 差异：main；T3 M4 证据工作区；concurrency-observability；g1-review-release；runtime-baseline。其他 11 个当前未检出文件差异，但保留其原有任务/分支身份，未判定为可删除。

项目级 `worktrees/` 下另外发现 9 个非 Git 注册目录，包含 integration、辅助工具、收据、桥接和安全备份等；**不统计为新 Worktree、不清理**。

注意 main 在 P1 独立索引提交后仍有原暂存区遗留的反向暂存状态；不允许把逆向差异当作需要执行 reset/restore 的理由。

## 本轮实际验证（2026-11-19）

- Windows PowerShell 5.1 和 pwsh：脚本 `-Mode SelfTest` 各 5/5 PASS；没有创建文件。
- 实际 16 个 Worktree 的 `-Mode Audit` 执行成功；能区分主工作区、T3 托管与项目级工作区。
- `-Mode Create` 正向预检返回 PRECHECK_READY，但没有创建目标路径；重复任务、已有分支/目录、目录越界、任务名称不一致均正确阻止。
- `-Mode Close` 对有改动的 Worktree、主工作区、T3 托管工作区、缺负责人/交接记录的工作区正确 BLOCKED；即使用户提供负责人和交接信息，返回的也只是 CLOSEOUT_REVIEW_READY，不执行删除。
- 原有 Git HEAD、原始 Index SHA 和 16 个注册 Worktree 在验证期间保持不变。全部测试未使用真实模型、数据库或网络。
- 运行现有 Markdown 检查器扫描 `docs/collaboration/`：16 个 Markdown、151 个链接、108 个历史缺失目标、0 个未解析引用、0 个扫描错误；本文件未引入新失效链接。本轮不修改历史记录使其凑绿。

## P2 交付边界和后续增强

本切片仅提供规则入口、实时盘点和只读双向预检；**没有**自动创建、自动删除、全局 Hook、自动 Garbage Collection、修改 T3 设置或清理历史目录。技术预检无法证明“没有其他活跃会话”或“已经获得所有权”。`-OwnerAcknowledged`、`-DispositionRecorded` 和 `-HandoffRef` 均由调用方提供，脚本不能独立验证其真实性；因此创建与移除必须仍按负责人授权。

如需真正把检查绑定到创建命令或 T3 自动创建流程，应先核对各 Agent/工具是否支持统一入口或执行策略，再单独实施，不假装仓库规则可以拦截任意外部工具的调用。
