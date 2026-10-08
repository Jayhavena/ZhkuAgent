# Task2B增量审查包

基线HEAD：135c9d3e58ddbf99f9507ed3f952bd839fd99b86；无提交，历史dirty工作区不属于本次审查。以本目录source-before.json和before字节为边界。

本次白名单：

- knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckRuleTemplate.java
- knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckDecision.java
- knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckDecisionService.java
- knowledge/src/test/java/com/zhku/knowledge/rulecheck/RuleCheckDecisionServiceTest.java
- knowledge/scripts/verify-rule-check-refactor.ps1（仅增加纯单元选类）
- docs/product/rule-check-2025-rule-template-draft.json及本目录导出脚本

要求与状态：服务端选定已审规则、有限条件及本轮检索snapshot；逐事实全部绑定证据必须通过现有RuleCheckEvidenceVerifier及最新reader复核；冲突双方有据才能显示冲突；资料缺口不追问用户；仅缺决定性条件才澄清；部分事实独立性由已审模板声明。输出正文来自模板，模型不能传入自由事实。

真实草案有20个已确认问题参考profile与10条款定位，尚无运行时UUID或片段跨度绑定；ruleDefinitionReviewStatus=DRAFT/studentVisible=false/runtimeActivated=false。这不是20条生产模板，也未实现自然语言路由、HTTP、CSRF、浏览器或模型调用。审核语义负责相关性、条件/例外完整性和冲突声明；组件不证明这些语义正确。

验证：baseline纯单元22通过；首轮沙箱编译AccessDenied（无测试，不计RED），诊断栈位于ZipFS.close/toRealPath缓存读取；安全runner沙箱外RED27项13断言失败/0错误/0跳过，GREEN27全过。完整纯单元白名单回归与runner合同正在执行，最终证据补充实际结果。

固定的设计取舍及成本：

1. 复用已有linked worktree及白名单，保留历史dirty，不提交/推送；成本是审查不能外推整个仓库。
2. 参考确认只能导出真实DRAFT，独立组件用合成已审夹具；成本是学生资料放行、自然语言应用和最终下发仍未完成。
3. 用安全runner明确选类，避免默认全仓DB/供应商副作用；成本是不能称全仓测试通过。
4. 多次顺序当前读取不保证最终HTTP下发窗口或跨条款原子快照；本批不声称解决并发撤回。

Review Focus（逐项检查）：

1. 原文4、模板绑定quote写40、ID/位置合法，必须拒绝，不能只验证ID。
2. 同一持久source/chunk有多个本轮别名，必须拒绝歧义，不取第一项。
3. 草稿、退役、当前撤回、版本/正文/hash漂移或reader故障，不能泄漏失效结论。
4. 关联例外缺失时不能显示依赖该例外的主张；partial只保留显式声明独立且条件匹配的事实。
5. 冲突双方必须完整核验，不允许任选一方；资料缺口不能通过追问用户掩盖。
6. 输入规则/条件集合不可因外部变更漂移；声明条件、允许值和事实前提必须一致。
7. 真实参考确认没有被改标为生产规则审核，20/16原始数据不改写，所有运行时标识缺口如实保留。

请返回Critical/Important/Minor、具体文件行、可触发输入、修复建议、Declined to judge与有限范围结论；只审查增量，不修改源码，不运行DB或供应商，不创建新审查轮次。
