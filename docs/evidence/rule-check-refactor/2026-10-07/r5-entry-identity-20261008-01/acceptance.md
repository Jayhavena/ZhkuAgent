# Task2P：v2入口迟到撤销及身份恢复

2026-10-08，V2_ENTRY_IDENTITY_RACE_AND_RECOVERY_VERIFIED。

|问题|修复|证据|
|---|---|---|
|旧身份入口失败invalidate/清Cookie伤新登录|v2只清请求上下文并401/no-store，不改共享Session/Cookie|真实SQL首读→B先登录完成题→撤销A第二读失败交付；旧响应无Cookie，B me/原文句柄及audit1有效|
|保留记录会阻断重新登录/退出|精确CSRF GET/login/logout POST仅跳旧actor，CSRF和新Provider不跳|有效恢复16/1RED；正常撤销连续me/POST401，csrf新取/新账号登录/显式logout真正invalidates|
|边界注释/合同落后实现|合同说明闲置超时、Controller仅中文注释同步|C0/I0/M2审查；独立scope extension原字节、去注释语义等价|

有效37/3 Cookie断言RED，16/1恢复RED→最终48类503（358纯单元+133临时库含22实际HTTP+12凭据相关配置检查），0fail/error/skip/sourceDrift，570源码，V22/18迁移/连接0/Java及容器退出。29合同为外部计数替身另记录。所有阶段原报告/XML/hash保留：首次37/3并发未到Cookie断言，首次502含计数夹具，首次503唯一失败因登录后旧CSRF先403；不冒称有效对应业务RED。最终phase为r5-entry-identity-final-green-20261008-02。

一次独立审查C0/I0/M2；实际9项增量及595保护审查通过，后显式扩Controller注释为10项，594其他保护不变；原9项before不覆盖。唯一后测试Java变化为注释等价，不再运行业务测试。前端80仅上批成绩、保护不变、未重跑。真实目录CLOSED、资料DRAFT、B/C/G2不变；无模型/原库写入/commit/push/deploy。

保留失效Session不授许可，15m是闲置超时而非绝对寿命。仅修v2入口失败；旧v1、显式logout、匿名csrf新Session和总体网络Cookie交付窗口仍有局限，不称全系统原子性。Task3C完整浏览器代理仍待后续。
