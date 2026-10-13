# T05 问题—事实—发布契约设计

状态：路线已获用户确认；本文是供实施前核对的设计，不是已经实现或验收通过的能力。仅修改新ruleagent发布衔接，不重构项目。基线5ee26d8；已完成复现检查点31d13f1。

## 1. 目标、事实与边界

目标：实际读取的审核事实，必须相关、适用并共同覆盖学生需求后才发布。未知理解不冒充资料范围错误；单侧比较回答不冒充完整；多规则可以联合回答。保留现有动作协议、证据工具、任务状态、权限、条件、原文核验和预算。

已验证：C01/C03同事实因主题为空被拒；C05第5/10条一般规则联合覆盖仍被逐规则containsAll拒；C04仅专业选修一侧被标COMPLETE。C02正常两侧发布、C06条件满足的无关重修事实拒绝必须保留。见[六案例](D:/alldesk/Projects/ZhkuAgent/docs/evidence/rule-agent-provider/2026-10-13/t05-11/case-matrix.json)。

根因位置：[逐事实主题检查](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleResultPublisher.java:19)、[空主题否决](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckTopicGuard.java:80)、[有主张无缺口就完整](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleResultPublisher.java:35)。新增第4条事实还继承旧CATEGORY覆盖，[增量目录](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleArticle4Overlay.java:30)并未独立表示修读安排维度。

用户已选择独立语义核验，计入原预算。未授权本轮外发；不调整4次/10秒/45秒/6动作/1024输出token等现有上限；旧许可和剩余额度不复用。不增加学生页面、HTTP、政策资料或个人教务能力。

## 2. 方案取舍

| 路线 | 能解决什么 | 边界与决定 |
| --- | --- | --- |
| 本地受控需求解析＋审核事实能力 | 可确定核对有限术语/结构，调用成本低 | 必须承认有限语言覆盖；继续扩同义词不能证明自然表达能力。本次不选 |
| 提议模型同时声明相关及完整 | 可输出结构化需求—事实绑定 | 同一提议自报通过不足以独立核验。本次不选 |
| 独立语义核验＋程序发布守卫 | 核验原问题与实际审核事实；程序独立计算提议覆盖、验证来源及约束 | 本次选定。增加一次受限核验，占原额度；判断仍可能错误，必须验证核验器，不能宣称语义得到数学证明 |

“独立”指职责与输入隔离，不表示两个调用的错误统计独立。初期建议仍用已配置qwen-plus的单独上下文，不增供应商/框架；此供应商选择尚未通过新协议实测。

## 3. 主链与核验输入隔离

保留：学生原问题/已知条件 → Agent SEARCH → 实际候选 → Agent READ → fresh事实 → Agent FINISH或ASK提议。

新增宿主发布门：确定性预检查 → 独立语义核验 → 程序逐事实相关/适用检查及子问题覆盖聚合 → 原Publisher生成审核正文、引文和状态。

核验输入只包含原问题及原子问题、已知用户条件、实际READ事实的ID/原文/when/审核范围、对应事实能力标注和受控核验合同。可见全部已读取合格事实，不因提议漏选一项就隐藏它。

不发送提议者的完整性声明、提议理由、参考答案、历史评分、owner/session、原文token或凭证。核验器不接收FINISH的factIds选择列表；宿主随后才把提议事实集合与核验关系比较。ASK核验亦不提供提议的“必要”结论，独立识别必要条件后才对照。

模型数据中的原文与用户文本都是数据，不授予工具权限。核验器只输出结构化需求、对应关系和受控不确定项，不能改事实正文、条件、权限或发布状态。

## 4. 内部合同（拟新增，非现有类）

建议集中于RulePublicationContract，不改学生快照字段结构。字段只供宿主/核验上下文使用。

| 类型 | 必需字段及约束 |
| --- | --- |
| FactCapability | factId；dimension；subject；allowedRoles；来源审核说明指纹。与事实when、对象、时间、范围说明分别保存，不覆盖原约束 |
| SemanticInput | 原questions、已知userConditions、实际verifiedFacts的安全投影、对应审核evidence与capabilities、合同/能力版本、输入指纹；不包含Owner、提议选择或提议理由 |
| DemandSpan | subquestionId、startInclusive、endExclusive；坐标基于该原子问题原字符串；不复写或归一化原文，不从工具文本冒充用户需求 |
| Requirement | id、span、dimension、subject、recognition（IDENTIFIED/AMBIGUOUS/UNCLASSIFIED）、必要conditionIds；比较两侧分别建原子需求，可共用同一个比较句跨度 |
| SemanticBinding | requirementId、factId、role（CORE/CONDITION_EXCEPTION）；仅实际已读事实；辅助解释不得单独满足CORE需求 |
| UnresolvedDemand | span、受控原因；不得自由正文或自报“已完整回答” |
| SemanticAssessment | 合同版本、输入指纹、requirements、bindings、unresolvedDemands；严格字段/类型/唯一性，覆盖所有原子问题ID |
| PublicationAssessment | 宿主生成acceptedBindings、coveredRequirementIds、uncoveredRequirementIds、受控问题、清晰度/拒绝原因；不是模型输出 |

建议单run最多8项原子需求、16项绑定、8项不确定项；超过上限停止并记录处理边界，不静默省略需求/事实。1024输出token能否容纳实际包仍需后续真实验证，不因JSON结构短就宣称预算可行。

初期dimension至少分开CATEGORY、STUDY_ARRANGEMENT、HOURS_NORMAL、HOURS_LIMIT、SEMESTER_CREDIT_LIMIT、DEFERRED_EXAM、RETAKE；其余既有事实按已审核能力登记，缺能力登记不默认全覆盖。subject描述公共选修/专业选修/一般本科规则等业务对象，不推定学生个人身份。

第4条两项事实分别支持（STUDY_ARRANGEMENT,PROFESSIONAL_ELECTIVE）与（STUDY_ARRANGEMENT,PUBLIC_ELECTIVE）；这不是P01字符串白名单。第5条原则16学时支持HOURS_NORMAL，上限32学时可作为明确关联例外；第10条支持SEMESTER_CREDIT_LIMIT。能力数据还须显式声明允许的辅助关系，例如HOURS_LIMIT可作为HOURS_NORMAL的CONDITION_EXCEPTION，不能由模型任意跨维度关联。不能把排课顺序或重修规定标为修读安排。

能力标注必须与已审核文本/解释逐项对应并版本化。旧B02/B03/T02回执不改写，新增字段不扩大资料许可。若标注引入新的政策含义，需额外语义审核；技术元数据不是新增规则审批凭据。

## 5. 确定性发布算法

1. 核验提议事实实际READ、属于本任务、fresh目录/原文/引用一致，无未解冲突；FINISH的when必须得到明确用户条件支持，提议覆盖全部子问题ID。ASK的支持事实允许尚缺其所追问条件，但不能与其他已知条件冲突，不能先要求缺失条件已经满足。失败时不调用语义核验、不发布提议。
2. 核验输入版本/指纹与当前run一致，所有需求ID、事实ID、跨度和绑定合法；每个子问题至少有需求或不确定项。跨度只证明需求来自原句，不证明语义解释正确。
3. 程序对每个提议事实寻找至少一个合法绑定，检查dimension/subject/role与能力标注一致。无关事实、未读事实、虚构维度不因其他事实覆盖充分而放行。额外相关背景只有明确CONDITION_EXCEPTION关联才允许，不用它充当主问题答案。
4. 保留逐事实对象/时间/when/审计约束。对象或其他年份明确冲突才能认定MATERIAL_SCOPE_MISMATCH；当前有效性、个人资格及实际开课缺口仍按原规则保留。词法主题为空不在新Agent路径中授予“范围错误”结论；旧v1/回放TopicGuard行为不修改。
5. 在子问题层面计算：covered = 由提议中合格CORE事实满足的原子需求集合。多条事实联合覆盖；不存在“每条规则覆盖整个问题”的要求。CONDITION_EXCEPTION关系单独保留依据，不增加主需求覆盖数。
6. COMPLETE仅在所有需求已识别、covered覆盖全部需求、没有未解决项及适用性缺口时成立。否则有可靠主张为PARTIAL，无可靠主张为NONE；必须附对应缺口，不仅改coverage标签。
7. 正文来自已有审核事实/受控条件说明，引用只关联实际发布主张和必要条件。核验器不能输出新正文。

本算法可检验结构、引用和已声明覆盖关系；不能程序化证明模型未漏识别同一长句内的隐含需求。混合请求漏项、否定/例句、相近维度误配必须纳入核验器实际评分；不能因字段合法自动判产品通过。

尤其是核验器把“修读安排”本身误识别为RETAKE、再作一致但错误的绑定时，维度匹配程序仍可能放行；程序只能阻断声明之间的矛盾，不能当作第二个自然语言真值来源。此类一致性误判必须由人工参考与实际核验输出评估检出。未证明核验器有效前，只能标工程候选，不能称三类业务缺口已修复。

## 6. 部分回答、未知与失败语义

| 情况 | coverage / task | 学生与工程语义 |
| --- | --- | --- |
| 需求全部由提议事实支持、无其他缺口 | COMPLETE / RESOLVED | 按审核版本解释条件与原文 |
| C04已读两侧，但提议漏选公共侧 | PARTIAL / FAILED | 已确认专业侧，公共侧本轮未完成；ANSWER_COVERAGE_INCOMPLETE / PROCESSING_FAILURE，不能称资料缺失 |
| 需求识别不确定，有可独立支持部分 | PARTIAL / FAILED；没有支持部分为NONE | QUESTION_UNDERSTANDING_UNRESOLVED / PROCESSING_FAILURE；说明未确认的是问题理解或核验 |
| 核验器超时/坏JSON/预算不足 | NONE / FAILED或STOPPED | 原调用故障/预算原因；不得改称DATA_GAP，不自动重试或跳过核验 |
| 健康检索或已审核详情明确不能支持部分 | PARTIAL或NONE，按原业务状态规则 | 使用原DATA_GAP原因，但须有实际检索/目录缺口依据，不从模型“没找到绑定”直接推断整套资料不存在 |
| 本科历史资料不能覆盖明确研究生/其他年份结论 | NONE或合法的一般说明 | 保留范围限制；不得推定个人资格。越界提议不发布 |
| 用户缺少必要且可补充条件 | WAITING_USER / INCOMPLETE | 审核conditionId存在、确实缺失、核验为当前需求所必要；不得重复已知条件 |

建议新增QUESTION_UNDERSTANDING_UNRESOLVED与ANSWER_COVERAGE_INCOMPLETE两个学生原因码，均PROCESSING_FAILURE；核验服务本身异常复用现有MODEL_*或TASK_EXECUTION_UNAVAILABLE。前后端原因映射必须同时更新。保持schemaVersion=3及原快照结构；发布器添加缺口后，现有Subquestion/前端的“claims＋issues→PARTIAL”结构检查仍可工作。是否有未知外部消费者依赖封闭原因枚举需实施前核对，不能只改Java枚举。

工程另记录PURPOSE=PUBLICATION_REVIEW、审核能力版本、需求/绑定ID、阶段与受控原因（REVIEW_SCHEMA_INVALID、REVIEW_BINDING_INVALID、FACT_NOT_RELEVANT、DEMAND_UNRESOLVED、COVERAGE_INCOMPLETE）。不记录完整自由模型正文、密钥或任意异常文本。

## 7. ASK衔接

ASK发布也走同一独立核验；核验器独立列出当前需求必要且尚未提供的conditionIds。宿主再验证提议conditionId属于审核模板、用户未提供、支持事实和当前需求有关、原when/条件约束确实需要它。可独立确认的已知部分按相同绑定/覆盖规则保留。

不扩展个人资格、任意条件推断或通用条件解释能力；沿用现有USER_DECLARED条件边界。课程类别已经明确时不得重复追问；泛泛的比较问题不因此要求学生提供个人课程类别。无法核验追问必要性属于处理未完成，不伪造为已完成澄清。

本次仅保留既有审核条件的追问。一般意图歧义若没有对应的受控追问配置，保留理解未完成；不能虚构policy conditionId或称已支持任意自由追问。

## 8. 预算、usage和退出

一次正常结束：SEARCH决策1、READ决策2、FINISH决策3、独立核验4；正常澄清为SEARCH、READ、ASK、核验。决策与核验共享同一run与批次AtomicInteger额度、同一个45秒deadline，单次仍最多10秒/1024输出token，不是“4＋1”。

预留1次语义核验，由一个宿主PublicationPolicy权威来源声明并投影给模型；决策阶段最多3次，最后一次只允许可核验的FINISH/ASK。第三次仍要继续SEARCH/READ、核验失败或额度不足时停止，无自动追加修复调用，不在新run偷偷重置批次额度。6个动作上限不改变；核验是宿主阶段，不冒充第五种模型工具。

Runner的execution.modelAttemptCount、TaskStore每task12次边界及Provider真实BEGIN统计必须包含核验尝试。宿主先领取剩余额度再发送，不在Publisher内部偷偷另发HTTP。两个用途共用已有HTTP客户端、取消、usage-before-validation及Trace三阶段；Trace添加用途，attempt仍全run递增。每完整响应已知usage先保存，缺失为null；核验解析失败同样保留已知用量。

剩余单次时间取min(10秒,run剩余时间)，取消或截止后不能发布。两用途都结束后才确认资源关闭；不构造第二个不受原Factory管理的模型工厂。耗尽预算不能作为政策缺口，也不能用旧缓存兜底。

此预算能否在真实调用中完成P01/P02是待验证假设。独立核验占用一步决策空间，对需多次检索的问题有明确限制；不通过则记录未达标，不擅自提高预算。

## 9. 拟实施文件与接口边界

以下新增项均是设计，当前不存在；实施前须先建立相应失败测试。

| 文件/接口 | 职责 |
| --- | --- |
| 新RulePublicationContract.java | 上述内部record/enum与权威容量/预留核验策略；不存题目答案 |
| 新RuleFactCapabilityCatalogue.java及独立版本化能力数据 | 绑定审核factId与能力；校验目录/审核来源，不改旧审核JSON |
| 新RuleSemanticReviewPort.java | `RulePublicationContract.SemanticAssessment assess(RulePublicationContract.SemanticInput input, Duration timeout)`；无工具执行/发布能力；本地用模拟实现 |
| 新RulePublicationGate.java | `RulePublicationContract.PublicationAssessment assess(RuleAgentRunner.Input input, RuleAgentContract.NextAction proposal, RulePublicationContract.SemanticAssessment review, List<RuleAgentEvidenceTools.VerifiedFact> facts)`；确定性对应及覆盖聚合，不发HTTP |
| 修改RuleAgentRunner.java | FINISH/ASK前预检查、共用预算的语义核验、停止/取消与执行统计；沿用原循环 |
| 修改RuleAgentProvider.java/RuleAgentOnlineFactory.java/RuleAgentProtocol.java | 共用受控HTTP传输与usage审计，不同用途的严格响应合同；投影预留预算；不改变四种动作字段 |
| 修改RuleResultPublisher.java | 消费宿主PublicationAssessment；替换新Agent主题否决和完整性决定，保留fresh证据及适用性检查 |
| 修改RuleAgentContract.java及agent/frontend/src/api/rule-agent-contract.js | 同步新增两个处理原因；保持快照结构和既有覆盖关系约束 |
| 修改RuleAgentLoopDatabaseTest.java及相关已有单元测试 | 将诊断案例改为目标行为回归；新增核验错误、预算、ASK、对象/时间反例 |

不能只改Publisher一行containsAll；不能复用旧请求包宣称新载荷已批准；不提前进入T06/T07。新Runner必须显式提供核验端口，本地测试显式注入模拟端口；ONLINE缺核验配置时拒绝运行，不设默认放行实现。新增类型的具体JSON与方法签名须在步骤3开工前与本文逐项一致，不能把本设计字段当现有可调用API。

## 10. 验收及停止标准

本设计检查：三个缺口均有对应检查；C02/C06行为保留；两种模型用途计入原预算；部分回答与处理失败不矛盾；前后端原因对齐；不存在白名单题目或参考答案输入。通过只意味着设计可进入人工核对，不证明实现有效。

步骤3先在本地模拟独立核验，真实工具/资料/发布器不替换：C01/C03两项发布、C02保持、C04PARTIAL且明确漏项、C05联合回答、C06拒绝。还须核对错绑定/虚构需求/未知factId、核验把无关重修映射为修读安排、核验漏掉混合宿舍请求、研究生/其他年份/当期/个人资格、缺条件ASK/条件已知不ASK、取消/第4次核验统计/额度耗尽/usage记录。刻意错误的核验输出不能通过确定性能力检查。

本地测试可以证明宿主接受正确核验、拦截结构与声明能力冲突；不能证明真实核验器总会理解正确。真实探针前，保留原问题的必要回答、禁止结论及需求覆盖参考；人工核对提议和核验两种输出，不能用核验器给自身评分。未预置表达与理解质量仍归T08，不能由P01固定探针替代。

步骤4需新的具体载荷/模型用途/次数授权。最小真实成功要求模型实际SEARCH→READ→FINISH→核验→合法发布两项事实及对应原文，调用≤4、资源关闭；ASK续答按独立run记录，不冒充旧协议成功。失败停止，不增加预算、不补固定答案。

本轮停在本文设计与记录，不实施步骤3。下一时段20分钟先完成内部合同及失败测试，未通过局部门时记录准确卡点；不承诺所有文件修改与真实探针在一个时段完成。
