# Task2G / A02第一段独立启动审查

仅审11项before字节→当前增量，历史dirty不在范围。changes-review.diff/source-before.json/source-review.json/before/*.bin及实际文件。只读，不改文件，不跑测试/Maven/数据库/模型/网络，不派子agent、不向其他线程发消息。

计划docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md最后Task2G；规范docs/product/rule-check-decision-contract.md/remaining-tasks A02；账本最后三条Task2G Ruling。持续执行用户已授权，不需重复设计审批。

新Application不扫描组件，只显式Import配置/A01安全链/AuthController；initializer与BFPP/数据源入口都复核。沿Boot3.4.4官方3.4文档，未加依赖/升级。新profile YAML入口/initialize默认false，单一DataSource/Flyway/身份/内部RuntimeFactory；仅允许精确127.0.0.1非54329专用zhkuagent_rulecheck_e2e，process-only密码，拒绝其他datasource/Flyway覆盖。必须initialize=true才迁移，已有V22，无新迁移。显式profile/cut/scope/reference2025.9.2/预算，实际catalogue固定空，selector/embedding外发关闭，返回缺证；没有真实规则审批配置或业务Controller。

Review Focus：
1. main initializer/直接配置BFPP/数据源验证是否先于连接/迁移/监听；profile默认false/混用拒绝，实际配置加载，无扫描旧匿名/治理/Agent。
2. 专用临时目标、端口/用户名/别名、凭据来源、自动Flyway覆盖拒绝；身份/迁移/RuntimeFactory同目标，不靠数据库连接失败保护原库。
3. 固定运行边界/预算、CLOSED目录确实不选题/检索/供应商外发，不能把空目录当真实2025装配或学生政策效果；本阶段索引未激活不自动授予资料资格。
4. 实际HTTP cookie名字/path/HttpOnly/SameSite、login session/token轮换、logout、真实身份revision变化和新账号、不接受v1 cookie及MAINTAINER；v1安全配置指纹不变。
5. 测试target在任何连接前校验；仅合成UUID账号清理，监听socket/身份线程退出；密码未打印；HTTP夹具真实socket+JDBC与框架替身区别。
6. 证据311=233纯单元+66真库(含3 HTTP认证)+12凭据配置，28类、29runner计数替身；临时55462无卷V22连接0退出；原库/供应商未操作，不整体关闭A02/A03/B/G2。

有效RED：配置10/9断言失败，HTTP3/3断言失败，均0errors/skips；初次GREEN48全部通过。最终相关311全部通过。A01 M1runner仅配置选类未迁移误报仍待后续工具改进；本批实际HTTP/DB迁移并通过。普通用户终端启动器、同源代理、业务Controller与页面仍待后续，不能宣称A02整体完成。

输出Critical/Important/Minor，实际file:line、触发/最小修复；Strengths/Declined to judge/Assessment。一次新上下文审查。
