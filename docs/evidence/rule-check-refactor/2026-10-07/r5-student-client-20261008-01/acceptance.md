# Task3A：学生v2客户端与问题状态

2026-10-08，STUDENT_V2_CLIENT_WORKFLOW_VERIFIED_NOT_PAGE_OR_BROWSER_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|旧学分登录/任务与查证混用|独立Session实例、只读v2 auth/查证/原文|独立路径/同源/CSRF/登录续验，不动v1|
|旧问题/身份的迟到成功和401伤新显示|身份epoch+问题round+本地generation；CSRF前置也归本题|忽略Abort的旧成功/401/原文丢弃；迟到前置JSON/HTML401保住新题|
|退出后me迟到复活主体|公开me与身份转换互斥、登录内部私有读取|logout pending→me→退出成功→迟到成功被挡|
|非JSON401当解析失败留下旧身份|先确认当前轮次再按HTTP401清主体|当前空/HTML401清actor+CSRF；旧epoch非JSON401不清新主体|
|伪状态/无依据/坏跨度显示成查证|五状态/revision/声明字段/非空依据/Unicode/span/pageKind/HTTPS结构|合法五状态通过，矛盾结构/无依据/半Unicode/坏context/不安全链接拒绝|
|网络失败重发或旧原文残留|Workflow当前问题、必要allowed delta、错误/新题/身份/离开清旧内容|未知结果未确认、调用一次；原文失败无旧正文，pending与业务状态分开|

初始可运行stub RED17/17、0cancel/skip，无import失败；GREEN17。补current401/半Unicode有效RED12/2→GREEN65；Unicode测试第一次失败的quote断言阻止context断言，该context反例在最终GREEN执行，不冒称独立RED。一次独立审查I1/I2/I3有效RED16/3→最终全Node69/69（新增23+既有46）、0fail/cancel/skip。原审查65和最终69各日志保留，无二次审查。旧Vue构建成功，新module尚无页面import，不称页面bundle或浏览器通过。10项before/after及diff/hash、保护Java/v1/真实DRAFT/已确认参考不变，后端501为上一批真实已验收，不在本批重跑。

格式不证明事实相关、完整或现行资格；网络JSON整体读后校验，不保证下载字节/内存上限；跨tab只me/当前401检测，未解决浏览器Cookie网络顺序原子性。默认CLOSED/真实不可见/B/C/G2不变。A08页面/A09旧v1文案/A10浏览器及普通终端继续；无新依赖、DB/供应商/原库、commit/push/deploy。
