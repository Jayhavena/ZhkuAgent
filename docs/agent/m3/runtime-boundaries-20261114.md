# M3 业务边界修复检查点（技术验收收敛，财务补证待办）

当前状态（2026-11-17）：固定 21 条要求已由本次 255 项核心回归覆盖；8fd53b48 重新构建后的首启/重启 HTTP 各 14 项通过，资源退出已核对。与 be762644 已授权真实复测的生产源码一致；真实两轮 8 次调用、Grader 2 PASS。汇总及 M4 交接见 [验收记录](acceptance-20261117/REPORT.md)。实际账单仍 UNVERIFIED，不宣称总体质量、main 集成或上线通过。以下按时间保留历史范围与失败记录。

基线：M2阶段结项记录9a088ea8，M2按用户决定阶段性结束，M2-BILL和M2-FAIL仍待补证。M3本轮只做离线业务复现/修复，不调用真实模型或数据库，不重建/重放已消耗请求包，不把旧质量分支整包合入。

## M3-01 缺执行前提的动作不能冒充预算耗尽

Runner在ActionState禁止某动作时统一返回BUDGET_EXCEEDED/STOPPED。复现：初始状态只允许SEARCH，决策却要求ASK，没有任何已读事实；应为INVALID_MODEL_RESPONSE/FAILED，原实现实际STOPPED。新增测试先观察该失败，再修复。

修复按原因区分：没有候选的READ、没有事实的ASK、没有观察的FINISH属于缺少执行前提，停止为协议错误；其余资源可行性限制仍保留预算停止。首次扩大回归发现第三次非终结动作侵犯独立核验预留的旧用例由STOPPED变FAILED，主Agent没有修改该旧期望，收窄修复后重新回归。此前将所有禁用动作判INVALID的尝试未作为最终语义提交。

新增参数化覆盖READ/ASK/FINISH三例：只消费一次决策、不执行工具/独立核验、无观察、toolStepCount=0、两个端口关闭、运行终止且明确FAILED/INVALID_MODEL_RESPONSE。真正预算不足/取消/超时/无进展沿用独立守卫。

## 核心边界回归映射

| 业务项 | 当前选类/验证内容 |
| --- | --- |
| 必要澄清 | RulePublicationGateTest：独立必要性+条件事实，缺用户条件不能被语义绑定伪造；OnlineFactory两轮及共享额度合成回归 |
| 规则适用性 | RuleCheckApplicabilityTest：对象/年份/历史规则与当前安排、个人资格、第一人称一般解释的区别 |
| 引用关联 | Gate/E13PublicationCapability：未读事实、改指纹、无关绑定、缺覆盖、跨任务核验不得发布 |
| 原文追溯 | RuleCheckOriginalEvidenceServiceTest：版本/摘要/资格/原文内容变更、跨度及代理对、URL白名单；不杜撰页码 |
| 异常停止与释放 | Runner/Provider/TaskStore：非法前置动作、预算预留、取消、超时、无进展及端口关闭；新增三例不触达供应商 |

上述多为已有测试，本轮新增测试仅针对M3-01，不把重跑旧测试称为新建全部覆盖。尚待独立审查必要澄清/适用性等覆盖空洞及旧质量修复未集成范围；本检查点不证明真实Agent质量提升、真实供应商失败停止或长期稳定性。

验证：初始新增测试1项失败（期望FAILED、实际STOPPED）；首次扩大选类192项仅旧预算预留用例失败，收窄修复并参数化后194项全通过，0错误/跳过。日志依次knowledge/target/m3-action-red.log、m3-boundaries-green.log（该文件名不是成功结论）、m3-boundaries-final.log。命令在仓库根用mvnw.cmd -o -pl knowledge -am、显式选类RuleAgentRunnerTest,RuleAgentProviderTest,RuleAgentTaskStoreTest,RulePublicationGateTest,RuleAgentE13PublicationCapabilityTest,RuleCheckApplicabilityTest,RuleCheckOriginalEvidenceServiceTest,RuleAgentOnlineFactoryTest,AgentEvalChainRegressionTest，附-Dsurefire.failIfNoSpecifiedTests=false、已创建-Dzhku.rulecheck.evidenceDir=<根>/knowledge/target/m3-evidence及-Dzhku.t06.handbookPdf=<本机已核对PDF>。读取审核原件做离线包/资料验证，不连接数据库/真实供应商；Provider与Factory测试的HTTP为本机合成loopback。

## M3-02 已知条件重复追问与确定性拒绝

独立只读审查请求OpenCode/opencode-go/deepseek-v4.1-flash，completed、executedModel未提供；主Agent核对后保留M3-01原样。审查发现旧precheck的INVALID只记录事件、继续循环，重复提议最终变NO_PROGRESS/STOPPED，掩盖真实协议错误；主Agent用既有legacy Runner与实际Provider适配器的loopback Factory分别复现FAILED预期落成STOPPED，2项先失败。

修复：确定性发布前置INVALID直接失败，不额外尝试至无进展；独立能力目录没有未知已审条件时，从下一动作和实际Schema/feedback移除ASK。只收窄能力，不授权事实、不替代Gate；若模型仍追问已知/不存在的条件，应INVALID而不是BUDGET。尚有未知条件时ASK可用，独立必要性仍要核验。

本机Factory反例明确让模拟供应商无视Schema追问PUBLIC：宿主在第3次决策停止为FAILED/INVALID，0次独立核验、不再重试、端口关闭。对实际HTTP请求的Schema及executionState同时断言ASK缺失；原有同task必要澄清两轮正常流程继续回归。迁入旧分支中仅4项纯Schema/状态单测（RuleAgentKnownConditionActionTest），没有迁入旧数据、旧整包代码或质量结论。

当前尚未装配首问声明，不能把此检查点说成首问显式PUBLIC已修复；缺口原因/能力不足的归因仍排在M3后段。M2-BILL/M2-FAIL待补证不变。

M3-02最终选择上节9类加RuleAgentKnownConditionActionTest，共199项通过，0失败/错误/跳过；日志knowledge/target/m3-known-condition-final.log。2项初始反例失败记录为m3-known-condition-red.log，先局部92项通过再扩大。既有真实预算/无进展、缺条件追问、续答发布与引用/原文守卫未放宽。没有额外调用真实模型或数据库，不能称真实质量分数提高。

## M3-03 首问声明装配与条件型发布

纯提取模块来自独立工作树提交74bd8ca345fddc720f6933f3c59afb8d88c11ef6，父级逐段阅读后迁入为4074e034。子任务请求OpenCode/opencode-go/deepseek-v4.1-flash；返回记录未证明供应商实际执行模型，不把配置身份写成执行证明。模块只输出PUBLIC/PROFESSIONAL用户声明及原文UTF-16跨度；多子问题、另一课程主体、否定/比较/转述/身份与无法判断的作用域保持未知。有限启发式仍可能漏提取，不承诺通用中文理解。

主Agent先新增TaskStore→实际OnlineFactory→实际工具/Broker→发布Gate集成反例。公共选修课/专业选修课首问均先失败：期望RESOLVED，实际WAITING_USER（m3-first-question-red.log，2项失败）。随后在Slot创建时装配声明到既有userConditions；声明跨度单独保存为任务级firstQuestionDeclarations，只读、不可变、Owner隔离，始终指向首问，不混入后续回复文本或扩展HTTP合同。没有把声明转为Evidence或绕过独立发布核验。

两条集成正例现在单轮完成：SEARCH→READ→FINISH→独立PUBLICATION_REVIEW，4次本机合成HTTP交换；分别只发布匹配PUBLIC/PROFESSIONAL的S10/RETAKE_ONLY条件事实，完整覆盖、Claim具有证据关系、无澄清、仅一条任务历史、端口关闭。重复create仍只排一个工作项。新增Store反例锁定另一主体、多问、否定/转述不写入条件，并验证emoji前缀跨度与跨Session拒绝读取。原有缺条件→追问→续答的两轮用例继续通过。

验证：纯提取/Store/Factory局部93项通过（m3-first-question-local.log）；再扩大为上一检查点10类加RuleAgentQuestionConditionsTest，共231项通过，0失败/错误/跳过（knowledge/target/m3-first-question-final.log）。命令参数沿用上节离线选类、evidenceDir与本机PDF路径。未连接数据库、真实供应商或浏览器；没有新增真实模型调用，不能推断新版本质量分数或持久学生HTTP ONLINE验收通过。

M3仍未结项：资料缺失与审核事实能力不足的归因需要继续核对和修复；本轮变更尚未进入main/CI。M2-BILL实际账单与M2-FAIL真实失败补证继续保留。

## M3-03 独立审查后的宾语作用域修补

独立审查completed，请求OpenCode/opencode-go/deepseek-v4.1-flash，executedModel仍未提供。主Agent确认其F1：谓词后的另一课程宾语会被误作省略主语，导致首问条件误填。新增5句分别在纯提取器和Store先复现，共16次测试中10次失败（m3-object-course-red.log）。修复不是只增加课程词：跨分句继承改为整句匹配有限规则谓词，不能仅匹配谓词开头；另加未在课程词表的“法语”反例。原有“公选课没过，能重修吗”和条件从句正例保留。

主Agent按已记录的仓库根、默认fork及显式PDF/evidenceDir参数复跑11类，242项通过，0失败/错误/跳过（m3-object-course-final.log）。审查者使用forkCount=0并缺PDF参数的93项复跑有路径错误，不能作为相同命令的反证；其新增两条Factory正例、纯提取与Store确实通过。测试路径仍有既有cwd依赖，本轮没有把基础设施差异藏成代码失败或更改原始测试记录。

审查F2保留为已知边界：跨度目前是本机任务级投影，独立核验输入仍只有条件值与问题原文，没有自动核对声明跨度；合成核验只证明接线，不证明会识破任意误声明。提取器仍是有限启发式，不能据此宣称所有中文语序安全。审查F3确认无fact原文回退应保留，不能机械要求acceptedBinding；下一步需避免完整覆盖被伪缺口降为PARTIAL，并区分能力缺口与原文详情缺失。旧evidence-first类别提取器与本轮Task API不是同一路径，其问题另列，未合并或宣称已修。

## M3-04 由Gate区分事实能力缺口与漏答（待最终审查）

主Agent没有直接采用审查建议的“只要uncovered非空即允许缺口”：漏选已读可用事实同样会产生uncovered，仍会错误归因。采用更窄的聚合：QuestionAssessment增加内部factCapabilityGapRequirementIds，只有已识别需求且当前已读、指纹匹配的能力目录没有对应dimension/subject/CORE能力才加入；不因用户条件未补齐就把已存在能力记成缺失。该字段是本机Gate结果，不增加模型响应合同或学生HTTP字段。

Publisher要求该子问题真实成功读取过VERIFIED_EVIDENCE/VERIFIED_FACT，并由Gate确认能力缺口，才自动产生REVIEWED_FACT_COVERAGE_GAP。已读事实能力可覆盖但未选中的需求仍为ANSWER_COVERAGE_INCOMPLETE/FAILED；完整覆盖不能借早先无fact读取自报缺口。无法理解的需求仍沿用原处理，不能被缺口码掩盖。无独立核验的旧legacy路径保持原兼容边界，未声称同样完成新语义验收。

4项新增合成反例先全部失败（m3-gap-red.log）：原文无fact的诚实回退实际FAILED、完整Claim伪缺口及漏选事实伪缺口未被拒绝、两类缺口文案相同。局部修复后Gate/E13/新增4项共28项通过（m3-gap-local.log），随后增加合法的“已有Claim同时缺另一需求能力”用例，验证宿主不依赖模型自报标签自动归因。fixture使用真实EvidenceTools、Broker、Gate、Publisher，两块合成原文的事实需要完整绑定：先只读一块确实得到VERIFIED_EVIDENCE，再读齐得到VERIFIED_FACT；不伪造观察，不连接供应商。

决策提示词同步允许经宿主确认的REVIEWED_FACT_COVERAGE_GAP；其文案明确“当前审核事实能力未覆盖，不能据此断言原文没有规定”，与模板标注详情缺失区分。不放宽Claim、checksum、when、独立核验或原文资格守卫。尚未证明真实模型会正确使用新码，也未补做M2真实账单或真实供应商失败。

扩大回归第一次未通过（m3-gap-final.log）：新增两例构造了协议禁止的空Proposal（2 error），新增提示词使旧固定载荷字节断言由4935变为5122（1 fail）。未修改旧业务状态期望；删除非法构造并以合法Claim+能力缺口用例替换，ProviderTest同步当前合成载荷字节基线和中文说明。该失败记录保留，不作为成功证据。

最终主Agent在仓库根以默认fork、显式evidenceDir与既有已核对PDF属性运行前述11类加RuleAgentGapAttributionTest，247项通过，0失败/错误/跳过（knowledge/target/m3-gap-final-verified.log，BUILD SUCCESS）。无真实供应商/DB/浏览器调用。新增内部Gate缺口归因需要独立审查后再讨论M3结项；F2来源标注、旧evidence-first提取与测试cwd依赖仍是已知范围边界，未声称已修复。M3代码未push、未入main/CI，M2-BILL/M2-FAIL仍待补证。

## M3-04 最终独立复核与回归补充

独立复核d6d3a2f6结论“不阻断本提交”，请求OpenCode/opencode-go/deepseek-v4.1-flash，未取得供应商executedModel证明。其亲跑新增5项+Gate20项共25通过、载荷字节1项通过、首问纯提取29项及Store26项通过；与主Agent的247项汇总分别记录，不声称独立复跑全部。

主Agent补两项合成回归（只改测试，不改运行逻辑）：实际读取齐两块、取得两项可用事实，只提议一项且不自报gap，发布结果必须FAILED/PARTIAL、保留已核验Claim、issues恰为ANSWER_COVERAGE_INCOMPLETE；已读条件型能力存在而userConditions为空时，Gate可以保留需求未覆盖，但factCapabilityGapRequirementIds必须为空，不能把缺条件当能力不存在。两类定向共27项通过，0失败/错误/跳过（knowledge/target/m3-review-followup.log）。

保留产品边界：VERIFIED_EVIDENCE既可能是审核事实未登记，也可能是多块事实只读取了一部分；当前缺口码仅表示“本轮当前已读事实能力未覆盖”，不证明目录永远没有该能力或原文没有规定。现有hasUnreadFact只影响READ可用性，未增加“发布前必须穷尽所有候选”的强制要求。此处选择记录，不以无关重构扩张M3范围；如产品要求区分，需另行设计采集状态。

## 当前修复版本的真实临时库补验

测试代码SHA a42bdab00ea3380c8c83702439706432001e317f（生产代码仍为d6d3a2f6）。先执行ensure-zhku-postgres.cmd -DockerOnly，确认Docker Linux引擎就绪、共享演示库未操作。重新检查数据库凭证，进程无值、Windows用户级可用，由既有导入器注入；验收启动器为本批临时实例另生成随机口令，不入命令行/报告。仅使用127.0.0.1:55539/zhkuagent_rulecheck_e2e、无挂载卷的独立临时容器。

首次执行遗漏显式PDF路径，3项因NoSuchFileException出错，未进入业务断言；批次m3-loop-db-20261114-a42bdab0保留，resourcesVerified=true。主Agent修正自己的调用环境，第二批m3-loop-db-20261114-a42bdab0-pdf通过子进程JAVA_TOOL_OPTIONS注入已核对PDF绝对路径，finally恢复原进程选项；未改测试、原始批次或批准资产。命令使用verify-rule-check-refactor.cmd -Mode Database -Tests RuleAgentLoopDatabaseTest -Port 55539 -EvidenceRoot docs/evidence/m3-runtime-20261114及独立Phase。

第二批3项测试全通过，0失败/错误/跳过。受控已审资料真实SQL/迁移/检索/读取/引用审计与Runtime参与，决策和语义核验仍是可编程测试端口，不调用模型、不证明自然语言质量。六个发布场景C01/C02/C03均RESOLVED/COMPLETE且2条Claim，C04为FAILED/PARTIAL、保留1条Claim、唯一ANSWER_COVERAGE_INCOMPLETE，C05为RESOLVED/COMPLETE且3条Claim，C06为FAILED/INVALID_MODEL_RESPONSE、0 Claim。另两项涵盖必要澄清实际工具链和多问题阈值运算。

Flyway实际与预期迁移均23，测试后业务连接0，sourceDrift=0，containerExited=true、resourcesVerified=true、无遗留新Java，55539不再监听。脱敏元数据及原始证据文件SHA见db-regression-a42bdab0.json；原始批次包含受控正文，保留本机docs/evidence，不暂存或共享。该批没有HTTP或浏览器，因此不能称当前持久学生HTTP ONLINE已验收。M3目前已完成这些工程修复检查点及独立复核；整体真实模型行为/账单/负向失败验证仍未补齐。未push、未合main，后续M4需先明确18个本地提交中M2证据与M3代码的集成范围，不能把整分支当作一个小修直接合入。

## 固定版本HTTP复验与下一批准备

当前被测SHA 93dc4a1c630bbbc29ba715e3a4292b0c0d418d9c，从源码重新构建persistent.jar（摘要见http-93dc4a1c/build.json）。专用实例m3-http-20261114：backend58190、identity55540、rules55541，SIMULATED，资料使用既有已核对只读资产根及显式PDF。数据库操作前重新核对Process/User/Machine凭据；启动器为专用库创建独立随机凭据，没有写共享演示库。

仓库HTTP脚本initial 14项和Restart -SkipBuild后的restart 14项均通过：注册/登录、CSRF、任务及续答幂等、WAITING_USER必要澄清、原文访问与跨账号隔离、RESOLVED、回放未覆盖、取消或已完成冲突、退出401。重启账号保留、旧任务失效；本批没有浏览器。Stop退出0、hostExitCode=0，实际host进程退出，规则容器不存在，身份容器exited且专用卷保留；58190/55540/55541均无监听。脱敏HTTP回执见http-93dc4a1c/，不含Cookie/CSRF/口令/原文。

随后仅Prepare/CheckPackage新M3最小两轮Probe：244c5d272cdd2e3bf2d39601bf1254c8a75a63ee189e6786ebfbdd3a26cdc596，零调用/零数据库准备校验通过、绑定器23项通过；6个私有冻结文件逐字节备份。本批未授权、未执行，建议预算1元/最多10次/两run，不沿用M2票据。范围、披露与限制详见eval/m3-minimal-20261114/README.md（相对于docs/agent为eval/m3-minimal-20261114）。工程验收已补齐本次关键HTTP，真实复测依然待新授权；不能声称新版真实质量已证明。

## 2026-11-16 M3真实复测：失败，未结项

用户明确授权后执行新包244c5d27…cdc596一次，实际2次DECISION/HTTP200后首轮失败，输入2547、输出95 tokens。模型在SEARCH后未READ，提议FINISH_CHECK，issue组合CONFLICT/CANCELLED违反领域配对约束；宿主ACTION_PARAMETER_INVALID→INVALID_MODEL_RESPONSE/FAILED，0 Claim、无续答/独立核验、无自动重试，资源关闭，单次票据已消耗。新缺陷M3-LIVE-01为Schema分开列举kind/reason、缺少二者配对约束。既有失败绑定器因task-results缺失不能绑定两run，Grader原生结果2 NOT_EVALUABLE、regressionPassed=false；与实际运行FAILED分开记录。

详见docs/agent/eval/m3-minimal-20261114/execution-20261116/RESULT.md及配套审计。本批只形成失败Trace/终态，没有成功规则Evidence。费用目录价估算0.0022941元，实际账单null/UNVERIFIED；Preview默认profile未登录费用控制台。不得据此更新旧20题成功率或宣布M3真实质量通过。下一步先离线处理Schema配对/失败采集，真实调用需新包新授权。

## M3-LIVE-01 离线修复检查点

Schema已按ReasonCode.kind约束合法配对；Probe先写实际发布终态再判批次是否成功，失败首轮可留下单条前缀；绑定器按前缀顺序绑定首轮，不伪造未发生续答，也不倒填未发布历史outcome。合成本机Provider/TaskStore/Collector/Grader回归验证1 FAIL+1 NOT_EVALUABLE，CI配置加入新守卫；289 Java与26 Python通过。细节、初始失败与限制见schema-failure-capture-20261116.md。旧真实失败报告保持原样；真实复验尚未执行，新包尚未冻结/授权，M3不因离线修复直接结项。

2026-11-17：f4b72a73离线修复独立复核无阻断。已补Schema缺字段与STOPPED回归、实际WAITING_USER/RESOLVED快照原子追加、真实Python绑定器→Grader组合；57 Java与27 Python通过，生产逻辑保持复核版本不变。CI显式安装Python3.11。旧真实失败/账单记录保留，新真实批次未准备或执行；离线修复检查点完成不等于M3真实任务成功结项。

## 2026-11-17 新授权修复后真实复测通过

用户明确授权重新冻结并执行，源be762644的新包3afb4f51…49c9fe仅运行一次，8次实际HTTP200（6决策+2独立核验）完成同task必要澄清及PUBLIC续答；首轮WAITING_USER，续答RESOLVED/COMPLETE、1条匹配条件Claim。新绑定器PASS、原生Grader两case PASS，19项补充原文/引用/外发/身份检查通过。完整脱敏记录见docs/agent/eval/m3-retest-20261117/RESULT.md，原始31文件私有归档。不是重放旧M2或旧失败数据。

费用目录价估算0.0147814元，输入14852/输出1074 tokens，实际账单仍UNVERIFIED/null（当前Preview费用控制台未登录）。本批技术链通过，不能推出总体可靠性、显式首问与能力缺口码等场景都通过；旧失败及费用待办保留。票据消耗、无重试，临时资源已回收。未合main/未运行远程CI，后续M4仍需界定本地M2/M3提交整合范围。
