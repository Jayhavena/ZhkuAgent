# R5 Task2C独立审查包

审查范围为本批白名单before字节→当前源码，不是HEAD整个历史dirty工作区。未提交或部署。

计划：docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md Task2C。规范：docs/product/rule-check-decision-contract.md及rule-check-scope.md。
接口裁定：.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md最后Task2C三条Ruling。

实现：两路SQL映射保留sourceId；旧七参构造器兼容且新链拒绝null；capture复用引用工厂/verifier/审计，整批当前核验及审计成功才返回不可变证据。无HTTP装配或真实模板激活。

重点逐一检查：
1. 旧七参对象不能伪造sourceId；两路SQL是否实际携带正确来源。旧v1对外DTO是否受到字段新增影响。
2. 同chunk重复/不同别名、重复或异常工厂、null/空输入不可交出未经核验快照。
3. 全文4→40、当前撤回/身份/版本/hash变更及reader不可用是否拒绝。
4. 审计部分写入失败不返回证据，CAPTURE审计不能误称已向学生下发；没有事务原子性承诺。
5. 捕获资格是当前reader核验结果；不得信任客户端或跳过后续决策重读；不将顺序读称最终下发原子性。
6. 合成真实SQL验收不等于2025资料/规则放行，不关闭RC-04-SEMANTICS或产品质量门禁。

证据：changes-review.diff、source-review.json、source-before.json及before/*.bin；请阅读实际文件，勿只凭计数。

|阶段|通过|测试|失败/异常/跳过|资源退出|
|---|---|---:|---|---|
|r5-capture-red-20261008-01|False|None|[0, 0, 0]|True|
|r5-capture-red-20261008-02|False|12.0|[9, 0, 0]|True|
|r5-capture-db-red-20261008-01|False|26.0|[3, 0, 0]|True|
|r5-capture-green-20261008-01|True|12.0|[0, 0, 0]|True|
|r5-capture-db-green-20261008-01|True|41.0|[0, 0, 0]|True|
|r5-capture-regression-20261008-01|True|177.0|[0, 0, 0]|True|

首轮仅测试构造器调用错误导致编译失败，不算RED；有效RED为第二轮12项9失败与真库26项3失败。
Unit不连库；DB仅无卷独立临时库，含合成embedding及内存审计替身，没有供应商调用。
引用审计真正数据库持久化、语义模板审核/匹配、身份/CSRF/页面、真实用户及报告留出效果未验收。
