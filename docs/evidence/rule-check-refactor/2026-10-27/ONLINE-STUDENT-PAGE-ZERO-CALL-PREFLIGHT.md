# `/rules` → 现有 ONLINE 测试装配：零收费预检

日期：2026-10-27，Asia/Shanghai。当前结论：**有限范围的 ONLINE 装配零模型预检通过；不等于浏览器真实模型问答通过。收费执行未获本轮授权，到此停止。**

## 1. 现场与接口差距

- 分支 `integration/zhkuagent-mainline-20261024`；HEAD `245880b6b8a62e2bdef69dcb8d6721f72164ceef`。初始已有 8 个 tracked 修改、三题宿主/资料/证据等 untracked，index 无差异。[保留文件与旧授权回执的 SHA 基线](online-page-preflight-baseline.json)。本轮不覆盖旧修改，不提交/推送。
- 已读[上一轮记录](REAL-STUDENT-PAGE-ONLINE-ACCEPTANCE.md#L207-L300)：真实本机 Chrome → Java **FIXTURE** 页面验收已经通过，不再沿用“缺浏览器控制”的历史阻断；但那不是 ONLINE 模型问答通过。
- 真实页面已使用 [异步客户端](../../../../agent/frontend/src/api/rule-task.js)及[严格 v3 校验器](../../../../agent/frontend/src/api/rule-agent-contract.js)。[Vite](../../../../agent/frontend/vite.config.js)及[回环代理](../../../../agent/frontend/rule-task-proxy.js)已支持显式目标，默认关闭，不需要改生产 API/页面/路由/CORS/Cookie。
- [三题宿主](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentThreeQuestionHostTest.java)的 Assembly 是真实 ONLINE 配置、真实 OnlineFactory/身份仓储/审核工具，但 `queue::add` 不执行 Runner；Live 才可外发。PageFixture 是另一个合成装配，不能代替本次 ONLINE 装配。Assembly/Live 的 Java HTTP 端口为随机值，不是历史 18087；原 `HttpPort` 只用于 PageFixture。
- **实际差距不是字段不匹配，而是测试驱动/生命周期不同。** 原 Assembly 仅从 Java 直连接口后退出；Live 自动执行 E02→E04→E13，不是供学生页面操作的租约。本轮只给 Assembly 增加有界代理预检，不开放 Live 页面租约。
- 页面允许任意新题/顺序和服务端返回的所有选项；[三题 Scope](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentThreeQuestionScope.java)却只允许固定原句、固定顺序、同一 Session/Owner，E04 逐字 `PUBLIC` 续一次，否则永久封批。中文选项只是展示，客户端发送 `PUBLIC` 正确。不能为适配测试而收窄生产页面或放松 Scope。

### 保持不变的协议

```text
真实 student 构建 /rules
页面 API Client -> 同源 /api/zhku/controlled-preview/v2/...
Vite preview 127.0.0.1:5174 -> 本次 Java 实际随机回环端口
Spring context-path /api + Controller /zhku/controlled-preview/v2/...
```

| 接口 | 线协议与安全边界 |
|---|---|
| `/auth/csrf`、`/auth/login`、`/auth/me`、`/auth/logout` | 登录为 form-urlencoded；Session + 登录前后 CSRF；USER 才可执行；退出换代。 |
| GET `/tasks/capabilities` | 精确 `{schemaVersion:3, asynchronous:true}`。页面当前不主动调用；本次预检显式核验。 |
| POST `/tasks` | 精确 `{question, clientRequestId}`；202 的 task/turn/run/version/duplicate 五字段。 |
| GET `/tasks/{taskId}` | 快照 11 字段、execution 6 字段；严格 JS 校验，不自动补字段/改枚举。 |
| POST `/tasks/{taskId}/messages` | `{message, continuationToken, expectedVersion, clientRequestId}`；必须当前等待态及版本。 |
| POST `/tasks/{taskId}/cancel` | `{runId, expectedVersion, clientRequestId}`；202 只是受理，仍须等待真实终态。 |
| `/tasks/requests/{action}/{key}`、`/tasks/{id}/originals/{token}` | 原请求查询不重 POST；原文只能访问当前会话任务已发布的合格关联证据。 |

身份不是生产学生账号：本轮在**新空临时库**创建合成测试登录名，但密码校验/身份有效性/角色读取由真实仓储完成。没有把合成身份称为真实学生账号。

## 2. 最小接线与零收费保证

本轮只修改/新增以下 **5 个测试/启动文件**，不改任何生产业务文件：

1. [启动器](../../../../knowledge/scripts/start-rule-agent-three-question-test.ps1)：新增 `PageProxyPort`，非零时仅允许 Assembly、独立空闲回环端口；拒绝 Live/Prepare/Verify/PageFixture 携带此参数。原 Live 授权、审包、预算和容器清理规则保留。
2. [三题测试宿主](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentThreeQuestionHostTest.java)：显式参数时复用同一个 ONLINE App，进入页面代理预检；没有此参数仍走原 Assembly/Live 分支。回执增加 scopeRuns/remainingProviderCalls。
3. [测试协调器](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentOnlinePagePreflight.java)：在 Java 实际端口就绪后，启动已有 Vite preview、注入 `VITE_RULE_TASK_PROXY_TARGET`，运行客户端预检并有界清理；没有新 HTTP/Agent 服务。
4. [HTTP 客户端预检驱动](../../../../knowledge/src/test/browser/rule-agent-online-page-preflight.mjs)：导入**现有页面 API Client 和严格校验器**，真实请求全部经过 5174 代理；Node 仅补内存 Cookie transport，不 mock 返回值。它不是浏览器/DOM 测试。只使用现有 E02 原句，不增加 Eval 问题。
5. [参数负控单测](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentOnlinePagePreflightTest.java)：只允许显式 Assembly，拒绝 Live/其他操作/非法端口，不创建资源。

模型零调用由服务端控制，而不是页面 banner：

- `live=false`，不读取/导入 DashScope key；启动器移除子进程模型环境变量，模型 key supplier 即使被误触也拒绝。
- TaskStore 仍使用 `queue::add`，不启动在线 worker。受理 E02 后验证真实 ACTIVE；客户端取消成功后才消费唯一排队票据。TaskStore 的 `isStopRequested` 分支在 `factory.open`/`Work.run` 之前直接发布 STOPPED，**不调用 Runner、Scope.begin、工具或 Provider**。
- 另有 `!live` Work 守卫，即使排队票据未取消也不得打开真实 OnlineFactory。原发布/审核门禁未放宽。
- 最终同时核对 `providerCalls=0`、`keyReads=0`、`scopeRuns=0`、`remainingProviderCalls=20`、`reservedRmb=0`。不是仅凭 `Mode.ONLINE` 或静态写入的零判断。
- 审核工具原本使用 JDBC lexical 检索/reader/audit，不含 Embedding 服务；本次又根本不进入工具执行。因此模型/Embedding 外发均为零。公开价格文档有一次无凭证 GET，不应把本轮误写成“所有网络请求为零”。
- Assembly 会读取**数据库**凭据并在 `127.0.0.1:55527/zhkuagent_rulecheck_e2e` 新建临时库；不是零数据库调用或零资源。拒绝原演示 54329、冻结评测 55441/55442，不连接生产库；随机 label 容器由原启动器核对身份后删除。

## 3. 命令、实际结果与失败记录

工作目录 mainline；前端命令在 `agent/frontend`。所有 Maven 依赖离线，不调用旧 Live/旧三题批次，不带批准参数。每次新建包目录，旧证据不覆盖。

```powershell
# 前端；清除 VITE_DEMO_MODE 和 VITE_RULE_TASK_PROXY_TARGET 后，使用同一学生页面
$env:VITE_ENTRY_MODE='student'
node node_modules/vite/bin/vite.js build --outDir dist-page-online-preflight
node --test tests/rule-task-proxy.test.mjs tests/rule-task-page.test.mjs tests/rule-task-workflow.test.mjs tests/rule-agent-contract.test.mjs tests/rule-check-api.test.mjs tests/student-routes.test.mjs

# 主目录；普通回归先移除本次子进程模型/数据库凭据，不设置原始HTTP evidenceDir
node --check knowledge/src/test/browser/rule-agent-online-page-preflight.mjs
mvn.cmd -o -pl knowledge -am '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=RuleAgentOnlinePagePreflightTest,RuleAgentThreeQuestionPackageTest,RuleAgentThreeQuestionScopeTest,RuleAgentTaskHttpTest,RuleAgentThreeQuestionHostTest' test

# 最终新包：仅用于本次装配审计身份，NOT_AUTHORIZED，不是单题Live批准包
$batch=Join-Path $PWD 'docs/evidence/rule-check-refactor/2026-10-26/three-question-online-prepare/batch-online-page-preflight-20261027-02'
./knowledge/scripts/start-rule-agent-three-question-test.ps1 -Operation Prepare -AssetsRoot $PWD.Path -BatchDirectory $batch
./knowledge/scripts/start-rule-agent-three-question-test.ps1 -Operation Assembly -AssetsRoot $PWD.Path -BatchDirectory $batch -PackageSha256 04d0a72e2705ebaa3061daadbef8b10c0f90c6e3470ec280dbac8bb3b442eca2 -PageProxyPort 5174 -DatabasePort 55527
```

- 构建 **exit 0**，Vite 4.5.14，135 模块；[构建日志](online-page-preflight-build.log)。独立目录不覆盖既有 student/FIXTURE/CDP 构建，保留用于复现。
- 前端 **exit 0，99/99**；[日志](online-page-preflight-frontend.log)。这是回归，不冒称本轮浏览器 ONLINE 动态交互。
- 初次后端 **exit 0，37 项/0 失败/0 错误/1 skipped**；[日志](online-page-preflight-backend.log)。36 项实际执行；宿主无显式 operation 时跳过，证明默认不开资源。包含参数负控3、审包7、范围25、HTTP生命周期1。Node 语法与 PowerShell AST 同次通过。
- 首次新包 `6321d641…b5a7919`：Prepare **exit 0**；Assembly **exit 1**，[原失败日志](online-page-preflight-assembly.log)。客户端检查实际通过，但进程退出后立即 TCP 检查仍可连接，最终在 `RuleAgentOnlinePagePreflight.java:84` 报 `Expecting value to be true but was false`。该批整体仍失败，不由局部通过覆盖；其模型0、scope0、额度20、Java与临时容器均正常退出。随后独立端口核对已无监听。
- 修正仅为测试收尾：端口最多等3秒直到真正拒绝连接，仍未关则失败；子进程 stdout 改落独立安全日志，避免直写 Surefire 通道。源码改变后重新 Prepare 新包，没有重用旧 SHA/授权。
- **最终02装配 exit 0**，[完整日志](online-page-preflight-assembly-02.log)；[宿主回执](../2026-10-26/three-question-online-prepare/batch-online-page-preflight-20261027-02/assembly-result.json)为 `httpChecksPassed=true, resourcesClosed=true, providerCalls=0, keyReads=0, scopeRuns=0, remainingProviderCalls=20, reservedRmb=0, browserTested=false`。临时库/ONLINE HTTP 宿主与 Node preview/client 均实际退出，不是静态组装一份成功 JSON。
- [客户端原件](../2026-10-26/three-question-online-prepare/batch-online-page-preflight-20261027-02/page-client-result.json)：**6组检查、42次真实回环HTTP交换**，全部经5174代理到Java 28831；没有 mocked/补齐响应，没有使用FIXTURE答案。覆盖 `/rules` 构建HTML200、匿名401/错密码401/USER200/维护员403、CSRF403/额外owner字段400、CREATE202/幂等重复/改题409/原请求查询、同学生不同Session404、严格5/11/6字段校验、ONLINE ACTIVE且无模型尝试、无原文404、非等待态续答409、过期取消409、取消202/重复/真实STOPPED、退出重登失去旧任务访问。
- [退出回执](../2026-10-26/three-question-online-prepare/batch-online-page-preflight-20261027-02/page-preflight-process-exit.json)：client/preview/listener均退出；[启动器回执](../2026-10-26/three-question-online-prepare/batch-online-page-preflight-20261027-02/launcher-assembly-25be0b19cc2d4135818ea333058d5ddd.json)：`failed=false, paidModelCallsPermitted=false, containerRemoved=true`。
- [独立断言复核](online-page-preflight-review.json)通过：上述计数与退出字段一致；既有8个tracked文件字节及5份旧授权回执完全不变、没有新授权占用；Provider trace/exchange文件不存在；检查的本批JSON不含口令值、原始Cookie/Authorization/Session/token/body等字段，网络路由中的任务标识已脱敏。本轮没有保存真实模型消息、密钥或用户学籍资料。
- 清理修正后的[最终相关后端回归](online-page-preflight-related-final.log) **exit 0，51项/0失败/0错误/1 skipped**：实际执行50项（原37项加原ONLINE HTTP Scope负控14项），显式零收费装配另外真实执行1项。前端仍为99/99，生产前端代码未因本轮修改。
- 额外扩大到旧 `RuleAgentOnlineFactoryTest` 的[一次回归](online-page-preflight-backend-final.log) **exit 1，97项/6 errors/1 skipped**：6个既有留档用例因未传 `zhku.rulecheck.evidenceDir`，在342/364/391/653行 `Path.of(null)` 抛 `java.lang.NullPointerException`；没有逻辑断言失败。它们会写原始续答/运行状态包，本轮不为消掉配置错误而开启该留档或改旧测试。最终相关51项明确不含这6项；**不宣称整个扩展套件或全仓库全绿**，失败原件保留。
- 静态复核未发现 PageProxyPort→Live 扩权路径。覆盖限度：协调器异常清理先stop(client)再stop(preview)，前一步被中断/抛异常可能短路后续；本轮成功批与首批失败后已独立核对无残留，**并未证明所有中断路径均清理完备**。不再扩展测试宿主生命周期改造。

## 4. 尚未验证及进入真实验收的阻断

本次没有执行真实浏览器 ONLINE DOM 交互，也没有真实模型决策、规则工具调用、澄清成功、答案生成、证据原文成功、语义核验与发布通过证据。只证明真实学生构建HTML＋现有JS API Client＋同源代理＋ONLINE装配接口、真实认证/任务状态及零模型外发；没有在浏览器执行构建bundle，不是复用 FIXTURE 成功答案。

**唯一尚未具备的执行前置项：一个本次有效、人工批准的“页面驱动受限 Live 执行包/租约”。** 不是只补一句“同意调用”就能直接跑当前自动三题 Live：该分支仍由 Java 自动执行三题，并无本轮页面单题租约。本次 `PageProxyPort` 故意拒绝 Live；未经后续最小测试入口准备、核价/封包/守卫验证及重新明确授权，不得发起首测。本轮按范围到此停止，不顺手新增收费执行入口。

### 供后续审批的首测提案（不是授权/不是已落地小预算）

- 首题 E02：**“有人说公选必须上满16学时，24学时的安排肯定违规，是这样吗？”** 固定资料可核查、单轮、错误前提，能检验不盲从学生结论；不涉及个人学籍或当晚通知，不先引入 E04 多轮变量。
- 预期：依据已审核公选规则核对，解释为什么不能由“16学时”推出“24学时必然违规”；证据不足就明确限度，不凭常识下违规结论。实际能否正确做出判断尚未验证。
- 链路应为真实 Chrome `/rules` → 原页面 API Client/同源代理 → 隔离 ONLINE HTTP 宿主 → 原 TaskStore/Scope/OnlineFactory/Runner → 已审核 JDBC 工具及发布 Gate → 北京 DashScope。不能用直接 Java POST 替代页面首测。
- 建议只允许 **1任务/1run、总 Provider≤5（包括独立核验）**，不开重试/续杯。当前三题宿主的实际整批硬帽仍为 **20次/16.10元**，**并未**因建议首测单题自动缩成5次/4.02元；这是待后续受限执行包落实的约束，不能拿现成20次批次冒充单题小帽。
- [本次公开核价](online-page-preflight-tariff.json)：2026-10-27 GET [阿里云官方报价](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)，北京 `qwen3.8-flash` 输入 **0.8元/百万tokens**、输出 **2.7元/百万tokens**；不假设缓存/折扣/免费额度。每次保守预留 `1,000,000×0.8/1e6 + 1,034×2.7/1e6 = 0.8027918元`；5次最坏 `4.013959元`，建议新硬帽 **4.02元**。超时/usage未知保留全额且停止，供应商账单仍须对账。
- 核价审批窗口仅至 **2026-10-27 23:59:59 +08:00**，是本地风控期限，不是供应商不涨价承诺；授权日晚于该窗口须重新查价/封包。
- 拟外发：测试原句、系统/协议提示词、用户声明条件（本首题无新增条件）、有界规则上下文/已核验事实/候选描述/审核片段与执行状态，及独立语义核验输入。模型密钥仅 TLS Authorization。不发送登录口令、Cookie/Session/CSRF/续答/原文token、身份库/个人学籍、整本PDF、源码或参考答案。细节必须以将来新包实际消息信封重新审核。
- 剩余风险：真实模型可能误解/选错工具、超时或usage未知、返回被严格协议拒绝、证据不足/发布拒绝；页面对真实 ONLINE 成功/澄清结果的渲染尚无本轮证据。保留所有现有门禁，任何异常停止，不放宽校验来制造成功。

## 5. Git 与本轮资源收尾

[最终检查](online-page-preflight-final-check.log) exit 0：分支/HEAD不变、index无差异、`git diff --check`通过（既有三份前端文件仅有LF→CRLF提示）；原8个tracked修改逐字节保留，本轮新增修改限于上述5个测试/启动文件及证据/独立构建产物。没有改生产业务、默认路由、Runtime、rc2冻结资产，没有新增Memory/Skill/MCP，没有提交/推送。

5174、55527、Java实际28831均无监听；所属preview PID50944已退出；`zhku.owner=three-question-test`容器数0；最终所属临时容器已删除。既有54329库及其他容器未被启停，本轮未启动浏览器/另建Node后端/运行旧Live或三题批次，所有本轮后台job已结束。独立构建和成功/失败证据保留复现，不留运行中的测试资源。

**到此停止。下一步须先准备并批准受限页面Live执行包；本报告不是收费调用授权。**
