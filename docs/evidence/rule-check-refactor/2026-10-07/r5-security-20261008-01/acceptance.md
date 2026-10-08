# R5 Task2F / A01：独立v2身份过滤链验收

2026-10-08。AUTH_FILTER_CHAIN_VERIFIED_NOT_APPLICATION_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|v1 cookie范围不覆盖v2，直接扩宽会混用身份入口|独立profile与ZHKU_RULE_CHECK_SESSION固定v2路径，共享Provider与当前身份实现|新14项、旧v1 9项；只清对应cookie，旧路径/方法拒绝|
|客户端自报身份或维护者可能越权|可信PreviewPrincipal与当前身份库；USER无角色继承|身份头、外来同名USER、MAINTAINER拒绝，停用/revision变化拒绝|
|登录/注销及CSRF状态容易混淆|框架sessionFixation/CSRF；CSRF先于当前身份|真实会话token登录后刷新，旧token403，新token200；登出session失效|
|守卫的默认配置可能掩盖实际cookie配置缺失|显式cookie、独立profile、回环绑定与15分钟配置；禁止其他模式|缺设置、混合入口、域/持久化等反例；失败先于身份Bean初始化|
|配置回归需要process-only数据库凭据但被归类纯单元|移入凭据/专用临时库验收，不弱化生产守卫|12项配置检查通过；不全称真库查询，H01仍OPEN|

最终26类298项通过：22类223纯单元、3类63实际真库、1类12凭据配置检查；0失败/异常/跳过。29启动器合同为计数替身。有效RED14项13断言失败；首轮test lambda编译失败、首次GREEN旧配置因移除凭据失败均保存，不计有效RED。源码与XML指纹复算、无卷临时库V22、连接0、随机容器退出。

独立审查见review.md/review-resolution.json；逐条裁定与暂缓项记录执行账本。该批没有独立Application、DataSource装配或查证业务Controller，sentinel仅检验授权；仓储替身不证明真实HTTP/cookie创建/数据库认证。A02继续完成这些环节。真实2025草稿、20题人工参考、36题原件、旧controlled配置和18迁移哈希不变；B-01/B-02、完整R5/G2、模型语义与用户收益均未通过。

引用文档：版本固定Security6.4.4，Context7核对CSRF/sessionFixation/logout/MockMvc；未升级依赖。[CSRF官方文档](https://github.com/spring-projects/spring-security/blob/6.4.4/docs/modules/ROOT/pages/servlet/exploits/csrf.adoc)。
