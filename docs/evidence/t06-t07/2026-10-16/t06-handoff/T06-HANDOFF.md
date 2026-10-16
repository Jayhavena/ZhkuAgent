# T06 交接说明（受控启动入口 · 身份仓储 · 资料装配）

> 生成时间 2026-10-16。供其他 Agent / 集成方消费。**全部为本地提交，未 push、未 PR、未标签、未部署、未合并。**

## 0. 一句话状态

**T06 工程局部通过，未最终通过。** 受控启动入口、装配兼容性、真实临时库资料安装与真实身份仓储已验证；
**SIMULATED 模式下的真实身份／资料 HTTP 整链未做**，在线链路未做；T05 仍未通过。

## 1. 分支与提交（限定集成范围）

| 项 | 值 |
| --- | --- |
| 分支 | `codex/t06-controlled-launch` |
| worktree | `C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent` |
| 基线 | `88e57d68799d3a5996bb84346090a84040a88387` |
| HEAD | `a1f5dc0be969cb3e520d3ce67f15ee0052af55b6`（工作区干净） |
| 整合计 | 7 commits，9 files，**+1303 行**，0 删除 |

提交链（**必须成套取用**）：

| SHA | 内容 |
| --- | --- |
| `75274a1` | 新增受控 HTTP 启动入口 `RuleAgentControlledLaunchConfiguration` + 6 项装配测试 |
| `fbc8624` | 该轮证据回执 |
| `7d8b5e4` | 装配兼容性修正（**撤回 `autowireCandidate=false`**，改为显式工厂唯一性校验）+ 8 项兼容性测试 |
| `768ff1a` | 该轮证据回执 |
| `3b7f6bf` | 专用临时库验收测试（资料安装通过、身份登录当时失败） |
| `9d254f1` | 修正口令夹具并复验真实身份仓储**通过** |
| `a1f5dc0` | 任务管理与身份边界说明（纯文档） |

> ⚠️ **不要只取 `75274a1`**：它给在线工厂加了 `autowireCandidate=false`，会让 ONLINE 入口无法装配；
> 必须带上 `7d8b5e4`（该标记已撤回，`RuleAgentOnlineHttpConfiguration.java` 最终与基线**零差异**）。

## 2. 已完成（含验证层级）

| 能力 | 证据 | 层级 |
| --- | --- | --- |
| 显式、默认关闭的受控启动入口；执行模式 `SIMULATED`/`ONLINE` 无默认值，缺值/未知值启动即失败；模拟执行器必须由宿主显式提供（本模块不含模拟实现）；模拟与在线装配互斥；执行工厂唯一性显式校验 | `RuleAgentControlledLaunchTest` 6/6 | **非 Web 装配检查**（零端口、零库、零外发） |
| 三入口开关/bean 依赖明确；失败矩阵明确；ONLINE 装配期零密钥读取零外发（密钥 Supplier 与审计做成"一调用即抛错"，上下文仍能启动） | `RuleAgentLaunchCompatibilityTest` 8/8 | **非 Web 装配检查** |
| 专用空临时库：迁移（public 26 表）+ 已审核资料安装（revision=`2025.9.2`、片段 `content_checksum` 64 位十六进制）；批准指纹校验确实生效（worktree 作资料根被正确拒绝） | `RuleAgentReviewedIdentityTempDbTest` 1/1 通过（11.28s） | **真实数据库** |
| 真实身份仓储：合规正确口令返回主体（`userId` 一致、`role=USER`、`authRevision=0`）；合规但内容错误被拒；合规未知账号被拒；传入 `char[]` 被清空；`status()`→`closed/inFlight=0/liveThreads=0` | 同上 | **真实数据库** |
| 口令边界（不合格输入被拒绝、不访问数据库即清空） | 复用既有 `PreviewIdentityRepositoryIntegrationTest`（真实库 8/8）、`PreviewIdentityBudgetTest`（2/2，DB-less） | **真实数据库 / 单元** |
| 任务与身份边界说明（task/turn/run、条件与最小历史、主体与会话归属、三种"版本"、内存态边界、退出三层判定、状态表） | `TASK-IDENTITY-BOUNDARY.md` | **文档（逐条对应源码）** |

回归：`RuleAgentTaskToolsRegistryTest` 9/9；`RuleAgentReviewedToolsConfigurationTest` 3 项（**1 项跳过**，不得记为全执行）。

## 3. 未完成（明确清单）

1. **SIMULATED 工程模式下的真实身份／资料 HTTP 整链** —— 创建/快照、必要澄清同任务续答、重复与并发、版本冲突、
   取消、原文读取与资格撤回、身份失效、绑定端口不被 `SERVER__PORT` 覆盖。**这是唯一指定的下一项。**
2. **既有 `RuleAgentTaskHttpTest` 仍处于"尚未完整验证"** —— 失败原因是本机 `SERVER__PORT` 动态注入导致端口冲突
   （见过 16578 / 50265），**不是代码回归；该测试未修改**，端口固定由用户协调。
3. 统一受控启动脚本（含普通用户终端 / PowerShell 5.1 GBK 边界与中文编码）——未实现。
4. 在线分支真机（真实供应商）——未做，且**不属于 T06 授权范围**；T05 未通过、旧额度已关闭，不得复用。
5. 浏览器整体验收、多实例/重启运行时行为——未做（后者源码明确不支持）。

## 4. 三层分开记账（不可互相替代）

| 层 | 状态 |
| --- | --- |
| 真实数据库身份 + 专用临时库资料安装 | **通过**（本轮范围） |
| 模拟执行器 HTTP（SIMULATED 整链） | **未执行** |
| 真正在线链路 | **未执行**（T05 未通过，额度已关闭） |

## 5. 文件归属与边界（集成方须知）

**本 T06 改动只在这些路径**：

```
knowledge/src/main/java/com/zhku/knowledge/ruleagent/web/RuleAgentControlledLaunchConfiguration.java   （新增）
knowledge/src/main/java/com/zhku/knowledge/ruleagent/web/RuleAgentOnlineHttpConfiguration.java         （最终与基线零差异）
knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentControlledLaunchTest.java            （新增）
knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentLaunchCompatibilityTest.java         （新增）
knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentReviewedIdentityTempDbTest.java      （新增）
docs/evidence/t06-t07/2026-10-14/{controlled-launch-01,launch-compatibility-01,tempdb-identity-assets-01,tempdb-identity-fixture-fix-01,task-identity-boundary-01}/
```

**未触碰（继续只读）**：`RuleAgentTaskHttpTest`、`RuleAgentTaskBrowserHttpTest`、前端、共享合同与样例、
T05 核心类型（`RuleAgentContract`/`RuleAgentRunner`/`RuleAgentOnlineFactory`/`RuleAgentOnlineProbeHost` 等）、
`scripts/Invoke-ZhkuOwnedProcess.ps1`、`scripts/Import-ZhkuCredentials.ps1`、`knowledge/scripts/verify-rule-check-refactor.ps1`。
**共享合同 v3 未变更**；审核指纹、事实、提示、主题守卫、发布规则均未改。

> 注：`docs/evidence/t06-t07/2026-10-14/` 下另有 `frozen-contract-01`、`nginx-browser-01`、`task-original-01`，
> 属其他 Agent（T05/T07）的证据，**不是 T06 产出**。

## 6. 复用要点与已踩过的坑

| 主题 | 结论 |
| --- | --- |
| 临时库配方 | 库名固定 `zhkuagent_rulecheck_e2e`（组件内硬校验，拒 54329）；镜像 `pgvector/pgvector:0.8.6-pg16-bookworm`（本机已有）；**每轮必须重建**（迁移后不再是空库，空库断言会失败）；先证明空库再迁移 |
| 资料根 | 必须用**已核对只读资料根** `D:/alldesk/Projects/ZhkuAgent`（主工作目录）。用 worktree 当根会被指纹校验正确拒绝（`B03批准指纹变化`） |
| Catalogue 生命周期 | `Reviewed2025ControlledCatalogue` 是 `AutoCloseable`，**`close()` 会卸载本次安装的数据** → 安装是任务级作用域；别把"关闭后查不到"当安装失败 |
| 口令规则 | `PreviewIdentityRepository.validPassword` 要求 **16–64 位、全部可打印 ASCII**；不满足会在查库与 BCrypt **之前**被拒。`authenticate` 会在 `finally` **清空调用方 `char[]`** → 每次调用必须新建数组 |
| 两个不同库名 | `RuleAgentReviewedIdentityTempDbTest` → `zhkuagent_rulecheck_e2e`；`PreviewIdentityRepositoryIntegrationTest`（经 `ArchiveTestDatabase`）→ `zhkuagent_run_archive_e2e`；未配置 `zhku.db.url` 时后者**整体失败（非跳过）** |
| `SERVER__PORT` | WorkBuddy 动态注入（双下划线被 Spring 当 `server.port`），**优先级高于 `setDefaultProperties`**，端口号随会话变化 → 端口冲突是**间歇性**的。规避：测试内 `System.setProperty("server.port","0")` 并在 `finally` 还原 |
| 进程退出 | **"命令返回 / BUILD SUCCESS" ≠ "JVM 退出"**：实测 maven 返回后 Maven JVM 与 surefire 分叉仍存活。核对退出必须按 PID + 命令行；本机常有其他会话并发 `mvn`，**禁止按进程名批量终止** |
| Spring 坑 | `autowireCandidate=false` **同时取消构造器参数注入**（不只是"顺手注入"）。要"只让显式装配生效"，改用显式唯一性校验 |
| 真启动纪律 | `ApplicationContextRunner` 不激活 profile 会整体排除 `@Profile`/`@ConditionalOnProperty` bean → 真机启动才炸。装配类验收必须真启动一次应用 |

## 7. 下一项与停止条件

**唯一下一项**：SIMULATED 工程模式下的真实身份／资料 HTTP 整链。
- 验收至少覆盖：从临时库读身份并登录、错误身份被拒；不同主体/会话不能互访任务与原文；原文可读且资格撤回/正文变化后旧访问被拒（写入仅限本次临时库）；明确区分启动故障／HTTP 故障／模拟业务失败／资料缺失；实际退出监听、线程、连接与本次容器。
- 停止条件：20 分钟一档；到点停止扩展、不循环追加修复；保留实际失败与准确卡点。
- 约束：模拟执行器只能由测试/工程宿主显式提供，**不得进入正式 ONLINE 装配，不得成为供应商故障回退**；真实供应商调用保持 **0**；模拟结果与实际取证分别记录，**不得把合成答案说成在线核验通过**。

## 8. 不可对外表述（重要）

- ❌ "T06/T07 已最终通过" —— T05 未通过，T06 只到工程局部。
- ❌ "HTTP 整链已通 / 在线已验证 / 供应商链路可用"。
- ❌ "任务可跨重启恢复 / 支持多实例" —— 源码明确不支持。
- ❌ 把非 Web 装配检查（8+6 项）算作数据库或在线验收；把"跳过"算作"通过"。
- ✅ 可以说：受控启动准入与失败矩阵已验证；专用临时库上迁移、已审核资料安装、批准指纹校验与真实身份认证（含拒绝路径）已实测通过；任务/身份边界说明与实际源码逐条对应。
