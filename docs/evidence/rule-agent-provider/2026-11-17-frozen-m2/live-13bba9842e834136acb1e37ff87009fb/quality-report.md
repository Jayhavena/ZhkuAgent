# 首批10题真实Agent质量报告

**里程碑二交付验收：PASS。当前回答质量：未达标。**

10题均已实际执行，没有替换或删除失败题；RFE-013完成同一任务的PUBLIC澄清续答，共10 task / 11 run。整题通过1题（RFE-003），失败9题，未判定整题0题、基础设施评分错误0题，**通过率1/10=10%**。所有题都有确定的整题结论，但仅5/10=50%满足全部适用维度可评估；另外5题虽已有失败依据，仍保留局部不可判定，未强行补分。

这是基于实际已发布Agent输出的端到端基线，包含宿主核验与模板输出的影响，不等同于自由文本模型知识能力测试。语义标注由Codex离线独立复核，独立于答题模型与发布核验模型，**不是用户人工终审**。后续人工复核可以另建标注版本；不能改写本次原始采集。

## 冻结与实际执行

| 项目 | 记录 |
| --- | --- |
| Repository / Worktree | `D:\alldesk\Projects\ZhkuAgent\main` |
| Branch / HEAD | `feat/zhkuagent-v3-frontend` / `338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a` |
| 题集 | 冻结v1.0，SHA `36e2827d7855a8b1ddcacbde013aa60dd59e4b99f215ec35b3b9c7470f86f280` |
| 请求包SHA | `18143180bc208127846495b726e5f218a7f6140d9b4ed3e2b30f61c4cdbcff5c` |
| 模型 | 请求与实际响应均为DashScope `qwen3.8-flash` |
| 固定样本 | 001、002、003、004、005、006、007、013、018、020 |
| 澄清 | 仅013，预登记courseType=PUBLIC；实际追问匹配后在同task续答 |
| 调用 | 42/55；33次决策、9次独立发布核验；42次HTTP 200 |
| Tokens | 供应商审计报告输入64,363 / 输出4,482；不是固定货币费用承诺 |
| 时间 | 批次宿主约93.77秒；最长轮次约12.79秒；最长单次调用约6.14秒 |
| 预算核对 | 每run≤5调用，每次输出≤1024 tokens/20秒；全部在授权有效期内，无自动重试 |
| 资源与额度 | 所有case-closeout确认资源关闭；专用临时容器移除；未用13次额度关闭，remainingAuthorizedCalls=0 |

[授权与额度关闭](../authorization-closeout.json)、[逐调用/逐轮预算核对](audit-verification.json)、[原始批次关闭记录](batch-closeout.json)、[清理记录](../supervisor-cleanup.json)。42次HTTP 200只证明收到响应，不能代替协议、语义、发布或答案质量通过。

首个标准启动器尝试在Java启动前被通用辅助函数的250秒参数上限拒绝，未创建task/run、未预留额度、未调用模型。随后使用1350秒专用进程监督器调用**同一个获批jar与SHA**，未修改或绕过宿主包/额度校验；这不是重试已执行样本。记录见 [预启动失败](../prelaunch-wrapper-validation.json)、[监督器身份](supervisor-provenance.json)、[实际监督器审计副本](supervisor-used.txt)、[启动退出结果](launcher-result.json)。执行后再次检查原包/源码/jar仍匹配，见 [post-execution-package-check.txt](post-execution-package-check.txt)。

## 口径与逐维汇总

- 固定分母10题。RFE-013两轮按一题汇总，任一轮明确失败保留FAIL，缺任一计划轮次或必要观察不计完整可评估。其余冻结10题未执行，不在本批通过率分母，也不得外推为已测。
- 确定整题结论比例=(PASS+FAIL)/10=100%；全部适用维度可评估比例=5/10=50%。二者不等价，失败可以与部分维度未知共存。
- 事实维度对已完整观察到的空主张清单仍检查必答事实缺失；不因为模型未生成事实就把漏答当作通过。处理失败/终止时，没有实质答案可供判断的适用性与澄清/缺口保留UNJUDGEABLE。
- 不适用须来自题目/轮次合同及实际清单，不能替代核验。RFE-020没有必答事实且无已发布引用，事实/引用不适用；其检索维度因第二次SEARCH的NO_PROGRESS拒绝没有工具观察而保守记不可判定，不代表第一次检索失败。
- `executionSafety`沿用现有Grader，包含终态、覆盖和动作合同；该维FAIL**不等于发现越权操作**。本批没有已观察到的办理教务操作或办理成功声明。

| 维度 | PASS | FAIL | UNJUDGEABLE | NOT_APPLICABLE | 适用分母 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 检索召回 | 9 | 0 | 1 | 0 | 10 |
| 事实支持/覆盖 | 3 | 6 | 0 | 1 | 9 |
| 引用完整性 | 3 | 6 | 0 | 1 | 9 |
| 范围与适用性 | 2 | 3 | 5 | 0 | 10 |
| 澄清与资料缺口 | 3 | 2 | 5 | 0 | 10 |
| 执行安全/契约 | 3 | 7 | 0 | 0 | 10 |

[机器可读报告](report-reviewed.json)给出每维计数、完整指标、每轮task/run身份与全部证据SHA；[逐题逐维明细](report-reviewed.md)列出每一条判定理由及capture/annotation链接。[汇总图](quality-summary.html)与同一JSON一致，未另行计算评分。

## 逐题结论与失败原因

| 题目 | 整题 | 完整可评估 | 实际结果、证据与失败原因 |
| --- | --- | --- | --- |
| RFE-001 | FAIL | 是 | 两个分类事实及第四条引用正确；正文未按题卡标明2025.9.2历史文本。元数据版本正确，失败是正文限定缺项，不是错版引用。[capture](RFE-001/ANSWER/capture.json) / [annotation](RFE-001/ANSWER/annotation-reviewed.json) |
| RFE-002 | FAIL | 是 | 未发布本科对象事实，也未说明不能外推研究生或建议核对研究生培养方案；返回泛化UNSUPPORTED_DEMAND/教务系统指引。必答事实、引用和范围说明缺失；终态/覆盖不符合预登记回答契约。[capture](RFE-002/ANSWER/capture.json) / [annotation](RFE-002/ANSWER/annotation-reviewed.json) |
| RFE-003 | PASS | 是 | 同时保留“原则上16学时”和“不超过32学时”，引用定位/版本/原文一致，完成执行契约。[capture](RFE-003/ANSWER/capture.json) / [annotation](RFE-003/ANSWER/annotation-reviewed.json) |
| RFE-004 | FAIL | 否 | FAILED/INVALID_MODEL_RESPONSE；FINISH和PUBLICATION_REVIEW记录FACT_NOT_RELEVANT。已检索/读取，但没有发布必答事实或引用；无实质答案的语义维度保留未知。[capture](RFE-004/ANSWER/capture.json) / [provider audit](RFE-004/provider-traces.jsonl) |
| RFE-005 | FAIL | 否 | STOPPED/BUDGET_EXCEEDED，前序FINISH记录INVALID_MODEL_RESPONSE，未发布事实/引用。实际4次调用低于5次上限，不能仅凭终态码推断为供应商次数耗尽；具体触发根因尚未还原。[capture](RFE-005/ANSWER/capture.json) / [provider audit](RFE-005/provider-traces.jsonl) |
| RFE-006 | FAIL | 否 | FAILED，保留ANSWER_COVERAGE_INCOMPLETE及DETAIL_NOT_IN_REVIEWED_TEXT；未发布必答人数事实或引用。没有将漏答伪装为资料不存在或成功回答。[capture](RFE-006/ANSWER/capture.json) / [annotation](RFE-006/ANSWER/annotation-reviewed.json) |
| RFE-007 | FAIL | 否 | FAILED/INVALID_MODEL_RESPONSE；第3次决策审计明确为ACTION_PARAMETERS/ACTION_PARAMETER_INVALID，没有进入发布核验。参数拒绝被保留，不能推断具体哪个参数错误。[capture](RFE-007/ANSWER/capture.json) / [provider audit](RFE-007/provider-traces.jsonl) |
| RFE-013 | FAIL | 是 | 正确询问courseType，PUBLIC后给出公共选修课不安排重修的正确分支。ASK正文未说明第18条两种课程处理不同，缺题卡要求的澄清理由；续答通过不能覆盖首轮缺项。[ASK](RFE-013/ASK/capture.json) / [PUBLIC续答](RFE-013/RESOLVE_PUBLIC/capture.json) / [同task链路](RFE-013/RESOLVE_PUBLIC/continuation-link.json) |
| RFE-018 | FAIL | 是 | 没有编造截止时间，也明确不代办并指向当期通知/系统，但遗漏网上选课计划必答事实/引用，未明确历史手册与本学期今晚的边界；实际UNSUPPORTED_DEMAND也不符合预登记CURRENT_NOTICE_MISSING契约。[capture](RFE-018/ANSWER/capture.json) / [annotation](RFE-018/ANSWER/annotation-reviewed.json) |
| RFE-020 | FAIL | 否 | STOPPED/NO_PROGRESS；第二次SEARCH被拒绝，未形成奖学金范围说明及正式办法/年度通知的完整下一步。没有捏造奖学金阈值；事实/引用N/A，未完成语义维度仍未知。[capture](RFE-020/ANSWER/capture.json) / [annotation](RFE-020/ANSWER/annotation-reviewed.json) |

RFE-013两份标注均独立完成：[ASK标注](RFE-013/ASK/annotation-reviewed.json)、[续答标注](RFE-013/RESOLVE_PUBLIC/annotation-reviewed.json)。续答实际重新SEARCH/READ，再FINISH和PUBLICATION_REVIEW；本批没有靠虚构新检索或无条件借用父轮观察来通过。相同task、不同run、version+2和预登记PUBLIC回复均已核对。引用凭证身份由同任务工具安全复用，原始能力令牌没有进入采集文件。

## 判定依据与可复核性

事实主张逐条与冻结卡和原文核对，匹配同时要求语义factId及发布正文一致，未只凭ID计通过。所有4次已发布引用出现（3个唯一条款，013跨轮复用）均核对2025.9.2版本、完整chunk checksum、引用span与原文切片、CLAIM/CLARIFICATION用途关系；检查记录是各轮citation-checks.json，原文来源为包中已绑定的verbatim-import资产。

001的历史限定和013的分支说明按**已沿用的回答正文口径**判断；元数据/引用原文正确不会自动补足正文说明。未执行前端呈现，不能声称页面一定遗漏或一定补足。独立人工复核若采用不同的展示口径，应新增明确规则与标注版本，不追溯修改本次记录。

原始供应商请求/响应正文及发布核验输入快照未在本批保存，只有白名单审计、摘要、已发布结果与工具观察。因而本报告只报告FACT_NOT_RELEVANT/ACTION_PARAMETER_INVALID等实际代码，不反推具体被拒绝的句子、错误参数或认定核验器误判。

## 后续优先项与交付边界

1. 先定位004的相关性拒绝、006的覆盖失败、007的动作参数错误及005预算终态的具体触发点；本批证据不足以直接给出实现层根因。
2. 改善002/018泛化缺口答复，保留已知规则事实并给出与问题对象一致的下一步。
3. 补足001历史来源限定、013必要澄清理由，再以新版本/新请求包复测。当前不修改生成逻辑，也不使用关闭额度追求更高分。

预执行44项回归通过，原包执行后核对仍通过；本轮没有修改获批生产代码或重建jar。临时监督器仅为绕开本机辅助函数250秒的参数限制，已保存审计副本；标准批次启动器仍需在后续新构建中接入支持获批长预算的监督入口，不能将本次等价执行宣称为标准Execute命令通过。

本批无宿主级强制中断；FAILED/STOPPED样本与局部未知均实际保留。宿主被强制终止后的恢复和22.5分钟超时杀进程路径未在本批实测。其余冻结10题、前端/E2E和人工终审未做。含原文/回答产物限本地30天，未加入Git/CI或公开发布。
