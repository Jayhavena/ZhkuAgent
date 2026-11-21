# M1.1 正式工程基线审查

审查范围：main `04d17902e4c8f253b70e7c57a7a15dc4501d033e` 到代码候选 `8c07cb64ce98006dc975632e0d492f5a5e88cf2d`，3提交27文件；PR #6。运行基线报告 `30a0d93317db4bb5898e924ce62b140d89f1d555` 是独立文档提交，不是Task API代码身份，也不在本PR原始head中。

独立只读审查配置OpenCode / `opencode-go/deepseek-v4.1-flash`；子报告自述同一模型，编排元数据executedModel=null，不能独立证明供应商实际模型。审查逐读候选并亲自运行离线测试，未访问数据库、供应商或浏览器；不是人工审批。主Agent复核如下。

## 合并阻断与处置

新增持久任务配置的显式Import未同步旧装配测试，CI白名单又漏选该类。主Agent独立复现 `RuleCheckPreviewConfigurationTest#startupApplicationUsesExplicitImportsWithoutComponentScanning` 失败：预期5项、实际多出PersistentStudentTaskConfiguration。修正精确导入断言并将整个RuleCheckPreviewConfigurationTest纳入CI；不能用仅有151项白名单通过掩盖此回归。生产代码本次不变。

## 审查边界

Task API仍按actor/session隔离；CSRF、幂等键、版本与nonce、取消终态、原文发布资格重验沿用既有实现。持久化指账号/身份库，任务仍在内存且重启失效，不承诺任务历史跨重启恢复。本次没有新增SQL迁移；已有身份V1/V2和规则V23的启动/重启实测记录在独立运行基线报告中，不冒充本次审查亲自重跑数据库。

配置默认SIMULATED；ONLINE缺配置失败且无静默回退；默认模拟仅支持固定问题。新增配置有条件守卫，既有同步规则查证入口仍需通过原装配/安全/Controller/原文回归。CI的Grader重放属于离线链路验证，不是当前模型质量。

## 后续必需项

- M2前补ONLINE正路径装配测试，目前Options读取进程环境、仅有拒绝路径测试；受控Probe须绑定新SHA/运行包/授权，不能沿用已消耗批次。
- M1.2浏览器复验登录、提问、澄清、续答、结果和原文；模式刷新及迟到响应会话隔离仍缺自动化回归，已有手工证据不等于持续门禁。
- M3/运维前修复启动器Stop未消费非0宿主退出码的问题；目前超时在宿主报失败，启动器仍可能打印STOPPED。
- 固定问题多处重复、半装配退出才失败、错误态需刷新、手工合成账号脚本实例校验不足作为已知限制跟踪。
- 旧jar/class摘要是保存回执，未由本次审查复算；合入后必须重新构建正式main并记录新的产物与HTTP/重启/关停结果。

上述允许后置项不构成真实在线准入；HTTPS、邀请范围、隐私留存、实际账单、模型质量及长期稳定性仍未完成。合入前远程CI须针对修订head重新通过；本文件不预先宣称main已集成或合入后验收已通过。

## 修订验证

2026-11-13主Agent先复现1项装配断言失败，再修正并离线执行更新CI的12个Java选类，加RuleCheckPreviewSecurityConfigurationTest、RuleCheckControllerTest、RuleCheckOriginalEvidenceServiceTest。Maven汇总218项，0失败/错误，1跳过（缺少已审手册原件时，既有完整装配测试主动跳过）；实际通过217项。显式Import断言已执行通过，不能把跳过项写成实测完整装配通过。日志为本机knowledge/target/m11-import-red.log及m11-import-green.log，不入库；命令采用mvnw -o -pl knowledge -am、显式-Dtest及已创建的-Dzhku.rulecheck.evidenceDir。没有数据库或真实供应商调用。前端/生产源码本修订未改，沿用候选本轮202项前端及构建通过记录。合入后的产物和HTTP验收仍须重新采集。
