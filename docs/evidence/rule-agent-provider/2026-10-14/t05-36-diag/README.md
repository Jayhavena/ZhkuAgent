# T05 P01_DIAGNOSTIC_01：诊断候选与首请求载荷重建（本地零外发）

本时段只做已选定方案的三件事：**B2 首决策请求重建与核对**、**B1 连接层可验证诊断**、**零调用构建/包校验/环境预检**。
**本时段真实调用 0 次、数据库操作 0 次、外发费用 0 元；未申请、未使用任何新额度；旧额度（t05-34 已关闭）不复用。**

## 一、身份

| 项 | 值 |
| --- | --- |
| 独立 worktree | `D:/alldesk/Projects/ZhkuAgent-t05` |
| 分支 | `codex/t05-online-evidence` |
| 本时段起点 HEAD | `5fc77f3`（父 `58e1ef7`，冻结基线） |
| 批次号 | **`P01_DIAGNOSTIC_01`**（取代旧批次 `P01_CLOSEOUT_07`；旧批次身份与 `P02` 均被拒绝） |
| 请求包 | `docs/evidence/rule-agent-provider/2026-10-14/t05-36-diag/request-package.json` |
| 包指纹 SHA256 | `4da0c6cb069948c5214c4226055918b0f13980e9833f1f1dc72f38e51385f2ee` |
| 构建产物 | `knowledge/target/knowledge-0.1.0-rc.1-controlled.jar`，SHA256 `94e450f3a0cde1d34cbdf203d15f33c6604fc1d1df5e319f999d9b8e7f57c237` |
| 执行策略修订 | `STATE_AWARE_DIAGNOSTIC_01` |

本时段改动的源码指纹（提交前实测）：

| 文件 | SHA256 |
| --- | --- |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java` | `334ad6b6e37549ea2c6616479249e384a01bbbb7416540fb5791f4152ad3963d` |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java` | `9e59e5250ddcd3f0dda714e994209190d41768d14d466f55526334cdb7984515` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentProviderTest.java` | `49dda41a90895f7bbecb27a892deeb32c8639650b0940fc2632b9aeac00d3915` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineFactoryTest.java` | `36930d374bf023fedfdeac3f3f6a1d65ce79aab0157f8a35f5d2e3735cafdcbb` |
| `knowledge/scripts/start-rule-agent-probe.ps1` | `43bd174891900885aaa0011a996c8c142a48e12b87e9a438e9fc8618d35e936f` |

**与 `P01_CLOSEOUT_07` 的请求级差异只有一项：宿主侧新增连接层观测字段 `connectedMillis`（含一次有界 TCP 探测）。
模型、端点、协议版本、预算常量、问题、schema 形状全部未改**——符合“不同时改模型、协议和预算”。

## 二、首决策请求重建与核对（B2）

用例：`RuleAgentProviderTest.firstDecisionPayloadReproducesRecordedShape`（入口见该条测试名与测试文件指纹）。

| 核对项 | 结果 |
| --- | --- |
| 实际请求体 | UTF-8 长度**恰为 4557 字节**，等于 t05-34 记录的 `requestBytes` |
| 端点 | 北京 DashScope `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| 模型 | `qwen3.8-flash`，`stream=false`、`enable_thinking=false`、`max_completion_tokens=1024` |
| 提示 | `SYSTEM` 文本块与源码逐字一致（按 Java 文本块规则归一化后比对） |
| schema | `json_schema` 严格模式、`oneOf` **单分支**（仅 `SEARCH_REVIEWED_RULES`）、**无 `enum`** |
| 状态字段 | `executionState.allowedActions=["SEARCH_REVIEWED_RULES"]`、候选为空、`reservedReviewMillis=20000`、`reservedPublicationMillis=5000`；`candidateSetId=null`；`currentCandidates/observations/facts` 为空；`remainingBudget.modelAttemptsIncludingThisCall=4` |
| 畸形 | **未发现**。载荷与 t05-34 记录一致，故“先建失败检查再修复”对载荷无对象 |

结论：t05-34 的 20.031s 无响应**不能归因于客户端请求畸形**——这一条从“未知”变为“已排除”。

## 三、零调用回执（三件，均 `providerCalls=0`、`databaseOperations=0`）

| 操作 | 结果 |
| --- | --- |
| `--PrepareProbe` | `PROBE_PACKAGE_SHA256=4da0c6cb069948c5214c4226055918b0f13980e9833f1f1dc72f38e51385f2ee` |
| `--CheckPackage` | `{"passed":true,"mode":"PACKAGE_CHECK_ONLY","providerCalls":0,"databaseOperations":0,...}` → `check-package.json` |
| `--CheckEnvironment` | `{"mode":"ONLINE","endpoint":"https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","model":"qwen3.8-flash","maximumProviderCalls":5,"timeoutMillis":20000,"runTimeoutMillis":110000,"budgetRevision":"T05_CLOSEOUT_BUDGET_04","providerCalls":0,"databaseOperations":0,"builds":0}` → `environment.json` |

**偏差登记（必须如实记录）**：本会话 PowerShell 为 5.1（Desktop），本机 `pwsh` 7 不可用（注册表、常见安装路径、WindowsApps 均已查无），
故启动器 `knowledge/scripts/start-rule-agent-probe.ps1`（`#requires -Version 7.0`）**无法在本会话直接运行**。
三件零调用核对改用**与启动器完全相同的 java 命令行**直呼探针主类
（`-Dloader.main=…RuleAgentOnlineProbeHost -cp <jar> org.springframework.boot.loader.launch.PropertiesLauncher --<操作>`，`ZHKU_RULE_AGENT_ROOT` 指向本 worktree）；
宿主自身的全部守卫（包指纹、批次、原库拒绝、回放键拒绝、证据目录边界）仍在 java 进程内执行。启动器脚本已同步更新为新批次，
真实外发时须在具备 pwsh 7 的终端运行。

另一个实测坑：以**相对** jar 路径调用时，PowerShell 工具的工作目录是主工作区 `D:/alldesk/Projects/ZhkuAgent`，
会静默跑到主工作区的**旧 jar**（表现为旧批次错误文本）。改用**绝对 jar 路径**后通过。零调用核对的 jar 身份以本页表格为准。

## 四、本地回归

`mvnw.cmd -o -pl knowledge -am -Dtest='RuleAgentContractTest,RuleAgentEvidenceToolsTest,RuleAgentOnlineFactoryTest,RuleAgentReviewPayloadBudgetTest,RuleAgentRunnerTest,RuleAgentTaskStoreTest,RulePublicationGateTest,RuleAgentProviderTest' -Dsurefire.failIfNoSpecifiedTests=false -Dzhku.rulecheck.evidenceDir=<本目录>/regression-unit test`

**155 项全绿，0 失败 0 错误**（基线 151；本时段新增 4：B2 载荷重建 1、B1 探测 2、诊断样例 1）。

> 注意：该回归**必须**传 `-Dzhku.rulecheck.evidenceDir=<绝对路径>`，否则 5 个写证据的用例会 `Path.of(null)` 抛 NPE，
> 表现为“回归失败”实为用法缺失。测试写出的产物见 `regression-unit/`。

## 五、本时段材料

- `AGENT-TRAJECTORY.md`：动态 Agent 轨迹（模拟／真实局部／完整在线成功分开标记）
- `VERIFICATION-MECHANISM.md`：核验机制（确定性检查／模型语义判断／程序发布约束）
- `VERIFICATION-SCOPE.md`：验证范围与计数（六类分开统计、未知项清单）
- `CALL-DIAGNOSTICS.md`：可验证调用诊断（阶段字段、未知规则、本地假服务覆盖矩阵）
- `REQUEST-APPLICATION.md`：新请求包申请材料（须另行明确批准）
- `regression-unit/provider-diagnostic-samples.json`：五类情形脱敏诊断样例
- `check-package.json`、`environment.json`：零调用回执

> `regression-unit/` 内的其余文件（`p01-request-package.json`、`decision-request-budget.json`、`actual-factory-continuation.json`）
> 是**测试写出的回归副产物**，非权威件；**权威请求包**只有本目录根下的 `request-package.json`（指纹 `4da0c6cb…85f2ee`）。
> 目录中所有证据均由本次回归实际写出；`.gitattributes` 对 `docs/evidence/**` 与 `knowledge/src/**` 保护字节原文，避免行尾转换破坏指纹。

## 六、状态

**P01 未通过（首请求 20.031s 无响应），P02 未执行，T05 未完成；工程成果已冻结，真实在线验收未通过。**
本时段所有结论均为**本地/宿主侧**证据，不含任何在线行为结论；完整在线成功仍缺（见 `AGENT-TRAJECTORY.md`）。
