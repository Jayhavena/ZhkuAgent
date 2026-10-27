# E02 单题页面 Live 执行包 · 冻结报告

日期：2026-10-27（Asia/Shanghai）　分支：`integration/zhkuagent-mainline-20261024`　HEAD：`245880b`
状态：**已冻结、未授权、零执行**（`authorizationStatus = NOT_AUTHORIZED`）

## 1. 包身份（唯一有效包）

| 项 | 值 |
| --- | --- |
| `request-package.json` sha256 | `b52d47d0ec31ca745b52b844da0459575ff1a7035b0f4beef87fe868abd93178` |
| 目录 | `docs/evidence/rule-check-refactor/2026-10-27/e02-single-page-live/prepare-offline-02` |
| batchId | `d1892c3e-c95e-49b4-b101-2786b4fd994f` |
| purpose | `E02_SINGLE_QUESTION_PAGE_LIVE_V1` |
| planSha256 | `d50f2a137379085d48ce139e443403a0609a42a1d6a5bea71dbb105dc6e32a4b` |
| 核价证据 sha256 | `899f17b66e634953ed8a9505361de068c16a6964932244c0d11ae4b8e97f6671` |
| 源码身份 | HEAD `245880b6b8a62e2bdef69dcb8d6721f72164ceef` + 985 个工作树输入文件字节哈希 |
| 页面产物 | `agent/frontend/dist-e02-page-live`（27 文件，逐文件哈希入包） |
| 运行时 classpath | 113 个条目（目录按树、jar 按字节） |
| 启动器字节 | `knowledge/scripts/start-rule-agent-three-question-test.ps1` = `6e1425f5…` |

上一包 `prepare-offline-01`（`3a96201b…`）因启动器随后加入 E02 档位已**作废**，见该目录 `SUPERSEDED-BEFORE-EXECUTION.md`。

## 2. 计划参数（硬编码，宿主逐项复算）

- 题面（逐字，只能这一题）：`有人说公选必须上满16学时，24学时的安排肯定违规，是这样吗？`
- 额度：1 个任务、1 个 run、最多 **5** 次 Provider 调用，**无自动重试、无续杯**；单次输出上限 1024 token，输入保守上限 1,000,000 token，请求体上限 65,536 字节。
- 端点/模型：`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` / `qwen3.8-flash`。

## 3. 金额与时效

- 单价（北京同步、非思考、标准价）：输入 0.8 元/百万，输出 2.7 元/百万；按文档允许的 +10 token 误差以 **1034** 计费输出。
- 单次最坏预留 `0.8027918` 元；5 次最坏 `4.013959` 元；硬帽 **4.02 元**（宿主在每次 BEGIN 前累加并拒绝越帽）。
- `tariffCheckedDate = tariffValidThrough = 2026-10-27`：**今日之外再授权会被 `tariff()` 直接拒绝**，须重新核价并重新冻结（新 SHA）。
- 计费口径：所有语义核验调用都计费；超时/usage 未知一律保留全额预留、不退款、不重试。供应商账单仍需人工对账。

## 4. 页面驱动边界

- 入口 `/rules`，`browserRequired=true`，`javaPostAllowed=false`，`oneSinglePageTask=true`。
- 宿主只做三件事：`vite preview` 提供已冻结的 student 构建、写 `browser-ready.json`（含 `backendPort/proxyPort/packageSha256/driver`）、等 `STOP`；随后按 Scope 冻结的同一 taskId 回读取证。
- 宿主**不代替页面提交、不发业务 POST、不跑第二次**；前端进程环境已剔除 `DASHSCOPE_API_KEY`、旧回放变量等，代理只指向本轮 127.0.0.1 后端。
- 浏览器驱动 `knowledge/src/test/browser/rule-agent-e02-page-live.mjs` 必须由人工在真实 Chrome 中启动（可断点接管的可观测步骤）。

## 5. 外发边界（实际请求信封由宿主逐字段比对）

唯一外发是模型 POST。信封字段仅允许：`model, stream, enable_thinking, max_completion_tokens, response_format, messages`；
`stream=false`、`enable_thinking=false`、`max_completion_tokens=1024`；`messages` 恰两条（system+user，各仅 `role`+纯文本 `content`）；
决策用 `json_schema`、核验用 `json_object`；请求体 ≤64KiB。不合规即阻断并停止。
不外发：期望答案、评分口径、owner/session/token、凭证正文、源码、整份 PDF、真实学生记录；API key 只出现在 TLS Authorization。

## 6. 本轮零调用验证结果

| 验证 | 结果 |
| --- | --- |
| `verify`（宿主直接调用，带 E02 计划闸门） | PASS `THREE_QUESTION_VERIFIED_NO_RESOURCES` |
| `verify`（经启动器 `-Operation Verify -Profile E02`） | PASS，20:24:31 |
| 单测（Package/Scope/PagePreflight/StudentPageProxy/E13） | 40 run / 0 fail / 1 skipped（需真实资源的 proxy 用例按设计跳过） |
| 前端产物 | `dist-e02-page-live` 27 文件，与包内哈希一致，未重建 |
| Provider 调用 / 密钥读取 / 数据库 / 容器 | 0 / 0 / 0 / 0 |

## 7. 本轮启动器改动（仅测试工具链，未提交）

1. `param` 新增 `-HardBudgetRmb`（默认按档位：E02 `4.02`、Three `16.10`）。
2. Live 授权闸门改为比对 `$HardBudgetRmb`，不再把 16.10 硬编码给 E02。
3. `Invoke-Maven` 显式追加 `-Dzhku.three.profile=E02`（缺省仍为 Three，不复用旧题）。

## 8. 授权后的人工执行链

```powershell
# 1) 人工授权（三个参数即授权表达；缺一即拒绝）
Open-Issue: 用户给出本包 SHA + 4.02 + 新授权编号
knowledge/scripts/start-rule-agent-three-question-test.ps1 -Operation Live -Profile E02 `
  -AssetsRoot <repo> -BatchDirectory <…/e02-single-page-live/live-01> `
  -PackageSha256 b52d47d0… -ApprovedSha256 b52d47d0… -ApprovedBudgetRmb 4.02 -AuthorizationId <id>
# 2) 宿主写出 browser-ready.json 后，人工在真实 Chrome 打开 proxyPort 的 /rules，
#    由页面提交这一题；驱动脚本可接管并记录
node knowledge/src/test/browser/rule-agent-e02-page-live.mjs <evidenceDir> http://127.0.0.1:<proxyPort> <repo>
# 3) 页面侧结束：写 STOP；宿主回读同一任务，产出 live-result.json
```

## 9. 仍未闭合（不属本轮范围，不粉饰）

- 浏览器驱动没有自动拉起：`browser-ready.json` 之后的点击必须由人完成。
- `billing = SUPPLIER_BILL_NOT_RECONCILED`；`humanProductReview = REQUIRED_NOT_AUTOMATICALLY_PASSED`。
- 计划只能问**这一题**；换题、追问、多轮澄清都需要新计划与新授权。
- 工作树仍有 8 个已改文件与 25 个未跟踪文件（本包依赖其当前字节），未提交、未推送。
