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

Task 2: Ruling: 用户再次授权“开始下一步”，R0显式单元入口已验证，融合截断缺陷可用独立合成排名验证 — 本轮仅推进R1，不等待新版资料完成；G0仍未通过，不启动真实质量评分或用户试点。代价是候选预算调优继续依赖人工参考，不能将局部工程修复写成收益验证。

Task 2: in_progress; evidence: docs/evidence/rule-check-refactor/2026-10-07/r1-fusion-01/；保留四参调用兼容，不修改调用方、融合器、POM或数据库。

Task 2: 首次r1-red-01受限会话testCompile访问本机依赖失败，6项尚未执行；按systematic-debugging调查，不算业务RED。依赖文件可由PowerShell读取，显式离线入口在获准本机权限下新批次r1-red-02完成测试：6项/1实际排名断言失败，期望C实际A。保留两个批次，不覆盖或误归因。

Task 2: GREEN两类19项；相关回归五类44项，无错误/失败/跳过；源码无漂移、Java退出，未运行数据库或供应商。已恢复四参兼容和独立预算；RC-02闭环只针对融合前截断。

Task 2: CODE_VERIFIED_CALIBRATION_PENDING — Steps1–4/6完成；Step5工程回归完成，真实开发集10/20/40预算比较NOT_RUN。G0未通过，R2–R4未开始。按本批增量与计划自审；不等于完整P0/分支的最终独立审查，不提交已有dirty。
