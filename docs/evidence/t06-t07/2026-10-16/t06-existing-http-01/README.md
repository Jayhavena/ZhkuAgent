# T06 既有 HTTP 测试：清除注入端口后通过（接口生命周期与终态矩阵，本轮通过）

> 时间 2026-10-16 19:43。对应交接文档 [T06-HANDOFF.md](../t06-handoff/T06-HANDOFF.md) 第 3 节
> 「既有 `RuleAgentTaskHttpTest` 端口冲突」——**该项本轮解决并通过**；第 3 节其余项未动。
> 全部为本地提交，未 push／PR／标签／部署／合并。

## 0. 一句话状态

既有 `RuleAgentSimulatedChainTempDbHttpTest` 之外的 **`RuleAgentTaskHttpTest` 在非侵入清除注入端口变量后通过**
（**1 项测试，0 失败 0 错误 0 跳过**），归档 **46 条真实 HTTP 交换**。该测试补的是**接口生命周期与终态矩阵**：
身份仓储为 Mockito 替身、执行器为合成分支、**零数据库调用、零供应商调用**——它是 HTTP 契约面证据，
**不是数据库验收，更不是在线链路验收**。

## 1. 端口冲突的根因与非侵入解法

| 项 | 值 |
| --- | --- |
| 冲突变量 | WorkBuddy 宿主注入 `SERVER__PORT`（双下划线），Spring 宽松绑定成 `server.port`，优先级高于测试内 `setDefaultProperties` |
| 注入值 | `13404`，且该端口被 **WorkBuddy.exe 自身（PID 41272）占用**——宿主监听自己注入的端口，属结构性冲突 |
| 解法 | 子进程中 `env -u SERVER__PORT` 清除该变量后运行（**未改测试、未改用户环境、未改守卫**） |
| 结果 | `EXIT=0`，15.321s，BUILD SUCCESS |

`LocalPreviewBindingGuard` 只接受回环地址属**设计约束**，本轮未触碰；受控后端仍按设计只绑回环。

## 2. 分层记账（与 T06 三层口径一致，不得混读）

| 层 | 本轮状态 | 证据 |
| --- | --- | --- |
| 真实 HTTP | ✅ 真实：随机回环端口真实 Tomcat、真实 Spring 上下文、真实 `RuleCheckPreviewSecurityConfiguration` + `RuleCheckAuthController`（CSRF 真实签发/校验）、真实 `RuleAgentTaskStore`（幂等/版本/取消/跨主体隔离）、真实 `RuleAgentTaskToolsRegistry`/`RuleAgentEvidenceTools`/`RuleCheckOriginalEvidenceService` | 46 条归档交换 |
| 身份 | ⚠️ 替身：`PreviewIdentityRepository` 为 Mockito mock（登录链是真的，账号源不是库） | 源码第 47–52 行 |
| 资料 | ⚠️ 合成：`RuleCheckRuleTemplate`/`RetrievedChunk` 为测试内构造（quote `合成条款：仅用于接口验收。`） | 源码第 81–86 行 |
| 决策段 | ⚠️ 模拟：合成分支执行器，`providerCalls: 0` | 归档 JSON `executionMode=TEST_SIMULATION_NO_PROVIDER` |
| 数据库 | **0 调用** | 归档 JSON `databaseCalls: 0` |

与档一/档二（`t06-simulated-chain-01`）的分工：那边是**真实临时库身份 + 真实已审核资料**的主链与代理贯通；
这边是**契约面矩阵**（校验/容量/终态/退出）。两者互补，均未触及在线链路。

## 3. 实际通过项（46 条交换归纳）

| # | 能力 | 实际观察 |
| --- | --- | --- |
| 1 | 匿名访问 | `GET /tasks/capabilities` → **401** |
| 2 | 角色边界 | `MAINTAINER` → **403**（学生端不继承维护者角色） |
| 3 | 能力查询 | 正确身份 → **200** |
| 4 | 错误 Content-Type | form 提交 JSON 端点 → **415** |
| 5 | 载荷校验 | 冒充 `actorId` → 400；重复 JSON 键 → 400 |
| 6 | 容量上限 | 65537 字节 → **413** |
| 7 | CSRF | 缺令牌 `POST /tasks` → **403**，且按 `clientRequestId` 查询 → **404**（未产生任务） |
| 8 | 受理 | 带令牌 → **202** + `Cache-Control: no-store` |
| 9 | 幂等 | 同键重复 → `duplicate=true`，执行次数未增加；同键换问题 → **409** |
| 10 | 跨主体隔离 | 另一账号读任务 / 查请求键 → **404** |
| 11 | ASK 澄清 | 首轮执行后 `WAITING_USER`（带 `continuationToken`） |
| 12 | 版本冲突 | 续答 `expectedVersion` 旧值 → 409；非整数值 → 400；正确值 → **202**；同键重发 → 202；换新键 → 409 |
| 13 | 解决态 | 第二轮执行后 `RESOLVED` |
| 14 | 原文核对 | 跨主体 → 404；随机令牌 → 404；本人 → **200**；原文非当期 → **409** |
| 15 | 终态矩阵 | 部分→RESOLVED(PARTIAL)；缺证→RESOLVED(NONE)；故障/理解未完成/漏答→FAILED（三种 reasonCode，漏答 `modelAttemptCount=5`）；取消→STOPPED（受理后取消，执行前终止，`terminated=false`） |
| 16 | 会话归属 | 登出后重新登录 → 原任务 **404** |
| 17 | 退出 | 上下文关闭后端口不可连接、`activeJobs=0`、执行队列空 |

原始交换（46 条，含方法/路径/状态/实际响应）见 [`http-exchanges.json`](http-exchanges.json)，
构建日志见 [`maven.log`](maven.log)。

## 4. 未验证范围（状态不变）

- 在线分支真机（非 T06 授权范围，未执行）；
- 多实例 / 重启运行时行为；
- 浏览器整体验收；
- 统一受控启动脚本（含 PowerShell 5.1 GBK 边界）；
- 真实库上的取消 / 容量 / 并发（档一/档二已覆盖真实库主链，但取消与容量仅在本合成层验证过）。

## 5. 复现入口

```bash
cd C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent
env -u SERVER__PORT ./mvnw.cmd -pl knowledge -Dtest=RuleAgentTaskHttpTest -DfailIfNoTests=false \
  "-Dzhku.rulecheck.evidenceDir=$PWD/docs/evidence/t06-t07/2026-10-16/t06-existing-http-01" test
```
