# Task2L / H01：旧数据库测试默认目标隔离验收

2026-10-08。LEGACY_DEFAULT_DATABASE_TARGETS_GUARDED_NOT_FULL_CI。

|解决的问题|实现|证据|
|---|---|---|
|直接运行旧测试可能回退原演示库|32处fallback改shared guarded getter|无DB RED11/10，缺目标/原库/非回环/扩展URL/非法端口拒绝；3代表性class init反例不调用生命周期|
|外层runner安全仍可能被直接迁移绕过|共享测试Flyway配置前guard|源检查固定三个既有测试库名，正向临时库V22；更窄archive/rulecheck/demo guard保持|
|改造误伤已有专用供应商测试库|补精确zhkuagent_e2e兼容|兼容/探针RED14/2；只调用target helper，不调用模型|
|子进程继承URL与父测试系统属性不同|孤立启动探针自包含archive guard在Application前运行|纯单元拒绝原库/其他库/扩展URL，合法archive通过；未称实际打包probe正向运行|
|测试数量被误当完整旧功能或产品质量|源入口清单与实际选类分开|32默认站点改造，额外12旧DB类39项执行；未运行供应商/报告留出|

45类444项（312纯单元+120专用临时库含12HTTP+12凭据配置）、29计数替身合同；无卷55467 V22连接0/退出。0失败/异常/跳过；源码566与XML哈希复算、42项before/after、生产main全部文件和真实2025DRAFT/20确认参考/36题不变。第一GREEN13类50PASS。一次审查见review.md/review-resolution.json，所有故障原报告保留。连接入口清单是源审查，不代表各入口均已执行。

H01旧默认原库入口风险已处理。默认全量Maven缺显式目标将fail closed；完整纯/DB/HTTP/供应商CI分类仍F06，不宣称全仓CI绿。无原库写入/启动/迁移、真实模型、报告集评测、部署或commit；正式目录CLOSED、真实2025DRAFT不可见不变。A06/A07、B/C及浏览器/用户收益G2继续。
