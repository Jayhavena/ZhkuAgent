# T06 受控HTTP启动入口：工程局部通过，真实身份／资料安装与在线未验证

## 身份与范围

| 项 | 值 |
| --- | --- |
| 基线提交 | `88e57d68799d3a5996bb84346090a84040a88387` |
| 分支 | `codex/t06-controlled-launch` |
| worktree 路径 | `C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent` |
| 共享合同 | v3（`docs/design/rule-agent-contract-v3.md`），本轮未改 |
| 执行模式 | 显式模拟执行器；真实供应商调用 **0**，数据库操作 **0** |
| T05 状态 | 未通过（58e1ef7 / P01 首请求 20.031s 超时）；未复用其关闭额度 |

前置材料按任务清单读取：`docs/evidence/t06-t07/2026-10-14/reviewed-tools-01/README.md` 与 `external-assets.json`
在本基线内；`docs/evidence/rule-agent-provider/2026-10-14/t05-34-p01-closeout/README.md` 与 `T05-CLOSEOUT.md`
只存在于 58e1ef7 线（见"分支关系缺口"），本次以只读方式从主线工作目录读取，未复制、未改写。

## 已有能力 vs 实际缺口

已有（本轮未重做）：

- 异步创建／查询／续答／取消、可信身份与归属、幂等、版本与并发控制（`RuleAgentTaskStore`、`RuleAgentTaskController`）。
- `RuleAgentTaskToolsRegistry`、`RuleAgentOnlineHttpConfiguration`、`RuleAgentReviewedToolsConfiguration`。
- 只读外部资料的资格核验与原文定位（`Reviewed2025ControlledCatalogue`、`RuleCheckOriginalEvidenceService`）。
- HTTP 生命周期由既有 `RuleAgentTaskHttpTest` 以测试替身覆盖。

实际缺口（本轮只补第一项）：

1. 只有一个"开关 + 缺配置就启动失败"的 `RuleAgentHttpConfiguration`，**没有显式声明执行模式、也没有把在线工厂暴露成 TaskStore 需要的工厂**；
   谁提供 `RuleAgentTaskStore.Factory`、什么模式在跑，都只存在于各测试类的自定义 App 里。
2. 真实临时库身份安装与资料安装仍未实现（本轮未做）。
3. 统一受控启动脚本仍未实现（本轮未做）。

## 本轮实际改动

| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/web/RuleAgentControlledLaunchConfiguration.java` | 新增 | 受控启动入口：双重显式开关、执行模式无默认值、模拟与在线互斥、模拟执行器必须由宿主显式提供 |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/web/RuleAgentOnlineHttpConfiguration.java` | 修改 | 在线工厂 bean 标记 `autowireCandidate=false`，防止"按类顺手注入"绕过准入模式校验 |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentControlledLaunchTest.java` | 新增 | 6 项：默认关闭、缺省/未知模式拒绝、模拟与在线互斥、模拟缺宿主执行器拒绝、在线缺装配拒绝、真实启动 + HTTP 生命周期 |

设计要点（为什么这样做）：

- **执行模式没有默认值**：缺值或未知取值一律在上下文刷新期失败，避免"配置写错就悄悄用替身"。
- **模拟执行器不落在本模块**：源码里没有模拟实现，模拟模式必须由宿主显式提供 `ruleAgentSimulatedExecutor`；
  因此模拟不可能被带进正式在线装配，也不可能成为供应商超时的自动回退。
- **装配通过 ≠ 外发许可**：在线分支只复用既有工厂，不构造凭证、不复用任何已关闭额度。

## 验收方式与证据

命令（worktree 根，限定模块）：

```
./mvnw.cmd -pl knowledge -DskipTests test-compile
./mvnw.cmd -pl knowledge '-Dtest=RuleAgentControlledLaunchTest,RuleAgentTaskToolsRegistryTest,RuleAgentReviewedToolsConfigurationTest,RuleAgentTaskHttpTest' -DfailIfNoTests=false test
```

| 测试类 | 结果 |
| --- | --- |
| `RuleAgentControlledLaunchTest` | 6 run / 0 fail / 0 error |
| `RuleAgentTaskToolsRegistryTest` | 9 run / 0 fail / 0 error |
| `RuleAgentReviewedToolsConfigurationTest` | 3 run / 0 fail / 0 error / 1 skipped（与既有记录一致） |
| `RuleAgentTaskHttpTest` | 1 error —— **环境导致，非本轮改动**（见下） |

`RuleAgentControlledLaunchTest` 覆盖：默认关闭（无 `zhku.rule-agent-launch.enabled` 时不产生任何执行工厂）、
缺省/未知模式拒绝、`SIMULATED` 与 `online-enabled` 互斥、模拟缺宿主执行器拒绝、在线缺真实装配拒绝、
以及**真实 Spring 上下文启动**后经 HTTP 完成 401→登录→创建 202→幂等重复→同键异载荷 409→运行→快照 RESOLVED→
原文 200→原文资格变化 409→身份切换后 404。

创建／快照、续答、并发、版本冲突、取消、失败与 HTTP 故障分离等其余生命周期项，仍由既有
`RuleAgentTaskHttpTest` 覆盖；本测试不重复实现，也不冒充已覆盖。

### 环境发现（与本轮改动无关，但会误导为"代码回归"）

本机环境存在 `SERVER__PORT=16578`（WorkBuddy 注入，被 Spring 宽松绑定解析为 `server.port`），
其优先级高于 `setDefaultProperties`，与 `WorkBuddy.exe` 已占用的 127.0.0.1:16578 冲突。

- 既有 `RuleAgentTaskHttpTest` 因此报 `Port 16578 was already in use`；该测试未 import 本轮任何新增／修改类，
  且 `online-enabled` 未开启，本轮的 `autowireCandidate` 改动不在其激活路径上，故与本轮改动无关。
- 本测试通过显式设置 **系统属性** `server.port=0`（优先级高于环境变量）规避，并在 `finally` 中还原。

建议（不在本轮文件归属内，未实施）：由用户协调在 `RuleAgentTaskHttpTest`/验收宿主中一并固定随机端口。

## 未验证范围（明确标未验证）

- 真实临时库身份仓储安装与资料安装：未执行。
- 在线分支的真实启动：未执行（缺真实供应商装配即应拒绝，本轮只验证了"会拒绝"）。
- 普通用户终端（PowerShell 5.1 / GBK 边界）启动方式：本轮无启动脚本产出，**未验证**。
- 中文编码端到端：未验证。
- 真实浏览器／在线链路：未执行。
- T05 P01：仍未通过，费用与 usage 未知；本轮零供应商调用，不复用其关闭额度。

## 分支关系缺口（需用户知悉）

`88e57d6`（本基线）与 `58e1ef7`（T05 冻结）**互不为祖先**，共同祖先是 `5ee26d89`：

- T05 收口证据（`docs/evidence/rule-agent-provider/2026-10-14/**`）只存在于 `58e1ef7` 线；
- T06/T07 证据（`docs/evidence/t06-t07/**`）只存在于 `88e57d6` 线。

因此"T05 冻结 58e1ef7"与"T06 基线 88e57d6"之间的证据不互通，读取需跨工作目录只读访问。未做任何合并。

## 合同

未变更。无权威定义改动；`RuleAgentContract`、审核指纹、事实、提示、主题守卫、发布规则均未触碰。

## 提交

| 项 | 值 |
| --- | --- |
| 提交 SHA | `75274a1e3c190087c2e01e10ede4b1d67bce3d78` |
| 分支 | `codex/t06-controlled-launch` |
| 暂存清单 | 上述 4 个路径，无其他改动、无构建产物、无凭证 |
| 本地操作 | 仅本地提交；未 push、未 PR、未标签、未部署、未改写历史 |

## 下一项

1. 专用空临时库上的真实身份仓储与资料安装（需专用库、独立命名容器／端口）。
2. 统一受控启动脚本（普通用户终端 + 中文编码验证）。
3. 在线分支真机验收（需单独外发授权，不在本轮范围）。
