# M3 业务边界修复检查点（进行中）

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
