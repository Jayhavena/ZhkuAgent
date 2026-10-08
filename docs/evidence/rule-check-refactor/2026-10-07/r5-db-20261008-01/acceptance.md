# R5 Task2A：输出前真库复核准备

2026-10-08，阶段 DB_ADAPTER_VERIFIED_NOT_INTEGRATED。新增JdbcRuleCheckEvidenceReader按服务端profile、切分版本、scope及source/chunk精确读取；只允许ACTIVE和现有schema可表达的资料/index资格，参数绑定，不重新检索或排序，不加controller/迁移/依赖。

核验器与adapter联合在真实临时数据库验证：首次读后撤回、内部化、分享资格/scope改变、profile退役/BUILDING、run异常、source版本漂移/同步换版、正文变而hash没变、正文和hash一起变、切分版本不回退。全部为顺序读写间变更；没有压力并发或线性化证明。SQL快照之后到HTTP输出仍有窗口，当前学生HTTP链未装配。

RED：[18项6断言失败/0错误跳过](../r5-db-20261008-red-02/results.json)。首批一项orElseThrow错误已保留，改断言形式后才记干净RED。GREEN：[3类37项](../r5-db-20261008-green-01/results.json)。最终相关回归：[18类172项](../r5-db-20261008-regression-01/results.json)，失败/错误/跳过/漂移0；[逐份XML和当前源码核验](verification.json)，[入口合同29项](runner-contract-final.json)。新15项真库合同与3项目标/连接失败纯单元单列，不能换算为质量准确率。

随机无卷临时库127.0.0.1:55439/zhkuagent_rulecheck_e2e，V21，连接0，容器与新增Java退出；未访问原演示库、未调用供应商。新DB测试类在setup迁移/连接前严格验证临时URL，拒绝后的cleanup不连接，真实方法纯单元已覆盖。

独立审查无Critical/Important，1项Minor：run/entry跨profile的专门反例未补，当前SQL正确阻断，作为覆盖缺口留账。见[审查报告](review.md)和执行账本。eligible=true仅表达所查存储字段通过；完整时间/取代/清单仍待R6，真实审核/个人适用/自由回答事实不由本组件证明。旧RAG4→40仍OPEN，五业务状态、审核模板和学生界面未实现；G0/G2未通过。

分享/撤回反例同时内部化以符合库约束，不声称独立隔离证明每个SQL谓词；正文变但hash未变由正文比较首先阻断，不声称该真库场景隔离证明了重新hash分支。重复结果防御与异常资源关闭做静态审查，未作故障注入。生产连接池/连接超时预算尚无应用装配，异常返回能受控拒绝不代表连接阻塞有时间上界。

回退先停止将来可能装配的v2入口，按本包before/新增文件及当前hash审查增量；此刻无学生入口可停。本批未回退工作区，历史失败与P0恢复样例保留。其余任务依赖见[卡点表](../../../../product/rule-check-blockers.md)。
