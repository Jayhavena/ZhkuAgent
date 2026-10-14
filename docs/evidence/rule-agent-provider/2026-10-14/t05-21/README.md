# T05新READ协议P01真实探针：失败并停止

本轮开始2026-10-14 12:26:28，主动执行上限20分钟。已按用户call_dd93875e53e3456c89bb8b169e48f195明确许可执行一次新包最多4次外发；实际只调用2次并停止，无重试、无P02。本轮只执行和归档，不修改运行源码、题目、预算、模型提示或历史评分。T05仍未通过。

## 实际身份与执行

- 源码检查点53616cc1636301b314fb1247c7d2a899ba4025b6；运行实现85a533c50aaf28ef05568c38ac2d73e18ffc0b25；执行前后运行源码无工作区差异。
- 冻结包t05-19/request-package.json，SHA256：7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560。
- jar SHA256：d9559bace280d41f7647933df6808ead00f2f7b21c189761adf682ffd6388b83；与前轮离线构建/零调用回执一致。
- 北京DashScope chat/completions / qwen-plus，P01_READ_REFERENCE_03；3决策+1独立核验上限4，单次10秒/1024输出token、run45秒不变。
- 操作前重新检查Process→User→Machine凭证并注入子进程；不打印密钥，不沿用旧剩余10次。本次专用无卷随机容器临时库端口55522，未操作原演示库。
- 实际启动12:27:33.580—12:27:47.077，退出码1，未超时；专属宿主与容器已退出。资源只读核对12:29:03确认无探针容器/Java残留。

## 事实与未知

| 环节 | 实际结果 | 可得结论 |
| --- | --- | --- |
| 第1次决策 | HTTP200，SEARCH合法，真实SQL检索签发10个候选且含第四条 | 检索完成，不能代表最终查证完成 |
| 第2次决策 | HTTP200完整响应，在ACTION_PARAMETERS / READ_HANDLE_COUNT拒绝 | handles是ARRAY且数组大小不在1—3；数量守卫生效 |
| READ / FINISH / 独立核验 / 发布 | 均未执行 | 真实编号映射、事实适用与最终发布仍未验证 |
| 最终状态 | FAILED / FAILED / terminated；PROCESSING_FAILURE；无证据/主张发布 | 没有把系统失败冒充资料不足或政策结论 |
| P02 | NOT_EXECUTED | 不评分、不扩大批次 |

安全诊断仅保留elementIndex=null、jsonType=ARRAY、stringLength=null、failureCategory=READ_HANDLE_COUNT、currentCandidate=null；未记录候选UUID、编号原值、集合代号或自由响应正文。数量检查先于集合代号/编号校验，因此本轮无法确认新编号是否正确，也不能把它称作UUID问题已经在真实模型上解决。

具体是空数组还是超过3个，未保留，仍为未知；不得猜测模型选了全部候选、复制错误或不理解比较问题。旧批READ_HANDLE_UUID原失败及原评分完整保留，不据新结果改写根因。

## 用量与停止

实际2次均为DECISION；PUBLICATION_REVIEW为0。按每次RECEIVED一次计量：输入737+1411=2148token，输出60+76=136token；费用金额未知。完整usage没有因动作非法丢失。

本批剩余2次关闭、不补测，不与旧剩余10次合并。所有真实模型历史调用计数由上一轮18增加为20，不宣称可靠性或用户收益提升。

## 验证与证据

assess-stopped-p01.mjs读取原始快照/调用轨迹，在真实前端合同上核验实际失败快照；检查2次调用、10候选、数量失败、无READ/主张、usage去重、退出状态，全部断言通过。此通过仅证明失败记录一致与安全拒绝成立，不是P01成功。

- authorization-and-preflight.json：明确用户许可、包/jar/HEAD、凭证仅可用性、专用目标。
- approved-launcher-result.json：实际时间、退出码、无重试、单一证据目录。
- failure-assessment.json：可观察结果和未知边界；原始trace/outcome SHA。
- resource-exit-check.json：只读资源核对。
- actual-outcome-sanitized.json：删除原文/续答token、遮蔽候选句柄后的归档；原始快照先经前端合同核验，脱敏归档不冒充可直接消费的原始快照。
- 原始安全轨迹/宿主汇总：../t05-live-1c7d94d4c9e94870bb299b35b38b5c53/provider-traces.jsonl、probe-result.json。

原始outcome及host.log只保留本机，不暂存。归档未含自由模型正文、密钥、数据库/整本PDF/原文访问token。每轮失败均保留，不重新评分、不重写历史证据。

## 唯一后续与Git

当前卡点是“合法READ选择数量的在线产生”未通过。下一项若继续，应仅在本地核对选择数量合同和模拟反例，优先以受控类别区分空选择/超量；不得据未知值改变业务答案，也不得自动截断候选、放宽1—3上限或追加真实补测。具体修正方案尚未实施/验证，不在本批扩展重构。P01通过前不申请P02、不进入T06。

本轮只保存**失败证据的独立本地提交**，不是完成提交。没有push/PR/标签/部署。提交前核对明确范围、脱敏、记录一致性和未变源码；SHA回执见failure-commit-receipt.json。既有四份产品文档及其他无关资料未纳入。恢复只能依据明确检查点和新许可，不重置工作区、回放旧响应或重新执行本批脚本。
