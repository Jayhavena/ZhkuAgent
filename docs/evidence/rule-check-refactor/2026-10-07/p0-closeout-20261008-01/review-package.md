# P0 incremental review package

Root: D:/alldesk/Projects/ZhkuAgent. Existing linked worktree with extensive prior dirty state; do NOT review all git diff against HEAD, reset or commit. HEAD is unchanged; actual bases are before byte snapshots and incremental changes.diff per batch.

Spec: docs/product/rule-check-scope.md; plan: docs/superpowers/plans/2026-10-07-rule-check-p0-plan.md; ledger: .superpowers/sdd/2026-10-07-rule-check-p0-plan/progress.md (read Ruling lines).

Review R0 safe explicit verification runner + R1 candidate budget + R2 admin raw text + R3 parser/guard + R4 default ACTIVE, explicit offline evaluation, qualified SQL before vector LIMIT. No migrations/POM/student protocol changes in these batches. G0 not passed and RC04 semantic/RC05 full lifecycle remain open. No production readiness or quality benefit claim.

Batch material under docs/evidence/rule-check-refactor/2026-10-07/: r0-baseline-01, r1-fusion-01, r2-admin-01, r3-output-20261008-01; R4 current changes-review.diff and before/ at r4-read-20261008-01. R4 red-03:22 tests11 assertion failures, green-01:24 pass. Final19-class Acceptance p0-20261008-regression-01 currently running; inspect results when available, do not infer success.

Read-only source/test/metadata inspection. No database, provider, new branch, test execution or cleanup; do not spawn additional agents. Write only review.md in this package directory, report concrete file/line/severity with evidence and all declined-to-judge boundaries. Source hashes and issue closure updates pending final evidence assembly, do not treat pending ledger bookkeeping as already claimed complete.

## Review Focus (verbatim)

1. 测试替身忽略 limit 会让 RRF 漏召回测试假通过：Task 2 替身必须截断。
2. 原文是 HTML/注入或扫描空白页：Task 3 安全显示并提示人工处理，不静默丢弃。
3. 直接构造 NaN 绕过 parser：Task 4 guard 同样拒绝，不只测 JSON。
4. 最接近的向量都不合格：Task 5 资格过滤前不得 LIMIT，合法较远条款仍应召回。
5. SQL 资格、Java资格、profile状态与索引内容漂移：Task 5 真库矩阵验证，Task 6 说明仍未完成时间映射。
