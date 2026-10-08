# R5 顺序实施账本

2026-10-08。计划：docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md。
P0最终20类151项、29合同、22样例恢复通过；见P0 closeout verification。HEAD未改，无提交/推送。

## Task1 start

Step1基线完成：新增4生产类型+测试路径不存在，before字节已保存。G0 NOT_PASSED；新组件无控制器/数据库/模型装配，不改变旧RAG4→40失败。

Ruling: 顺序内联执行，沿用当前非main隔离checkout和独立ledger，不另建worktree/提交 — 保留用户历史dirty修改及连续授权 — 成本是最终证据按本批增量审查而非整树提交。

Ruling: 先实现确定性原文组件，不猜审核规则/评分参考 — 已确认R5抽取路线允许独立合成合同 — 成本是Task2–4仍依赖B-01/B-02与真实参与者。

接口预检：四类型名称、UTF16范围、UTF8 SHA256及reader身份合同一致；结果只承载服务端substring。只捕获reader RuntimeException，运行时异常受控拒绝；不添加语义裁判。

## Task1 GREEN / Task2A start

Task1 RED19项17断言失败/错误跳过0，GREEN19项全过，相关15类130项全过，合同29项全过；独立最终审查进行中。尚未装配学生端，旧RAG语义OPEN。

Ruling: citationId是本轮别名，当前读取以持久source/chunk身份校验，latest别名不同允许 — 数据库无法独立重建本轮别名 — 成本是应用必须从本轮approved绑定别名，不接受客户端自证。

Ruling: Task2A真实数据库读取准备不依赖真实审核模板，可按补充清单单独实施 — 连续授权要求推进无外部依赖的工程项 — 成本是只证明现有字段复核，不代表完整时间/最终下发竞态或真实资格审核。

Task2A基线已保存：新reader及DB/目标纯单元测试路径不存在，runner before字节保存。SQL要求服务端固定profile/chunking/scope，按source/chunk精确读取；不排序候选，不新增第三套RAG。不装配控制器/发布入口。

## Task1/Task2A final

Task1 COMPONENT_VERIFIED_NOT_INTEGRATED，Task2A DB_ADAPTER_VERIFIED_NOT_INTEGRATED。DB RED18项6断言失败/0错误跳过→GREEN3类37项；最终18类172项通过、合同29，XML/current源码一致，临时库连接0/容器与新Java退出。无模型/浏览器/真实质量/用户实验。

Task2A red-01正常路径先orElseThrow导致1 error，保留失败，改显式isPresent后red-02为纯断言RED；不是数据库约束失败。

Ruling: Task1审查Minor独立别名正向单测延后，当前实现正确且后续DB正常流程已验证不同current别名 — 不为非阻塞项重写本批测试 — 成本是独立组件单测仍有回归覆盖缺口。

Final: Ruling: 审查未判定的语义完整性/个人适用/可信server构造/账号授权/PDF与资料资格/完整时间/读后HTTP撤回/真实模型用户效果均保持OPEN — 当前只证明片段一致与现有字段真库复核 — 成本是不能接学生入口或关闭RC-04语义。

Ruling: 独立准备完成后不伪造审核模板或用户样本；剩余Task2–4所需外部资料/人工参考/参与者在blockers明列 — 所有不依赖这些条件的本阶段必要工程已准备 — 成本是本次不能宣布校园查证闭环完成。

不删除ledger或历史证据，无commit/push/deploy；后续同一授权沿本计划接续。

## Final review rulings（逐项范围裁定）

Task2A审查Critical0/Important0/Minor1，报告已读；没有待修的阻塞发现。

Final: Ruling: M1跨profile专门真库反例延后，当前SQL正确要求run/profile一致 — 属于测试覆盖缺口而非已发现漏放，按审查Minor留账 — 成本是未来误删谓词可能没有本reader专项回归报警。

Final: Ruling: 完整时间/取代/清单/真实审核资格仍OPEN — 存储字段通过不能替资料审核 — 成本是G0及完整时效不能关闭。

Final: Ruling: 读后到HTTP/流式下发撤回仍OPEN — 仅顺序数据库变更合同、无学生入口 — 成本是集成阶段须确定下发与撤回策略。

Final: Ruling: 摘录相关/条件例外/自由事实与旧4→40仍OPEN — 两个组件只控制来源一致 — 成本是不能用新组件测试宣布旧RAG修复或产品正确。

Final: Ruling: server record与reader可信创建、绕过公开构造的应用误用未验收 — 当前无学生输入装配，类型名称不作为授权 — 成本是Task2必须拒绝客户端自证快照。

Final: Ruling: 真账号/浏览器/模型费用/用户收益未执行 — 不存在相应闭环或真实样本 — 成本是后续人工资料与参与者仍需到位。

Final: Ruling: PDF原件审核与传播资格未通过 — 文本hash只证明片段字节一致 — 成本是不可替原件和适用审核。

Final: Ruling: 历史dirty/P0/其他旧DB类不外推 — 本次审查限定增量，P0历史证据独立保留 — 成本是RC-10全仓旧默认风险仍OPEN。

Final: Ruling: 生产连接池/连接超时预算待应用装配 — DataSource异常返回已受控，阻塞时间未测 — 成本是Task2接入时必须落实调用预算，不承诺当前耗时上界。

Final: Ruling: 约束导致分享/撤回合并内部化，正文漂移首先由text比较阻断；重复行与异常关闭只做静态判断 — 接受真实覆盖边界，不冒充隔离谓词/并发/故障注入证据 — 成本是未来增加专项工程证据。

