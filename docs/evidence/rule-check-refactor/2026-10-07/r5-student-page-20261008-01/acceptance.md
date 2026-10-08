# Task3B：学生查证页面与首页入口

2026-10-08，STUDENT_PAGE_BUNDLED_STATIC_BROWSER_CHECKED_NOT_PROXY_CHAIN。

|解决的问题|实现|证据|
|---|---|---|
|产品入口仍围绕学分实验|首页查证主CTA、/rules独立v2页面、旧实验保留|实际SFC模板与静态浏览器首页点击|
|回答条件/未知/冲突与执行pending混淆|五状态、声明条件/事实前提/范围/未知/冲突双方，pending独立|9项真实SFC SSR检查，非事件或网络|
|引用难核对、页码易误读|原文旁栏相对跨度高亮、记录页码/未知、纯文本转义/HTTPS|SSR实际模板及恶意文本/URL拒绝|
|登录pending离开返回丢未知状态|共享状态先标未知，同epoch结束；组件销毁不清未知，先me再登录|有效11/1 RED→GREEN；Vue实际setup/mount/unmount虚拟host，旧身份不污染新身份|
|有效尾斜杠路由布局遗漏|命名路由及成功导航class|实际浏览器/rules/ bodyClass，首页移除，主CTA回/rules|

初始RED8/8、登录未知RED9/1；第一次生命周期夹具11/2 TypeError不算有效RED，修正SSR context后11/1断言失败有效。最终全Node80/80（旧69+SSR9+生命周期2）、0fail/cancel/skip；真实页面bundle20.28KB。一次独立审查I1/M1修正，未重审。静态浏览器686宽无水平溢出，主入口/实验链接可见，无后端proxy所以me错误是预期；没有真实登录/查证/原文交互，A10不算通过。4175预览服务已停止、浏览器临时tab关闭。10项before/after/diff/hash和保护文件核对；Java/API/旧v1/真实DRAFT/人工参考不变，无数据库/供应商操作。

工程交付不证明资料语义或收益。A07入口风险下一批，A09旧v1 RC11、Task3C代理/普通终端/实际浏览器继续，真实资料仍CLOSED，B/C/G2保持OPEN。无commit/push/deploy。
