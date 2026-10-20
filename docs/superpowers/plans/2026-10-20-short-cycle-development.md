# ZhkuAgent 短周期开发基线与任务

> 执行方式：用户确认具体开发任务后，在本会话使用 executing-plans 逐项实施，不派生代理。复选框只记录实际验收结果。

**目标：** 继续现有工程，先交付学生可完成的规则问答闭环，再验证真实模型的答案质量。
**实现：** 沿用 knowledge 独立规则循环、审核资料工具、发布核验与现有学生页面；不新增框架。
**技术栈：** 现有 Java 21 / Maven / Spring / PostgreSQL / Vue / Windows 启动器。
**需求来源：** [原任务合同](2026-10-13-rule-check-agent-plan-review.md)、[既有集成计划](2026-10-18-project-integration-and-closeout-plan.md)。本页只收敛近期工作，不重写需求、不替换历史验收。

## 核对范围和真实状态（2026-10-20）

- 当前目录 D:/alldesk/Projects/ZhkuAgent；分支 zhkuagent-modules，HEAD `a687c059371ec7177e2eb25dda72daa45f756c21`，暂存区为空。
- 主线有四份既有 docs/product 修改，并有大量未跟踪资料；全部保留。本轮不移动、清理或纳入提交。
- 实际接续工作在 C:/Users/nangua/.codex/worktrees/rule-agent-integration/ZhkuAgent，分支 codex/rule-agent-integration-20261018，HEAD `46b05452e20b05ca1ec08c2fb2400f3969052da6`。T05/T06/T07 已整合，不能再照主线旧文档从头合并。
- 集成工作区暂存为空，状态列出 639 个已跟踪变化、500 个未跟踪文件；普通 diff 汇总仅两份安全脚本有内容差异，knowledge main/test 忽略行尾的差异为空。未跟踪的模拟宿主及启动器是重要新实现，不能因 diff 为空就认为工作区可丢弃。未做全体字节恢复审计；执行前只核对所选任务及必要依赖。
- 集成工作区的 HEAD 不包含全部接续源码。M1-04A identity.json 记录 711 项源码/工具、22 项资料及 25 份历史测试报告；M1-04B 报告记录尝试前后逐项一致。本轮只读取这些记录，没有重新全量计算其摘要，也没有复验历史通过结论。
- 最新事实入口是集成工作区 docs/preparation/m1-03/RESULT.md、m1-04a/RESULT.md、m1-04b/RESULT.md；它们晚于已提交的 M1-02 未放行结论及主线 10 月 18 日状态。

## 最小需求基线与实现依据

代码相对路径下文默认位于 knowledge/src；标为“集成”的文件须在上述集成工作区读取。历史测试结果与本轮测试分开解释。

| 最小需求 | 状态 | 代码或测试依据 / 边界 |
| --- | --- | --- |
| 从审核规则中检索、读取并返回可定位依据 | 已实现；资料范围有限 | main/java/com/zhku/knowledge/ruleagent/RuleAgentEvidenceTools.java、web/RuleAgentReviewedToolsConfiguration.java；RuleAgentEvidenceToolsTest。使用词法检索，不能据向量模块存在声称当前已用向量。 |
| 结合适用条件形成解释，缺条件时追问 | 已实现工程机制；通用语义质量尚未验证 | RuleAgentRunner.java、RulePublicationGate.java；RuleAgentRunnerTest 的 askingRetainsAlreadyKnownIndependentFact、RulePublicationGateTest 的 askingMissingCourseTypeRequiresIndependentNeedAndConditionalFact。历史 T05 固定在线例子不等于未预置题质量通过。 |
| 学生补充条件后继续同一任务，发布有关联的主张和引用 | 已实现内存跨轮 | RuleAgentTaskStore.java；RuleAgentTaskStoreTest 的 clarificationResumesSameTaskNewRunWithoutRepeatingKnownCondition、withdrawnEvidenceStopsContinuationWithoutMoreDecisions。进程重启恢复尚缺，不纳入近期范围。 |
| 有界执行、取消、幂等、身份与资料撤回约束 | 已实现；历史模拟 HTTP 已验 | web/RuleAgentTaskController.java、RuleAgentTaskStoreTest；集成的 RuleAgentSimulatedChainTempDbHttpTest。M1-03 记录指定两方法 2/0/0/0 及两份 chain JSON，使用真实临时库/HTTP，决策模拟。 |
| 学生页面完成提问、追问、原文核对与终态展示 | 部分实现 / 普通启动交付未通过 | agent/frontend/src/views/RuleCheckView.vue、state/rule-task-workflow.js。旧浏览器分层记录存在；新增独立模拟宿主尚未成功正常启动，不能继承测试租约宿主的浏览器通过身份。 |
| 独立启动一个零真实模型的模拟业务宿主 | 已实现准备；运行验收未通过 | 集成 main/java/com/zhku/knowledge/ruleagent/demo/RuleAgentSimulatedDemoHost.java、RuleAgentSimulatedDemoFactory.java 及 RuleAgentSimulatedDemoTest；M1-04A 记录 11 项 Java 与普通终端 10 项检查。固定重修题只作工程模拟。 |
| 未预置问题可答、条件正确、引用相关且不滥追问 | 尚未验证 | T08 正式未执行；codex/t08-acceptance-prep 保留准备成果，不能把合成工具测试当模型质量成绩。 |
| 超出资料范围时说明缺口；学生实际查证更快且质量不降 | 缺口合同已实现；产品收益尚未验证 | RuleAgentContract.java、agent/frontend/tests/rule-agent-contract.test.mjs；T09 学生/PDF 对照正式未执行。当前资料限 2025.9.2 历史本科规则，不代表全校规则覆盖或现行政策。 |

**本轮实际测试：** 在主线 agent/frontend 执行 `node --test tests/rule-agent-contract.test.mjs tests/rule-task-workflow.test.mjs tests/replay-entry.test.mjs`，57 通过、失败/跳过 0，退出 0。只证明本次选定前端合同/工作流/模式路由；没有运行 Java、数据库、浏览器或真实模型。

## 优先任务（依次执行，每项独立验收）

估时为工作量判断，不承诺日历日期；每项先复现最小失败，再做必要修改。已有通过项不重复开发。

### 1. 补齐普通终端模拟后端启动闭环（推荐立即开始，约半天）

**目标：** 接续 M1-04B，消除执行环境造成的假启动成功，取得独立模拟应用的实际 HTTP 就绪与退出证据。

**范围：** 新增 scripts/tests/rule-agent-simulated-demo-terminal-contract.ps1，固定同一普通用户环境下的 EXE/CMD 调用与退出码回归；复用集成的 knowledge/scripts/start-rule-agent-simulated-demo.cmd / .ps1、tools/m1-02/Safety.ps1。只有回归证实项目启动器存在缺陷才修改它。最近已证实的 PATHEXT 遗漏位于临时执行控制器，不预设 Java、Spring 或业务启动器有错，不把外部临时控制器发展成新框架。

**验收标准：**

- [ ] 同环境原生标记命令输出准确，退出 29 原样传递；缺 PATHEXT 时明确失败，不能由空 LASTEXITCODE 得到成功。CheckEnvironment 明确为预检，没有 DB/Provider 副作用。
- [ ] 在用户确认本任务和实际临时资源范围后，普通终端通过原 CMD 启动独立模拟宿主；取得 host-ready、capabilities=SIMULATED、登录/CSRF 和一个本人任务的真实 HTTP 响应。只用专用临时库，不读取或注入模型 Key。
- [ ] 正常 Stop 有 host-exit 和资源回执；本轮 Job/JVM、HTTP 监听、临时容器/卷退出均可核对。失败时保留原生退出码和首个错误，不以预检成功代替实际启动，不自动循环重试。

**测试方法：** 先跑新增无数据库终端回归；再用现有 M1-04A identity 与 prepared 制品核对任务必要文件，若代码改变则重建独立副本及更新身份。复用原 Start/Stop/Recover，保存单批就绪、HTTP、退出结果。已配置口令按项目规则每次重新检查，不输出值。

### 2. 学生页面完成一次规则查证与追问闭环（依赖任务 1，约半天至一天）

**目标：** 学生能登录、提问“我这门选修课没过，能不能重修？”，补充课程类别，读到对应结论和可打开的依据，并能结束任务。

**范围：** 复用 agent/frontend/src/views/RuleCheckView.vue、src/state/rule-task-workflow.js、src/api/rule-task.js 及相应 tests/rule-task-page.test.mjs、rule-task-workflow.test.mjs；复用现有同源代理。集成 RuleAgentSimulatedDemoFactory 只在真实目录/分支不匹配被复现时修复。保留 student/engineering/replay 分离。

**验收标准：**

- [ ] 同一 task 先 WAITING_USER，再按公共/专业选修课分别续答到对应有据终态；已知条件不重复追问，原文可打开，演示明确显示模拟身份。
- [ ] 退出/换身份/取消后迟到响应不恢复旧正文；原文资格撤回后清空受限内容。未覆盖问题说明演示范围，不能伪装通用在线答案。
- [ ] 实际普通启动、学生构建、浏览器观察和正常退出属于同一候选；不拼接不同批次为全绿。

**测试方法：** Node 运行 rule-agent-contract、rule-task-workflow、rule-task-page、identity-bus、student-routes、replay-entry 相关测试；按现有入口分别构建。临时库模拟宿主下实际浏览器验证两个课程类别及取消/撤回，不调用模型。只修实际失败，不重写页面。

### 3. 最小规则问答质量回归与在线验证准备（依赖稳定候选，约一天准备）

**目标：** 把“链路能跑”推进到“回答可判对错”，让后续真实模型验收直接暴露条件、引用和误拒答问题。

**范围：** 复用 codex/t08-acceptance-prep 的 docs/preparation/t08 与 tools/t08-acceptance；定向使用 RuleAgentRunnerTest、RulePublicationGateTest、RuleAgentEvidenceToolsTest。只按缺失行为添加场景，不重建评测平台。后续真实失败再决定是否修改 Runner/发布核验/模型协议。

**验收标准：**

- [ ] 先对已曝光开发例固定五类预期：条件完整直接答、缺条件必要追问、多问题部分可答、相关依据不足、处理故障；分别核对主张—条件—引用、结束原因，不把拒答自动计成功。
- [ ] 离线回归全部通过；记录这些是模拟/开发例，不宣称证明自然语言模型质量。复用的报告校验器测试通过，候选身份与资料范围明确。
- [ ] 新的未预置报告题在候选固定后独立形成；真实模型调用和费用须另行明确授权。无授权时只交付离线回归和具体可审请求包，T08 保持未执行。

**测试方法：** 选类运行上述纯单元测试、既有 T08 校验工具的合成测试；正式在线批次逐题保留错误、失败、耗时及未知费用。学生/PDF 对照 T09 继续依赖 T08 准入，当前不扩展到招募或正式试验。

## 进度与边界

| 条目 | 状态 | 下一退出证据 |
| --- | --- | --- |
| 本阶段基线与短计划 | 已整理，待用户确认开发首项 | 本文件；主线 57 项定向前端测试 |
| T05/T06/T07 集成 | 已在隔离分支，不重做合并 | 新增未提交依赖按实际验收范围保存，不整体吸收工作区 |
| M1-03 模拟 HTTP | 历史报告通过 | 保留原记录，本轮未重跑 |
| M1-04A 独立宿主 | 已实现、历史准备通过，未提交 | 保留已有源码及 identity |
| 任务 1 / M1-04B | 待确认；两次历史 Start 均未成功 | 普通终端实际 ready→HTTP→Stop |
| 任务 2 | 待任务 1 | 同候选学生浏览器完整模拟业务闭环 |
| 任务 3 / T08 | 准备可复用，正式未执行 | 离线质量合同、后续获准在线逐题结果 |
| T09 / T10 | 暂缓 | 质量准入、真实学生/PDF 对照、最终交付核对 |

每项按“明确结果→验收→核对明确暂存文件→本地提交→记录 SHA”推进。第一任务的必要未提交依赖先核对源码与历史验收身份；不能从 HEAD 46b0545 新建空白工作树后声称已携带模拟宿主。

本轮只新增本文件并执行指定 Node 测试；没有改业务代码、集成工作区、数据库、生产配置或供应商设置，没有启动服务。正式开发等待用户确认任务。近期不做新主题扩容、SSO、任务重启恢复、向量替换或架构升级。
