# Task3C1：v2代理范围

2026-10-08，NGINX_V2_PROXY_FIXTURE_VERIFIED_NOT_JAVA_BROWSER_DB_CHAIN。

现有Nginx只放v1导致/rules无法调用v2。新增完整v2尾斜杠prefix，保留URI/显式同一BackendPort；bare精确404，其他/api不进upstream，no-store/禁缓存和upstream重试，上游错误保留。原v1段逐字比较不变。启动文案说明由所选后端决定可用功能，不声称双后端或全部业务可用。

实际Nginx→127.0.0.1 Node夹具：RED v2预期403实际404；GREEN5项：v2状态/URI/body/no-store，v1 URI兼容，bare及其他namespace404不进上游，POST计数1，SPA200/缺资源404。单次403 POST计数不证明所有断连情形，配置proxy_next_upstream off另有代码证据。Docker host.docker.internal能实际到达此loopback夹具，不直接推断Java链路。

独立项目zhku-rulecheck-proxy-20261008-01已down，58480/58184监听均无，Node夹具已终止。无数据库/Java/模型/浏览器登录。一次只读审查C0/I0/M0；两项before/after/diff/hash。现有官方固定digest/loopback/容器环境不扩展，没有新凭据或新镜像。真实目录CLOSED、资料DRAFT/B/C/G2不变，完整Task3C/A10后续。
