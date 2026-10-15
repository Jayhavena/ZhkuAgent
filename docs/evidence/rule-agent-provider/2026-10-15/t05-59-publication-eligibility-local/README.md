# T05-59：发布事实资格与核验条件原键的本地修正

本地实现及明确范围验收完成，尚未构建新jar或准备新外发申请包。202项本地测试通过不等于在线P02或产品质量通过。工程成果已冻结，真实在线验收未通过。当前真实模型/数据库/Docker操作0，旧外发额度仍0。

## 已实现边界

- RuleFactCapabilityCatalogue以现有能力登记及原始Fact定义指纹派生publicationFactIds，并提供统一requireCapability/requireProposal；Gate复用相同能力检查。能力目录未扩登记。
- Runner将资格列表与完整已读事实分别提供决策；保留全部候选、6事实、原文和PUBLIC/PROFESSIONAL分支。Provider在事实上明确publicationEligible，Schema的ASK支持ID/FINISH事实ID枚举取同一列表。空资格列表只允许空事实数组，不生成默认factId或c1。
- ASK/FINISH继续执行原有前置权限/适用性/when/fresh原文检查，随后在独立核验计次前校验事实能力。非法提议终止并留下实际action及publicationReason=CAPABILITY_UNAVAILABLE/FACT_NOT_READ；不会制造PUBLICATION_REVIEW事件、增加review次数或自动重试。合法提议仍走独立核验及发布fresh校验。
- 独立核验解码从当前SemanticInput.conditions派生合法原键，带前缀/未知条件明确拒绝REVIEW_BINDING/REVIEW_CONDITION_UNKNOWN；输入指纹错误仍优先保留REVIEW_INPUT_MISMATCH。完整分支、跨度、事实关联、能力和when守卫不变。不做别名替换、去前缀、答案预置或强制FINISH。
- 动作合同显式升级RULE_AGENT_ACTION_CONTRACT_06；核验record形状未变，新增检查落实原合同02已有的“原键”约束。旧无独立核验工程路径保留null资格元数据兼容；它不作为新在线验收路径。

## 实际验证

先写5项真实批次夹具测试，红例4失败/0错误：缺少资格投影、非法ASK仍调用一次核验、前缀/未知条件未明确拒绝；合法双分支正例通过。实施后5/5通过。

独立审查要求补FINISH、指纹漂移、身份错误优先级三项覆盖，现8项新增用例全部通过。其中真实RuleAgentEvidenceTools执行SEARCH/READ，内存端口提供原批quote/source/checksum，返回完整6事实；非法COMBINED ASK和已知PUBLIC后的FINISH均0 review，合法RETAKE_ONLY双分支经过受控独立核验与fresh原文读取后进入WAITING_USER、无答案主张。这是本地流程，不是真实模型成功。

首轮明确11类回归199项中4失败/7错误，原日志保留。7错误及1失败来自遗漏-Dzhku.rulecheck.evidenceDir；另1旧协议05断言随显式06更新；两个旧review取消/失败测试使用未登记r:category，现被正确前置阻断，已换成合格的真实READ夹具，使测试仍验证进入核验后的取消/失败、无发布与关闭，不降低原断言。

最终11个明确本地类共202项，0失败/0错误/0跳过，Maven退出0、未超时、资源退出确认。没有运行含数据库/真实供应商的全项目集成套件；原194项是同一明确范围，本轮增加8项。测试供应商仅本机回环HTTP替身，凭证变量在测试子进程删除，Maven离线运行。测试生成的p01-request-package.json只是历史单测夹具，位于regression-evidence，本轮不将其作为冻结候选包或授权申请，不纳入提交。

## 身份、证据与限制

基线a0615866；6个生产文件、4个测试文件变更。source-binding.json记录所有选定文件SHA、其余329项生产/启动文件及额外14构建输入未变，11份测试报告SHA与计数。旧55/57/58原始包、披露、轨迹、回放和3个旧冻结jar未变，主仓库58e1ef7及4份既有文档改动字节未变。

仅执行Maven test，target/classes为新实现，actual/frozen controlled.jar仍是旧82c25684…1541，未新建jar。不得混用新源码身份与旧jar，也不能以旧55包启动新实现；后续须独立构建/资产身份核对及重新披露新字段与合同。当前授权0，没有PrepareProbe/真实Probe、Docker或数据库操作。

独立只读审查最初3项Important验收缺口已补齐，复核未发现剩余Critical/Important问题；审查未代替测试或线上证据。编译日志包含既有deprecation/unchecked及file.encoding设置警告，测试日志出现部分Windows父进程编码显示问题；未改用户终端或源码编码规则，不宣称全量终端中文验收。本轮未改传输超时、预算、权限、原文、现行适用范围或能力审核。

[验收](verification.json)、[源与测试身份](source-binding.json)、[独立审查](review.json)、red/green/regression/regression-final日志及受控执行回执供复核。首次读取升级审批曾超时，之后普通只读访问及授权的范围内修改均完成，不把审批超时推断为动作不安全。

恢复以本地提交撤销这6个生产和4个测试文件及必要文档为单位；原失败证据、冻结jar和关闭额度保留，不恢复历史额度。下一步是独立本地构建与资产身份核对；未来真实模型是否遵守新资格枚举和原键、P02能否完整续答仍未知，本轮不自动进入新外发。
