# E13 测试专用接线：离线回归

- 基础身份：main@614a17c43cc7a14a060ce08952d6929d95b35f8c；本轮只修改测试设施，无生产 Runtime/学生路由改动。后续 Prepare 绑定实际新 HEAD 和全部输入字节。
- Provider / Embedding 调用 0；未启动 Live、未提交真实问题、未创建数据库或容器。断言使用合成/本地受控输入，不代表真实模型通过。

|验证|命令（仓库根目录，前端命令除外）|结果|
|---|---|---|
|Java 定向回归|`mvn -o -pl knowledge -am -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=RuleAgentE02ExecutionRegressionTest,RuleAgentE02TerminalReceiptTest,RuleAgentE13ExecutionRegressionTest,RuleAgentE13PreparationTest,RuleAgentE13ArchivedReviewRegressionTest,RuleAgentE13InsufficientMaterialTerminalTest,RuleAgentOwnedPreviewStopTest test`|53 tests，0 failures/errors/skipped；exit 0|
|浏览器驱动离线谓词及租约|`node --test knowledge/src/test/browser/rule-agent-e13-page-offline.test.mjs knowledge/src/test/browser/rule-agent-e02-page-offline.test.mjs`|10/10；exit 0；未开浏览器|
|启动器离线拒绝/分流|`pwsh -NoProfile -File knowledge/scripts/tests/three-question-launcher.test.ps1`|66/66，actualMavenRuns/databaseOperations/credentialReads/providerCalls=0；exit 0|
|标准前端构建|`npm run build`（agent/frontend）|exit 0|
|计划指定学生产物|`VITE_ENTRY_MODE=student npm run build -- --outDir dist-student`（agent/frontend，Bash环境赋值）|exit 0；冻结目标是此产物，不是默认 dist|

日志在本目录 `offline/`；Java/PowerShell 原始日志保留原始编码，其余 UTF-8。Java 明细：E02Execution 18、E02TerminalReceipt 1、E13Execution 18、E13Preparation 5、E13Archived 4、E13Insufficient 3、OwnedPreviewStop 4。

## 已覆盖的边界

E13 独立档位/计划/批次/授权保留键；固定原始 E13 问题（不是先前离线短题的替换）；单任务单 run/5 次调用/无续跑；同一所有者与版本；严格费用预留/体积与输出上限；协议、未知 usage、工具或审计失败封闭；中断/回调清理；混合未理解需求不能冒充安全终态；E13 必须 RESOLVED/INCOMPLETE/NONE + UNSUPPORTED_DEMAND；空事实/无引用合法。E02 继承回归同时通过。真实模型、Chrome页面、费用账单和容器清理尚未在本轮执行，不能据模拟回归宣布产品通过。

核价、包指纹和独立 Verify 结果另存本证据目录；旧包和旧授权不沿用。跨日必须重新核价并生成新包，任何 Live 都等待另行明确人工授权。
