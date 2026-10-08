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

Task 3: Ruling: 用户授权下一步承接R2 — 只扩展本机管理DTO和页面，保留学生v1响应及服务端审核/发布约束；R0显式入口与专用临时库用于验收，G0仍未通过。代价是全量HTTP候选仍有大文档传输成本，不声称后端分页已完成。

Task 3: in_progress; evidence: docs/evidence/rule-check-refactor/2026-10-07/r2-admin-01/。先用既有MockMvc断言管理员JSON包含rawText获得行为RED，避免新accessor不存在导致的编译RED；另留真实浏览器旧页面观察。

Task 3: Ruling: 真库验收使用本批新增local-browser-service.ps1与fixtures.sql — 现有单元/Database入口会立即清理资源，无法保留服务供浏览器操作；新增仅证据目录中的专用辅助入口，固定回环目标、随机无卷容器、随机临时口令、逐次刷新凭证并finally清理。代价是本批只验证SQL提取态合成候选，PDF上传/提取仍NOT_RUN，不能声称导入全链已完成。

Task 3: Ruling: 页面草稿与已保存数据显式分离 — 草稿跨切页/筛选保留，未保存变化禁止通过；保存后保留说明、审核结束只读、拆分清除父草稿。代价是页面刷新仍会丢草稿，已在界面提示；不新增自动保存或服务端草稿协议。

Task 3: RED12项中2个JSON断言失败；旧真实页面25卡、无原文区域、编辑区空。GREEN16项通过，局部自审补拆分草稿清理和只读数据来源后，最终八类60项回归通过，无源码漂移/错误/跳过。未新增页面源码字符串镜像测试，以真实浏览器行为补证据。

Task 3: 首次辅助脚本因before目录冲突启动前拒绝，改browser-before/after；一次受限会话无法读取已配置Windows凭证，获准本机权限后重查成功。严格标签选择器包含选项文字时未匹配，按DOM观察改用select ID；异步保存/拆分刷新后重新观察终态，不把提前读取当已完成。

Task 3: 10个浏览器场景通过；浏览/输入/翻页/筛选时DB事件0，显式保存/通过/丢弃/发布/拆分产生5事件。真实readonly PATCH 409；合成资料发布仍INTERNAL_ONLY/分享未确认，片段1。前后服务临时库均V21，Java/容器退出、连接0，未调用模型或触达原库。

Task 3: VERIFIED_LOCAL_SYNTHETIC — RC-03仅本机原文对照缺陷CLOSED，Task 3本批步骤完成；R3/R4未开始，G0仍未通过，R1真实预算校准仍NOT_RUN。按最小增量自审，未做完整P0最终独立审查、不提交/部署或清理其他dirty文件。

Task 4: Ruling: 2026-10-08用户授权“开始下一步”，承接R3结构修复；G0仍未通过，仅使用合成响应和本机HTTP桩，不运行数据库/真实供应商或学生试验。2026-10-07是本轮重构证据命名空间，执行日期单独记录为2026-10-08。

Task 4: in_progress; evidence: docs/evidence/rule-check-refactor/2026-10-07/r3-output-20261008-01/。先保存12个已有文件的原始字节及哈希；新parser/guard测试独立隔离反例，不改变四字段协议和0.5门限。ModelResponse保持直接构造兼容，在guard补下发校验。

Task 4: Ruling: 追加文件声明GroundedAnswerComposerTest.java、SpringAiAdapterContractTest.java及验收入口单元白名单；用于确认严格解析后仍受控降级，以及真实Spring AI客户端调用共享解析器。已核对适配器测试仅绑定127.0.0.1随机端口、使用固定虚构key、finally/AfterEach停桩；允许显式选类，不放宽通配符或真实供应商禁令。

Task 4: Context7已resolve并两次query，但返回3.x文档，不能当2.18.3依据；已改查官方jackson-databind-2.18.3标签JsonNode源码核对类型检查和取值语义，以当前依赖离线编译验证，不升级依赖。

Task 4: 首次r3-20261008-red-01新增测试访问私有DECLINE_MESSAGE导致编译失败，无测试执行；修为公开输出合同断言，不扩大常量可见性。新批次red-02实际46项/17断言失败，无错误/跳过，构成真实行为RED。

Task 4: GREEN四类46项通过；最终相关回归11类96项通过，入口合同29项通过，无源码漂移/Java残留，不运行数据库/真实供应商。守卫同时覆盖直接构造，ModelResponse/POM/系统提示/学生协议未改。

Task 4: 合成语义探针：原文4、回答40、合法引用；guard未拒绝且composer返回ANSWERED，qualityGatePassed=false。仅保留已知风险，不写成应当放行的回归，不算真实质量分。RC-04-STRUCTURE CLOSED，SEMANTICS OPEN，父项PARTIAL。

Task 4: VERIFIED_STRUCTURE_SEMANTICS_OPEN — 本批Steps1–6完成；G0未通过，R1预算校准仍NOT_RUN，R4未开始。按增量自审类型、分值、空引用、调用链与证据完整性，不代表完整P0最终独立审查，不提交/部署或清理其他dirty。
