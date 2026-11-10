# 候选版本谱系审查（2026-11-10）

## 结论

**唯一学生试用代码基础：`0a08ebffd7a5dfb0ca1642fb318ce3e712ad9210`。**

- `0a08ebff` = G1 交付 `94a89aa3` + 5 个 G2 修复/测试提交。
- `a35be4b9` = `0a08ebff` + G2 最终验收证据。
- `96e6fb09` = `a35be4b9` + G1 独立签核 `ba3efc54` 的合并提交，作为 G2 PR 的审查 HEAD。
- PR #1 `ae017631` 不包含 G1/G2；它不能作为学生试用候选的代码基础。
- `338c1ea1` 是主 Worktree 当前 HEAD，含大量未提交改动，不是候选，不参与交付。
- `6e149df5` 是 G1 源候选，未直接迁移到 `42d4491d`；交付以 `94a89aa3`/`ba3efc54` 为准。

## 提交依赖图

```text
main ee0562af
  |
  +-- 24 commits --> 42d4491d (PR #1 基线)
        |
        +-- ae017631  PR #1：13 文件归档边界清理（feat/zhkuagent-v3-frontend）
        |
        +-- 94a89aa3  G1 交付：10 个 knowledge/src 文件（release/g1-reviewed-20261110，PR #3）
        |     |
        |     +-- ba3efc54  G1 独立签核回执（docs-only）
        |
        +-- 86771a7c  G3/G4 P02 证据（evidence/release-git01-20261110，PR #2）
        |
        +-- 0a08ebff  G2 代码候选：G1 之上 5 个提交
              |
              +-- a35be4b9  G2 最终验收证据（15 文件）
                    |
                    +-- 96e6fb09  merge ba3efc54（G2 审查 HEAD）

G1 源支线（未直接交付）：
338c1ea1 -> f1637ffc -> 6e149df5
（G1 的 10 个 knowledge/src 文件与 94a89aa3 逐字节一致）
```

## 候选归属

| 对象 | SHA | 类型 | 父/基线 | 分支 | 远程/PR | 归属与状态 |
| --- | --- | --- | --- | --- | --- | --- |
| 基线整合 | `42d4491d` | 24 提交基线 | `06022f70` | — | 可达于 `origin/feat` | PR #1 的基线；不单独交付 |
| PR #1 | `ae017631` | 基线 + 13 文件清理 | `42d4491d` | `feat/zhkuagent-v3-frontend` | `origin/feat`，PR #1 -> `main` | 基础整合；无 review；CI 2/2 SUCCESS；MERGEABLE |
| G1 源候选 | `6e149df5` | G1 语义修复 | `f1637ffc` | `fix/g1-gap-semantics-2026-11-09` | 本地 only | 源材料；10 文件被逐字节迁移，不直接交付 |
| G1 交付 | `94a89aa3` | 10 文件迁移 + 审查记录 | `42d4491d` | `release/g1-reviewed-20261110` | `origin/release/g1`，PR #3 -> `feat` | G1 工程接收；CI SUCCESS |
| G1 签核 | `ba3efc54` | 签核回执（docs-only） | `94a89aa3` | `release/g1-reviewed-20261110` | 同上，PR #3 | 固定 10/10 文件身份与 128 项离线结果 |
| G3/G4 证据 | `86771a7c` | P02 证据（18 文件） | `42d4491d` | `evidence/release-git01-20261110` | `origin/evidence/release-git01`，PR #2 -> `feat` | 真实 P02 证据；CI SUCCESS；账单 UNVERIFIED |
| G3/G4 本地源 | `e856da47` | 同内容本地提交 | `338c1ea1` | `evidence/g3-g4-p02-20261110` | 本地 only | 与 `86771a7c` 仅差 3 个后加文档；以远程版为准 |
| G2 代码 | `0a08ebff` | 4 修复 + 1 租约测试 | `72a6671c`（链上含 `94a89aa3`） | `trial/student-candidate-20261110` | 本地 only | G2 唯一代码基础；CI/浏览器证据见 `a35be4b9` |
| G2 证据 | `a35be4b9` | 15 文件验收证据 | `0a08ebff` | `trial/student-candidate-20261110` | 本地 only | G2 证据；本轮进入发布分支 |
| G2 审查 HEAD | `96e6fb09` | merge G1 签核 | `a35be4b9` + `ba3efc54` | `release/g2-student-trial-20261110` | G2 PR（本分支推送后创建） | 代码不变，新增 G1 签核与发布文档 |
| 主 Worktree | `338c1ea1` | 工作 HEAD | `42d4491d` 链 | `feat/zhkuagent-v3-frontend` | 本地工作区 | 含大量 staged/unstaged/untracked 改动；不是候选 |
| 冻结候选 | `f1637ffc` | Probe 冻结 | `338c1ea1` | `freeze/probe-2026-11-09` | 本地 only | 源候选基线；不直接交付 |

## 文件差异表

| 范围 | 文件 | 行数 | 说明 |
| --- | --- | --- | --- |
| `origin/main..ae017631` | 226 | +20051 / -2440 | PR #1 完整基础整合 |
| `42d4491d..ae017631` | 13 | +85 / -21426 | PR #1 的归档边界清理，移除 11 个原始包/sidecar/网页抓取 |
| `42d4491d..94a89aa3` | 12 | +1202 / -13 | G1：10 个 knowledge/src 文件 + 2 份审查记录 |
| `94a89aa3..ba3efc54` | 2 | +112 | G1 独立签核回执 |
| `94a89aa3..0a08ebff` | 15 | +309 / -21 | G2 代码、测试、租约入口与 2 份 CHANGE 说明 |
| `0a08ebff..a35be4b9` | 15 | +500 | G2 最终验收证据 |
| `42d4491d..86771a7c` | 18 | +1364 | G3/G4 P02 证据 |
| `e856da47..86771a7c` | 3 | -177 | 本地源比远程版多 3 个后加文档；证据目录本身一致 |
| `a35be4b9..96e6fb09` | 2 | +112 | 合并 G1 签核；G2 代码树不变 |

## 依赖与冲突

### 代码依赖

1. **G2 依赖 G1**：`0a08ebff` 的父链包含 `94a89aa3`；G2 的 `RuleAgentTaskController` 快照资格校验建立在 G1 的发布门约束上。
2. **G2 证据依赖 G2 代码**：`a35be4b9` 的 `acceptance.json` 固定 `testedCommit=0a08ebff`；不能用其他提交替代。
3. **G1 交付依赖源候选逐字节迁移**：`94a89aa3` 的 10 个文件与 `6e149df5` 的 blob 完全一致；`6e149df5` 本身基于 `f1637ffc`，不能直接合入 `42d4491d`。
4. **G3/G4 证据独立**：`86771a7c` 只新增 `docs/evidence/g3-g4-p02-20261110-reviewed/`，不改产品代码；与 G1/G2 无代码依赖。
5. **PR 层级**：PR #1 以 `main` 为目标；PR #2/#3 以 `feat/zhkuagent-v3-frontend` 为目标；G2 PR 以 `release/g1-reviewed-20261110` 为目标。这是堆叠关系，不是同层并行。

### 冲突检查

使用 `git merge-tree --write-tree` 对候选两两试合并，均 **0 conflicts**：

| 合并对 | 结果树 | 冲突 |
| --- | --- | --- |
| `ae017631` + `94a89aa3` | `cd3ff2822936eae601fad524b37bc10a441073ab` | 0 |
| `ae017631` + `86771a7c` | `6979b414be027bd2c60b2991edfaca3da909dae2` | 0 |
| `ae017631` + `0a08ebff` | `ab920576b5b7578b09d01efd7f97588efbab17bb` | 0 |
| `ae017631` + `a35be4b9` | `534eb2c3e895328296109128666326efe327aec9` | 0 |
| `ba3efc54` + `0a08ebff` | `a47d9090fb58280168370fd3b3d1ff90ccddc99f` | 0 |
| `ba3efc54` + `a35be4b9` | `3f58324be0107470f2fa40687aa006c59dd993d4` | 0 |
| `0a08ebff` + `86771a7c` | `2e67cec39e29b7c961b31103aeed5e9b06625d18` | 0 |

无文本冲突不等于无发布顺序风险。**PR #1 的 head 就是 `feat` 分支**：如果在 PR #1 未决时把 G1/G2 合入 `feat`，PR #1 的 diff 会自动扩张，破坏“PR #1 独立收束”。因此建议二选一：

- **推荐**：先单独决定/合并 PR #1（基础整合），再把 PR #3、PR #2、G2 PR 重定向到 `main` 或新的 `feat` 后续分支；
- 或者明确把 PR #1 当作堆叠底座，接受其范围随后续 PR 合并而扩张，并重新审查。

本阶段不执行任何 Merge，也不改变 PR #1 的目标分支。

## 基础选择

**选择 `0a08ebff` 作为唯一学生试用代码基础，`a35be4b9` 作为其证据，`96e6fb09` 作为 G2 PR 审查 HEAD。**

理由：

- 它同时包含 G1 的 10 文件语义修复和 G2 的 C1/C2/C3 修复；
- `a35be4b9` 的最终浏览器证据固定了 `0a08ebff`，身份可追溯；
- `96e6fb09` 只增加 G1 签核，不改变代码树；
- PR #1 的 `ae017631` 缺少 G1/G2，不能作为试用基础；
- `338c1ea1` 的脏工作区不能作为可复现候选。

## 复核命令

```powershell
git log --graph --oneline --decorate ae017631 94a89aa3 86771a7c 0a08ebff a35be4b9 6e149df5
git diff --shortstat origin/main ae017631
git diff --shortstat 42d4491d 94a89aa3
git diff --shortstat 94a89aa3 0a08ebff
git diff --shortstat 0a08ebff a35be4b9
git merge-tree --write-tree ba3efc54 a35be4b9
```
