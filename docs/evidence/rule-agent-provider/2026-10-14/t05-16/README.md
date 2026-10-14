# T05 ASK独立核验：实施待整链验收

本轮从2026-10-14 08:06:22开始，20分钟窗口截止08:26:22。执行数据库验证的工具调用被中断；恢复后时钟为09:41:42，已超过窗口，立即停止新增修改与追加测试。不把墙钟经过时间称为20分钟内完成；中断原因及期间是否发生等待未知，不编造归因。

基线：04192dbe67587acd0a816d7db5c7e532402c78f2。暂存区初始及结束均为空，仅六个明确源码/测试文件已修改，保留四份既有无关产品文档改动。未保存完成提交，没有push、PR、真实模型调用。

| 问题 | 已实施改动 | 当前证据/限制 |
| --- | --- | --- |
| 新核验路径拒绝ASK | Gate支持ASK，Runner将ASK与FINISH共用预留核验预算 | red-01新增测试以ACTION_UNSUPPORTED失败；unit-green-02共75项通过 |
| 自报追问理由可能替代必要性 | 独立requirements的necessaryConditionIds、实际条件事实when及审核模板共同检查 | 必要追问通过；无独立必要性、修读安排冒充追问依据、已知条件重复追问均拒绝；新受控码CONDITION_NOT_NECESSARY/CONDITION_ALREADY_PROVIDED |
| 缺失条件被提前要求成立 | precheckAsk仅允许所追问条件缺失，其他条件、原文、权限、冲突检查保留 | Gate单元验证通过；真实资料链路尚未验收 |
| ASK自动展示无关背景 | Gate只认可当前需求的CORE独立事实，Publisher按合法绑定保留已知部分 | 已添加真实资料三场景测试，但未获得执行结果，不宣称修复完成 |

本地测试：t05-ask-wire-red-01为75项中新增2项失败（其余历史检查保持）；t05-ask-wire-unit-green-02为75项全部通过，sourceDrift=0，resourcesVerified=true。此单位验收不能替代真实工具/发布器整链或真实模型能力。

数据库待验收：t05-ask-wire-db-green-03仅留下command.json、source-before.json、database-attempt.json。START_ATTEMPT时间08:11:20；没有results.json、XML、actual-ask-flow.json或外层退出记录。恢复时Docker Desktop Linux Engine管道不可用；attempted临时容器为zhku-rulecheck-3b36be42be01，55520专用临时库，无挂载卷。不能认定容器已建立、数据库已运行、测试通过或容器已删除。额外只读进程/资源检查见interruption-check.json。

前端frontend-green-04调用未收到启动或结束证据，视为未执行。本轮不能引用上一轮84项当成本轮新结果。

剩余最小动作：恢复Docker可用性后先核对/清理本轮唯一容器（不操作演示库）；使用新phase运行RuleAgentLoopDatabaseTest全部3个测试方法，确认新增A01必要追问、A02保留相关专业修读事实而不展示公共修读背景、A03同task/version2已声明PUBLIC后的FINISH。再用前端真实合同校验实际快照，并运行相关前端回归，记录源码身份、自审差异后才能保存局部本地提交。不实现在线适配器、不进入T06、不追加供应商调用。

限制：模拟核验不证明自然语言理解；新增测试中的同task续答通过直接构造Input验证本域循环，不证明TaskStore的真实续答令牌消费。ASK有额外未理解/未支持需求时保守拒绝，不冒充合法澄清；当前未验证一般自由意图追问。旧在线工厂仍为旧路径，供应商共享usage/额度适配未完成，T05未通过。

恢复方式：保留当前工作区草稿，下一轮先复核diff和源码身份，不重做已实现部分，不用历史评分证明新输出。如果最终验证失败记录精确阶段，不能作完成提交；如要放弃本草稿，仅恢复本轮六文件并保留证据，不重置整个仓库。

## 2026-10-14 09:47:46恢复验收

用户确认Docker启动。Docker可访问，原临时容器已确认不存在，记录见docker-recovery.json。新容器zhku-rulecheck-505d28ca6703仅用于55520专用临时库，无持久卷；db-green-05全部3项测试通过，迁移22、测试结束连接0、容器退出、sourceDrift=0、资源校验通过。原db-green-03中断记录保留，不补写成功结果。

本次fresh unit-green-07共75项全部通过（Gate15、Contract7、Runner21、Provider32），front-green-06共84项全部通过。verify-ask.mjs直接将Java实际的3个ASK场景及6个FINISH场景输入真实前端合同，9个快照均接受。必要追问使用审核问题而非自由模型理由，A02仅保留专业修读事实，不展示同次READ的公共修读背景；A03同task的version2明确PUBLIC，发布公共选修不安排重修的已审事实，无重复追问。四次逻辑尝试及双端口退出均核验。资源退出证据不能替代真实供应商退出证据，本轮没有构建真实供应商核验链路。

actual-flows-sanitized.json仅为原始输出的脱敏副本，去除originalToken与continuationToken；verification.json记录两个原始文件SHA256及前端日志摘要。原始文件本机保留，不入Git。资料保持已授权受控本机用途；不授予公开上传许可。

自审：只变动原六个源码/测试文件，不重复FINISH实现；ASK与FINISH共享核验预算、权限/原文检查；Gate不接收模型自报必要性；条件缺失不能提前作已满足，支持事实不能充当已发布结论。新路径ASK只支持确实影响当前需求且可由一次审核条件推进的澄清，不支持任意自由追问、一般条件缺口或完整全目录覆盖。原假设性核验仍需真实模型、人工作业参考验证。

恢复验收通过后可保存局部本地提交，SHA/源码身份见commit-receipt.json及stage-audit.json；不push、不创建PR。下一项为独立核验在线适配器、共享调用/usage与工厂接线的本地验证，再准备新真实探针请求包。旧在线工厂仍是旧路径，T05未完成，不进入T06。

提交自审首次被red-01原始失败XML中CDATA的行尾空白拦截。没有改写失败文件，仅对这一精确XML豁免行尾空白检查，其余文件继续严格校验；该例外和暂存身份记入stage-audit.json。
