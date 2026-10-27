# ZhkuAgent 真实学生问题能力验证：本轮结论

日期：2026-10-26（Asia/Shanghai）。工作树：`D:\alldesk\Projects\ZhkuAgent\worktrees\integration\mainline`。

**结论：已有真实模型与受控证据工具的Agent核心，但还不是经验证可服务真实学生问题的产品。最缺的不是另加Memory、Skill、MCP，而是：非预置输入的可用真实入口、与产品承诺匹配的可发布规则覆盖、以及真实自然问答的逐题端到端质量证据。**

本轮完成用户要求的收尾、只读链路审计、18题设计及零外部模型验证；在线题目执行数为0，没有宣称18题通过。未修改生产业务代码、Runtime或前端源码；未增加功能、提交或推送。只有本报告目录和rc2 manifest实际遗留状态收尾落盘。测试会更新忽略的target产物，不等于源码变更。

## 1. rc2 三路评测收尾

- 原15道正向检索题：LEXICAL/VECTOR/RRF_HYBRID的Recall@5和完整证据集率均1；MRR分别0.966667/0.966667/1；六项禁入、绑定与scope门禁计数均0。它是小规模开发诊断集，不是Agent回答成功率。
- 资料为17个chunk中8个合格、9个排除。候选与独立审核仍是PENDING，严格原文定位评分仍关闭；没有把这些状态改成产品合格。
- 已存在的额外重放于21:31完成：JUnit 1项成功；使用缓存query embeddings，额外外部模型调用0。本轮**没有再跑一次数据库重放**。核对5个输入字节一致，三个输出仅generatedAt（包括嵌套execution.generatedAt）及派生inputResultsSha256不同，排名、绑定和评分一致。最初只忽略顶层时间造成误报，检查结构后修正比较；未发现检索漂移。
- 临时源目录中的harness已无残留；本轮Maven重新编译后无其class残留。核对并删除原8文件临时重放目录，不删除证据目录内正式留存的harness源文件。保留既有隔离rc2容器与数据库；不是本轮创建的资源，不擅自销毁，不操作原演示库。
- manifest遗留的`PENDING_ASSET_VALIDATION`与`assetValidation=PENDING`已据实际验证改为PASS，并更新lastRun。**没有更改candidate status或independentReview。** 22:14:41执行既有校验器，12项PASS，包括全部artifact哈希。
- 历史manifest保留此前3次query embedding API调用和3行source_url修复，不把历史费用与写库伪记成0；本轮新增外部模型调用／DB写入均0。

证据：[检索报告](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-source-retrieval-v2-rc2/retrieval-comparison-report.md>)、[manifest](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-source-retrieval-v2-rc2/manifest.json>)、[收尾记录](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-question-agent-readiness/rc2-replay-closeout.json>)、[本轮校验日志](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-question-agent-readiness/rc2-validation.log>)。

### Git状态

核对HEAD=`245880b6b8a62e2bdef69dcb8d6721f72164ceef`，分支`integration/zhkuagent-mainline-20261024`。tracked工作区diff与暂存diff均空；三组未跟踪目录为：原有rc2证据、原有检索基线证据、本轮readiness证据。没有把原有未跟踪资料删除、暂存或提交。只报告这个工作树的状态，不推断其他worktree干净。

## 2. 真实调用链与四种模式

学生默认`/rules` → v2异步task API → TaskStore → OnlineFactory/Runner（共享agent-runtime）→ SEARCH/READ → DashScope `qwen3.8-flash`决策 → 单独语义核验请求 → 确定性publication gate → 审核事实模板主张与fresh原文凭据。

|层次|真实模型|真实资料／处理|非预置问题边界|
|---|---|---|---|
|旧sealed/task-plan replay|否|工程回放协议|不证明自然语言理解；旧启动器明确NOT_LIVE|
|SIMULATED demo|否，决策和核验是替身|真实Runner、DB审核资料、Gate|固定原题及重修分支；不能借真实DB把它称为在线Agent|
|ONLINE通用核心|有真实Provider接线|词法检索、审核资料、capability、Gate|接受非白名单自然语言输入；仅10个factId可发布；实际语义质量尚未本轮在线验证|
|现有ONLINE HTTP验收宿主|具备真实调用路径|带审包、同task两run、原文凭据|仍只准“我这门选修课没过，能不能重修？”+逐字PUBLIC；非预置题在Provider创建前被拒。旧核价授权在2026-10-25后失效|

源码依据：[Runner:55–121](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentRunner.java#L55-L121>)、[Provider:19–36](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java#L19-L36>)、[HTTP硬范围:9–33](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentOnlineHttpScope.java#L9-L33>)、[启动门禁:85–90](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/knowledge/scripts/start-rule-agent-http-check.ps1#L85-L90>)。

**入口文本框可以自由输入，不等于当前部署已经接好通用在线宿主。** 普通controlled主入口只扫描controlled/security；本轮未启动服务器、查看在运行页面或查询实时数据库，所以不声称当前页面正在使用上述哪种模式。

### 证据与发布的真实边界

- Agent工具是`Reviewed2025ControlledCatalogue + Article4Overlay`、JDBC中文bigram词法检索、fresh reader和citation audit。没有在这条链路使用向量检索、网页搜索或MCP。执行READ会写citation audit，不能把它说成只读数据库工具；本轮仅读其源码和内存测试。
- 默认目录是2025历史本科选修10条逐字条款；可发布进一步限为10个精确factId：分类2、修读安排2、学时2、学期4学分1、重修2、公共缓考1。读得到／已审事实≠可发布事实。
- rc2的8个合格chunk是另一个scope/version/绑定的数据集，并未自动接入该Agent目录。不能用其检索满分推出当前Agent可答第9、11、14、15条。
- 多轮仅限WAITING_USER下结构化澄清，需version/token/TTL及fresh evidence重验；不是任意连续聊天。自然句“刚才说错了，是专业选修，不是公选。”不符合当前类别短句正则，静态可确定会要求重新确认，而非直接接受纠错。详见[TaskStore:135–155](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentTaskStore.java#L135-L155>)。
- 核验是同一Provider的另一次用途独立请求，不是独立异构模型。Gate能校验模型已声明需求的绑定、角色、条件与覆盖，不能独立证明原问句中没有被模型漏识别的需求。参见[Gate:6–44](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationGate.java#L6-L44>)。

## 3. 18题Eval交付与当前支持程度

完整原话、逐轮输入、必要证据、禁止行为、可判定验收与当前能力映射见[Agent E2E Eval清单](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-question-agent-readiness/agent-e2e-eval.md>)。

- 直接、模糊、自然多轮、组合证据、资料不足、错误前提均覆盖。
- E02/E04/E12/E16是当前核心规则能力的正向候选；**不是已通过**，仍需真实理解／检索／条件流程验证。
- E07规则可解释，但自然纠错流程已发现明确入口限制。
- E08/E11/E18包含可答部分和未开放／无法核实部分，不能标整问COMPLETE。
- 其余10题用于诊断有据但无发布能力、缺审核目录、缺当期／个人数据、受限资料、年份和阈值等边界。它们不是“现有系统应当全部答出”的正向基线。
- 每题分开记录安全验收和产品完成度。所有题只拒答可能没有幻觉，却不能算可信问答产品已经可用。

## 4. 本轮实际执行的无外部模型验证

|验证|结果|证明范围|
|---|---|---|
|既有rc2资产校验|12项PASS|版本、17/8/9盘点、15+1+2题数、绑定、冻结v1、URL修复、检索缓存与所有hash|
|Java精选测试|71通过，0失败／错误／跳过|合同7、内存证据工具12、合成Runner22、TaskStore精选8、合成Gate15、span/excerpt7|
|前端合同＋workflow|55通过，0失败／跳过|fixture合同、mock transport、幂等／轮询／身份版本／取消／失效原文|
|已完成额外重放的静态复核|输入一致、排名和评分一致|不重复触发DB或embedding；不是本轮新跑端到端Agent|

Java使用本机Maven 3.9.15的`-o`及精确测试白名单。`-o`本身不能阻止测试代码联网，所以先审阅选中测试；没有整包运行Provider、本机HTTP宿主、数据库、浏览器或在线测试。Java执行仅移除子进程继承的模型／DB凭证，不读取密钥值。测试会写target和本轮日志；没有操作业务数据库。

实际命令和计数保存在[验证摘要](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-question-agent-readiness/offline-verification.json>)；原始日志：[Java](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-question-agent-readiness/offline-java.log>)、[前端](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-check-refactor/2026-10-26/real-question-agent-readiness/offline-frontend.log>)。

**不能据此证明**：18题真实模型成功率、当前部署模式、真实DB已导入、供应商当前可用、2026政策现行、个人资格、多轮自然纠错、真实延迟和账单。

## 5. 在线调用事前说明：本轮未执行，等待未来明确授权

目前没有新问题审包，也没有与其范围匹配的可直接执行HTTP宿主。本轮不改门禁、不沿用旧授权、不构建新功能。以下只是下一轮候选预算；同意费用本身不等于授权改代码或跳过输入范围审查。

### 调用数与估价假设

模型候选：DashScope北京compatible-mode／`qwen3.8-flash`；每run最多5次模型请求（含语义核验），每task最多12次，失败或超时也可能计费，不自动重试／续批。

|候选范围|调用数量上界|仅供规划的典型费用估计|历史全模型上下文保守包络|
|---|---|---|---|
|先做E02及E04一个获批类别分支：2任务、至多3run|15次|约0.14–0.33元|约12.04元|
|完整18题，含允许范围内补充澄清|216次（18×12）|约1.97–4.73元|约173.40元|

估价**不是现价承诺或已获批支出**。仅采用现存旧审包记录的历史单价：输入0.8元／百万token、输出2.7元／百万token；典型假设每请求8k–24k输入、最多1024输出，不计免费额／缓存折扣。保守包络照旧包采用每请求100万输入+1024输出；不是预期用量。真实65,536字节请求限制不应直接等同token数。公式为`N×(输入token×0.8+输出token×2.7)/1,000,000`。

旧包核价日期2026-10-18，执行截止2026-10-25，已失效；须**重新核对官方价格、确定人民币硬上限、冻结具体输入和calls预算并取得明确授权后**才可调用。全套216次只是最坏调用上界，不承诺全套均应消耗到上限；单factory全批额度至多24，必须显式拆批且不得自动续批。实际初始两任务只能在新获批范围内执行，现有一题宿主无法直接复用。

历史估价来源：[旧审包:2906–2932](<D:/alldesk/Projects/ZhkuAgent/worktrees/integration/mainline/docs/evidence/rule-agent-integration/2026-10-25/online-http-214811-60a29445/request-package.json#L2906-L2932>)。本轮没有为核价访问官网，也没有调用供应商。

### 外发范围与风险

拟外发：合成学生问句及允许的澄清值、子问题、已审核条款摘录／fact文本、候选摘要、证据版本和定位、剩余预算与行为／核验schema。模型决策与语义核验是两种请求，均计入上述次数。

不外发：真实姓名／学号／个人教务记录、登录内容、Cookie／CSRF、Owner／session／continuation和original token、数据库密码、源码、整本PDF、预期参考答案。API key仅通过供应商HTTPS Authorization完成鉴权，不能误称供应商完全收不到鉴权密钥；不进prompt／日志。

风险：问题和条文将到第三方供应商；其留存政策需在新授权中明确；幻觉／漏需求／同模型相关误判；词法检索漏召回；超时仍计费；2025被误当现行；测试READ会在专用临时库写引用审计。使用合成身份、新回环临时库、脱敏本地证据，不碰原库；这些边界也必须进入新审包，不能从本报告推定已执行。

## 6. 最高优先级的三个产品问题

### 1）自由问题入口与实际可执行服务范围不一致
学生能输入任意文字，但模拟是固定题，现有ONLINE宿主也只授权一题，且已过期；核心可接受任意字符串不等于已有真实学生服务入口。自然更正与已结束任务续问也不支持开放聊天。

下一步验收要回答：一个全新措辞能否从学生入口，走到真实模型、合法澄清、带证据答案与原文打开；并把不支持的自然多轮明确记录。**本轮只指出并设计用例，没有扩展入口或Runtime。**

### 2）资料存在、检索命中与可发布答案之间存在显著断层
rc2的8个合格chunk不等于Agent默认目录；Agent目录有十条，实际只开放10个事实能力。常见的未选课成绩、相近课程、已合格重复、缺勤等问题无法凭“资料里有”就得到当前Agent答案。无差别拒答虽安全但不可用。

下一步产品决策应先明确首批承诺回答的规则家族，再针对本清单记录完整／部分／缺证，而不是只扩大检索指标或继续做无关治理。**是否开放更多规则需另行授权，不在本轮实现。**

### 3）可信与完整还缺真实自然语言检验及适用性证明
2025历史条文≠2026已认证现行政策，更≠当期通知或个人资格。独立核验仍与决策同模型；Gate能卡结构和引用，不能证明没漏懂问题。71+55离线通过不能补上真实成功率、自然多轮、延迟与费用的证据。

下一步应以逐题真实链路轨迹、需求完整性人工判定、版本／个人边界与有用完成度为验收，不以“没有幻觉”或“检索Recall=1”单独宣称产品可用。

## 最终回答

**当前ZhkuAgent离“学生输入真实问题并获得可信答案”，还差把已存在的有界Agent核心变成范围真实、入口可用、证据覆盖足够且经过真实自然问答验证的服务。** 目前能证明的是工程边界与安全合同已具备相当基础；尚不能证明非预置学生问题能被端到端稳定解决。本轮已完成限定工作，到此停止，不启动在线调用或新开发。
