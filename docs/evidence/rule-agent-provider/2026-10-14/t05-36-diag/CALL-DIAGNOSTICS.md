# 可验证调用诊断（请求准备→提交→响应头→响应体→异常→退出）

诊断字段来自 `RuleAgentProvider.Diagnostics`（本时段新增 `connectedMillis`）与 `RuleAgentProvider.Trace`。
**规则：连接／TLS 时刻只有真实观测到才记录，测不到一律 `null`（未知）；不得把 `sendAsync`／派发标记或本地假服务接收当成真实供应商已收到。**

## 一、阶段与字段对应

| 阶段 | 字段 | 语义与边界 |
| --- | --- | --- |
| 请求准备 | `requestBytes`、`requestSha256` | 本机构造的 UTF-8 请求体大小与摘要；**尚未发生任何网络行为** |
| 客户端提交 | `dispatchedMillis` | 客户端侧派发标记。**源码注释明示“dispatch 不代表网络发出”**，不能当作“已提交给供应商” |
| 连接 | `connectedMillis` | **本时段新增**：真实调用前的一次**有界 TCP 探测**（含 DNS，超时 3 s，守护线程 + join 上限）。**只到 TCP，不含 TLS 握手**；探测失败只留 `null`，不伪造连通、不改变分类与计数 |
| 响应头 | `headersMillis`、`status`、`requestId` | 只有真正读到响应头才写入；未读到则 `null` |
| 响应体 | `firstBodyMillis`、`bodyCompletedMillis`、`responseBytes` | 首字节与读完时刻；分块到达可区分“有头无体” |
| 异常类型 | `reason`（`ReasonCode`）、`responseReceived` | 失败分类；`responseReceived=false` 表示**未取得响应**，不得写“供应商返回了错误” |
| 实际退出 | `elapsedMillis`、`phase=FINISHED`、额度消耗、`isTerminated()`／`resourcesClosed` | 真实退出时刻与资源状态；`phase` 序列 `BEGIN→(RECEIVED)→FINISHED` |

`null` 一律表示**未知**，不表示“没有”或“零”。`responseBytes=0` 只表示未读到响应体字节。

## 二、本地假服务覆盖矩阵（真实观测值）

产物：`regression-unit/provider-diagnostic-samples.json`（由 `RuleAgentProviderTest.diagnosticSamplesAreSavedForLocalFailureModes` 写出的**脱敏实测量**）。

| 情形 | reasonCode | phase 序列 | 本地假服务调用 | connected | headers | responseBytes | requestId |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 正常响应 | `null` | BEGIN,RECEIVED,FINISHED | 1 | 1 ms | 8 ms | 231 | `local-request-01` |
| 拒绝连接 | `MODEL_CONNECTION_FAILURE` | BEGIN,FINISHED | **0** | `null` | `null` | 0 | `null` |
| 接收后静默 | `MODEL_TIMEOUT` | BEGIN,FINISHED | 1 | **1 ms** | `null` | 0 | `null` |
| 延迟响应（>5 s） | `null` | BEGIN,RECEIVED,FINISHED | 1 | 1 ms | 5219 ms | 231 | `local-request-01` |
| 取消／迟到响应 | `CANCELLED` | BEGIN,FINISHED | 1 | 2 ms | `null` | 0 | `null` |

读法：

- **拒绝连接**：`connectedMillis=null` + 假服务调用计数 0 → 连接未建立、请求未到达处理器；宿主侧仍恰好一次尝试。
- **接收后静默**：`connectedMillis=1ms`（**已建立 TCP**）而 `headersMillis=null` → 把“网络路径已通”与“未取得响应头”分开；原因码 `MODEL_TIMEOUT`，`responseReceived=false`。
- 样例中 `responseModel` 恒为 `null`：本地假响应体未包含 `model` 字段，诊断**如实记 null，不猜**。
- `requestBytes` 各情形不同（决策视图 7491、核验视图 2699），是**测试视图**大小，与 P01 首决策 4557 无关。

## 三、无自动重试／尝试不退还／资源退出（证据）

| 断言 | 用例 |
| --- | --- |
| 无自动重试 | `rateLimitIsClassifiedWithoutRetry`、`serverFailureIsClassified`、`invalidReviewRetainsUsageAndNeverRetries`、`reviewHttpFailuresKeepPurposeAndDoNotRetry` |
| 超时消耗额度、不退还 | `timeoutIncludesWholeResponseAndConsumesAllowance`；`reviewTimeoutAndExhaustedSharedAllowanceStopLocally`（额度 0 时**不再发请求**） |
| 额度用尽不发请求 | `exhaustedAllowanceMakesNoHttpCall`（`httpCalls=0`） |
| 审计失败阻止外发 | `auditFailureBlocksExternalCall`（`httpCalls=0`） |
| 取消不返回迟到结果 | `closingInFlightReviewCancelsAndDoesNotReturnLateAssessment`（`CANCELLED`、单次调用） |
| 资源退出 | 每个用例 `@AfterEach` 断言 `port.isTerminated()`；样例中取消情形的 `resourcesClosed=true`；`RuleAgentOnlineFactoryTest` 断言 `factory.resourcesClosed()` |

## 四、边界

- 本矩阵全部为 `LOCAL_TEST_ONLY` 假服务；**不证明**真实供应商会收到请求、会返回响应或如何计费。
- `t05-34` 的真实诊断只有：`requestBytes=4557`、`dispatchedMillis=14`、`elapsedMillis=20031`、`reason=MODEL_TIMEOUT`，
  其余（含连接、TLS、响应头、响应体、requestId、usage、responseModel）**全部未知**；当时尚无 `connectedMillis` 字段。
  **新增该字段的意义正是把这类“未知”在下一批拆分为“未连通”与“已连通但静默”。**
