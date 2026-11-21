# P3 统一任务交付检查

> 日期：2026-11-19
> 状态：实施与验证完成；提交身份以 Git 日志为准。
> 目标：把既有 Git、测试证据、文档链接与 Worktree 生命周期检查汇总为一个只读交付预检入口。

## 范围与复用关系

P3 不新建测试框架、CI 平台、交付数据库或第二套 Worktree 管理器，只增加一个统一入口脚本、一份最小使用说明和一条 `AGENTS.md` 入口规则：

| 交付维度 | 复用能力 | P3 的职责 |
|---|---|---|
| Git | Git CLI + `docs/git-commit-workflow.md` | 报告仓库、Worktree、分支、HEAD、staged/unstaged/untracked 和两类 whitespace 检查 |
| 测试 | 项目现有 Maven、Node、Playwright、Agent Evaluation 命令 | 接收真实执行者提供的状态和证据文件路径；**不执行测试、不读取日志推断 PASS** |
| 文档 | `scripts/validate-doc-links.ps1` | 按指定 `DocsRoot` 调用既有检查器，原样报告 CLEAN/BROKEN 结果 |
| Worktree | `scripts/check-worktree-lifecycle.ps1` | 审计注册工作区；收尾模式直接委托 P2，不自行删除 |
| 交付报告 | `AGENTS.md` §9 与本地提交流程 | 汇总事实、缺项和待人工复核项，不替代任务报告或验收结论 |

## 使用方式

### 1. 统一预检

```powershell
pwsh -NoProfile -File scripts/check-task-delivery.ps1 -Mode Audit -TaskId issue-123 -TestStatus PASS -TestEvidencePath tmp/issue-123-tests.log -DocsRoot docs/feature
```

如要把未提交改动和文档链接问题作为阻断项，再添加：

```powershell
-RequireClean -RequireDocsClean
```

`DocsRoot` 是本次需要检查的文档目录；不要默认扫描全仓库并把历史断链当作本轮新缺陷。

### 2. Worktree 收尾

```powershell
pwsh -NoProfile -File scripts/check-task-delivery.ps1 -Mode Closeout -CloseoutTarget D:/alldesk/Projects/ZhkuAgent/worktrees/issue-123 -Owner team-a -HandoffRef issue-123 -OwnerAcknowledged -DispositionRecorded
```

这只是调用 P2 的收尾预检。返回 `CLOSEOUT_REVIEW_READY` 仍不等于授权删除；项目负责人须另行批准。主工作区和 T3 托管工作区继续遵守 P2 的保护规则。

### 3. 自测

```powershell
powershell -NoProfile -File scripts/check-task-delivery.ps1 -Mode SelfTest
pwsh -NoProfile -File scripts/check-task-delivery.ps1 -Mode SelfTest
```

## 状态语义

- `READY_FOR_HUMAN_REVIEW`：机械检查已完成且必需证据已提供；不是产品验收 PASS，也不授予 Push、Merge、部署或删除权限。
- `INCOMPLETE`：存在未提供的测试/文档证据，或工作区仍有差异但未要求 clean。
- `BLOCKED`：必需证据缺失、whitespace 检查失败、要求 clean 但工作区不 clean、要求文档 clean 但链接检查失败，或 P2 审计失败。
- `DECLARED_PASS`：调用者声明测试通过且证据文件存在；脚本不验证日志内容、命令是否真实执行或测试覆盖范围。最终报告仍须列出真实命令和结果。
- 退出码：0 = READY_FOR_HUMAN_REVIEW；1 = INCOMPLETE/BLOCKED；2 = 输入或检查错误。

## 安全边界

- 默认只读；不运行测试/构建/模型，不写报告文件，不暂存或提交。
- 不修改或修复 staged/unstaged/untracked 内容，不自动解决 Index 漂移。
- 不把历史链接基线、旧测试记录或离线回放伪装成当前 PASS。
- 不替代负责人对文件归属、验收范围、证据真实性和 Worktree 处置的判断。
- P3 的提交继续遵循 `docs/git-commit-workflow.md`，使用精确文件清单；不 Push、不 Merge。

## 验证记录

- P3 脚本自测：Windows PowerShell 5.1 与 pwsh 均 **8/8 PASS**。
- 干净临时 Git 仓库集成验证：两种 PowerShell 下均得到 `READY_FOR_HUMAN_REVIEW`；脏工作区、缺失测试证据、失效文档链接均按预期返回非零；仓库外 `DocsRoot` 返回输入错误。临时仓库已清理。
- P1 文档链接检查器自测：Windows PowerShell 5.1 与 pwsh 均 **3/3 PASS**。
- 当前主工作区实测基线（HEAD `d2156dcf`）：16 个注册 Worktree 的 P2 审计 PASS；main 的 Git 状态为 **85 staged / 71 unstaged / 3441 untracked**。两类 `git diff --check` 均失败，既有暂存差异在 `agent/frontend/src/api/index.js` 等文件包含尾随空格，未暂存差异在 `knowledge/src/test/java/.../RulePublicationEligibilityTest.java` 等文件包含尾随空格。本轮未修复或重置这些差异。
- `docs/collaboration` 链接扫描：17 个 Markdown、151 个链接、108 个历史缺失目标、0 个未解析引用、0 个扫描错误。P1 记录的基线为 16 个 Markdown、151 个链接、108 个缺失目标；新增 P3 文档没有新增链接，缺失数未增加。
- 当前主工作区的统一检查结果为 **BLOCKED**（既有 Git/whitespace 差异）并有 **INCOMPLETE** 项（本轮未提供业务测试证据）；这不是 P3 自身的产品测试失败。P3 正向/负向集成验证使用独立临时 Git 仓库完成。
- `-Mode Closeout` 对 main 按预期 BLOCKED；主工作区受保护，且仍有未处置差异。没有删除、创建或切换任何 Worktree。

不得把当前主工作区已有差异归入 P3。
