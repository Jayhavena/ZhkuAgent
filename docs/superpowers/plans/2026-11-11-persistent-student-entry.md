# Persistent Student Entry Implementation Plan

**Goal:** 持久宿主提供学生任务完整接口；默认零模型，真实模型入口默认关闭。

**Architecture:** 复用 RuleAgentControlledLaunchConfiguration、ReviewedTools、TaskStore 及现有模拟执行器。新增仅持久学生入口启用的装配，复用独立持久身份库与临时规则库。SIMULATED 固定问题明确披露，ONLINE 必须显式模式、授权标识、密钥及有界调用额度，失败不回退模拟。

**Tech Stack:** 现有 Java 21 / Spring Boot、PostgreSQL、Vue、PowerShell 启动器。

- [x] 先新增默认 Task API 开启、ONLINE 缺配置拒绝测试，观察失败；实现持久学生模式装配。
- [x] 确保模拟未覆盖问题标 MISS；退出 TaskStore 后等待 executor 终止，避免卸载资料与工作线程竞态。
- [x] 启动器传递明确执行模式，模拟模式清除供应商密钥，ONLINE 只从环境解析秘密；保存本地配置，无秘密命令参数。
- [x] 运行定向离线测试及构建；在具名专用库验证真实宿主注册、登录、任务、澄清、原文、隔离、取消、重启、停止。
- [x] 浏览器验证学生页；在线入口仅缺配置/本地替身验证，真实供应商调用保持 0。
- [ ] 独立复核、修复有效发现、保存精简证据与本地提交。保留任务 15 分钟 TTL 及重启失效协议；宿主本身无测试租约死线。
