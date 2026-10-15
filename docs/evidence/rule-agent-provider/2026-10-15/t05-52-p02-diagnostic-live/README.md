# T05-52：P02诊断批次失败归档

本次完整P02未通过。首run已检索、读取并调用独立核验；核验没有建立追问必要性，发布门禁拒绝ASK，未进入用户续答及答案发布。工程成果已冻结，真实在线验收未通过。

## 批准、身份与实际执行

最新用户“批准”对应冻结包`8250d8263bcfe383f14750a2b2257c20aa1255d5956bb75ba320c243931a8788`、完整外发披露、北京端点及qwen3.8-flash，一次P02、最多2 run/10调用/12元。实际只启动一次探针、一个run、5次真实调用，剩余5次关闭、剩余额度0，旧授权未复用。源代码、模型、提示词、样例、门禁、预算及冻结请求包均未修改。

启动身份：Git `547f73dc8bb0ec99d135c647557f299d111b8a2b`，实际jar `85b95a1ccf1c4c810b7c86a9e92c9099d406b8be2295c2b4903c63cc816a7e87`。源码335项、运行资产15项、应用class658项及共享运行库在执行前核对。主仓库未合入。

## 本次可证实的失败路径

1. 调用1 SEARCH返回全部10候选；调用2模型在当前集合自主选择c1，宿主映射真实UUID并READ，取得第十八条原文及6事实。没有额外READ或证据容量拒绝。
2. 最早拒绝在调用3：ASK的conditionId为`S10/RETAKE_ONLY:courseType`，已提供的合法条件键是`courseType`。`RuleResultPublisher.java:38-41`在核验前以INVALID_MODEL_RESPONSE拒绝。事实和ruleContexts已提供正确键；不能归因于模型不知道已读事实。
3. 调用4改用courseType和两个已读重修事实，通过本地前置检查，发出调用5独立核验。第4次请求observations只有前两次成功工具结果，未含第3次拒绝原因，故编号改正不证明错误反馈有效。
4. 调用5收到HTTP200，协议、JSON和输入指纹关联均通过。响应把原问句标为RETAKE/UNSPECIFIED/AMBIGUOUS，`necessaryConditionIds=[]`、`bindings=[]`，另有AMBIGUOUS_SCOPE。并非输入关联异常或网络失败。
5. `RulePublicationGate.java:95-98`找不到同问题、CORE角色、包含courseType必要条件的支持事实绑定，在第一个事实的matching.isEmpty()分支以CONDITION_NOT_NECESSARY拒绝。123行的未解决需求守卫也不满足，但未执行至此。门禁没有依据仅凭模型自报或已有文本强行发布追问。

本次已定位到“合法追问提议与独立核验表示不一致”的实际机制；为何核验模型选择纯歧义、怎样稳定表达可澄清歧义，仍需独立零外发任务验证。不能直接归责于门禁错误，也不能把新响应倒推成旧t05-48的REVIEW_BINDING_INVALID原因。 核验system已有“必要条件只列当前需求确实依赖的审核事实when条件”的泛化说明；输入没有决策侧ruleContexts条件目录、courseType问题标签和选项，合同也未明确可澄清歧义应如何用IDENTIFIED及CORE关系表达。这是已见的信息缺口，因果作用仍属候选。

## 调用、费用和退出

5次均HTTP200且有usage，共8436输入/568输出token。按北京公开0.8/2.7元每百万token、不计优惠估算0.0082824元；实际账单未核，费用不是零。Provider耗时合计12.519秒，启动至退出26.4809692秒；本次不是20秒单次或110秒run预算耗尽。connectedMillis均null不作HTTP连接阶段推断。

失败快照为FAILED/FAILED、coverage NONE、answerSource NONE、continuationToken null。只有一个run；没有合格WAITING_USER、续答或答案发布。host报告resourcesClosed，独立复核探针容器/Java进程均无残留、原演示容器完整State未变。临时容器删除前行数未独立测量，保留未知。

## 复核入口和边界

- [逐次账本](attempt-ledger.json)：请求/响应SHA、requestId、usage、原始wire动作、宿主解码动作、Runner拒绝事件。
- [诊断分层](diagnosis.json)、[结果](result-summary.json)、[关闭授权](authorization.json)、[独立资源检查](independent-resource-check.json)、[完整性验收](verification.json)。
- 原始受控记录位于`../t05-51-p02-diagnostic-request/live-8760779ff323488e8b5100c4795b56ce/`：14份交换记录、outcome、15行provider trace、probe-result及host原始日志；原始内容不改写。PROPOSED_ACTION是宿主解码后的动作，READ中的UUID不是模型wire选择；须与RESPONSE中的c编号及REQUEST中的集合对照。
- 冻结51目录的approval-state是准备时NOT_AUTHORIZED历史快照，实际新授权与消费以本目录authorization为准，不覆写原申请包。

本地确定性测试只证明受控输入下合法动作可执行、非法动作不执行；本次真实工程探针失败；冻结后的可靠性、未知问题质量和学生收益尚未评估。保留既有P01单次完整成功，不据此宣布T05通过。

本批结束，不修源码、不重跑、不补额度。若另行安排下一项，只先零外发复现和审定澄清合同，保留原文、权限、适用范围及独立发布核验；不预置答案或放宽门禁。恢复本次归档只需撤销本证据提交，冻结源码/包/jar未动；不得为恢复而改写既有历史或重用已关闭额度。以上为失败证据检查点，不是修复完成提交。
