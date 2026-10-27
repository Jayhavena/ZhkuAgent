# 真实学生页面本机接线验收（2026-10-27）

> **最新结论（浏览器续验 03，最终批次 07）：真实本机 Chrome → `/rules` → 同源代理 → Java FIXTURE 的页面验收通过；真实模型未执行。** 下方最小接线与续验 02 保留为历史记录，其“未执行/阻断”不再代表当前浏览器状态。最终证据、失败尝试、命令与资源清理见文末。

## 范围与结论边界

本轮只接线、编译和本机零收费 FIXTURE 验证；不执行 Live，不创建或删除 Docker/数据库，不读取真实凭据，不提交或推送。FIXTURE 身份及输入均为公开合成测试值，不是用户账号。原有三个生产 Java 文件的未提交修改保留。

- **A 页面传输：前端服务器 `/rules` HTTP 200、同源 API 代理至 Java 的真实 HTTP 交互通过；真实浏览器操作未执行，不判整页验收通过。**
- **B MOCK/FIXTURE：前端 99 项、后端 142 项及代理/HTTP 2 项测试全部通过，学生入口构建与宿主退出通过。页面呈现证据限于 SSR/契约测试，动态交互未验证。**
- **C 真实模型：未执行。** 非预置问题理解与决策、真实规则检索、证据充分性判断和真实答案生成均没有本轮成功证据。

## 实际断点与最小修改

1. `/rules` 实际组件已经使用异步 schema-v3 客户端，不是旧同步 `/rule-check`。挂载先读 `/auth/me`，提交后使用 `/tasks`、快照轮询、续答、取消和原文接口。客户端和 Spring 路由本来一致，不需要重写业务 API/状态机。
2. 原 Vite 配置无 API 代理，同源请求停在前端服务器，无法到达 Java 宿主。已有 Nginx 配置面向 Docker 的 `host.docker.internal`，本轮不启动或修改 Nginx。
3. 原三题宿主 `Assembly` 需要临时数据库，只排队不执行任务，随测试退出；`Live` 则需要外发授权。二者都不是本轮可用的持久页面夹具。新增显式 `PageFixture` 分支，复用现有无数据库/无供应商浏览器 HTTP 夹具，不改原 Live 审包、范围和预算边界。
4. 页面夹具采用真实 SecurityFilterChain、Session、CSRF、TaskController、TaskStore 和证据原文接口；身份仓储与执行器/规则数据是合成替身，不运行真实模型 Runner/发布 Gate。后者仅由独立单测回归，不能算成页面完整模型链路通过。

### 修改文件及目的

| 文件 | 目的 |
|---|---|
| [Vite 配置](../../../../agent/frontend/vite.config.js) | dev 与 preview 复用显式同源 API 代理；默认关闭。 |
| [代理约束](../../../../agent/frontend/rule-task-proxy.js)（新增） | 只接受带端口的 HTTP 回环源，拒绝远端、凭据、路径与查询；完整保留 v2 路由。 |
| [代理测试](../../../../agent/frontend/tests/rule-task-proxy.test.mjs)（新增） | 检验默认关闭、地址限制与不改写路径/Origin。 |
| [学生页面](../../../../agent/frontend/src/views/RuleCheckView.vue) | FIXTURE 构建在登录前明确声明合成身份/规则/答案、零模型调用；不改请求流程。 |
| [页面渲染回归](../../../../agent/frontend/tests/rule-task-page.test.mjs) | 验证 FIXTURE 声明可见，正常页面不展示该声明。 |
| [三题宿主](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentThreeQuestionHostTest.java) | `page-fixture` 在任何审包/数据库/Provider 入口之前分流。 |
| [启动脚本](../../../../knowledge/scripts/start-rule-agent-three-question-test.ps1) | `PageFixture` 不要求 Live 参数，不找 Docker/不导入凭据；固定仓库 reactor、独立新证据目录和空闲回环端口。其他操作仍要求原 AssetsRoot/BatchDirectory。 |
| [浏览器 HTTP 夹具](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentTaskBrowserHttpTest.java) | 提取可复用显式租约；固定监听端口/回环地址、关闭在线装配，并断言无 OnlineFactory/DataSource bean；STOP 后核验退出。 |
| [HTTP 测试夹具](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentTaskHttpTest.java) | 合成认证不再接受任意口令；增加两条自然表达到既有澄清/资料不足场景的显式映射，不声称理解能力；测试 HTTP Client 显式使用 HTTP/1.1，避免 Java 默认 h2c Upgrade 在 Vite 本地代理挂起。 |
| [代理 HTTP 验收](../../../../knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentStudentPageProxyTest.java)（新增） | 经真实前端服务器代理访问 Java，验证契约与安全响应；只落盘去标识路径/状态码，不落认证和 token。不是浏览器测试。 |

## 实际路由与 HTTP 契约

本轮计划监听：页面 `http://127.0.0.1:5174/rules`，Java `http://127.0.0.1:18087`。以最终 ready/退出记录为实际证据。

```text
/rules -> 构建的真实学生 Vue 页面
浏览器/HTTP client -> 同源 /api/zhku/controlled-preview/v2/...
Vite preview/dev -> http://127.0.0.1:18087/api/zhku/controlled-preview/v2/...
Spring context-path /api + Controller /zhku/controlled-preview/v2/...
```

代理不 rewrite、不放宽跨域、不修改 Cookie 域/Path。设置 `VITE_RULE_TASK_PROXY_TARGET` 才开启，只代理 v2 前缀。部署 Nginx 未在本轮执行。

| HTTP | 契约 |
|---|---|
| GET `/auth/csrf`, POST `/auth/login`, GET `/auth/me`, POST `/auth/logout` | 真实安全链；登录表单仍需 CSRF；客户端正常协议处理敏感字段，工具不提取其值。 |
| GET `/tasks/capabilities` | schemaVersion=3，asynchronous=true；匿名 401，维护身份 403。 |
| POST `/tasks` | `{question, clientRequestId}`；202 返回 task/turn/run/version/duplicate。 |
| GET `/tasks/{taskId}` | 服务端任务快照；ACTIVE、WAITING_USER、RESOLVED、FAILED、STOPPED。 |
| POST `/tasks/{taskId}/messages` | `{message, continuationToken, expectedVersion, clientRequestId}`；仅合法条件/当前版本续答。 |
| POST `/tasks/{taskId}/cancel` | `{runId, expectedVersion, clientRequestId}`；202 受理不代表已终止，仍等待终态。 |
| GET `/tasks/requests/{action}/{requestId}` | 查询原幂等请求，不自动重新 POST。 |
| GET `/tasks/{taskId}/originals/{token}` | 仅当前 Session 的任务已发布关联证据；跨 Session 404，资料变化 409。 |

## 安全前置条件

- 仅回环地址；独立 preview profile、独立会话 Cookie、HttpOnly、SameSite=Strict、精确 v2 Path、15 分钟、不持久化。禁止切换成通配 CORS 或直接跨源前端客户端。
- USER 角色才可执行；同一个合成学生在另一个 Session 也不能读任务/原文。真实身份数据库未验证。
- 保留 CSRF、精确字段校验、幂等、任务版本、续答 token、取消与终态竞争约束，不向页面补充伪造身份字段。
- 页面执行分支没有 Provider/OnlineFactory/DataSource；脚本仅删除子进程凭据变量，不读取用户级/系统级变量或凭据文件。模型/Embedding 外部调用为零。
- 夹具快照沿用既有 `Mode.ONLINE` 枚举，但其 providerResponseReceived=false、合成结果与零模型声明不能被解读为 ONLINE 模型成功。FIXTURE 提示与证据 mode 明确覆盖这一误用风险。

## 已执行命令与结果

工作目录为 mainline，除启动脚本特意从项目父目录调用以验证 reactor 路径固定。日志只记录本轮实际输出。

```powershell
git status --short --branch
git rev-parse HEAD
node --test tests/rule-task-proxy.test.mjs tests/rule-task-page.test.mjs tests/rule-task-workflow.test.mjs tests/rule-agent-contract.test.mjs tests/rule-check-api.test.mjs tests/student-routes.test.mjs
$env:VITE_ENTRY_MODE='student'; $env:VITE_DEMO_MODE='http-fixture'
node node_modules/vite/bin/vite.js build --outDir dist-page-fixture
$env:VITE_RULE_TASK_PROXY_TARGET='http://127.0.0.1:18087'
node node_modules/vite/bin/vite.js preview --outDir dist-page-fixture --host 127.0.0.1 --port 5174 --strictPort
```

- 前端回归：exit 0，99/99；[日志](student-page-frontend-regression.log)。覆盖真实组件 SSR 的部分答案/资料不足/处理故障/回放标签、规则转义与关联证据、身份变化提示；API/workflow 测试覆盖加载轮询、丢失回执只查询不重复 POST、同任务澄清续答、取消待终止、版本冲突和迟到响应隔离。它们不是浏览器动态交互证据。
- 构建：exit 0，Vite 4.5.14，135 模块；[日志](student-page-build.log)。独立构建目录，不覆盖原 student/dist 构建。
- PowerShell 启动器 AST 解析：PASS，exit 0。
- 后端首次命令：exit 1，未引用的 Maven `-D` 参数被 PowerShell 拆分，`Unknown lifecycle phase ".failIfNoSpecifiedTests=false"`；[日志](student-page-backend-regression.log)。已将本轮新增脚本参数加引号。
- 后端第二次：exit 1，143 项中 1 failure/1 error，原因是两个既有留档测试要求 `zhku.rulecheck.evidenceDir`，未提供。HTTP 生命周期自身通过；[日志](student-page-backend-regression-rerun.log)。后续使用独立证据目录重跑单元部分，HTTP 另测，避免原 HTTP 夹具原始响应写入持久证据。

本轮后续命令（均使用 Maven 离线依赖，先清空子进程的模型/数据库凭据变量）：

```powershell
mvn.cmd -o -pl knowledge -am '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=RuleAgentTaskStoreTest,RuleAgentRunnerTest,RuleAgentEvidenceToolsTest,RuleAgentProviderTest,RulePublicationGateTest,RuleAgentThreeQuestionScopeTest' "-Dzhku.rulecheck.evidenceDir=$PWD/docs/evidence/rule-check-refactor/2026-10-27/student-page-unit-evidence" test
# 从 D:\alldesk\Projects\ZhkuAgent 启动，脚本固定 reactor 到 mainline：
& 'D:\alldesk\Projects\ZhkuAgent\worktrees\integration\mainline\knowledge\scripts\start-rule-agent-three-question-test.ps1' -Operation PageFixture -HttpPort 18087 -PageEvidenceDirectory 'D:\alldesk\Projects\ZhkuAgent\worktrees\integration\mainline\docs\evidence\rule-check-refactor\2026-10-27\student-page-fixture-01'
# 以下命令回到 mainline 执行：
mvn.cmd -o -pl knowledge -am '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=RuleAgentStudentPageProxyTest,RuleAgentTaskHttpTest' '-Dzhku.page.proxyPort=5174' "-Dzhku.page.proxyEvidence=$PWD/docs/evidence/rule-check-refactor/2026-10-27/student-page-fixture-01" test
```

- 后端最终回归：exit 0，142/142，0 skipped；[日志](student-page-backend-regression-final.log)。Provider 测试仅使用本地替身，不调用外部供应商。
- 宿主已启动：监听 18087，schemaVersion=3，providerCalls=0、databaseCalls=0、TEST_SIMULATION_NO_PROVIDER；[ready](student-page-fixture-01/browser-ready.json)。前端 5174 监听记录：[日志](student-page-preview.log)。
- 首次代理测试：exit 1，GET `/rules` 等待 90 秒超时，尚未进入登录；[失败日志](student-page-proxy-http.log)。不带认证的本机 `Invoke-WebRequest -NoProxy -TimeoutSec 8` 同路由返回 200、956 bytes。Java 测试客户端默认 h2c Upgrade 与 Vite 升级处理不兼容；显式 HTTP/1.1 并给页面请求设 5 秒超时后，同一页面/宿主通过，未改生产业务处理。
- 代理/HTTP 最终回归：exit 0，2/2，0 skipped；[日志](student-page-proxy-http-final.log)。经 5174 前端代理核验 14 类检查：构建页面 HTTP 200、匿名/错误合成登录 401、维护角色/缺 CSRF 403、幂等重复 202 与内容冲突 409、同学生不同 Session 404、ACTIVE→WAITING_USER→RESOLVED、旧版本 409、原文本人 200/他会话 404、资料不足无断言、FAILED、取消 STOPPED、退出后旧任务不可见；[去标识状态记录](student-page-fixture-01/proxy-http-result.json)。这里只访问实际页面资源与页面同源 API，未执行页面 JS，仍不算真实浏览器通过。
- 宿主在写入本轮 STOP 后正常退出，exit 0、1/1；[日志](student-page-host.log)；[退出回执](student-page-fixture-01/browser-host-exit.json)证明监听和 worker 退出、activeJobs=0、Provider/数据库调用均为 0。Vite preview 用所属 job 精确取消（killed；主动清理，非测试失败）。

## 浏览器证据边界

已实际尝试 BrowserSkill `bsk_session(action=start)`，工具返回：

```text
Error: the bsk CLI ("bsk") was not found.
```

未能创建浏览器会话，故未实际点击 `/rules`，没有浏览器 Network/控制台/截图、动态加载或最终 DOM 成功证据。没有换用独立脚本/SSR 冒充浏览器验收，也没有安装或改配用户浏览器。真实 HTTP 代理测试只证明传输/接口部分；SSR 仅证明覆盖的渲染分支。

## Git、资源清理与唯一阻断

- 初始分支：`integration/zhkuagent-mainline-20261024`；HEAD：`245880b6b8a62e2bdef69dcb8d6721f72164ceef`。
- 初始已有三份生产 Java 修改以及三题/rc2/其他证据等未跟踪内容。本轮不覆盖、不清理旧工作，不复用已存在的 Live 授权/保留文件。
- 当前 HEAD 仍为 `245880b6b8a62e2bdef69dcb8d6721f72164ceef`，分支未变；工作区未提交，8 个 tracked 文件修改（含原有 3 个生产 Java 文件），新增/原有 untracked 保留，index 无差异，无 commit/push。完整状态见[最终检查](student-page-final-check-confirmed.log)，exit 0，`git diff --check`=0。
- 本轮只清理自己创建的 preview 进程和 PageFixture 租约。第一次清理检查发现取消 preview job 后 Node 子进程仍占 5174，故[初次检查](student-page-final-check.log) exit 1；随后核对回环端口、原 PID 41320、Node 可执行路径与启动时间 14:45:41，精确停止该本轮进程，没有按名字批量杀进程。最终 5174/18087 均无监听，已知 Java PID 24944 与 Node PID 41320 均不存在。不建删 Docker、数据库或其他持久化资源。
- **上一轮记录的页面验收阻断：BrowserSkill 缺少 `bsk` CLI。** 环境恢复后还需在真实 `/rules` 点击验证登录、加载、澄清续答、证据展开和错误状态。真实模型验证仍须另行明确授权。

## 浏览器续验 02（2026-10-27，Asia/Shanghai）

### 本轮结论：BLOCKED，未执行浏览器交互

本轮按要求先排查本机控制能力，未盲目安装、下载依赖、修改浏览器设置或生产代码。Chrome 已安装且存在运行进程；但本会话提供的两个本机控制通道均不可用。按“确实没有可用控制能力即停止”的要求，没有启动前端 preview 或持久 PageFixture 宿主空跑；只完成能力核对、相关离线回归和资源检查。**未完成登录、提问、加载、最终答案展示、证据展开、错误界面或控制台的真实浏览器验收。**

[能力与工具错误记录](browser-recheck-02-capabilities.json)是对本轮实际工具返回的脱敏整理，不是浏览器 Network 导出，也不是自动化成功回执。

### 1. 本机浏览器与替代路径核查

| 核查项 | 实际结果 |
|---|---|
| 本机 Chrome | `C:\Program Files\Google\Chrome\Application\chrome.exe` 存在，且有正在运行的 Chrome 进程；没有关闭或附着用户已有窗口。 |
| 本机 Edge | 可执行文件存在，也有运行进程；安装浏览器本身不代表当前工具能控制它。 |
| BrowserSkill 已连接会话 | `bsk_session(action=list)` 返回 `no active browser sessions`；这仅代表本工具的受管会话，不是所有 Chrome 窗口清单。 |
| BrowserSkill 实际启动 | `bsk_session(action=start, url=about:blank)` 失败，提示缺少 `bsk` CLI。PATH 与两个常用本机候选位置均未发现该可执行文件。没有获得 session ID，没有创建可清理的 Agent Window。 |
| 不依赖 bsk 的内置工具 | 已尝试 `dsh_browser_session()`；仅注册的 Electron provider 不可用，报无可启动的 Electron；`ELECTRON_PATH` 未配置。没有执行错误提示中的安装命令。 |
| 项目 Playwright/Puppeteer/Selenium | 在前端目录用 Node `createRequire(...).resolve(...)` 核对 `playwright`、`playwright-core`、`@playwright/test`、`puppeteer`、`puppeteer-core`、`selenium-webdriver`，均为 `NOT_RESOLVABLE`；项目文件检索也未发现可直接复用的对应控制脚本。该结果不等于穷举本机所有软件。 |
| 既有浏览器测试环境 | [受控 HTTP 夹具](../../../../agent/frontend/tests/controlled-browser-fixture.mjs)、[Java 学生页面代理](../../../../knowledge/src/test/browser/reviewed-student-browser-proxy.mjs)、[历史 attach 代理](../../t06-t07/2026-10-14/student-entry-01/two-window/browser-attach-proxy.mjs)是 HTTP 服务/代理，不是本机浏览器控制器，不能生成本次点击与 DOM 成功证据。 |
| 现有安全启动入口 | 已核对[启动脚本](../../../../knowledge/scripts/start-rule-agent-three-question-test.ps1)的 `PageFixture` 分支：沿用回环监听、独立目录、零 Provider/数据库装配，不触碰 Live 授权。控制能力未就绪，故本轮未执行该入口。 |

两次失败的工具返回（工具未启动浏览器，故不冒记为进程 exit 1）：

```text
bsk_session(action=start, url=about:blank):
Error: the bsk CLI ("bsk") was not found. BrowserSkill must be installed and on PATH for browser tools to work — install it from https://github.com/Tencent/BrowserSkill (see the README install script or `cargo install`), then retry.

dsh_browser_session():
Error: browser provider(s) electron are registered but report themselves unavailable — for dsh-builtin-browser this means no spawnable Electron binary was found; run `npx install-electron` in the active profile or set ELECTRON_PATH
```

结论不是“所有浏览器自动化都必须装 bsk”，而是“BrowserSkill 依赖 bsk；本次也尝试了独立内置工具，但其本机 carrier 同样不可用”。没有用云端浏览器、另一套未经核对的自动化或 HTTP/SSR 替代真实浏览器。

### 2. 浏览器验收与证据清单

| 要求 | 本轮状态 |
|---|---|
| 实际 `/rules` 登录、提交已有问题 | NOT_EXECUTED，控制通道在导航前失败。 |
| 浏览器请求、响应真实到达 Java FIXTURE | NOT_EXECUTED；上一轮 HTTP/代理 2 项成功证据保留，但不改称浏览器请求。 |
| 动态加载、任务状态、最终 DOM、证据一致性 | NOT_EXECUTED；本轮 SSR/API 契约回归不能填补该缺口。 |
| 错误界面与浏览器控制台 | NOT_EXECUTED，不声称“控制台无错误”。 |
| 实际截图与浏览器网络证据 | **无**；没有生成空截图、伪造 HAR 或复用历史截图充数。 |
| 密码、Cookie、Session/续答 token | 未提取、输出或持久化；未登录任何浏览器账号。 |

本轮证据均在当前报告同目录：

- [能力与阻断记录](browser-recheck-02-capabilities.json)。
- [前端回归日志](browser-recheck-02-frontend.log)。
- [后端局部回归日志](browser-recheck-02-backend.log)。
- [最终 Git 与端口检查](browser-recheck-02-final-check.log)。

### 3. 实际命令、结果与退出码

安装与命令查找使用 `Get-Command`、`Test-Path`、`Get-Process`；只查看程序路径/进程身份，没有读取浏览器命令行、用户数据目录或凭据。项目依赖核对采用以下只读检查，exit 0：

```powershell
# 工作目录：mainline/agent/frontend；不执行 npm/npx 安装
node --input-type=module -e "import{createRequire}from'node:module';const r=createRequire(process.cwd()+'/package.json');for(const n of ['playwright','playwright-core','@playwright/test','puppeteer','puppeteer-core','selenium-webdriver']){try{console.log(n+'='+r.resolve(n))}catch(e){if(e.code!=='MODULE_NOT_FOUND')throw e;console.log(n+'=NOT_RESOLVABLE')}}"

# 同目录；输出至 browser-recheck-02-frontend.log
node --test tests/rule-task-proxy.test.mjs tests/rule-task-page.test.mjs tests/rule-task-workflow.test.mjs tests/rule-agent-contract.test.mjs tests/rule-check-api.test.mjs tests/student-routes.test.mjs

# 工作目录：mainline；先仅移除本次子进程的模型/数据库凭据变量，不读取其值
# 不设置 zhku.rulecheck.evidenceDir，避免 HTTP 夹具原始响应/token 落盘
mvn.cmd -o -pl knowledge -am '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=RuleAgentTaskHttpTest,RuleAgentThreeQuestionScopeTest' test

git diff --check
git diff --cached --quiet
git status --short --branch
git rev-parse HEAD
Get-NetTCPConnection -State Listen -LocalPort 5174,18087 -ErrorAction SilentlyContinue
```

- 前端：**exit 0，99 tests / 99 pass / 0 fail / 0 skipped**。内容为 SSR、契约、API/workflow 与代理地址约束测试，不是真实浏览器。
- 后端：**exit 0，26 tests / 0 failure / 0 error / 0 skipped，BUILD SUCCESS**。其中 HTTP 生命周期 1 项、三题执行范围 25 项；不把上一轮 142 项写成本轮重跑。HTTP 测试只短暂启动回环随机端口的无数据库、无供应商 Java 合成夹具，测试结束验证端口关闭和 activeJobs=0；没有启动持久 PageFixture 或前端服务器。
- 上一轮的前端构建、142 项后端、2 项代理/HTTP、1 项宿主生命周期结果是历史记录，本轮未重复这些完整批次，也未新增评测问题。
- 最终检查 exit 0：`git diff --check`=0、index 无差异；页面端口 5174/18087 均无监听。状态与 HEAD 见[最终检查日志](browser-recheck-02-final-check.log)。

### 4. 能力边界、清理与下一步唯一阻断

- FIXTURE 仍只证明合成认证/规则/答案的传输与接口约束；固定映射和固定答案绝不能称为真实模型理解或生成。
- 真实问题理解、Agent 决策/工具选择、真实资料检索、充分性与发布判断、模型生成答案的可信度，仍须另行授权的真实模型端到端验证；本轮调用 DashScope/Embedding/收费模型均为 0，不复用任何旧 Live 授权。
- 本轮没有修改生产/测试业务代码、安装新包、改 Runtime/检索基线、增 Memory/Skill/MCP、操作生产数据库或 Docker、commit/push，也没有清理原有未提交内容。只补充本报告及本轮诊断/回归/检查日志；Maven 常规生成产物保留。
- 未创建可用浏览器会话、持久宿主或 preview 进程；局部 HTTP 回归的临时 Spring context 正常退出，无需强杀进程。原有 Chrome/Edge 进程未动。
- **下一步唯一阻断：先恢复一个经验证可用的本机浏览器控制通道。** 可恢复 BrowserSkill 的本机 CLI/连接，或配置已有内置工具可启动的本机 carrier；这属于环境补齐，不应靠生产代码绕过。恢复后再从真实 `/rules` 登录开始执行本轮尚未执行的点击、Network、控制台和截图验收。本次到此停止，不擅自安装或继续扩大范围。

## 浏览器续验 03：本机 Chrome/CDP 实际页面验收（最终批次 07）

### 1. 最新结论与边界

**PASS：真实本机 Chrome 对 Java FIXTURE 的学生页面验收通过。** 最终浏览器运行 exit 0、12 个检查通过；[机器结果](student-page-cdp-07/browser-result.json)与[独立证据核对](student-page-cdp-07/review.json)一致。执行时间为 2026-10-27 16:00:18–16:00:31（Asia/Shanghai）。

- A 页面：真实 Chrome 执行现有 Vue JS、填写并点击真实控件，登录、提交、动态轮询、澄清续答、最终答案及原文、资料不足和错误界面均有实际证据。不再只有 SSR/HTTP client 证据。
- B FIXTURE：身份、规则、答案、澄清及问题映射均为现有合成替身；页面显著声明此边界。Java Security/Session/CSRF、TaskController、TaskStore、原文接口与同源代理参与实际 HTTP 流程。
- C 真实模型：**本轮 NOT_EXECUTED**，Provider/Embedding/数据库调用均为 0；不使用旧 Live 授权。不能由本次结果推导真实问题理解、Agent 工具决策、真实规则检索、证据充分性及模型答案可信度已经通过，也不改写其他批次的模型评测历史。

### 2. 实际控制实现与隔离

复用 `C:\Program Files\Google\Chrome\Application\chrome.exe`，CDP 返回 `Chrome/156.0.8078.48`；Node `v24.19.0` 原生 `WebSocket` 可用。已核对系统 .NET `ClientWebSocket` 类型可用，但未再换用另一套实现。本轮没有再次尝试 bsk，没有安装 Playwright/Puppeteer/Electron 或其他依赖。

[验收驱动](student-page-cdp-03-run.mjs)使用 Node 内置 child_process/http/WebSocket/fs：独立临时 profile、`--headless=new`、回环调试端口 19227，未加 `--disable-web-security`。未附着、重启或读取用户原有 Chrome 会话。正式 `/rules` 没有替换 HTML、注入答案或拦截伪造接口响应；JS 仅用于真实控件交互、状态观察及白名单证据提取。

先访问独立的回环 smoke 页面，实际读取标题/URL、点击改变 DOM、保存真实截图，再由外部 PowerShell 核对调试监听必须为 `127.0.0.1:19227`、PID 与 Chrome 可执行路径相符，写入本轮允许标记后才继续。最终 [CDP smoke/版本](student-page-cdp-07/cdp-ready.json)、[回环进程身份](student-page-cdp-07/owned-processes.json)、[smoke 截图](student-page-cdp-07/00-cdp-smoke.png)均保留。

真实页面为 `http://127.0.0.1:5174/rules`，Java 为 `http://127.0.0.1:18087`。独立 student/http-fixture 构建没有覆盖既有 dist；宿主沿用已有 `PageFixture` 启动器。最终浏览器 07 复用仍存活的本轮 06 宿主，其 [ready](student-page-cdp-06/host/browser-ready.json)明确 `TEST_SIMULATION_NO_PROVIDER`、无 OnlineFactory/DataSource，最终正常退出。

### 3. 实际页面检查与截图

| 流程 | 实际观察与结果 | 真实截图 |
|---|---|---|
| 匿名打开 `/rules` | 登录框与 FIXTURE 声明可见；匿名 auth/me 401 被正常处理 | [登录页](student-page-cdp-07/01-login-empty.png) |
| 错误合成登录 | 请求真实返回 401，页面显示错误；截图前清空口令字段 | [拒绝登录](student-page-cdp-07/02-login-rejected.png) |
| 学生登录、提交 E04 | 真实登录成功，提交“我那门选修挂了，下次直接再上一遍就好了吧？”；快照 ACTIVE→WAITING_USER | [澄清界面](student-page-cdp-07/04-clarification.png) |
| E04 澄清续答 | 选择公选并实际续答，ACTIVE→RESOLVED；展开关联证据并点击“核对原文” | [答案、证据及原文](student-page-cdp-07/05-answer-evidence-original.png) |
| 最终内容一致性 | DOM 标题“有依据可确认”；确认内容“合成确认内容”；关联引用与原文均为“合成条款：仅用于接口验收。” | 同上；[DOM 白名单核对](student-page-cdp-07/browser-result.json) |
| E13 资料不足 | 提交“这学期公选补选今晚几点关？现在还能帮我补上吗？”；实际加载后显示资料不足，不捏造截止时刻/办理成功 | [加载状态](student-page-cdp-07/03-active.png)、[资料不足](student-page-cdp-07/06-data-gap.png) |
| 既有故障输入 | 实际等待后快照 FAILED，页面显示本轮处理失败，而非成功答案 | [故障界面](student-page-cdp-07/07-failure.png) |
| 退出登录 | 清空前次结果，回到登录界面 | [结果检查](student-page-cdp-07/browser-result.json) |
| 关闭本轮 Java 的负控 | STOP 后确认监听/worker 退出；重新打开页面 auth/me 返回 500，显示“暂时无法核对登录状态，请稍后重试。” | [Java 停止后的页面](student-page-cdp-07/08-java-stopped-negative-control.png) |

截图均为实际 Chrome `Page.captureScreenshot` 输出，1440×1100；不是绘图、SSR 或旧截图替代。[核对记录](student-page-cdp-07/review.json)保存各图 SHA-256。没有扩充正式 Eval 集；只操作现有 E04/E13 夹具映射及既有故障分支。

### 4. 请求确实经过代理到达 Java，而非前端 mock

[浏览器 Network 白名单](student-page-cdp-07/browser-network.json)包含最终批次 30 次 API 请求，记录实际方法、去标识路由、HTTP 状态、时间、回环远端地址，以及安全的任务状态/coverage 响应摘要；包含 `/auth/login`、`/tasks`、`/messages`、`/originals/` 和真实 ACTIVE/WAITING_USER/RESOLVED/FAILED 快照。没有保存请求头、登录正文或原始响应正文，所有不透明路径标识均移除。成功请求 `fromServiceWorker=false`、远端为 `127.0.0.1:5174`。

[本轮仅观测的代理记录器](student-page-cdp-03-proxy-audit.mjs)由 Node `--import` 加载，只观察原有 Vite `http.request` 的回环 18087 下游响应/错误，不改变请求/响应。[Java 下游代理记录](student-page-cdp-03/proxy-to-java.jsonl)按最终运行时间可对应 30 次下游尝试；此前失败批次也保留，不混作最终运行。独立 [核对脚本](student-page-cdp-06-review.mjs)实际断言登录、任务、续答、原文均出现 Java 下游 2xx。

更强的因果负控：通过本轮 STOP 关闭 Java 后，代理出现 `ECONNREFUSED`，真实 Chrome auth/me 随之 500，页面出现不可用提示。该负控与正常成功响应共同支持“真实 Java HTTP 链路”，而非前端固定返回。30 次中包含这次故意失败的下游连接，不声称 30 次全部成功到达 Java。

[浏览器控制台](student-page-cdp-07/browser-console.json)没有 runtime exception，但**不是零错误**：匿名/错误登录 401 是预期；favicon.ico 404 为既有非阻断资源问题，未为此扩展产品代码；Java 关闭后的 500 是预期负控。保留这些记录，不压掉错误来宣称全绿。

### 5. 实际命令、退出码、失败证据与回归

所有命令工作目录为 mainline，前端命令在 `agent/frontend`。启动与退出核对均限定本轮 PID/启动时间/可执行文件，未打印口令、Cookie 或 token。

```powershell
# 隔离构建与真实 Vite preview；回环目标需显式设置
$env:VITE_ENTRY_MODE='student'; $env:VITE_DEMO_MODE='http-fixture'
$env:VITE_RULE_TASK_PROXY_TARGET='http://127.0.0.1:18087'
$env:ZHKU_CDP_PROXY_AUDIT='<mainline>/docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-03/proxy-to-java.jsonl'
node node_modules/vite/bin/vite.js build --outDir dist-page-cdp-03
node --import ../../docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-03-proxy-audit.mjs node_modules/vite/bin/vite.js preview --outDir dist-page-cdp-03 --host 127.0.0.1 --port 5174 --strictPort

# 最终存活宿主 06：仅在 Chrome loopback smoke 与 PID 核验后启动
./knowledge/scripts/start-rule-agent-three-question-test.ps1 -Operation PageFixture -HttpPort 18087 -PageEvidenceDirectory '<mainline>/docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-06/host'
# 最终浏览器 07：独立新 profile，复用上述已就绪宿主；并行外部核验后写入该批 ALLOW-ACCEPT
node docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-03-run.mjs 07 docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-06/host
node docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-06-review.mjs 07 docs/evidence/rule-check-refactor/2026-10-27/student-page-cdp-06/host

# 浏览器验收后重跑，不以历史 142 项冒充本轮重跑
node --test tests/rule-task-proxy.test.mjs tests/rule-task-page.test.mjs tests/rule-task-workflow.test.mjs tests/rule-agent-contract.test.mjs tests/rule-check-api.test.mjs tests/student-routes.test.mjs
mvn.cmd -o -pl knowledge -am '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=RuleAgentTaskHttpTest,RuleAgentThreeQuestionScopeTest' test
```

| 本轮执行 | 退出码与结果 | 日志/失败原件 |
|---|---|---|
| student/http-fixture 构建 | 0，Vite 构建成功 | [构建日志](student-page-cdp-03-build.log) |
| 浏览器 03 | 1，`Timeout: external loopback listener validation`；协调标记未在时限内到达，未进入学生流程 | [03 原件](student-page-cdp-03/browser-result.json) |
| 浏览器 04 | 1，`CDP timeout: Page.captureScreenshot`；首次学生页面截图 8 秒预算不足，改为截图专用 30 秒及稳定捕获选项 | [04 原件](student-page-cdp-04/browser-result.json) |
| 浏览器 05 | 1，主页面流程通过，但 `Timeout: Java host clean shutdown`；验收驱动复用 04 宿主却等待了 05 目录回执。修复为真实 hostDirectory，不改业务代码 | [05 原件](student-page-cdp-05/browser-result.json) |
| 浏览器 06 | 1，`Unexpected end of JSON input`；既有 ready 文件直接写入，文件存在早于 JSON 完整写入。驱动改为等待完整 JSON；复用同一存活宿主 | [06 原件](student-page-cdp-06/browser-result.json) |
| **浏览器 07** | **0，12 检查通过，success=true** | [最终运行日志](student-page-cdp-07-run.log)、[结果](student-page-cdp-07/browser-result.json) |
| 独立关联/脱敏核对 | 0；浏览器/代理记录、负控、9 张截图及敏感字段核对通过；42 份本轮文本证据未发现合成口令值 | [review](student-page-cdp-07/review.json) |
| Java PageFixture 06 | 0，1 test / 0 failures / 0 errors，正常 STOP 退出 | [宿主日志](student-page-cdp-06-host.log)、[退出回执](student-page-cdp-06/host/browser-host-exit.json) |
| 最终前端回归 | **0，99/99，0 fail，0 skipped** | [前端日志](student-page-cdp-07-frontend-regression.log) |
| 最终后端局部回归 | **0，26/26，0 failure/error/skipped**（HTTP 生命周期 1＋三题范围 25） | [后端日志](student-page-cdp-07-backend-regression.log) |
| preview 主动清理 | 所属 Node 精确停止后外层 job 为 4294967295；主动资源清理，不是构建或页面测试失败 | [清理回执](student-page-cdp-07/cleanup-confirmed.json) |

没有删除或覆盖失败批次；它们仍为 success=false，不由最终通过覆盖。修正均限于本轮验收驱动的协调/截图超时/路径/文件就绪读取；没有为通过验收修改生产或测试业务代码。

### 6. 敏感数据、Git 与资源收尾

- 不读取真实 API Key/数据库凭据/用户浏览器资料；夹具口令仅从已有合成测试源在内存传给浏览器，截图前清空。没有输出/保存口令、Cookie、Session/CSRF/续答/原文 token；CDP 原始事件与协议头不落盘，控制台参数值不保存。[独立核对](student-page-cdp-07/review.json)同时检查口令值与禁止的原始网络字段。
- 最终 Chrome PID 42040 通过 Browser.close 正常退出（exit 0），独立临时 profile 已删除；03–06 的本轮 Chrome/profile 也核对已退出/删除。没有停止用户既有 Chrome/Edge。
- Java STOP 后监听/worker 均退出、activeJobs=0。preview 仅在确认 PID 44652、Node 路径、启动瞬间与独立构建/观测器标记一致后停止；首次身份检查因 PowerShell 自动把 JSON 日期转为 DateTime 而字符串比较失败，未杀任何进程，改成 UTC 瞬间比较后全部一致才停止。[资源回执](student-page-cdp-07/cleanup-confirmed.json)确认 5174、18087、19227 全无监听。
- 本轮仅新增验收驱动/观测器、截图和日志并更新本报告；独立构建产物保留用于复现，没有覆盖既有 dist 或旧证据。未安装依赖、未建删数据库/Docker、未修改 Runtime/检索基线、未添加 Memory/Skill/MCP、未执行 commit/push。
- [最终 Git/端口检查](student-page-cdp-07-final-check.log)：exit 0，`git diff --check`=0，index 无差异；分支仍为 `integration/zhkuagent-mainline-20261024`，HEAD 仍为 `245880b6b8a62e2bdef69dcb8d6721f72164ceef`。既有 8 个 tracked 修改及原有 untracked 内容保留，没有把原有业务修改算成本轮变更。

**到此停止。浏览器控制与本机 Java FIXTURE 页面链路的旧阻断已解除；本轮不启动真实模型验收，不把合成答案冒充学生真实问题的可信答案。**
