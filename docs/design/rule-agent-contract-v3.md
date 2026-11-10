# 校园规则查证Agent：v3共享合同

日期：2026-10-13。对应主计划T01/D1。业务范围沿用既定2025版受控本科查证，不新增资料或工具权限。

本合同是供T02–T07接线的独立类型与消费校验，**当前学生页面、旧回放接口均未接入**。类型合法不表示主张得到原文支持，也不表示模型已在线执行。事实资格、来源新鲜性、条件必要性、问题覆盖语义和实际操作仍需独立领域守卫。

实现：[Java合同](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentContract.java)、[严格JSON入口](D:/alldesk/Projects/ZhkuAgent/knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentJson.java)、[前端快照校验](D:/alldesk/Projects/ZhkuAgent/agent/frontend/src/api/rule-agent-contract.js)。两端使用[同一组合成样例](D:/alldesk/Projects/ZhkuAgent/knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json)，不能将样例当政策或正式回答模板。

## 1. 任务、运行、结果三层分开

TaskSnapshot.schemaVersion=3，身份包括taskId/turnId/runId/version。TaskStatus为ACTIVE、WAITING_USER、RESOLVED、STOPPED、FAILED；RunState直接复用agent-runtime，不新增校园业务枚举到共享模块。terminated只说明本轮资源结束，不能替代TaskStatus或结果覆盖。

| TaskStatus | 允许的本轮RunState与terminated | 结果/继续约束 |
| --- | --- | --- |
| ACTIVE | 未terminated；可以创建/运行/停止请求，也允许运行已有终态但尚未完成清理的窗口 | 不能发布本版本结果；可保留旧版本已核验结果；无继续token |
| WAITING_USER | INCOMPLETE且terminated=true | 一项必要澄清及CLARIFICATION issue，继续token必需 |
| RESOLVED | SUCCEEDED或INCOMPLETE且terminated=true | 没有待回答澄清；coverage可COMPLETE/PARTIAL/NONE |
| STOPPED | CANCELLED或INCOMPLETE且terminated=true | 明确CANCELLED/BUDGET_EXCEEDED/NO_PROGRESS；可保留独立部分内容 |
| FAILED | FAILED且terminated=true | 明确PROCESSING_FAILURE；可保留独立部分内容 |

ACTIVE可转WAITING_USER/RESOLVED/STOPPED/FAILED；WAITING_USER可在有效补充后转ACTIVE，或停止/失败。终态不能恢复ACTIVE；相同状态的快照或幂等读取不构成新执行。实际版本更新、owner校验和CAS在T04实现，canTransition只提供状态边界。

等待时结束本轮线程；补充后相同task、新turn/run。不能让原线程长期等待学生。取消请求先记录STOP_REQUESTED，不能提前显示“已退出”；已经发布的版本不能被晚到运行覆盖。

ResultEnvelope带publishedVersion与originatingRunId。当前结束结果必须与快照版本、run一致；续答ACTIVE可显示较早结果，但不能把它算作本轮新答案。资料变更后能否保留仍由fresh核验决定，合同校验不赋予历史事实永久资格。

## 2. 结果以子问题组合，不以单一拒答状态概括

ResultEnvelope包括catalogueVersion、referenceRevision、coverage、items、evidence、evidenceRelations。

每个子问题包括id、claims、issues、clarification及coverage：有主张且没有缺口为COMPLETE；有主张及缺口/追问为PARTIAL；没有可发布主张为NONE。总体COMPLETE要求每项COMPLETE；总体NONE表示没有任何主张，其余PARTIAL。这是内容覆盖结构，**不是人工判定的任务成功分数**。

因此，完整解释“现有资料不能确定当期日期”的缺证任务可以符合参考要求，但coverage仍为NONE。另一方面，列出一段正确背景却不回答可回答的主问题，不能因coverage为PARTIAL就判通过。

Claim仅由宿主发布：id、审核factId、解释text、when、conditionSource=USER_DECLARED。声明条件不能变成已验证修读记录/资格。模型FINISH中只有factIds及允许操作，不能提交自由政策正文；text来自审核事实及受限渲染，待T02/T03实现。

| issue.kind | reasonCode示例 | 判定/学生表达 |
| --- | --- | --- |
| DATA_GAP | RETRIEVAL_NO_MATCH、DETAIL_NOT_IN_REVIEWED_TEXT、CURRENT_NOTICE_MISSING、PERSONAL_RECORD_NOT_AVAILABLE、REVIEWED_FACT_COVERAGE_GAP、UNSUPPORTED_DEMAND | 当前取得/允许使用的依据不足。“能确认……；不能确定……；可核对……”；零召回不等于整本资料没有规定。UNSUPPORTED_DEMAND专指需求超出已审核资料与办理能力的边界（如当期截止时间、代办操作），属于资料缺口而非技术失败，此时任务为RESOLVED、runState为INCOMPLETE |
| CLARIFICATION | MISSING_CONDITION | 必要且学生可补充的条件尚不明确；展示已知部分，再问该条件 |
| PROCESSING_FAILURE | RETRIEVAL_UNAVAILABLE、MODEL_TIMEOUT、INVALID_MODEL_RESPONSE、AUDIT_UNAVAILABLE等 | 执行环节未完成；不能替换为“资料没有规定”；保留独立核验内容 |
| SCOPE_LIMIT | MATERIAL_SCOPE_MISMATCH、CURRENT_APPLICABILITY_UNKNOWN | 对象/时间/用途未被当前范围支持；解释可支持的部分，限制适用结论 |
| CONFLICT | UNRESOLVED_CONFLICT | 已有至少双方可核验依据，阻断对应项；其他独立子问题不受连带影响 |
| REPLAY_MISS | REPLAY_NOT_COVERED | 工程回放未覆盖，0在线调用；不判断资料或在线能力；学生ONLINE中不能出现 |

每个issue有安全message和nextStep。reasonCode与kind的对应关系封闭校验，不接受未知“所有资料都没有规则”等原因码。原因不能由前端根据空答案猜测。

澄清每轮至多一项，包含conditionId、问题和允许值；是当前任务的必要条件，不是收集学号/成绩的通用表单。候选条件来自已审核定义，必要性及重复追问检查在T03/T04；合同接受conditionId本身不能证明追问合理。

## 3. 结论与依据关系

Evidence包含id、sourceId、chunkId、revision、checksum、UTF16起止跨度、quote及originalToken。不能把未核验候选作为Evidence发布。revision必须与ResultEnvelope一致，跨度长度与quote一致、UTF16必须完整，原文内容/hash/权限仍由T02及原文服务重读。

EvidenceRelation是唯一关联表，包含evidenceId/subquestionId/role/targetId：role为CLAIM、CLARIFICATION、CONFLICT或SCOPE。CLAIM关联主张，CLARIFICATION关联必要条件，CONFLICT关联冲突issue，SCOPE关联该项原因解释。每个主张至少有一条关系，冲突至少两条不同证据关系；未关联证据、未知目标、重复关系拒绝。

这只能证明“关系和字段完整”。是否同一原文真的支持主张、两方是否真有冲突、issue依据是否相关，仍由领域核验及人工语义评估证明。合同通过不等于事实受控制已经全部完成。

学生显示顺序保持：能确认什么 → 缺什么及原因 → 下一步 → 相应原文。原文默认折叠；只展示关系表所指的相关段落，不展示完整retrievalTrace。T01未实现展示。

## 4. 下一动作与工具观察

NextAction使用封闭type，JSON不启用默认多态，不允许未知字段。

| type | 合同输入 | 留给后续领域守卫的检查 |
| --- | --- | --- |
| SEARCH_REVIEWED_RULES | subquestionId、query | 服务端注入范围/资格，模型不能挑目录、模式或任意资源 |
| READ_VERIFIED_RULE | subquestionId、1–3个唯一UUID句柄 | 句柄确由本task签发、owner/版本/预算有效；禁止路径/URL/SQL |
| ASK_NECESSARY_CONDITION | subquestionId、conditionId、reason、supportingFactIds | 必要、尚未明确、学生能补充；宿主使用审核条件生成实际问题 |
| FINISH_CHECK | 1–4项Proposal：subquestionId、factIds、ProposedIssue、可选Operation | fact/条件/操作数及原文真实支持、问题完整覆盖、解释范围合法 |

Operation是有限GT/GE/EQ/LT/LE比较，使用factOperandId、declaredValue和USER_DECLARED原句跨度；没有计算器或教务办理工具。跨度与原任务文本一致性、操作数是否属于被核验事实，必须在T03再次验证，不能信任模型声明。

ToolObservation带actionId/runId/step、status/reasonCode、dataLevel、catalogueVersion，以及类型化candidates/evidence/factIds：

- CANDIDATE是可继续读取的候选；不是已核验事实。
- VERIFIED_EVIDENCE是合格原文；可以尚无可执行审核事实。
- VERIFIED_FACT是已核验原文和已审核事实ID，实际绑定由T02提供。
- NO_MATCH只配RETRIEVAL_NO_MATCH；UNAVAILABLE只配检索/审计不可用；REJECTED配非法动作、范围/版本或预算拒绝。非成功观察不带伪造数据。

观察是模型下一次决策的输入，但并不自动发布到学生。T01没有执行检索、读取、模型或动作循环。

## 5. 身份、版本、幂等与接口语义

Owner(actorId,sessionNonce)仅为服务端内部归属，不在TaskSnapshot或模型合同中。RequestKey由owner+CREATE/MESSAGE/CANCEL+clientRequestId构成，不能只用问句或客户端传来的userId判断归属。

请求类型固定：CreateTask(question,clientRequestId)；Message(message,continuationToken,expectedVersion,clientRequestId)；Cancel(runId,expectedVersion,clientRequestId)。额外的mode、tool、rule、权限字段拒绝。

同键同载荷返回同run；同键异载荷409；先占位再调度；未知POST结果先查询键，不换新键重发。expectedVersion匹配后才创建新run/取消。token是继续凭据而非身份，必须同时绑定主体、Session、task及有效版本。T01只定义合同，T04实现实际索引和竞争控制。

正式路径沿原方案：/api/zhku/controlled-preview/v2/rule-check/tasks。POST快速202；GET取快照；messages续答、cancel请求停止、原文再次核验。v2身份/CSRF复用，旧POST rule-check保留工程配置；**本轮没有创建这些HTTP接口**。

401/403为身份；409为版本/上下文冲突；429为准入；GET503仅表示查询服务故障，不删除已受理任务或重发创建。运行异步失败在可取得的快照中明确FAILED及reason，不能与HTTP503混为一谈。

## 6. 在线、回放与执行身份

Execution包括mode、modelAttemptCount、toolStepCount、providerResponseReceived、answerSource、replayCoverage。

ONLINE只能NOT_APPLICABLE回放状态，不能标SEALED_REPLAY。ENGINEERING_REPLAY必须模型尝试0、providerResponseReceived=false，不能标LIVE_AGENT；MISS必须有REPLAY_NOT_COVERED。VERIFIED_RULE_ONLY独立标明规则路径，不能算当次模型成功。

计数和布尔字段只是声明；T05/T08必须提供当次供应商/动作关联才能证明真实。历史raw响应不算当前在线调用。模式由服务端配置选择，客户端请求没有切换字段；配置隔离和在线缺配置拒绝启动在T05实施。

## 7. 首期防御预算与约束身份

本次采用原方案建议作为v3首期默认上限，T05实测后若调整须记录新版本，而不是静默提高：

| 约束 | 初始值 |
| --- | --- |
| 每run模型尝试/领域动作 | 4 / 6 |
| 单模型超时/run总执行 | 5秒 / 25秒 |
| 每task run/模型累计/必要澄清 | 3 / 12 / 2轮 |
| 每次READ句柄 | 3 |
| task有效原文 | 8片段 / 32KiB UTF-8 |
| 单次模型载荷 / 输出 | 64KiB / 1024token |
| WAITING_USER / task TTL | 5分钟 / 15分钟 |

默认不重试供应商、失败尝试不返还额度；等待用户不计run执行时间但受TTL限制。授权批次总额度优先；上限不授权外发。线程池、队列和TaskStore精确容量在T03/T04装配时确定，必须有界，T01不创建这些资源。上述值不是延迟/质量SLA，也未在真实供应商上验证。

2026-10-13 T05预算修正候选：`T05_READ_BUDGET_02`将单模型/整run改为10秒/45秒，其他默认预算不变。上表保留初始身份；此次因P01两次真实决策触及5秒上限而进行一次有界验证，不声明已解决供应商超时或达到产品延迟目标。新宿主仅接受独立P01、最多4次的新请求包，包中完整预算、提示及审核资料须与当前实现一致；旧5秒/双题请求包和旧授权不能沿用。响应头/正文诊断见T05-04，本地回归和新包见T05-05与T05-probe-02。真实验证尚待新授权。

## 8. 验收口径和评分定义

本次退出是共享合法样例均通过、非法字段/状态/关系/原因拒绝、新测试可执行且fresh、旧相关回归保持。未预置能力、真实工具、HTTP/UI和学生收益不由T01代替。

后续每个任务参考须在运行前记录：问题及补充、目标范围、必要回答、禁止结论、必要条件、相关原文、允许缺口及结束要求。分别评分：

1. 任务完成：可答项完整、有依据错误拒答、漏项及答非所问单列；有用澄清只算本轮正确行为，用户未补充不提前算跨轮完成。
2. 事实支持：每个主张有实际原文支持；只验证ID/hash不够。
3. 范围/条件：对象、时间、例外、用户声明与个人资格不混同。
4. 部分/缺证：先答可确认项，缺口明确，背景不能冒充完成所问任务。
5. 执行：必要动作、真实工具反馈、实际模式、幂等、版本、取消/资源、调用次数和失败成本。
6. 用户价值：参与者的判断与原文核对质量、总查证耗时；采用已确认的“不下降且降低耗时”门。

关键错误包括：无依据确定规则/个人资格、伪造引用/执行成功、资料/任务越权、把回放当在线、重复非获准外发。关键错误0是首期退出门。一般完成失败、拒答、未知及不可判定各自报告，不能只以关键错误0宣称质量达标。

评分优先人工参考/可确定代码检查；如加模型裁判，先校准，不替代实际事实审阅。开发集与新报告集分开；失败后曝光题归回归，旧结果不回改。报告题数量及学生对照具体配对方案留T08/T09定稿，不生成新的前置题库阶段。

## 9. 验证入口与回退

新单元类RuleAgentContractTest已登记，EvidenceRoot可选择仓库docs/evidence下的新目录；绝对路径、..、目录链接拒绝，默认旧路径及package04专用身份保留。

从仓库根执行（Phase须新且唯一）：

```powershell
knowledge\scripts\verify-rule-check-refactor.cmd -Mode Unit -Phase t01-contract-check-unique -Tests RuleAgentContractTest,RuleCheckEvidenceVerifierTest,RuleCheckDecisionServiceTest,RuleCheckControllerTest -EvidenceRoot docs/evidence/rule-agent-contract/2026-10-13
node --test agent/frontend/tests/rule-agent-contract.test.mjs agent/frontend/tests/rule-check-api.test.mjs agent/frontend/tests/rule-check-workflow.test.mjs agent/frontend/tests/rule-check-page.test.mjs
```

选类入口必须检查fresh XML与实际执行数量，不能只看退出0。Node检查不是浏览器E2E。严格Jackson解析还检查未知字段、重复键、尾随JSON、缺字段、数字枚举和强制类型转换；没有更换旧ObjectMapper或引入新框架/依赖。

回退范围只涉及新增未接线合同、消费校验/样例及本项验收登记/证据参数；不撤销用户之前的同文件修改，不重写旧审核、评分或回放批次。T00恢复包保持原样；T01差异和实际源码指纹独立记录。Git仍未提交，用户已指示在工作区候选上推进下一项，不据此补造提交SHA。
