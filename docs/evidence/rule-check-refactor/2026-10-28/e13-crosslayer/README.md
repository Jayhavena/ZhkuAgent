# E13 资料不足终态跨层验收证据（2026-10-28）

范围：Java 终态 → HTTP/JSON 响应 → 前端契约解析 → `/rules` 页面渲染。四层均已取得证据；页面产物的受控构建方式与遗留项见文末。

## 产物

| 文件 | 说明 |
| --- | --- |
| `http-e13-insufficient.json` | `GET /tasks/{id}` 的真实 HTTP 响应：E13「今晚补选几点关？帮我补上。」资料不足终态 |
| `http-e13-failed.json` | 「故障」问题响应，用于确认技术失败语义未被改写 |
| `http-e13-summary.json` | 前端契约模块解析结果与终端语义断言摘要 |
| `http-host-mvn.log` / `browser-ready.json` / `browser-host-exit.json` | 夹具宿主运行记录（`providerCalls=0`、`databaseCalls=0`、`activeJobs=0`） |
| `drive.mjs` | 复现用 HTTP 驱动脚本（回环地址、无模型/数据库调用） |
| `frontend-build.log` / `frontend-page-test.log` | 页面渲染层被环境阻断的原始日志 |

## 执行记录

1. Java ↔ 前端原因码一致性（新增自动化检查）

   `cd agent/frontend && node tests/rule-agent-contract-parity.test.mjs` → 2 passed，exit 0

2. 既有共享契约用例

   `cd agent/frontend && node tests/rule-agent-contract.test.mjs` → 45 passed，exit 0

3. 夹具 HTTP 宿主（真实安全链、任务存储与 HTTP 层；无数据库、无供应商）

   ```
   cd knowledge
   MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true" mvn -o -DforkCount=0 \
     -Dtest=RuleAgentTaskBrowserHttpTest -Dzhku.t07.browserLease=true \
     -Dzhku.t07.port=18089 \
     "-Dzhku.rulecheck.evidenceDir=<本目录>" test
   ```

   exit 0；`browser-host-exit.json` 显示 `activeJobs=0`、`providerCalls=0`、`databaseCalls=0`。

   注：`MAVEN_OPTS=-Djdk.attach.allowAttachSelf=true` 仅用于让 Mockito 在沙箱内自附加；不加时 ByteBuddy 会尝试创建子进程并得到 `CreatePipe error=5`。

4. 跨层解析（HTTP 响应交给真实前端契约模块）

   `node drive.mjs 18089` → exit 0，摘要：

   - 资料不足：HTTP 200；前端契约解析通过；`taskStatus=RESOLVED`、`runState=INCOMPLETE`、`coverage=NONE`、`reasonCode=UNSUPPORTED_DEMAND`、`kind=DATA_GAP`；`claims=0`；无臆造时间点；`nextStep` 明确说明无办理能力。
   - 技术失败：「故障」仍为 `taskStatus=FAILED`、`runState=FAILED`、`MODEL_UNAVAILABLE`（`PROCESSING_FAILURE`）。

## 页面渲染层：已用受控方式验证

### 原始阻断（保留原始日志）

- `cd agent/frontend && npx vite build` → exit 1：esbuild 加载配置时报 `Error: spawn EPERM`（见 `frontend-build.log`）。原因：`agent/frontend/node_modules` 是指向工作区外备份目录（`D:\alldesk\_safety_backups\...\worktree-16\files\agent\frontend\node_modules`）的符号链接，沙箱拒绝 node 创建子进程（实测 node 连 `cmd.exe` 也无法 spawn）。
- `node tests/rule-task-page.test.mjs` → exit 1：`Error: spawn EPERM`（见 `frontend-page-test.log`）。

### 绕过方式（只用于取证，未改产品代码）

沙箱只禁止 node 创建子进程，不是禁止读取；因此用等价的内联配置驱动 esbuild 直接构建，不改任何产品文件：

1. 在 `agent/frontend` 下用一个临时脚本以 `configFile: false` 复刻 `vite.config.js` 的插件（`@vitejs/plugin-vue`）、别名（`@` → `src`）与入口，输出到 `knowledge/target/e13-crosslayer/dist-e13`，`target: 'esnext'`、`minify: false`（便于核对产物含 `UNSUPPORTED_DEMAND`）。
2. esbuild 构建前会探测 UNC 时执行一次 `net use`；该处同样被 spawn 拦截，故临时脚本在进程内把 `child_process.exec` 对 `net use` 的探测改为返回 `error`（等价于“无驱动器”），其余构建逻辑不变。
3. 产物与正常 `npm run build` 的差异：未压缩、target 为 esnext。功能语义相同，但不等于发布构建产物。

### 页面验收记录

1. `node knowledge/target/e13-crosslayer/serve.mjs 18089 18090`：同源静态托管 + `/api` 回环代理，避免跨源 Cookie；`STATIC=200`、`API=200`。
2. 用 DSH 内置浏览器（Electron）打开 `http://127.0.0.1:18090/rules`，登录 `student-a`，在 `#rule-question` 提问“今晚补选几点关？帮我补上。”，点击“查证规则”。
3. 页面实际渲染（截图 `page-insufficient-material.png`）：

   - 标题“仍有未确认项”；分节“当前能确认什么 / 还缺什么，下一步怎么办 / 资料缺口”；
   - 文案“当前可用的资料中没有足以确认当期截止时间的依据，无法确认今晚补选几点关闭。”；
   - 下一步“本系统没有办理补选的权限，无法代为办理；请登录教务系统或联系教务处核实当期通知。”；
   - 无编造时间点、无“已代办”表述、无整页解析失败或空白。

4. 对照用例：在同一页面提问“故障”，渲染为“本轮处理失败 / 处理未完成 / 模型处理失败 / 下一步：稍后重新查证”（截图 `page-technical-failure.png`），说明技术失败未被改写成资料不足。
5. 依据来源：本页面链路全程指向回环夹具宿主，宿主 `browser-host-exit.json` 记录 `providerCalls=0`、`databaseCalls=0`；不是真实模型、也不是生产页面。

### 遗留未验证

- 未在解除 sandbox 子进程限制的环境中执行标准 `npm run build`，因此“发布构建产物”本身未验证。
- 页面证据来自本地夹具宿主的合成响应，不代表真实模型在 `/rules` 上的输出。
- 截图由人工操作 DSH 内置浏览器取得；未自动化到测试套件。
