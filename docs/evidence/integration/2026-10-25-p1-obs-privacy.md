# ZhkuAgent P1：隔离主线与首批 OBS 日志隐私整合

日期：2026-10-25。范围：只整合 OBS02B 中两处日志隐私修复及必要离线测试；不进行 T08/Sprint02 合并、最终迁移、远程推送或 Worktree 清理。

## 主线候选与来源

- 临时工作目录：D:\alldesk\Projects\ZhkuAgent\worktrees\integration\mainline
- 本地分支：integration/zhkuagent-mainline-20261024
- 起点／本提交父提交：5e496216bf64a47ffcd57f8ca3dba4d77d2e449d（OBS02E）。
- 隐私实现来源：1146c85030b5c3539145bee91ff34fe27ddbdda1；OBS02B 的 08045c080fe75714e0b0ce2a2eeaa33218555735 保留了这些实现和测试。
- 本文与实现同一提交，实际 Commit SHA 以 Git 历史及本机 .p1/commit-receipt.json 为准，避免为记录自身 SHA 重写提交。

重新审查显示 OBS02E 包含历史基准 46b05452e20b05ca1ec08c2fb2400f3969052da6 之后的 10 个提交，原工作区干净。zhkuagent-modules 与 OBS02E 为 1/82 个单侧提交，OBS02B 与 OBS02E 为 8/82 个单侧提交；因此只选择所需变更，没有整树覆盖或整分支合并。

## P0 遗留风险

创建工作树前，P0 的 402 项记录校验值重新核验通过，267 项本地／辅助排除成果全部仍在原处，大小与修改时间未变。分类为：169 项构建输出、13 项运行缓存、46 项测试证据、26 项源配置候选、3 项敏感配置候选（.env）、7 项历史／不透明归档、3 项有价值的既有工作区归档。

P0 不等于敏感内容全量备份。上述内容没有被本轮复制、覆盖或删除；本次日志修复不依赖其恢复。敏感配置后续应由用户管理的加密容器保全，密钥分离保存并私下验证恢复，不把原始值写入普通备份、Git、参数或报告。

5 个配置文件均位于旧 rule-agent-integration 工作树的 knowledge/src/main/resources/，目前字节与该分支 HEAD 完全一致，没有发现未备份的独有配置内容；旧 Git 修改标记未被刷新或消除：
| 文件 | 用途 | 本轮处置 |
| --- | --- | --- |
| application-zhku-controlled-preview.yml | 受控预览与入口隔离 | 不恢复旧工作区状态 |
| application-zhku-local-admin.yml | 本地治理台与上传边界 | 不恢复旧工作区状态 |
| application-zhku-local-preview.yml | 预览、RAG 环境变量名称与流式参数 | 不恢复旧工作区状态 |
| application-zhku-rule-check-preview.yml | 规则检查预览及入口约束 | 不恢复旧工作区状态 |
| application.yml | 公共应用入口与绑定 | 不恢复旧工作区状态 |

新树沿用 OBS02E 已提交配置，未从旧工作区导入配置覆盖或敏感值。因此满足创建并使用本轮隔离树的安全前提；无关且未变化的排除项留作后续处置。

## 实际变更

| 文件 | 变化 |
| --- | --- |
| agent/src/main/java/com/zhku/agent/advisor/MyLoggerAdvisor.java | 仅记录固定调用／流式生命周期标记；不读取请求、回答、异常正文；移除为日志聚合回答的步骤 |
| knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewConversationFacade.java | 遥测旁路失败只记录 TELEMETRY_WRITE_FAILED，不带异常消息、参数或堆栈 |
| agent/src/test/java/com/zhku/agent/advisor/MyLoggerAdvisorPrivacyTest.java | 恢复 9 个用例，增加部分输出后异常及逐次需求传播 2 个用例 |
| knowledge/src/test/java/com/zhku/knowledge/observability/TelemetryFailureLogPrivacyTest.java | 恢复旁路日志、回答、取消契约测试；改用独立合成标记及嵌套故障 |
| 本报告 | 记录边界、证据、失败及后续对齐项 |

Runtime Observer、TaskStore、配置、POM、前端和启动脚本没有修改。同步调用保留请求与响应身份、null／空响应及同一异常；流式保留原分块对象、逐次需求、订阅、取消和部分输出后的同一异常传播。遥测失败仍是旁路，不改变回答与取消结果。测试仅使用开发期合成数据和内存依赖。

流式开始标记保留原 Advisor 装配时机，未订阅时也可能出现，重复订阅也不对应独立开始标记。它不是实际订阅数、运行开始或物理终止的权威事实；这些事实仍以 Runtime Observer／TaskStore 契约为准。本轮不扩展其统计语义。

## 验证、失败记录与未验证范围

JDK：Zulu 21.0.11；Maven：3.9.15。仅在新工作树使用离线 Maven 和本机依赖，无网络下载、真实模型、付费调用、Docker 创建、数据库连接或迁移。

| 阶段 | 用例数 | 断言失败 | 执行错误 | 说明 |
| --- | ---: | ---: | ---: | --- |
| 最初基线 | 108 | 0 | 6 | 102 通过；误纳入数据库测试类，完整错误保留 |
| 修复前隐私复现 | 12 | 10 | 2 | 预期失败；2 个错误来自原日志读取 null／空回答 |
| 授权后的离线最终验证 | 114 | 0 | 0 | 全部通过，跳过 0 |

最初 6 个错误全部来自 AnonymousTelemetryTest：静态 DataSource 初始化要求显式专用临时库，ZhkuDatabaseTestConfig.verifyTarget() 在连接前拒绝，其他用例继而出现类初始化错误。没有把这些错误计为通过。用户随后明确授权保留失败、将该数据库测试列为本轮未验证，并继续仅离线整合；本轮没有修改或绕过其数据库保护逻辑。

最终分模块结果：agent-runtime 20、agent 13、knowledge 81；其中隐私测试 12，其余离线 Observer／TaskStore／投影与聚合契约 102。三个模块均完成 Java 编译和测试编译。独立只读审查没有关键或重要阻塞问题；流式开始标记的解释限制已在上文记录。

准确执行命令、逐类结果、原始日志和修复前错误保存在被忽略的本机 .p1/ 目录：
- baseline-command.json、baseline-result.json、baseline-maven.log、baseline-AnonymousTelemetryTest.xml
- privacy-red-command.json、privacy-red-result.json、privacy-red-maven.log 及对应错误 XML
- offline-green-command.json、offline-green-result.json、offline-green-maven.log
- validated-source.json、code-review.json、preservation-precommit.json、preservation-postcommit.json

可复核的 Maven 选择器：
    AgentRunObservationDispatcherTest,AgentRunObservationIsolationTest,BaseAgentObserverIsolationTest,AnonymousRuntimeObserverContractTest,ObservationProjectionTest,ObservationDeliveryTest,T08AnonymousObserverCombinedContractTest,RuleAgentTaskObserverContractTest,RuleAgentTaskStoreTest,TelemetryAggregatePrivacyTest,MyLoggerAdvisorPrivacyTest,TelemetryFailureLogPrivacyTest

命令使用 -o -B -ntp -pl agent-runtime,agent,knowledge -am -Dsurefire.failIfNoSpecifiedTests=false，并明确设置 -Dtest、可写的 zhku.rulecheck.evidenceDir 及临时目录；子进程去除凭证环境变量。全量数据库／在线／Docker 套件与真实演示没有运行。现有 ThreadDeath 弃用告警仍属原基线，未在本轮扩大修复。

## 原资产保护与本地提交边界

提交前后分别比较 38 个旧工作树的 HEAD、分支、index SHA-256、状态 SHA-256；所有原本地／远程引用应与开始时完全相同，只允许新增 integration/zhkuagent-mainline-20261024 引用与 Worktree 登记。P0 按只读使用，402 项记录及 final-audit.json 复核，267 项本地排除文件再核对大小／修改时间。Maven 产物和诊断记录只留在新树，未加入提交。

暂存仅限上述 4 个 Java 文件与本报告，并逐文件比较已验证工作文件与暂存字节。若任何保护检查、测试或暂存检查失败，停止提交，不重置或清理现场。恢复参照父提交与保留的原分支，不自动执行回退、历史改写或迁移。

## 下一阶段需要具体对齐的内容

1. T08 adapter 与 Sprint02 均修改 tools/t08-acceptance/sprint02/README.md、cli.mjs、engine.mjs 及 tools/t08-acceptance/test/sprint02.test.mjs。必须逐段对齐 Java trace adapter 和 synthetic adapter 的入口、数据格式、来源标记与评分规则，不能整树覆盖。
2. T08 adapter 还修改 RuleAgentOfflineTraceExportTest 和 RuleAgentRunnerTest；须保留当前 TaskStore／Observer 宿主 ABI、原始轨迹与停止／物理终止边界。
3. Sprint02 的 cases、rubric、run-plan、policy-ledger 与 freeze.json 必须作为同一冻结版本复核，不能修改冻结内容来凑通过。
4. OBS02E 的 sink、shutdown 和部署日志策略门禁仍需独立实现与验收；本次离线日志隐私测试不代表生产 sink、第三方日志采集或留存策略已获认证。

这些是静态分支差异确认的对齐项，不是已执行／已解决的合并冲突。本轮在本地提交和保护复查后停止，不进入任何上述合并或部署阶段。
