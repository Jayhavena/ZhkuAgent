# 两窗口真实浏览器验收（用户明确授权 B：自建具可证退出的宿主）

2026-10-16 22:16–22:50。用户明确授权方案 B：「用 T06 的链装配自建具可证退出的宿主，并按其退出协议核销」。
本轮**只做验收**，未改共享合同、后端、宿主、`vite.config.js` 或任何 T07 源码。

## 1. 版本身份（为什么这就是"统一版本"）

| 项 | 值 |
| --- | --- |
| 共同基线 | `88e57d68799d3a5996bb84346090a84040a88387` |
| T06 侧 | `codex/t06-controlled-launch` @ `a03af69` |
| T07 侧 | `codex/t07-student-entry` @ `de96fa4` |
| 合并干跑树 | `0fd984e44552c9597b61bb07eb7528880e5f0092`（冲突 0，文件交集 0） |
| 物化脚手架提交 | `50f812212cd5fb613d9de423400320acf9535c1a`（本地，非主线集成，见下） |

**blob 等价证明**：合并树中 T06 的 6 个非文档文件、T07 的 9 个非文档文件与各自分支**逐一同 OID**（6/6、9/9）。
因此「后端＝T06 的 Java 树、前端＝T07 的构建产物」在字节级等同于合并结果。

> **执行方式说明（务实取舍）**：统一 worktree 两次创建失败（一次被打断、一次 3m58s 后索引重置失败并回滚），
> 遂不重复第三次。后端按上面等价证明**在 T06 的 worktree 内运行**（会写该 worktree 的 `target/`，未改其源码/暂存区），
> 前端用 T07 worktree 已构建的 `dist/`。脚手架的 `50f8122` 只用于记录"当时认定的统一身份"。

## 2. 宿主与退出协议

宿主＝既有 `RuleAgentTaskBrowserHttpTest`（**未修改**），模式：显式租约 `-Dzhku.t07.browserLease=true`，
`env -u SERVER__PORT`（WorkBuddy 注入 `SERVER__PORT` 会覆盖 `server.port`），临时库与真实资料**均不涉及**。

| 租约 | 证据目录 | 结果 |
| --- | --- | --- |
| 01 | `host-lease-01/` | **未在 480s 内收到 STOP → 测试断言失败 `MAVEN_EXIT=1`**；宿主按设计**拒绝冒称成功**（这本身验证了退出协议）。此轮各次浏览器尝试的失败轨迹见 §4。 |
| 02 | `host-lease-02/` | 显式写 `STOP` → `browser-host-exit.json = {"backendListenerExited":true,"workerExited":true,"activeJobs":0,"providerCalls":0,"databaseCalls":0}`，**`MAVEN_EXIT=0`** |

`host-lease-02/browser-ready.json`：`backendPort 57911`、`schemaVersion 3`、`executionMode TEST_SIMULATION_NO_PROVIDER`、
`providerCalls 0`、`databaseCalls 0`、`sourceSha 50f8122…`。

同源脚手架：`browser-attach-proxy.mjs`（自有端口 127.0.0.1:8161 → 后端 127.0.0.1:57911，静态服务 T07 的 `dist/`）。
理由：会话 Cookie 的 path 是 `/api/zhku/controlled-preview/v2` 且客户端用 `credentials:'same-origin'`，页面必须与 `/api` 同源。

## 3. 验收结果（`two-window.json`，HARNESS_EXIT=0）

| 断言 | 结果 | 含义 |
| --- | --- | --- |
| `aHadPublishedResult` | true | A 窗口提交合成问题后取得**已发布结果**（`#decision-title` 出现） |
| `otherWindowRevokedDisplay` | true | B 退出后 **A 的已发布结果被清空**（`#decision-title` 计数 0） |
| `revocationMessageShown` | true | A 显示"登录状态已在其他窗口变更，请重新登录。" |
| `lateResponseDidNotRevive` | true | 观察窗口 5s 内**迟到响应未复活**旧结果 |
| `cannotSubmitWithoutConfirmedIdentity` | true | 未获后端确认身份前 A **不能再次提交**（查证输入区不存在） |
| `resumesOnlyAfterBackendConfirmation` | true | A 经**后端确认的新身份**后才能继续查证 |
| `browserExitVerified` | true | 本次拉起的浏览器进程在关闭后全部消失（按归属特征核对） |

步骤轨迹：A 进入查证台 → B 进入查证台（共享同源 Cookie，**不出现登录表单**）→ A 取得已发布结果 → B 退出登录 → A 重新认证后可继续。

## 4. 失败轨迹（可复用）

1. **maven 属性名写错**：`-DfailIfNoSpecifiedTests` 无效，正确是 `-Dsurefire.failIfNoSpecifiedTests`（否则 `-am` 下 agent-runtime 无匹配测试即 BUILD FAILURE）。
2. **代理路径前缀比较被 Windows 分隔符搞坏**：`dist` 是 `/`、`path.join` 产出 `\` → 资产回落 index.html、以 `text/html` 返回，浏览器拒载模块脚本（报 MIME 错）。改用 `path.resolve` 统一。
3. **harness 假设错了（产品行为是对的）**：同源 Cookie 会话共享，第二个窗口**本来就是已登录**，不会出现登录表单 → 登录必须**幂等**（有 `#rule-question` 就直接用，只有真未登录才走表单）。
4. **多标签可见性**：同一上下文里后开的标签页成为前台，另一个进入后台受渲染限制 → 每次操作前 `bringToFront()`。
5. **`playwright-core` 1.63 没有 `browser.process()`** → 退出核对改为进程集合。
6. **退出核对误判**：最初按「运行期间新出现的任意 msedge」判定 → 用户自己的 Edge 波动被误判为泄漏（实测两个 PID 全带 `--lang=zh-CN`、无 headless/playwright 特征）。改为按**归属特征**（`headless|playwright`）判定后为 true。

## 5. 资源退出核对

- 端口：8161（脚手架）、57911（宿主）、49932（上一租约后端）**全部 000 已关闭**。
- 进程：`java.exe` 2 个，按命令行核对**均为 IDE 语言服务器**（VS Code / Qoder 的 redhat.java）；含宿主特征（surefire/knowledge/mvnw/BrowserHttp）的进程数 **0**。
- 浏览器：带 `headless|playwright` 特征的 `msedge` 进程数 **0**；用户自有 Edge 未被触碰（全程不按进程名批量终止）。
- 未使用容器与数据库（`databaseCalls 0`）。

## 6. 标签与不可表述

- 身份为夹具测试账号 `student-a`；身份仓储与目录均为**合成/替身**（Mockito），**不是**真实临时库与真实审核资料。
- **`executionMode = TEST_SIMULATION_NO_PROVIDER`**（`browser-ready.json` 权威标注），`providerCalls 0`。
  注意：合成的快照内部会声明 `Mode.ONLINE`，那是夹具产物，**不得据此表述在线**。
- ✅ 可以说：统一版本上、真实浏览器、两个同源窗口，**跨窗口撤销展示、迟到响应不复活、新身份须后端确认**已实测通过；宿主按显式 STOP 退出并留下回执。
- ❌ 不得说："真实在线整链通过""真实身份/资料已验证""T06/T07 已最终通过"（T05 未通过；T06/T07 仍是工程局部）。
