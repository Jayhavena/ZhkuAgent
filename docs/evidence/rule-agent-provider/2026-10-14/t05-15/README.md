# T05局部检查点：FINISH独立核验接线

执行范围：2026-10-14 00:02:09起，主动执行上限20分钟。本轮仅本地FINISH接线；不实现ASK、不接在线工厂、不调用供应商。决策和语义核验均为模拟，资料、动态候选、SQL检索、原文读取与权限审计使用真实已审核2025文本和专用临时库。

| 问题 | 改动 | 实际验证 |
| --- | --- | --- |
| 关键词未识别被当成资料不适用 | 新显式核验路径先校验权限、事实、条件与原文，再独立识别需求并经Gate发布；旧工程路径保留 | C01原P01、C03不同表达均发布2条修读事实；旧关键词诊断仍保留在记录中 |
| 单规则必须覆盖全部问题，不能组合事实 | Publisher消费子问题层面的覆盖结果，仍检查每条事实的相关绑定 | C05第5/10条共同回答学时及学分，3条事实完整发布 |
| 漏答可能冒充完整回答或资料缺失 | 宿主生成ANSWER_COVERAGE_INCOMPLETE；理解未完成使用QUESTION_UNDERSTANDING_UNRESOLVED；两者均PROCESSING_FAILURE | C04保留1条已支持主张，FAILED/PARTIAL；前端接受新原因码 |
| 真实但无关的事实可能混入结论 | 每个提议事实必须通过独立需求绑定与事实能力检查 | C06重修事实用于解释课程类别被FACT_NOT_RELEVANT拒绝，没有结论及学生证据 |
| 核验增加调用或迟到发布 | 新路径最多3次决策+1次核验，共用4次/45秒；单次预算仍10秒；双端口退出 | 新增预算/取消测试通过，取消后不发布，review端口关闭 |

最终验证：后端73项（Gate13、Contract7、Runner21、Provider32），前端84项，数据库2项流程测试含六案例。数据库迁移22、测试后连接0、容器移除；sourceDrift=0。六个Java实际快照均经现有前端validateRuleAgentSnapshot校验，结果见verification.json。

数据库首次启动失败是Docker Desktop未运行（db-green-02），不属于业务测试失败。隐藏启动后green-04通过；未结束Codex或浏览器。red-01保存新增构造器尚未实现时的编译失败，历史记录未改写。unit-green-03的71项仅为中间检查，最终身份以unit-green-05为准。

关键位置：RuleAgentRunner的新4参数构造器及FINISH分支；RuleResultPublisher的precheckFinish/finish/checkScope；RuleSemanticReviewPort.close；RuleAgentContract原因码与前端对应映射；RuleAgentLoopDatabaseTest六案例；RulePublicationGateTest预算与取消测试。

证据目录的原始快照含本机访问令牌，不入Git。actual-cases-sanitized.json为副本移除originalToken后的记录；verification.json保存原始文件SHA256。其余审核原文只在既有受控本机用途内保存，不构成公开许可。

未验证范围：真实核验模型的理解正确率、供应商共享quota/usage适配、超时强制终止、HTTP及学生UI、ASK澄清续答的新核验路径、全目录事实能力、用户/PDF收益。现有在线工厂仍调用旧3参数Runner，新链路不能据此宣称在线通过。模拟核验根据测试参考维度输出，不是自然语言核验算法。

下一项应单独实施ASK核验及在线适配/工厂边界，先本地整链验收，再准备包含新核验载荷与预算的具体请求包；任何真实探针都需新明确授权，旧额度不复用。若失败按阶段保留原因，不能猜测旧失败原因或改写旧评分。

恢复：本轮保存独立本地提交，必要时以其父提交恢复本轮明确文件；不回退无关工作区改动，不操作原演示库。临时库无持久卷，模型/数据库凭证不随Git保存。本检查点仅证明局部FINISH接线，T05未完成。
