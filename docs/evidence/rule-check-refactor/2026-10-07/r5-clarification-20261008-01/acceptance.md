# R5 Task2I / A04：必要澄清验收

2026-10-08。NECESSARY_CLARIFICATION_HTTP_VERIFIED_CATALOGUE_CLOSED。

|解决的问题|实现|验证|
|---|---|---|
|追问后条件与规则可能漂移|服务端绑定原问题/目录全文/settings/当前待问；只补一个允许值|规则变化/退役/settings变化拒绝；分支不重复追问，错误键/值/已声明值拒绝|
|续答可能沿用撤回前的旧引用|跳过选题但重新检索/捕获/审计/决策|实际临时库新审计2轮、selector只1轮；ARCHIVED来源后缺证、无事实/续答|
|token可重放、串账号、期限滑动|每Session单slot、随机一次nonce、全actor绑定、本轮绝对5分钟|store到期边界、role/revision；真实HTTP换账号、受控clock300秒、重复消费409|
|较早慢请求覆盖或误删新问题|begin先占generation；consume交接原子锁；finish及异常清理只匹配本轮|10项store含三并发合同；真实socket并发旧请求409、新问题可续答|
|条件、范围与事实不透明|用户声明合并USER_DECLARED；审核scope/条件与主事实when|15接口投影/续答；不自造结构化例外、不证明个人学籍|

有效RED：32/13、24/2、10/3、15/4、HTTP12/4，0异常/跳过；core GREEN88、generation GREEN35、定向GREEN163、最终30类357项，268纯单元+77真库含12HTTP+12凭据配置。29计数替身runner合同另计。无卷55464/18既有迁移至V22/连接0/容器退出；每HTTP夹具监听与身份线程退出。源码560及XMLhash复算；16项before/after，真实DRAFT/20参考/36题/YAML/18迁移/旧安全与配置保护不变。

4新增HTTP用真实数据库身份/socket/cookie及测试-only Primary合成service/clock，证明续答协议而非完整JDBC政策正向质量；真实核心数据库续答/撤回由独立审计类验证。模拟clock到期不称真实等待5分钟。每合法续答开启下一轮TTL；错误尝试不延长本轮。每Session仅一个slot，新问题替代旧问题、丢失响应需要重新查证，不提供完整咨询历史。

一次增量审查见review.md/review-resolution.json；I1第17项无法合法续答，实际RED27/1后单次修复，最终完整相关GREEN；已有空白第16项可补填保留。A04工程完成，真实2025仍DRAFT/不可见、正式目录CLOSED；真实语义/例外/展示B组、模型A05/C、原文A06、最终身份/资料再核验及窗口A07、页面/浏览器A08–A10/G2仍OPEN。不是供应商效果/全校准确率/学生收益。既有正文UTF8与runner配置-only误报延期不由本批关闭。
