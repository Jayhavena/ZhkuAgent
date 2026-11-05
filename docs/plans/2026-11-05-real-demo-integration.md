# 真实演示链路集成：接口核对与在线验收准备

日期：2026-11-05（Asia/Shanghai）。状态：零供应商调用的真实数据库/后端检查已完成；在线单题必须另获具体包 SHA 与 4.02 元硬上限批准。本文件不代表完整本地演示结项。

## 1. 根因与选择

`knowledge/scripts/start-controlled-web-demo.ps1` 启动 `ControlledWebDemoHost`，后者进入 `ControlledPreviewApplication`，启用 `zhku-controlled-preview`，提供 v1。当前 student 前端使用 v2 认证和异步任务合同。nginx 只负责代理，不能给 v1 宿主补出 v2 接口；页面 HTML 200、v1 匿名接口 401 不能证明当前页面可登录和运行。

本轮选择已有 v2 测试宿主 `RuleAgentThreeQuestionHostTest` 的独立 E02 页面档位。它复用真实身份仓储、`RuleCheckPreviewSecurityConfiguration`、`RuleCheckAuthController`、`RuleAgentTaskController`、真实 TaskStore/Runner/工具与发布守卫。新增的 `start-real-demo-integration.cmd/.ps1` 只处理普通终端入口、独立输入索引与固定档位转发；不改生产后端、认证、nginx 或前端页面。

`PersistentRuleCheckLocalHost` 用于封存同步回放，不能直接当作 student 异步在线宿主；`RuleAgentSimulatedDemoHost` 是显式模拟宿主，也不能改名当在线入口。旧 v1 启动器保留，用途不扩展。

## 2. 实际接口对照

以下路径含应用的 `/api` context path。

| 项目 | 旧 v1 宿主 | 当前前端与 v2 实现 |
|---|---|---|
| 命名空间 | `/api/zhku/controlled-preview/v1` | `/api/zhku/controlled-preview/v2` |
| Session Cookie | `ZHKU_CONTROLLED_SESSION`，v1 Cookie Path | `ZHKU_RULE_CHECK_SESSION`，v2 Cookie Path；Session 闲置期 15 分钟 |
| CSRF/登录/退出 | 各自命名空间下的 `/auth/csrf`、`/auth/login`、`/auth/logout` | 路径随 v2；CSRF 由服务器签发；登录仍是表单编码；退出仍是受认证的 POST |
| 账号 | 受控身份会话读取 | v2 另有注册、账号读取、修改密码；`/auth/me` 返回当前账号与 authRevision。此轮不改变认证语义，不据临时库宣称持久账号验收 |
| 提交任务 | `POST /runs`；`Idempotency-Key` 请求头；课程/学分领域请求 | `POST /tasks`；JSON `{question, clientRequestId}`；202；严格 5 字段受理票据 |
| 查询 | `GET /runs/{runId}`；另有 submission/history | `GET /tasks/{taskId}`；严格 schemaVersion=3 快照；task/turn/run/version 分开 |
| 未知受理确认 | `GET /submissions/{submissionKey}` | `GET /tasks/requests/{CREATE\|MESSAGE\|CANCEL}/{clientRequestId}` |
| 续答 | 独立 conversation 合同 | `POST /tasks/{taskId}/messages`；message、continuationToken、expectedVersion、clientRequestId |
| 取消 | `POST /runs/{runId}/cancel`，不接受业务字段 | `POST /tasks/{taskId}/cancel`；runId、expectedVersion、clientRequestId；202 仅表示受理，终止须继续查快照 |
| 原文 | v1 运行/历史来源体系 | `GET /tasks/{taskId}/originals/{token}`；本人当前 Session、已发布关系与原文重核验 |

前端 `rule-task.js` 的路径、字段与 v2 控制器一致。身份/CSRF 401/403、跨 Session 404、幂等/版本 409、容量和失败状态保持服务端合同。不能通过 Cookie 改名、去掉 CSRF、复制 SecurityContext 或添加通用代理把两个版本强行混接。

## 3. 已完成验证与边界

| 检查 | 实际结果 |
|---|---|
| 离线 Maven 打包 | 退出 0；未读取供应商凭据、未调用模型 |
| 审包辅助单元测试 | 8/8，通过；新增独立 E02 计划选择先失败后通过；拒绝 E13 及任意路径覆盖 |
| 浏览器驱动离线测试 | 8/8，通过；真实 Chrome 兼容当前 RuleAccount 和旧夹具登录，使用实际点击；合成 HTTP 夹具不算真实后端 |
| ONLINE 零调用装配 | 真实 Spring HTTP、真实临时 PostgreSQL、真实身份；未获许可的任务在取密钥前拒绝；keyReads=0、provider traces=0；1/1 通过 |
| HTTP 身份/任务/资料合同 | 真实临时库、真实已审核资料与工具；2 个方法通过；1 个 nginx 代理方法因未配置跳过，未计为通过。模型决策显式模拟 |
| 真实 Chrome 页面链路 | 第二次独立临时库验收通过：v2 登录、POST 202、轮询、取消至 STOPPED、模拟决策结果 RESOLVED、真实数据库证据及本人原文 GET 200、退出后任务查询 401；供应商调用 0 |
| 失败记录 | 首次页面链路最后一步误用独立登录页选择器；真实页面退出后显示内嵌 RuleLogin，导致驱动超时。保留失败记录，修正外部观察条件后重新使用新临时库验证；没有为此改产品代码 |
| 资源退出 | 上述自有数据库容器均已回收；后端、worker、Provider 资源及自建前端预览监听已退出；未写共享演示库 |

原始执行材料位于仓库外的 `D:/alldesk/_safety_backups/ZhkuAgent/execution-2026-11-05-real-demo-integration/`。本轮不提交 Cookie、Token、密码、完整 HTTP 头、PDF、数据库或构建产物。零调用检查不构成在线模型或产品答案成功。

## 4. 待批准的在线单题

计划：`knowledge/src/test/resources/rule-agent-e02-integration-plan.json`；本轮核价证据：[tariff-evidence.json](assets/2026-11-05-real-demo-integration/tariff-evidence.json)。原 E02/E13 计划属于其他任务，本轮未修改。

- 北京 `qwen3.8-flash`；官方输入/输出原价为每百万 token 0.8/2.7 元，2026-11-05 核对：[官方型号与价格](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)。不假设缓存、折扣或免费额度。
- 固定提问：“有人说公选必须上满16学时，24学时的安排肯定违规，是这样吗？”
- 仅真实 Chrome 的当前 `/rules` 页面发出一次业务提交；1 task、1 run，最多 5 次供应商调用，每次最多 1024 输出 token。无额外澄清/续答额度。
- 费用硬上限 **4.02 元**；最坏预留 **4.013959 元**。每次按完整百万级上下文及 1034 个保守计费输出 token 预留；超时和未知 usage 不返还预留，失败不自动重试或续杯。供应商账单仍须对账。
- 外发仅含合成问题、已审核历史 2025.9.2 条款片段与必要工具观察；不外发真实学生资料、源码、完整手册、账号、Session、Token 或密钥。密钥只用于 TLS Authorization。
- 必须核对 task/run/版本一致、ONLINE/providerResponseReceived 及真实供应商审计；有发布证据须通过本人原文回读并拒绝跨 Session 访问。答案的“原则 16 学时、最多 32 学时、24 的局部判断、历史适用边界”仍需人工内容判定；安全拒答不自动算产品完成。
- 本地核价/批准窗口至 **2026-11-06（Asia/Shanghai）**。这不是供应商价格保证。过期、源码/构建/资料/包指纹变化、协议/鉴权/审计失败、预算耗尽、页面租约中断均停止；不得沿用历史包或历史授权。

## 5. 可复现启动方式

先在普通 Windows 终端、仓库根使用已安装的 Node/npm 与仓库 Maven 包装器构建。保持既有 E13 工作区文件原样，不暂存它们。

```powershell
./mvnw.cmd -o -pl knowledge -am -DskipTests package
$savedEntry=$env:VITE_ENTRY_MODE; $savedDemo=$env:VITE_DEMO_MODE
try {
    $env:VITE_ENTRY_MODE='student'
    Remove-Item Env:VITE_DEMO_MODE -ErrorAction SilentlyContinue
    npm.cmd --prefix agent/frontend run build -- --outDir dist-real-demo-integration
    if ($LASTEXITCODE -ne 0) { throw '学生页面构建失败' }
} finally {
    if ($null -eq $savedEntry) { Remove-Item Env:VITE_ENTRY_MODE -ErrorAction SilentlyContinue } else { $env:VITE_ENTRY_MODE=$savedEntry }
    if ($null -eq $savedDemo) { Remove-Item Env:VITE_DEMO_MODE -ErrorAction SilentlyContinue } else { $env:VITE_DEMO_MODE=$savedDemo }
}
./knowledge/scripts/start-real-demo-integration.cmd -Operation Prepare -BatchDirectory '<本轮新的E02证据子目录>'
./knowledge/scripts/start-real-demo-integration.cmd -Operation Verify -BatchDirectory '<该目录>' -PackageSha256 '<实际64位SHA>'
```

批次目录必须位于既有 `docs/evidence/rule-check-refactor/2026-10-28/e02-single-page-live/` 的新子目录。这是沿用既有有界档位的路径约束，不重写旧证据；日期以本轮新包为准。Prepare/Verify 不创建数据库或读取供应商凭据。新的包装器自动使用仓库外、从当前 HEAD 初始化的独立输入索引；原索引 SHA256 在退出时再次核对，不执行 reset、重新暂存或普通索引提交。

只有取得本包 SHA、4.02 元硬上限与新授权编号的明确批准后，才允许 `-Operation Live` 并传递对应批准参数；获批前不生成批准参数来试跑。真实浏览器驱动必须先建立同批 lease，宿主只发布就绪票据并等待页面操作。批准前不得调用此 Live 分支，也不得改成模拟模式冒充在线成功。

最终审批包应在本轮准备代码提交后重新生成并 Verify，提供实际 SHA 与文件链接；提交前的调试包不用于批准。包冻结 HEAD 加实际工作区输入字节（包含未提交、未吸收的其他任务源码），源码或制品有变化即重新审包。

## 6. 当前结项边界

前端工程任务已经结项。本轮只完成真实集成验收准备与零供应商调用的真实数据库/HTTP/浏览器检查。完整在线演示仍待具体包批准、当次在线执行、结果与资源退出证据，以及人工内容判定，当前不得标为完整演示已结项。
