# T07 统一集成材料（student-entry-01）

状态：**T07 工程局部检查点，待统一集成核验**。T05 未通过，故 T07 继续标"工程局部通过，待真实集成"，不进入 T08。

## 1. 提交身份与证据入口

| 项 | 值 |
| --- | --- |
| 分支 | `codex/t07-student-entry` |
| 基线提交 | `88e57d68799d3a5996bb84346090a84040a88387` |
| 实现提交 | `94dc928151efd96f08ff99f1280ee1871b05907a`（树 `8ca021a7517127c28daadaf001bcf8d8e59ad5e9`） |
| 回执提交 | `43e659d202c795e2dc13913456992ece85fe957f`（只含本目录 `commit-receipt.json`） |
| 证据入口 | `docs/evidence/t06-t07/2026-10-14/student-entry-01/` |
| 合同版本 | schemaVersion = 3（未改） |
| 执行模式 | `TEST_SIMULATION_NO_PROVIDER`，真实供应商 0、数据库 0 |

回执提交自身身份只能从 Git 日志与答复查询，不写入自身内容。

## 2. 统一集成只取的明确文件范围

**源码与测试（9 个，均为本会话新增/修改）**

| 文件 | 状态 | SHA256 |
| --- | --- | --- |
| `agent/frontend/src/router/engineering-routes.js` | 新增 | `b2e80d3e1141f18f68ebeba47537be19302dd2b98e1e4d31394ab0e6d3c8c858` |
| `agent/frontend/src/router/index.js` | 修改 | `2a2829c57618e5340f21e1112e2661760db4352fa178f75c87004c6a13461fc5` |
| `agent/frontend/src/state/identity-bus.js` | 新增 | `92cccb17f2dcbb0344ff2e3b058d8404e7561ae591c0e67d73aa9d8f41ec3e15` |
| `agent/frontend/src/state/rule-check-session.js` | 修改 | `543de077d5246f1124205d62fabb67b4cdf666a0ccc0fea796f86d68a48220dd` |
| `agent/frontend/src/views/RuleCheckView.vue` | 修改（仅 +1 行文案） | `b3b30aadd79d7863e7ba84207731aef73caa807ae7a8d256fa4bacf175737c8a` |
| `agent/frontend/tests/entry-routes.test.mjs` | 新增 | `e6cb33ce19d73619eb1245b279c308b60469f7f024a3b9afd5b9b36d43244db1` |
| `agent/frontend/tests/identity-bus.test.mjs` | 新增 | `4f2a52dbecedbd62f7e94b703098a8df80e36d189fdccb2d7260945451547d40` |
| `agent/frontend/tests/student-routes.test.mjs` | 修改 | `a13d899c9b1837630a32533ed6fdab4a8a563815770cac75601b3477c326d82c` |
| `agent/frontend/tests/rule-task-page.test.mjs` | 修改 | `cb7f826bd521a6e3dd0d34b80fc95c916d53a9a86c4449933734adc13bf9d0dd` |

**证据目录（只读参考，可随集成一并带入）**：`baseline.json`、`source-manifest.json`、`commit-receipt.json`、`README.md`、`INTEGRATION-HANDOFF.md`、`STUDENT-FLOW-EVIDENCE-INDEX.md`、`red-entry-routes.log`、`red-identity-bus.log`、`frontend-full.log`、`student-build.log`、`engineering-build.log`、`browser-entry-routes.json`。

**未包含**：`dist/`、`dist-engineering/`、`node_modules/`（构建产物已删除或忽略，均不提交）。

**只读输入未改（验收源码身份）**：`agent/frontend/src/api/rule-agent-contract.js` = `ebbe9bd4…7ecd`；`knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json` = `55c02922…a580`；`knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentContract.java` = `14d52d0a…9b65e`。未改动的 `student-routes.js` = `a93edadd…b22d6`、`controlled-session.js` = `1cf183ad…2855`。未修改共享合同、后端与宿主文件。

## 3. 跨窗口广播的语义边界（只撤销，不授权）

广播**只用于撤销当前展示并要求后端重新确认身份**，不能据广播内容认定新身份或授权：

1. **接收端不读广播内容。** `rule-check-session.js:17` 的订阅回调不接收参数，只调用本地未包装的 `clear('IDENTITY_CHANGED_ELSEWHERE')`——通道里的 `reason` 等载荷被完全忽略，不能携带身份、角色、task 或任何授权字段。
2. **`actor` 只能由后端响应写入。** 全仓库对 `state.actor` 的非空写入点只有 `api/rule-check.js:137`（`GET /auth/me` 的响应经合同校验后写入）与 `api/controlled.js:75`（学分入口的另一会话）。`state/controlled-session.js:13` 只负责置空。广播不经过任何写身份的路径。
3. **撤销后必须重新向后端取凭据。** `clear` 会清空 `actor`、`checked`、`csrf` 并作废在飞请求；此后任务读写同时受 `rule-task-workflow.js:10`（要求 `actor.role === 'USER'`）与 `api/rule-check.js` 的 CSRF/`identityBusy` 门禁约束，必须再由服务端确认身份与新 CSRF 才能继续查证。
4. **失效方向是 fail-closed。** 同源脚本即使伪造一条广播，最多让本窗口退回登录态、重新向后端确认，不会获得任何访问；广播无法提升权限。

## 4. 测试覆盖矩阵（已覆盖 / 未覆盖）

「重复广播」「迟到响应」「组件退出后订阅清理」「BroadcastChannel 不可用」四项现有覆盖如下。

**已覆盖**

| 关注点 | 断言 | 位置 |
| --- | --- | --- |
| 一次本地换代只广播一条 | `channels[0].sent.length === 1` | `identity-bus.test.mjs` 用例 1 |
| 接收端不回声（防重复广播互灌） | `channels[1].sent.length === 0` | 同上 |
| 本无身份时的清理不广播 | 再次 `clear()` 后仍为 `1` | 同上 |
| 另一窗口收到广播即换代 | `actor === null`、`epoch + 1`、`reason === 'IDENTITY_CHANGED_ELSEWHERE'` | 同上 |
| 迟到响应不恢复旧结果（跨窗口触发） | 换代后才 resolve 的旧快照不写入：`snapshot === null`、`ticket === null` | `identity-bus.test.mjs` 用例 2 |
| 迟到响应不恢复旧结果（同窗口路径） | `rule-task-workflow.test.mjs`「old polling success cannot repopulate next question or identity」（覆盖 `reset()` 与 `session.clear()` 两条） | 既有用例，本轮未改 |
| BroadcastChannel 不可用 | `available === false`；`publish()` 不抛；`subscribe()` 返回函数；`close()` 不抛 | `identity-bus.test.mjs` 用例 3 |
| 无通道环境下整页仍可用（间接） | 8 个 SSR 页面用例在 `document` 未定义、无通道时全绿 | `rule-task-page.test.mjs`、`rule-check-page.test.mjs` |

**未覆盖（明确列出，本轮不为补清单追加修复）**

- **重复投递同一广播**：连续两条相同消息是否各触发一次换代，未断言（只测了一次投递）。
- **`dispose()`/`close()` 之后的静默**：未断言 dispose 后广播不再生效，也未断言 close 后 `publish()` 不再投递。生产路径的订阅是**模块级**的（`bindIdentityBus()` 在 `rule-check-session.js` 模块作用域执行），生命周期与页面一致，因此没有组件级订阅需要清理；用例里的 `dispose()` 只用于清理替身。
- **工作流 `dispose()` 后 epoch 变化不再触发 reset**：未断言。
- **"广播不可用于认定身份或授权"**：这是第 3 节的代码结构事实，**没有对应测试**。
- **同源伪造广播注入**：未测（按结构最多 fail-closed 退回登录）。
- **真实两窗口/跨标签页**：未跑，见第 5 节。

## 5. 统一版本上的两窗口验收步骤（待宿主与 T06 就位后执行）

前置：等待验收宿主 Agent 提供稳定提交与退出方式，并等待 T06 提供可登录装配；本轮不启动不稳定租约、不绕过退出卡点。执行时只用测试身份与合成问题，不接真实供应商。

1. 同一浏览器开 A、B 两窗口，同一测试身份登录，均落在学生查证页。
2. A 窗口提交合成问题并取得已发布结果（能看到"当前能确认什么"与相关依据）。
3. B 窗口执行**退出**（或切换身份）。
4. 断言：A 窗口的已发布结果被清空，回到登录态并显示"登录状态已在其他窗口变更，请重新登录。"；`ticket`/`snapshot` 不再保留。
5. 在 A 窗口制造或等待一条**迟到响应**（例如在第 4 步前后发起一次快照查询），断言其成功返回后仍不写入、不复活旧结果。
6. 断言：A 窗口必须经**后端确认的新身份**（重新登录/`/auth/me` 通过）才能继续查证；未确认身份时提交被门禁拒绝。
7. 记录两个窗口的实际页面、接口结果、源码 SHA、合同版本、模式与资源退出，且只操作本会话自有的页面/端口。

## 6. 集成约束

- 只取第 2 节列出的明确提交范围，**不合并整个工作区**。
- **2026-10-16 更新：集成就绪已实测**——T06（`88e57d6..a03af69`，35 文件）与 T07（`88e57d6..de96fa4`，21 文件）**文件交集为 0**，`git merge-tree --write-tree` 干跑 exit 0、冲突 0，合并树 `0fd984e4…`，两侧 blob 均未被对方改写；门禁 ②（宿主稳定提交与退出方式）**仍未交付**，两窗口验收保持待验证。详见同目录 `INTEGRATION-READINESS-20261016.md`。
- 不修改共享合同、后端或宿主文件；如发现合同缺口，按"现有定义 → 具体问题 → 建议增量 → 影响模块 → 对应测试"登记后交用户协调。
- 真实两窗口验收保持**待验证**；T05 未通过前 T07 不宣布最终通过，不进入 T08。

## 7. 页面／交互主张（简历可用口径）

逐项证据索引见同目录 `STUDENT-FLOW-EVIDENCE-INDEX.md`。身份代号：**S-A** = `09b4f07`（T06，早于本轮实现），**S-B** = `94dc928`（本轮）。

### 7.1 现在可以支持（已交付且可指向证据）

| 主张 | 证据身份与类型 |
| --- | --- |
| 学生入口收敛为单一规则查证页，首页与品牌都回查证；`/campus/run`、`/super-agent` 与未知路径一律回 `/rules?entry=rules` | S-B 单测（`entry-routes` / `student-routes`）+ S-B 真实浏览器 5 例 |
| 工程独立模式保留底层实验能力：两个实验入口、回放页、受控布局与登录页均可达且受控入口仍要求身份 | S-B 单测 + S-B 真实浏览器 5 例 |
| 提问走异步受理＋轮询，进度、停止请求受理与最终停止区分表达 | S-B 单测 + S-A 真实浏览器 3 态 |
| 必要澄清每轮至多一项，且以同任务续答（continuationToken + expectedVersion）继续原任务 | S-B 单测 + S-A 真实浏览器 2 态 |
| 部分答案可与资料缺口、处理失败共存，四类缺口（资料缺口／需要补充条件／处理未完成／回放未覆盖）分开标识 | S-B SSR（共享合同样例驱动）+ S-A 真实浏览器 2 态（`partial-answer-and-gap`、`processing-failure-with-supported-partial`） |
| 结果先"能确认什么"、再"还缺什么／下一步"、最后才是相关依据；依据按结果关系过滤 | S-B SSR（含顺序断言）+ S-A 真实浏览器 3 态 |
| 原文默认折叠，只展示关系表所指段落，并按需向服务端核对当前原文 | S-B SSR + S-B 单测 + S-A 真实浏览器 2 态 |
| 同窗口的退出、换身份、迟到响应都不会复活旧结果 | S-B 单测 + S-A 真实浏览器 4 态 |
| 同源多窗口的身份失效会撤销本窗口的展示 | S-B 单测 + S-B SSR（**不含真实两窗口**） |
| 前端只消费后端结果：不自行认定规则结论、任务成功、资料缺失或资源退出 | S-B 源码结构 + S-B 单测/SSR 的消费校验路径 |

### 7.2 仍须等待集成（不得提前声称）

| 主张 | 卡点 |
| --- | --- |
| 真实两窗口：A 有结果 → B 退出/换身份 → A 清旧结果且迟到响应不恢复 → 新身份经后端确认后才能继续查证 | 需宿主 Agent 稳定提交与退出方式 + T06 可登录装配 |
| 真实受控账号身份下的完整学生流程 | 同上；现有浏览器证据用测试身份＋合成资料（模拟后端） |
| 真实审核资料与原文服务下的依据展示与原文核对 | 同上 |
| 正式 nginx 代理下的同款路由与交互 | 需统一版本宿主 |
| 任何在线供应商相关结论 | T05 未通过，本轮 `realProviderAttempts=0` |

### 7.3 禁止表述

- ❌"学生入口已验证有效"／"真实在线整链通过"／"学生构建已彻底排除工程资源"。
- ❌把 176 项回归、两种构建、10 项路由检查**合并**成一条"整链通过"。
- ❌把 `bindIdentityBus()` 的模块级订阅写成"与页面组件同生命周期"，或声称销毁后已静默（**未验证**）。
- ❌把跨窗口广播说成能认定新身份或授权（广播**只撤销展示**，身份必须由后端确认）。
- ❌把 `09b4f07` 的浏览器交互证据算作本轮 `94dc928` 的验收。
