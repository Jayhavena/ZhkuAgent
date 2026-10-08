# Task2I / A04 必要澄清最终增量审查

只审本批16项before→当前增量，历史dirty不在范围。changes-review.diff/source-before.json/source-review.json/before/*.bin以及实际源。只读；不改文件、不跑测试/Maven/DB/模型/网络、不派子agent、不发其他线程消息。

规范docs/product/rule-check-decision-contract.md；Task2I计划及执行账本最后条目；remaining-tasks A04。用户授权持续执行；默认关闭目录/真实资料草稿和20参考不变。

ApplicationService保留旧check包装，新checkWithContext给decision+服务端审核范围/声明条件/事实前提；NEED才构造Continuation。绑定settings、已选CatalogEntry全文、原question、明确条件、当前待问condition；resume只一个允许值，跳过selector，但查当前目录与完整fresh retrieval/capture/audit/decision，目录退役/变化、来源撤回拒绝，不复用旧引用。Scope不是个人学籍/语义证明，独立例外不补造。

Store每HttpSession一个slot，random UUID，TrustedActor全快照，绝对5分钟（每一轮开始时计时；错误尝试不延长；合法续答开启下一轮）。begin先替换slot，finish只能同generation/actor且未过期；旧慢请求不能发布或删除新slot。consume条件/问题校验后一次消费；消费+begin同session锁，错误delta允许更正；旧nonce不能清新slot；换actor只清token指向的旧slot。返回丢失需重新查证，非完整聊天历史/多标签并行会话。

Controller严格原两字段加可选canonical UUID clarificationToken；实际principal USER+既有session，不创建匿名session。新问题checkWithContext，续答store consumeAndBegin→service resume，回复USER_DECLARED合并条件/审核Scope/事实when/token。终态本轮清理；Failure固定409，格式400，服务异常503，全部no-store/no cause；异常清理只匹配generation。HTTP结束前最终资料/身份复核窗口仍A07，不宣称原子性。

Review focus:
1. 服务端ctx绑定/当前重新取证及目录复核，分支不重复追问，缺证不追问；公开Continuation构造器本身不证明真实性，HTTP不能传入。
2. 单session锁、generation发布/清理、一次消费、5分钟到期边界、账号/revision/角色；请求替换与错误delta对可恢复性的合同一致。每轮可重开TTL是明确设计，非整条咨询TTL。
3. Strict JSON token额外字段/类型/格式，现有身份CSRF/32KiB在controller之前；异常与敏感输入不回显；scope/when只映射审核目录，不虚构例外。
4. HTTP12项：8既有CLOSED/身份/限长、4新协议真实socket+真实JDBC身份，后4用测试-only Primary合成service/clock，**不是完整JDBC政策正向回答**；正式配置无夹具模式。真实核心DB另24项含fresh续答/ARCHIVED撤回两反例。Fixture clock到期非实际等5分钟；并发真实socket/latch有界且finally清理。
5. 最终30类355：266纯单元+77真库含12HTTP+12凭据配置；29启动器合同是计数替身。无卷55464/V22/连接0/容器退出；源码560及XMLhash、保护原DRAFT/20参考/36题/旧配置/18迁移。

有效RED：核心32/13、真库24/2、generation10/3、API15/4、HTTP12/4，0errors/skips、资源退出。core GREEN88、generation GREEN35、定向GREEN163、综合355。一次最终审查，不每小任务dispatch。

阶段NECESSARY_CLARIFICATION_HTTP_VERIFIED_CATALOGUE_CLOSED。A04工程完成；真实模板语义/资格B组、模型A05/C、原文A06、最终下发A07、页面/浏览器A08–A10/G2仍OPEN。既有M1严格正文UTF8及runner配置-only迁移误报不由本批关闭。

请返回Strengths/Critical/Important/Minor（file:line、触发、最小修复）/Declined to judge/Assessment。只一次审查。
