# 浏览器资格撤销修正：独立窄审阅

审阅基线：`ba0e80f3de34cf59d40f435b9cb7d98c120e7b49`。审阅对象为该提交之后 `RuleAgentReviewedBrowserLeaseTest.java` 的未提交两行差异；所读文件 SHA256 为 `aa792158d05306f06f9a1c6af1d69867e193cfc02ea0709ccf9142831436b435`。

结论：这是符合既有数据库状态约束的最小测试宿主修正；未发现 Critical、Important 或 Minor 问题。可继续使用新租约验证，不能据此将旧失败批次或新浏览器场景标为通过。

- 原 SQL 仅撤销分享权利，保留学生可见范围，违反 `V2__add_knowledge_source_visibility_governance.sql:12` 的约束。修正以同一条 UPDATE 同时设为 `visibility_scope='INTERNAL_ONLY'` 和 `sharing_rights_confirmed=false`，避免产生不合法状态；按原 profile 子查询限定的更新范围未扩大。
- 变更仅在默认关闭的浏览器测试宿主中。相对基线，生产源码及迁移文件无差异；`JdbcRuleCheckEvidenceReader.java:53` 仍同时要求学生可见和分享权利确认，`RetrievalEligibilityFilter` 也保留两项检查。没有降低权限守卫或移除数据库约束。
- `browser-20261018-205102` 的 README、结构化观察和资源回收记录明确保留整体 FAIL、SQL 约束错误、撤销未完成，以及不能将后端不可用解释为 reader 拒绝。原 Maven/XML 和宿主退出码 1 与该表述一致。部分 UI 观察明确注明来自主会话，未冒充本审阅者独立观察。

本审阅只读 Git 差异、源码和既有证据；未运行 Maven、数据库、Provider 或 CUA，未修改源码、索引或分支。唯一新增文件是本独立审阅记录。

不作判断：`online-preflight-green-06` 的测试是否覆盖该手动控制分支，以及进行中的 `browser-20261018-210519` 实际撤销、浏览器提示和资源退出结果。上述项目须由对应新批次证据证明；本记录不是真实浏览器通过或在线质量结论。
