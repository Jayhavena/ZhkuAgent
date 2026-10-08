# R5 Task1：原文跨度核验组件

2026-10-08，阶段 COMPONENT_VERIFIED_NOT_INTEGRATED。四个新生产类型、一个测试；确定性核验，无新依赖/模型/学生接口。新组件对合法ID与位置但4→40的提议明确拒绝，失败不输出正文；未知/重复ID、UTF16跨度/Unicode、UTF8 SHA256、当前资格/身份/修订/hash/text及reader异常分别有合同。只有通过的server substring进入产物。

RED19项17断言失败、0错误/跳过；[GREEN19项](../r5-20261008-green-01/results.json)，[15类130项相关回归](../r5-20261008-regression-01/results.json)。这证明新组件合同，不能说旧RAG4→40已修。[独立审查](review.md)无Critical/Important，1项Minor：尚缺latest别名不同的独立正向单测，记录为延后；后续DB适配正常流程已用不同current别名通过，但不改写本次审查结论。

Task2A后来增加DB reader及runner选类，当前源码/白名单以[后续最终核验](../r5-db-20261008-01/verification.json)为准；本批组件源码未变。原文相等不证明相关、条件/例外完整、真实适用/传播资格，也不保证输出前最后一次读后的撤回窗口消失。服务端record是内部数据类型，公开构造不是授权证明；学生端不能反序列化这些快照自证资格。

RC-04-SEMANTICS保持OPEN，G0/真实模板/人工评分/G2收益未通过。回退清单见source-before/changes-final.diff，未恢复原工作区，不删历史证据。
