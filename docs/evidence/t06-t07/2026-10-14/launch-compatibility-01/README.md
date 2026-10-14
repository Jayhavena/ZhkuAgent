# T06 启动装配兼容性核查：通过（工程局部），既有HTTP测试仍记"尚未完整验证"

## 身份

| 项 | 值 |
| --- | --- |
| 基线提交 | `88e57d68799d3a5996bb84346090a84040a88387` |
| 分支 / worktree | `codex/t06-controlled-launch` ／ `C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent` |
| 上一检查点 | `75274a1`（T06 工程局部检查点，待统一集成核验） |
| 共享合同 | v3，本轮**未改**；T05 核心实现、审核指纹、事实、提示、守卫、发布规则均未触碰 |
| 执行模式 | 非 Web 上下文装配检查；**数据库操作 0、供应商调用 0、密钥读取 0** |

## 本轮唯一结果：三个入口的开关／bean 依赖已明确，且失败路径显式

| 入口 | 需要的开关 | 需要的 bean | 实测结论 |
| --- | --- | --- | --- |
| 原入口（既有） | `zhku.rule-agent-http.enabled=true` | 宿主提供 `RuleAgentTaskStore.Factory`、`RuleAgentTaskToolsRegistry`、`RuleCheckOriginalEvidenceService`、`PreviewIdentityRepository` | 装配正常；**不受本轮改动影响**（在线 bean 根本不存在） |
| SIMULATED 入口 | 上述 + `zhku.rule-agent-launch.enabled=true` + `executor=SIMULATED` | 必须由宿主提供 `ruleAgentSimulatedExecutor`（Factory） | 装配正常；模拟模式下**不创建在线工厂** |
| ONLINE 入口 | 上述 + `zhku.rule-agent-launch.enabled=true` + `executor=ONLINE` + `zhku.rule-agent-http.online-enabled=true` | 真实供应商 `Options`（Settings + 密钥 Supplier + 审计 + 额度）；索引由在线配置自建 | 装配正常；密钥与审计在装配期**未被调用**（见下） |

失败路径（均实测为明确失败，而非静默降级）：

| 组合 | 实际行为 |
| --- | --- |
| 缺 `executor` 值 / 未知取值（含 `simulated` 小写） | 上下文刷新期失败，提示需显式声明取值 |
| `SIMULATED` + `online-enabled=true` | 失败：模拟不得与在线装配共存 |
| `SIMULATED` 但缺 `ruleAgentSimulatedExecutor` | 失败：必须由宿主显式提供该 bean |
| `ONLINE` 但缺 `online-enabled=true` | 失败：提示需同时开启在线开关并提供真实装配 |
| 重复执行工厂（宿主 Factory + 在线工厂） | 失败：`同时存在多个执行工厂 …；只允许一个` |
| 只开 `launch.enabled`、缺 `http.enabled` | 受控入口整体不生效：无 TaskStore、无在线工厂、不额外贡献任何工厂 |
| 只开 `http.enabled`、不给 launch 开关 | TaskStore 因缺 Factory 而明确失败 |

## 关键发现：`autowireCandidate=false` 会阻断构造器注入（本轮实测）

上一检查点为了让在线工厂"不被按类顺手注入"，给它加了 `autowireCandidate=false`。
本轮实测证明**该标记同时取消构造器参数注入**：ONLINE 分支连自己需要的 `RuleAgentOnlineFactory`
都注入不到，装配期即失败（`No qualifying bean of type 'RuleAgentOnlineFactory'`）。

处置：**撤回该标记**，改为在准入校验里显式做"执行工厂唯一性"检查。
- `RuleAgentOnlineHttpConfiguration.java` 最终内容**与基线 `88e57d6` 完全一致**（`git diff 88e57d6` 为空），
  既有行为零风险。
- "重复工厂必须失败"的语义由受控入口的显式校验承担，不再依赖 `@Primary` 静默取胜。

## 命令与结果

```
./mvnw.cmd -pl knowledge -DskipTests test-compile
./mvnw.cmd -pl knowledge '-Dtest=RuleAgentLaunchCompatibilityTest,RuleAgentControlledLaunchTest' -DfailIfNoTests=false test
./mvnw.cmd -pl knowledge '-Dtest=RuleAgentTaskToolsRegistryTest,RuleAgentReviewedToolsConfigurationTest,RuleAgentTaskHttpTest,RuleAgentLaunchCompatibilityTest' -DfailIfNoTests=false test
```

| 测试类 | 结果 |
| --- | --- |
| `RuleAgentLaunchCompatibilityTest`（本轮新增） | 8 run / 0 fail / 0 error |
| `RuleAgentControlledLaunchTest` | 6 run / 0 fail / 0 error |
| `RuleAgentTaskToolsRegistryTest` | 9 run / 0 fail / 0 error |
| `RuleAgentReviewedToolsConfigurationTest` | 3 run / 0 fail / 0 error / 1 skipped |
| `RuleAgentTaskHttpTest` | **1 error —— 尚未完整验证，未关闭** |

### "零读取／零外发"的证据构造

在线装配使用的密钥 Supplier 与调用审计 Consumer 都被实现为"一旦被调用即抛错"。
上下文能成功启动，即证明装配期**没有读取密钥、没有产生调用审计**；再配合
`remainingCalls()==额度初始值`（未消耗）、`activeNodes`／`activeJobs()==0`、`resourcesClosed()==true`，
构成可复核的"零外发"证据。这是**装配期**证据，不等于在线可用性验证。

## 既有HTTP测试：仍为错误，只记"尚未完整验证"

按要求**未修改**该测试，也未修改浏览器宿主。保留端口冲突证据：

- 本轮实测抓到两次不同端口：`Port 16578 was already in use`、`Port 50265 was already in use`。
- 根因为本机注入的环境变量 **`SERVER__PORT`**（被 Spring 宽松绑定当作 `server.port`），
  优先级高于 `setDefaultProperties`，因此测试里写的 `server.port=0` 无效；
  该值**在本次会话内由 16578 变为 50265**，占用方均为 WorkBuddy 自身进程。
- 因该变量动态变化，冲突是间歇性的 → 不能据"某些轮次未复现"关闭回归问题。
- 该测试未加载本轮新增／修改类仍是事实，但按用户要求**不足以据此关闭**；状态维持"尚未完整验证"，
  由用户协调统一固定端口后重跑。

## 未验证范围

- 专用空临时库上的真实身份仓储与资料安装：**本时段按约定未进入**，未执行。
- 在线分支真机启动、真实密钥读取、真实供应商调用：**0 次**。
- HTTP／浏览器端到端（含澄清续答、取消、迟到响应、资源退出）：本轮未跑；既有测试当前因环境失败。
- 普通用户终端（PowerShell 5.1／GBK 边界）与中文编码：未验证。
- T05 P01：仍未通过，费用与 usage 未知，未复用其关闭额度。

## 资源退出证据

- 测试全部使用非 Web 上下文，未绑定任何端口；运行结束后 `tasklist` 查 `java.exe`：**无残留进程**。
- 未创建容器、未启动数据库、未写入任何仓库外资源。

## 提交

| 项 | 值 |
| --- | --- |
| 提交 SHA | `见返回回执`（提交后写入） |
| 范围 | `RuleAgentControlledLaunchConfiguration.java`、`RuleAgentControlledLaunchTest.java`、`RuleAgentLaunchCompatibilityTest.java`、本 README |
| 本地操作 | 仅本地提交；不 push、不 PR、不标签、不部署、不改写历史、不合并整条分支 |
