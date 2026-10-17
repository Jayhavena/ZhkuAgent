# T05-67：核验条件键提示澄清（本地验收）

范围：仅RuleAgentProvider.REVIEW_SYSTEM和RulePublicationProtocol.description的提示说明。必要条件改为when原键；新增conditionIdForm，系统提示与合同共用同一常量，明确逐字复制input.conditions[].conditionId、合法/非法示例，以及分支通过id/subject/事实绑定区分。没有合并条件记录、放宽守卫或改模型、预算、权限及指纹。

TDD：新增5项测试先出现1项预期失败（缺少conditionIdForm），0错误；两文件实现后11个明确本地领域类163项通过，0失败/错误/跳过。随后测试增加实际payload落盘并仅复验新增5项通过，未在该测试记录变动后重跑全部163项；生产实现未变。独立只读审查无Critical/Important/Minor发现，审查者未运行测试。

载荷：test-evidence/t05-65-review-prompt-request.json是实际reviewPayload构造得到的本地字符串，未发送给供应商。核验输入与t05-65真实输入完全相等，两条courseType记录、inputFingerprint及record字段形状保持。请求由6924增至7706字节，当前上限65536，输出上限仍1024token。该样本余量不能代表所有可能输入。

行为：原始带前缀条件键和未知键继续被拒绝；仅改两个键的t05-65响应仍通过现有Gate，判定UNRESOLVED。既有受控Runner/发布测试覆盖原键澄清及合规续答；这是本机合同验证，不证明真实模型会遵守。

已知范围限制：RuleAgentRequestPathTest和RuleAgentOnlineFactoryTest仍有断言指向62，而先前提交已将实际Host/launcher绑定65。本轮只读识别该既有失配，未运行这两类，也未修改；163项统计不含它们，不能写成全套回归通过。后续入口验收需单独处理。

零真实模型、数据库、Docker操作。应用jar未重建，默认jar仍为旧t05-65构建（79a22f1c…59ed70）；生产源码现在已有提示变更，禁止把旧包/旧jar冒充新候选验收。原始65归档、66对照及主仓库既有改动保持。

取舍：共用提示常量增加请求字节但避免口径漂移；不改记录和守卫，避免扩大语义范围；不顺带修历史路径测试，故本轮仅声明领域局部通过。完整Ruling和代价见progress.md、review.json。

下一步应先确定全新请求路径并处理对应入口测试，再一次性构建与核对新资产身份；之后才准备新包/披露并申请新的在线授权，不覆盖65旧包、不复用关闭额度。本轮不进入这些后续步骤。恢复本次修改须独立逆向提交，保留历史证据。

工程成果已冻结，真实在线验收未通过。
