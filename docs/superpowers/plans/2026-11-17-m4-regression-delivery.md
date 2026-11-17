# M4 持续回归与候选交付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. One independent whole-candidate review follows local verification.

**Goal:** 把已通过 M3 的业务边界变成每个 PR 必跑的最小门禁，形成可审查的团队开发候选。

**Architecture:** 保留既有生产修复及执行历史。用单一纯标准库清单驱动 Maven 选类和执行报告检查；保留前端构建和归档 Grader 重放。候选说明绑定官方主线、必要依赖、实际测试和明确未验证范围。

**Tech Stack:** Java 21 / Maven / Python 3.11 标准库 / Node 22 / GitHub Actions。

**Spec:** 用户 M4 要求“核心 Case 回归、CI 质量门禁、发布候选和团队开发基线”；M3 技术边界见 `docs/agent/m3/acceptance-20261117/REPORT.md`。

## Global Constraints

- 基线 origin/main `505a0dea86c5b88f030c6692d8a9c0df9f52898a`；候选起点 `365ee0c9de0e0e20668a8120ed18be75a01f39fd`。
- 本轮不调用真实模型，不沿用已消耗票据；不读凭据/私有请求/响应/PDF 正文。
- 本地提交已授权；用户进入包含 PR/CI/整合验收的 M4，按该范围准备并发布独立候选 PR；不直接推 main，不发布正式标签。
- 不改历史真实报告/评分期望，不把离线回归称为模型效果，不关闭实际账单待办。
- 不清理既有未跟踪材料、数据卷或其他 worktree。

## Review Focus

- Maven 白名单漏掉 M3 关键类：清单必须包含 21 条矩阵所需全部类。
- Surefire 无报告、零用例、跳过或失败：门禁必须失败，不能“命令绿但未执行”。
- 参数化方法命名：按方法名前缀匹配，且 21 条要求均有成功实例。
- CI 不能依赖外置 PDF/Windows 路径/数据库/供应商；资产测试仍属独立受控本机检查。
- 整合必须包含一次性授权、失败采集、绑定器等依赖，不能只取 Runner 修复；私有材料不入候选。

### Task 1: 固定门禁和执行检查

**Files:** `knowledge/scripts/core-regression.json`、`knowledge/scripts/verify_core_regression.py`、`knowledge/scripts/tests/test_verify_core_regression.py`、`.github/workflows/agent-eval-chain.yml`。

执行发现：OnlineFactory 类有 3 项依赖外置 PDF 的包准备检查。移到同包 `RuleAgentProbePackageAssetsTest`，保留断言并在本机显式 PDF 复验；PreviewConfiguration 内混合正负例也导致 1 项跳过，完整正例移到 `RuleCheckPreviewAssetsTest`，纯边界断言仍在 CI。核心类不跳过任何测试。原失败日志保留。

- [x] 先写报告缺失、零执行/跳过/失败、方法未覆盖、合法参数化和清单漏类测试，确认缺实现而失败。
- [x] 合并既有 CI 类与 M3 核心类，加 ProbeBatchClaimTest；单一清单输出 Maven selector，检查 Surefire 中所有类/方法真实执行。
- [x] CI 使用同一 selector，执行 Python 回归、前端测试/构建、归档 Grader 重放并保存门禁摘要。
- [x] 从仓库根按 CI 同选类完成本地验证：25 类 345 项，0 失败/错误/跳过；Python 9+27 项，前端 202 项及构建通过。

### Task 2: 团队候选和审查

**Files:** `docs/agent/m4/README.md`、`docs/agent/m4/candidate-20261117/`。

- [x] 审核 origin/main..候选全部提交/文件，按产品代码、测试/脚本、证据区分依赖和发布边界；没有前端或 SQL 变化已以 diff 实证。
- [x] 保存基线/执行摘要、恢复与验收入口、PR 标题正文、剩余账单/ONLINE/上线边界。
- [x] OpenCode / DeepSeek V4.1 独立只读复核整体候选，无合并阻断；文档口径修正，保留非阻断项，executedModel=null。
- [x] 明确清单暂存、本地提交、记录 SHA；发布独立 Draft PR #7，7e110c27 两项 CI 已通过，后续文档修订等待新头 CI。

### Task 3: 正式整合后验收（依赖远程发布与合并）

- [ ] PR 审查及远程 CI 通过后再决定合入 main，保留 PR/CI 身份。
- [ ] 正式 main 重新构建，专用实例关键 HTTP 与重启验收、资源回收；形成正式开发基线。
- [ ] 当前候选不等于已完成本步骤，也不是公网正式版本。
