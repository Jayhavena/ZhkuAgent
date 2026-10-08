# SDD ledger — plan: docs/superpowers/plans/2026-10-07-rule-check-p0-plan.md

Scope: 用户“开始下一步”承接上次明确的R0；本轮执行Task 1，不自动扩至R1–R4。

Workspace: 现有linked worktree，分支zhkuagent-modules；保留全部已有dirty改动，不创建/提交新分支。

Pre-flight: Task 1的显式选类与隔离入口由后续Task 2–5消费；P0固定非供应商白名单，不能复用3C默认全量回归或旧BOARD/lease。

Task 1: Ruling: 技能的bash工作区辅助器/自动提交与本机及计划冲突 — 使用既有独立worktree、原路径ledger和唯一证据批次，当前计划优先，不清理/提交旧dirty；代价是本轮回退由原字节与增量证据承担。

Task 1: Ruling: 全量Maven默认测试可能写54329或触发供应商 — 只运行计划显式非供应商选类和专用临时库；代价是未选类能力不由本轮证明。

Task 1: Ruling: 官网目录已列2026版，既存2025资料标本地验证且不可学生发布 — 当前只建立历史快照候选题与审核缺口，不冒充当前有效资料或人工金标准；代价是G0资料/评估部分可能仍未通过。

Task 1: in_progress; evidence: docs/evidence/rule-check-refactor/2026-10-07/r0-baseline-01/

Task 1: Ruling: 新入口无Node操作 — 不为计划文字引入Resolve-ZhkuNode依赖，ordinary PATH直接验证根wrapper与Java；代价是前端环境仍留后续阶段验证。

Task 1: RED入口缺失→GREEN19项；并发锁真实行为RED→GREEN29项，XML旧/缺/零/跳过/失败/漂移与非零退出均不放行。

Task 1: 实际.cmd三种危险选择退出1且不创建批次；当前入口单元7类49项、临时库结构1项通过，V21、连接0、容器退出。普通Machine/User PATH下PowerShell5.1调用.cmd实际49项通过，中文与Maven检查通过。

Task 1: sandbox Docker失败批次保留；已通过隔离入口获得本机Docker访问后用新批次复验，不把环境失败改绿。首次PDF渲染缺fitz，改用已有pdfplumber完成相关两页检查。

Task 1: PARTIAL — 技术入口/基线已验证；2026正文/当前有效性/学生发布资格及人工参考待确认，36质量候选与16风险草稿未锁定、未评分；G0未通过。停止在用户资料依赖，不进入Task 2。

Review: 本轮按R0范围自审入口、记录、资料和评分边界；未做完整P0/业务分支最终审查，不派生新任务或清理历史工作区。
