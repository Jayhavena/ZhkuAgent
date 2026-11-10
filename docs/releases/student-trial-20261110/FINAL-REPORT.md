# ZhkuAgent 首个学生试用候选：工程交付总报告

日期：2026-11-10
状态：**CONTROLLED_TRIAL_CANDIDATE_DELIVERED**
结论：**受控本地工程试用 CONDITIONAL_GO；真实学生在线试用与生产发布 NO_GO。**

## 1. 结论摘要

- 唯一学生试用代码基础：`0a08ebffd7a5dfb0ca1642fb318ce3e712ad9210`。
- 代码 = G1 交付 `94a89aa3` + 5 个 G2 提交；G1 源候选 `6e149df5` 的 10 个 `knowledge/src` 文件已逐字节迁移。
- G2 证据：`a35be4b9ef3d10e0052b4726d64546ab2079ab3e`（1 项租约宿主、12 组浏览器检查、11 项账号安全；`providerCalls=0`）。
- G1 独立签核：`ba3efc54dbf2ee7e5c34a396ccaa9a45bb3ede35`（10/10 文件一致；128 项离线测试 127 PASS、1 SKIP、0 FAIL/ERROR）。
- G2 审查 HEAD：`96e6fb09`（合并 G1 签核；代码树不变）。
- G2 交付 PR：**#4**，base `release/g1-reviewed-20261110`。
- PR #1 `ae017631` 仍只是基础整合，不含 G1/G2；PR #2 `86771a7c` 是 G3/G4 证据；PR #3 `ba3efc54` 是 G1 + 签核。

## 2. Git 分支与 PR 关系

| PR | head | base | 状态 | 范围 | Merge |
| --- | --- | --- | --- | --- | --- |
| #1 | `ae017631` | `main` | OPEN；CI 2/2 SUCCESS；无 review | 226 文件、+20051/-2440；25 提交；含 13 文件归档清理 | 单独授权 |
| #2 | `86771a7c` | `feat/zhkuagent-v3-frontend` | OPEN；CI SUCCESS | 18 文件、+1364；G3/G4 P02 证据 | 单独授权 |
| #3 | `ba3efc54` | `feat/zhkuagent-v3-frontend` | OPEN；CI SUCCESS | G1 10 文件 + 审查/签核；+1202/-13 +112 | 单独授权 |
| #4 | `e52217d9`（报告提交前） | `release/g1-reviewed-20261110` | OPEN；CI 以 PR 页面为准 | G2 5 代码提交 + 15 证据 + 发布文档 | 单独授权 |

依赖图：

```text
main ee0562af
  -> 24 commits -> 42d4491d
       +-> ae017631  PR #1（基础整合）
       +-> 86771a7c  PR #2（G3/G4 证据）
       +-> 94a89aa3  PR #3（G1）
             -> ba3efc54  G1 签核
                  -> 0a08ebff  G2 代码
                       -> a35be4b9  G2 证据
                            -> 96e6fb09  G2 审查 HEAD
                                 -> e52217d9  发布文档
```

G2 PR 的差异只相对 G1 分支，不重复 PR #1 的 24 条历史提交。

## 3. 已合并与未合并

- `main` 仍为 `ee0562af`；**没有任何候选被合入 main**。
- `42d4491d`、`ae017631` 在 `feat/zhkuagent-v3-frontend` 上，尚未合入 main。
- G1（`94a89aa3`/`ba3efc54`）、G2（`0a08ebff`/`a35be4b9`）、G3/G4 证据（`86771a7c`）均未合入目标分支。
- 所有 Merge 都待用户单独授权；本轮只 Push 和创建 PR。

## 4. Worktree 剩余改动归属

| Worktree | 分支 / HEAD | 状态 | 归属与处置 |
| --- | --- | --- | --- |
| `D:/.../main` | `feat` / `338c1ea1` | 大量 staged/unstaged/untracked 改动 | 用户既有工作；**不得清理、重置或混入本交付** |
| `C:/Users/.../evidence-g3-g4-p02-...` | `evidence/g3-g4-p02-20261110` / `e856da47` | clean | G3/G4 本地源；内容已由 `86771a7c` 远程化，可在 PR #2 合并后归档 |
| `worktrees/agent-eval-mvp` | `feat/agent-eval-mvp` / `d825bb0d` | clean | 独立评测 MVP；不属本次试用候选 |
| `worktrees/concurrency-observability-mvp` | `feat/concurrency-observability-mvp` / `30dadcd3` | 7 个 tracked 文件修改 | 用户既有并发/观测工作；不属本次交付，保留 |
| `worktrees/evidence-release-git01-20261110` | `evidence/release-git01-20261110` / `86771a7c` | clean | PR #2 的证据工作区 |
| `worktrees/g1-fix-01-20261109` | `fix/g1-gap-semantics-2026-11-09` / `6e149df5` | clean | G1 源候选；归档保留，不直接交付 |
| `worktrees/g1-review-release-20261110` | `release/g2-student-trial-20261110` / 本轮文档提交 | 仅 `docs/evidence/student-trial-20261110/` 与 `.superpowers/` 未跟踪 | 原始日志/命令保留本地；`.superpowers` 为工具状态；不提交 |
| `worktrees/g1-signoff-20261110` | `release/g1-reviewed-20261110` / `ba3efc54` | clean | G1 签核工作区；可在 PR #3 合并后移除 |
| `worktrees/pr1-sanitization-20261110` | `integration/pr1-sanitized-20261110` / `ae017631` | clean | PR #1 工作区 |
| `worktrees/probe-freeze-2026-11-09` | `freeze/probe-2026-11-09` / `f1637ffc` | clean | Probe 冻结源；不直接交付 |
| `worktrees/team-baseline-verify-42d4491d` | detached / `42d4491d` | clean | 基线验证快照；可保留 |

## 5. G1/G2/G3/G4 最终状态

### G1：规则发布语义

- 状态：**ACCEPTED_FOR_CONTROLLED_ENGINEERING_INTEGRATION**。
- 源候选 `6e149df5`；交付 `94a89aa3`；签核 `ba3efc54`。
- 10/10 `knowledge/src` 文件与源候选 blob 完全一致。
- 128 项离线测试：127 PASS、1 既有 Host 显式分派 SKIP、0 FAIL/ERROR。
- 阻断性 findings：0；初始空 requirements 疑点已按协议撤回，不做推测性修复。
- `databaseOperations=0`、`providerCalls=0`。
- 未覆盖：真实模型质量、G2 浏览器扩展、生产发布。

### G2：学生页面与账号边界

- 状态：**PASS_WITH_EXPLICIT_LIMITS**（受控工程范围）。
- 代码 `0a08ebff`；证据 `a35be4b9`。
- 真实临时 PostgreSQL、真实 Spring HTTP、Nginx 学生页、只读已审核资料；`providerCalls=0`、`sourceDrift=0`、连接数 0、资源回收。
- 4 个加载资产与 `agent/frontend/dist` 一致；4 个后端验收类与 `knowledge/target/classes` 一致；jar 不提交，启动脚本从源码构建。
- 1 项租约宿主 + 12 组浏览器检查：注册登录、提交轮询、澄清续答、刷新恢复、取消、会话失效、重新登录边界、跨账号隔离、撤销前后、退出、资产一致性。
- 账号安全 11 项通过（合成夹具，单独标注）。
- 残余：同标签页 15 分钟恢复；快照资格逐条读取非原子；503/网络未知保留手动入口；无真实模型。

### G3：真实 Agent 执行

- P02 单批真实执行：`qwen3.8-flash`，8 次调用（6 决策、2 发布核验），输入 14617 / 输出 1026 Tokens。
- 结果：2 PASS / 0 FAIL / 0 NOT_EVALUABLE；采集 COMPLETE。
- 证据：PR #2 `86771a7c`，18 文件；原报告与白名单 fixture，不含原始请求/响应/审核原文。
- 限制：执行源 HEAD `338c1ea1` 含 dirty 增量；不能把父版本冒称为完整执行源码。

### G4：Evaluation Pipeline

- 现有 `RuleAgentTraceEvaluator` + collector/runner/grader 身份已记录；未新增评分框架。
- 报告 2 案例 PASS，0 mismatch；suite `g3-20261110-p02`。
- 证据校验：`python docs/evidence/g3-g4-p02-20261110-reviewed/verify.py`。
- 限制：只证明已保存证据与报告一致；不能仅凭证据包重放供应商调用或核对账单。

## 6. 未核实账单

- 授权上限：人民币 12 元。
- 实际账单：`null` / `UNVERIFIED`。
- 是否低于上限：未知。
- 现有宿主没有实时人民币账单熔断；8 次 HTTP 200 和 usage 字段不等于结算成功。
- 不得用价格估算替代账单，也不得把上限写成消费。

## 7. 已知风险

1. **PR 堆叠顺序**：PR #1 的 head 是 `feat` 分支；在 PR #1 未决时把 G1/G2 合入 `feat` 会扩张 PR #1 的 diff。必须先把 PR #1 独立收束，或明确接受堆叠扩张并重新审查。
2. **G1 签核主体**：独立工程复核为 Agent，不是人类审批或学校业务签章。
3. **G2 证据可复核性**：脱敏包可核对已保存证据与结论；原始浏览器交互、原始日志和账单不能仅凭包重放。
4. **G2 模型边界**：浏览器矩阵是工程回放，真实模型质量未验证。
5. **G3/G4 证据独立**：PR #2 不在 G2 分支；合并 PR #2 不改变 G2 代码树，但需要单独的 review/merge。
6. **工作区污染**：主 Worktree 与 concurrency Worktree 有既有改动；不得把它们当作候选或清理。
7. **版本号未 bump**：仍为 `0.1.0-rc.1`，以保持与被测构建的身份对应；如要发布新版本号，必须重新构建并重新对应证据。
8. **上线缺口**：在线宿主、HTTPS/安全 Cookie、反向代理识别、邀请范围、学校 SSO、真实学生账号、真实账单均未完成。

## 8. 学生试用准入结论

### 受控本地工程试用：CONDITIONAL_GO

条件：

- 使用本报告固定的 `0a08ebff` + `a35be4b9`，不修改代码后继续引用旧证据；
- 使用隔离数据库/专用端口，仅回环访问；
- 不接真实供应商模型，不写共享演示库；
- G1/G2 PR 完成 review，Merge 单独授权；
- 运维按 `docs/releases/student-trial-20261110/README.md` 启动和停止。

### 真实学生在线试用：NO_GO

直到完成：

- 在线宿主与 HTTPS/安全 Cookie；
- 邀请范围与真实账号/身份方案；
- 供应商预算、实时账单或至少可核对的账单；
- 真实模型质量评估与人工抽检；
- 学生数据/隐私/留存评审。

### 生产发布：NO_GO

本候选不构成生产发布批准。

## 9. 遗留工作归属

| 事项 | 归属 | 下一步 |
| --- | --- | --- |
| PR #1 范围/merge | 用户 | 单独决定是否 Merge；不要与 G1/G2 混同 |
| PR #3 G1 签核/merge | 用户 | review `ba3efc54` 后单独授权 |
| PR #4 G2 review/merge | 用户 | 以 G1 为 base 审查；不申请 Merge |
| PR #2 G3/G4 证据 | 用户 | 独立 review/merge；不改 G2 代码 |
| 实际人民币账单 | 财务/供应商负责人 | 用真实账单替换 `UNVERIFIED` |
| 在线宿主/HTTPS/SSO | 运维/产品 | 另立上线任务与安全评审 |
| 真实模型试用 | 产品 + 预算负责人 | 新包/预算/供应商授权 |
| 主 Worktree 改动 | 用户 | 保留；不清理、不混入交付 |
| concurrency Worktree 改动 | 用户 | 保留；独立任务 |
| `e856da47` 本地重复 | G3/G4 负责人 | PR #2 合并后可归档；当前保留 |
| G2 版本号 | 发布负责人 | 如需 bump，重新构建并重做身份对应 |

## 10. 复核入口

```powershell
git rev-parse release/g2-student-trial-20261110
git log --oneline --graph -8 release/g2-student-trial-20261110
git diff --stat 94a89aa3 0a08ebff
git diff --stat 0a08ebff a35be4b9
Get-Content docs/releases/student-trial-20261110/release-manifest.json -Raw | ConvertFrom-Json
```

关键文档：

- `docs/evidence/release-candidate-20261110/CANDIDATE-GRAPH.md`
- `docs/evidence/g2-student-acceptance-20261110/REVIEW.md`
- `docs/evidence/g1-formal-review-20261110/SIGNOFF.md`
- `docs/releases/student-trial-20261110/README.md`
- `docs/releases/student-trial-20261110/release-manifest.json`

> 最终 head 以 PR #4 页面或 `git rev-parse release/g2-student-trial-20261110` 为准；本报告所在提交不能包含自身 SHA。
