# 工程验收收口记录（旧v1浏览器一项受阻）

2026-10-08。v2合成浏览器矩阵、普通终端和本批审查修复已完成；完整A10继续OPEN，真实资料/G2不放行。

|解决的问题/验收目标|实际结果|证据|
|---|---|---|
|五状态只有组件测试、缺浏览器整链|实际Vue→Nginx→Java→专用临时库：回答/部分回答/必要澄清及续答/缺证/冲突；冲突两侧原文、记录页码及摘录高亮正确|live05和browser-matrix.json；浏览器操作由CUA完成，没有注入前端结果|
|撤回与身份切换容易保留旧内容|撤回后原文拒绝并清除正文、再次查证缺证；退出、学生B登录后空白问题/回答/原文；维护者无学生表单；编辑新题清旧依据；首页返回/刷新核对身份|本轮实际AX/DOM观察，1280宽无水平溢出|
|旧v1可能被新客户端破坏|实际应用/鉴权/派发、受理及完成查询、同键不同内容409通过；Nginx就绪|live02与terminal-http；浏览器工具拒绝接管保留页，旧v1页面动作未验证，不冒称通过|
|用户普通终端不能识别pwsh，中文编码边界不明|实际Windows PowerShell5.1，重建Machine/User PATH，pwshOnPath=false，CMD包装入口找到PS7；UTF8输入/输出/管道及中文样本正常；从该环境运行相关两类3项实际HTTP验收通过|ordinary-terminal.json、ordinary-terminal-http.json及terminal-http/results.json|
|额外片段初始化失败跳清理|真实第一INSERT成功，第二INSERT因无效run外键失败；有效RED2项/1fail/0error，首片段残留1；将初始化纳入finally后GREEN3项全通过，保留base source/chunk|browser-cleanup-red与terminal-http/XML；一次合并审查I1修复，不重审|

前端完整85/85，0fail/cancel/skip，实际构建成功；runner29合同通过，externalStarts是计数替身。没有重新跑无变更的503历史全套，测试数不累加成产品准确率。live05的JUnit1项包含HTTP预检与宿主结束，不是浏览器断言数；浏览器观察单列。

保留所有失败：live04新增夹具访问器编译错误；legacy-live01因Docker停止而未启动Maven/数据库，resources无法核验；恢复官方Docker后台服务后live02通过；前端首次沙箱realpath EPERM导致两个测试文件未运行，同集合正常权限重跑85通过；普通终端业务3项成功后，首次采集报告因PS5.1默认GBK读UTF8 JSON失败，明确-Encoding UTF8后仅重采既有报告通过，没有无意义重跑数据库。

一次fresh-context审查C0/I1/M0；I1真实SQL反例修复，当前C0/I0，无延期Minor。审查不判断的真实资料/语义/收益、墙钟TTL、Cookie网络窗口及发布原子性保留明确边界。两处旧文案和Java字段映射生产改动收齐；后续新Java全部test classpath，无新审批开关。

v2与旧v1专用Java/数据库/Nginx正常退出；数据仅随机无卷专用临时库，不写原演示库、不调用供应商。用户要求保留浏览器后没有主动关闭标签页/窗口。Docker后台恢复后不擅自退出用户的Docker应用。没有commit/push/deploy。

唯一尚未收齐的本轮页面验收：旧v1实际页面提交、完成刷新及换账号显示。浏览器工具以URL安全策略拒绝接管现有页，已告知并请求重新连接；不通过创建其他页面、shell浏览器自动化或其他表面绕过。恢复后续跑该项，完整A10才可决定关闭。
