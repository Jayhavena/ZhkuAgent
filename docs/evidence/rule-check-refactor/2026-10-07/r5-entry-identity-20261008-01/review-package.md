# Task2P 一次入口身份增量审查

只读9项before→current/diff/hash和必要依赖；禁止写/跑测试/DB/模型/网络/派agent/发其他thread。规范task2p-brief和计划Task2P；巨大dirty其余不审。

v2身份失败仍401/no-store/无数据，只清请求SecurityContext，不再invalidate Session或发删除Cookie，以免迟到失败破坏新登录。CSRF GET及精确POST login/logout只跳当前旧actor查询，CSRF前序不跳、Provider login仍authenticate+requireCurrent新actor、logout框架显式结束Session+clearCookie；auth/me/rule-check/original每次fresh DB，不继承许可。旧v1 cleanup保持。Session记录15m自然TTL/显式logout，不持久化新历史，不扩大已审核目录或真实资料（CLOSED）。不承诺所有Cookie、显式logout网络交付顺序或匿名CSRF新Session原子性。

首次37/3失败中并发test实际停在“已撤销A阻止B登录”而未到删除Cookie；首次502全回归3失败包含旧Provider invocation计数夹具，均保留，不宣称对应GREEN。改并发夹具：入口A第一次真实SQL读后暂停，B先成功登录和新题，随后撤销A，释放时用实际仓储第二次SQL抛A失效，交付到入口；恢复旧cleanup后有效37/3RED在Cookie断言（三组），新增身份恢复纯单元16/1RED（csrfGET被旧actor阻断）。最终48类503（358纯单元+133临时库包括22实际HTTP+12配置），0fail/error/skip/sourceDrift，资源退出；29合同计数替身通过。新增并发GREEN保B真实me+原文句柄+audit count1。正常撤销连续GET/POST均拒；csrf可获取然后新账号认证，显式logout真正invalidates。实际业务仍合成Runtime，无供应商或真实资料批准。前端80及全其他595项hash保护，不在本批重跑。

重点审查CSRF/Provider/角色/方法的安全边界是否削弱、请求SecurityContext清理是否伤新Session、失败不自动logout的恢复/保留记录约束、actualSQL并发夹具和证据范围。返回Strengths/Critical/Important/Minor（绝对file:line/触发/最小修复）/Declined/Assessment。一次fresh review后Important一轮RED→GREEN，不二次审查。
