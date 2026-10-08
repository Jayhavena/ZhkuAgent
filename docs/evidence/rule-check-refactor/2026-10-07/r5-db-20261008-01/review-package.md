# R5 Task2A 数据库证据复核适配审查包

依赖分析后补入 docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md 的Task2A；完整Task2真实模板/学生应用仍受B-01/B-02。连续用户授权，细化现有“输出前真库重读”路线，无新产品/控制器/迁移/依赖。

基线HEAD和当前HEAD均135c9d3e58ddbf99f9507ed3f952bd839fd99b86，未提交。只审本包source-before的新增JdbcRuleCheckEvidenceReader、JdbcRuleCheckEvidenceReaderTest、JdbcRuleCheckEvidenceReaderTargetTest及runner新增两个选类。Task1四个类型只作为consumer合同，刚由新鲜审查验证，不重审历史dirty或整个P0。

Spec/计划：docs/product/rule-check-scope.md、docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md。Ledger：.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md。READ接口：按server固定profile/chunkingVersion/scope及source/chunk读取，只有ACTIVE、发布/学生可见/分享确认、COMPLETED/run/profile/chunking/source版本/checksum一致才返回。参数绑定防注入。source/chunk精确身份不重排检索候选；要求明确切分版本，不回退其他版本。返回current-chunk别名，verifier保留approved中的本轮别名。

关键验证：
- 新DB测试在迁移/连接前严格专用URLguard，setup失败cleanup不连接；无原库默认地址。生命周期真实方法纯单元拒绝证据。
- 新API默认empty壳RED：r5-db-20261008-red-02，18项6断言失败/0错误跳过。red-01一项orElseThrow错误已保留，改成显式isPresent断言后RED干净。
- GREEN：r5-db-20261008-green-01，3类37项通过：新DB15、新目标与连接失败纯单元3、Task1组件19；真实专用随机无卷临时库V21，连接0，容器/新增Java退出，无模型。最终相关18类172项随后执行，按现存结果核对而不把未生成报告当失败。

Review Focus：1) source/chunk双身份、server范围/索引、切分版本是否真的约束SQL；2) withdrawal/internal/rights/scope/profile退役/run/修订/正文/hash变更是否真正阻断oldquote；3) SQL元数据一致但正文已变，是否由verifier重新hash识别；4) 裸运行新DB测试是否在任何连接/迁移前拒绝并安全cleanup；5) 只声明两次顺序真实读写之间变化，不能说读后下发窗口消失或并发线性化；6) null/异常、资源关闭、参数绑定、重复结果是否fail closed。

已知边界：完整时间/被取代/发布清单未映射；eligible=true只是所查存储字段通过，不代表真实审核/传播资格；未装配学生入口，旧RAG4→40仍OPEN；真实模板/账号权限/HTTP下发及模型/用户效果待Task2–4。数据库adapter不是第三套RAG，不加排序或模型调用。

请按requesting-code-review/code-reviewer.md输出Critical/Important/Minor/Declined to judge；只读，不改实现，不执行Maven/DB/模型，唯一可写本目录review.md。必须区分新测试风险与其他旧DB类风险，不以外围缺口假称本批已完成。
