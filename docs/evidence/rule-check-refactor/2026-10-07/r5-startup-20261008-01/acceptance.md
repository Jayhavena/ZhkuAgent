# R5 Task2G / A02第一段：独立启动及实际HTTP身份验收

2026-10-08。CLOSED_CATALOGUE_HTTP_AUTH_VERIFIED_NOT_RULE_CHECK_API。

|解决的问题|实现|验证|
|---|---|---|
|过滤链已通过但没有独立应用|仅显式Import新配置/安全/Auth；新profile默认关闭|实际Boot/嵌入式监听，旧Controller/配置不存在，旧路由拒绝|
|配置错误可能先碰原库或其他入口|主initializer及BFPP/数据源守卫，精确专用e2e目标、单DataSource/Flyway/身份/工厂|目标、凭据来源、开关、预算反例；无卷55462，不连接原库|
|框架替身不能证明真实cookie和数据库身份|合成账号BCrypt/JDBC + 实际HTTP|name/path/HttpOnly/SameSite、会话/CSRF轮换、logout、revision撤销、换账号/v1 cookie拒绝/维护者403|
|未审真实规则可能因启动自动放行|CLOSED目录固定空，内部应用返回缺证，选择/embedding禁用|无确认事实，无业务Controller，真实草稿/参考/资料资格不变|
|测试通过但资源仍驻留|夹具清本批UUID账号，关闭上下文并观察socket/线程|每次监听不可连接、身份closed/inFlight0/liveThreads0；临时库连接0与容器退出|

有效RED：配置10项9断言失败，HTTP3项3断言失败，均0异常/跳过；定向GREEN48；最终28类311项零失败/异常/跳过：233纯单元、66真库含3实际HTTP、12凭据配置。29runner合同为执行器替身，另计。18既有迁移至V22，XML/源码指纹复算，11条before/after范围保存；新迁移/依赖/升级/原库/供应商均无。

一次独立审查及处置见review.md/review-resolution.json。普通用户终端启动器、同源代理、业务查证Controller/页面、真实资料与规则放行仍未完成；A02整体、完整R5/G2不因此通过。真实HTTP身份结果不是自然语言效果、浏览器结果或用户收益。A01留下仅配置选类时runner迁移误判Minor仍记录，不影响本批含实际迁移的组合验收。

[Spring Boot 3.4显式Import官方文档](https://docs.spring.io/spring-boot/3.4/reference/using/using-the-springbootapplication-annotation.html)，Context7核对，实际依赖3.4.4未升级。
