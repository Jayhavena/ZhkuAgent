# P02唯一真实批次失败归档

冻结包a1d1e50bb565851f60ff6e8172bd39aaf389950d7b24a919a7f0608d3575db01；jar6da3aa7908e968a819c8296384022adfddcb4622fcba16eceb31f53f97af96d0。执行前HEAD为9be96b58e85b316e632e33530ac5f0a12488bf69；底层实现06534fd未变。主仓库58e1ef7未合入。

用户在具体目的地及完整资料清单明确后回复“再次确认”，授权同包一次P02、同task最多两run、最多10次真实调用/12元；本次新授权独立记录。上一轮自动审批拒绝发生在进程创建前，为0调用，旧余量关闭；本批获准实际启动，不绕过旧拒绝。

## 结果与最早失败

本批只启动1次、实际1个run，4次HTTP200（3决策+1独立核验）；宿主19.8132958秒后失败退出。原始probe-result的passed=false、resourcesClosed=true、allowanceConsumed=4保持原样。首轮没有合格必要追问，未提交PUBLIC补充、未进入第二run、未执行在线幂等检查、未完成P02。

原始events能直接证明SEARCH成功（完整10候选）与READ成功（第十八条1份原文、6个审核factId），原文UTF-16跨度及SHA与批准资产一致。第三决策通过动作校验并触发独立核验；其正文未归档，按Runner路径只知是ASK_NECESSARY_CONDITION或FINISH_CHECK，不能称已执行追问。核验结果在attempt4的REVIEW_BINDING/REVIEW_BINDING_INVALID被拒，最终FAILED/coverage NONE，无答案主张或澄清发布。

本次不是超时、容量拒绝或预算耗尽：四次调用分别1651、1285、2091、3285ms，总8312ms；单次20秒/run110秒/Java235秒上限未触及。宿主总耗时包含启动、迁移、工具、核验和清理，其余时间未逐项测量，不能虚构工具分账。不能把本次原因倒推到历史超时。

## 已证实边界与关键未知

RulePublicationProtocol.java:32-62先严格解析/绑定核验JSON，再校验问题跨度、inputFingerprint、问题需求覆盖和binding引用。当前码确定失败在后一组输入关联校验，不是最终RulePublicationGate放行；同码覆盖多个分支，原始响应正文未落盘，无法确定哪一字段不匹配。不能断言模型幻觉、供应商不支持schema、指纹回传错误或客户端请求畸形中的任一个。

Provider.Failure在Runner外层收敛失败，第三动作与失败核验事件未进入outcome.events；不能将events仅2项理解为没有发生核验，真实调用以provider-traces计。任务仓终态快照因宿主只在全程通过后写task-results而缺失，故不宣称失败快照逐字段匹配任务仓；宿主记录的失败/无第二run和Runner outcome保留。上述可观测性缺口只记录，本批不自动修复。

## 调用与费用

按(taskId,runId,attempt)去重共4次，已发请求全部保留，不因语义拒绝退还次数；余6次永久关闭，剩余0，旧P01余量仍0。输入6143/输出441 token均有usage。按执行前复核的[官方北京原价](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)输入0.8/输出2.7元每百万token估算0.0061051元；这不是账单，实际费用NOT_RECONCILED/未知，不视为0。本批无usage缺失调用，不合并不同历史批次算成功率。

## 资源与范围

专用临时库zhkuagent_rulecheck_e2e使用回环端口21779；独立检查无自有探针容器/Java残留，原演示库逐字段状态未变、保持停止。删除容器前未量数据库清理行数，记NOT_MEASURED，不把资源退出等同特定表0行。原始日志、trace、outcome和probe-result按原字节保存。

核验是同一模型另一次上下文/职责分离调用，不能声称跨模型/供应商独立性。2025.9.2本科原文、权限、适用范围和独立发布核验保留；topic草案不升级许可。P01既有完整在线成功保留；P02失败，T05整体真实在线验收未通过。工程成果已冻结，真实在线验收未通过。可靠性、产品质量、未预置问题与学生收益未评估。

本批结束，不修源码/预算/门槛、不重跑、不补额度、不进入T06。若继续，应另开有界本地定位任务，先补核验失败关联检查的可复核证据再讨论新包；当前没有新在线授权。恢复仅反向撤回本次证据提交，不改原始轨迹、不恢复额度。
