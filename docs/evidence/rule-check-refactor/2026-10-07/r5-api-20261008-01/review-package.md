# Task2H / A03第一段严格JSON接口审查

只审16项before→当前增量，历史dirty不在范围；changes-review.diff/source-before.json/source-review.json/before/*.bin及实际源。只读，不修改、不跑测试/Maven/DB/模型/网络、不派子agent、不发其他线程消息。

计划docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md最后Task2H；规范decision-contract及remaining-tasks A03；账本Task2H三条Ruling。用户已授权持续执行。

Controller显式Import，仅question/confirmedConditions JSON字段。结构/类型/必填/数量/UTF16边界手工检查，拒绝trusted额外字段；只调用内部service，返回五状态事实/澄清/未知/冲突，用户条件标记USER_DECLARED，固定知识scope/引用版本。事实引用投影原citationId/source/chunk/revision/span/text，省内部hash/qualified；不自造原文地址/个人适用证明/例外数组。输入错误400、接口异常503，no-store且不回显cause。严格mapper拒绝重复键/尾随JSON。

新增mediaTypeFilter只读头，先于CSRF，业务路径只接受UTF-8 application/json；阻断form使CSRF不会先解析不可信业务body。实际body限32KiB在CSRF/当前身份/授权后；复用旧ControlledRunRequestLimitFilter构造参数化，旧默认v1/runs16KiB保留；不全局注册。新security框架测试改实际JSON类型，并用超大body验证匿名401/维护者403先于大小413。

Review Focus：
1. 严格两字段JsonNode、明确类型/required/bounds、不可信身份/审核/快照拒绝，duplicate/trailing实际mapper路径，不回显敏感输入/异常。
2. 五状态内容与原引用完整保留，用户声明与事实区分，scope只是固定知识范围不是个人适用；不能据DTO合法证明语义；规则范围/结构化例外A04/B02仍未完成。
3. 新媒体头守卫在CSRF前且不读body，不能form参数绕过；UTF-8 JSON界定，错误type415顺序是明确取舍。body读取在身份/授权后，实际最多上限+1，不信Content-Length，旧16KiB/路由兼容。
4. 新Controller只显式进入新App；旧v1路由/身份/CSRF/error/cookie语义不变。登录token刷新后实际maintainer403，原HTTP夹具之前未刷新token可能仅证明CSRF403，本批修正为fresh csrf再测角色。
5. 实际HTTP6项中3原认证、3新业务/固定/真正无长度chunked；standalone11项含5状态为合成服务替身，不是假实际JDBC五状态/真实模型；目标guard、UUID账号清理、监听/identity线程退出。
6. 最终29类325=244纯单元+69真库含6HTTP+12凭据配置，29runner计数替身另计；新无卷55463 V22连接0/退出；真实2025DRAFT/20参考/36题/18迁移/旧controlledYaml配置与新closed YAML哈希不变，无模型/原库操作。

有效RED：Controller11/11断言失败；HTTP6/3断言失败；0errors/skips，资源退出。定向GREEN62；综合325通过。A01 M1runner仅配置选类误报仍留，组合实际迁移不受影响。

阶段STRICT_JSON_HTTP_API_VERIFIED_CATALOGUE_CLOSED。A03第一段，不整体关闭A03；A04范围/跨轮、A06原文定位、A07最终身份/资料再核验与外发窗口、A08页面、B/C/G2仍OPEN。新App空目录没有任何正文输出，此前顺序证据检查不是最终HTTP原子性。本批不授予真实学生使用许可。

请返回Strengths/Critical/Important/Minor(file:line+具体触发/最小修复)/Declined to judge/Assessment；一次审查。
