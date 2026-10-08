# R5 Task1 可独立组件最终审查包

基线HEAD=135c9d3e58ddbf99f9507ed3f952bd839fd99b86，未提交，当前历史dirty不属于本批。只审本包source-before声明的4个新生产类型、1个新测试和runner白名单新增，不审整个HEAD差异。新代码在knowledge/rulecheck，无控制器、Spring装配、数据库适配、模型或学生入口。

Spec：docs/product/rule-check-scope.md；docs/superpowers/plans/2026-10-07-rule-check-refactor-roadmap.md的R5；任务计划docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md **仅Task1**。执行账本.superpowers/sdd/2026-10-08-rule-check-r5-plan/progress.md含Ruling；其余Task2–4受G0/人工参考门禁，不能假审核。

RED r5-20261008-red-01：19项17断言失败/0错误/跳过，新组件安全默认拒绝最小壳未实现详细合同，非旧RAG修复复现。GREEN r5-20261008-green-01：19项通过；相关15类纯单元回归随后执行，检查results当前是否已生成完整，不用缺失推断失败。真实DB/模型均未运行。

审查重点（计划原文）：
1. 原文4、模型写40、位置合法：核验不能只检查ID或字符范围，必须检查提议文本与实际substring一致。
2. 相同文字出现在多处或重复citation ID：按给定位置和服务端身份核对，重复标识拒绝而不是取第一条。
3. 查询后撤回、版本/hash改变、reader返回另一个source/chunk：输出前重读并拒绝；数据库实际竞态由Task2补证，不用内存回调宣称真库控制。
4. UTF-16位置跨越代理对、负数/越界/空白：必须受控拒绝，不能切出半个字符或泄漏未核验的提议文字。
5. 证据重复原文但不相关、遗漏例外、模型裁判分高：Task1不证明语义完整，Task2/3的审核模板与真实人工评分必须补齐。

额外语义：SHA256是UTF8片段文本，hex值不区分大小写，不能替代PDF字节审核；revision独立比对。citationId是本轮别名，当前reader以持久source/chunk重读，latest别名不要求一致。拒绝不返回proposal文字或底层异常；服务端record与reader必须由应用创建，类型名称不是密码学资格。其集成/授权仍未实现，旧RAG4→40已知失败继续OPEN。

请按requesting-code-review/code-reviewer.md给出Critical/Important/Minor与Declined to judge；只读，不改代码、不运行DB/供应商/全量Maven。唯一可写报告：本目录review.md。若审查证据尚未齐备，明确分开实现意见与待核验报告。不要把文档中明示的门禁缺口当本批组件已承诺上线。
