# 两窗口身份失效验收（attach-only 工具）

**本目录不是验收结果，是等待宿主就位期间准备好的可执行验收工具及其机制级自检。** T07 的真实应用级两窗口验收仍为**待验证**。

## 1. 为什么是 attach-only

T07 的卡片要求"最终浏览器验收等待验收宿主 Agent 提供稳定提交及退出方式""不绕过退出卡点"。因此本工具**绝不启动任何应用宿主**（不起 Java、不起 nginx、不起数据库、不起容器）：

- 传入的 `--base-url` 不可达时**直接拒绝执行**（exit 2），实测输出：
  `FAILED: 拒绝执行：http://127.0.0.1:8199 不可达。本工具不启动宿主，请先由验收方提供宿主`，
  且拒绝时**不创建任何浏览器进程**（msedge 进程集合无新增）。
- `--self-check` 只用**自有的静态夹具页 + 自有端口 8151**，与产品、后端、资料无关。

即：宿主没就位就跑不了，而不是先跑起来再补退出证据。

## 2. 用法

```powershell
# 机制级自检（不需要宿主；已验证通过，见 self-check.json / self-check.log）
$env:NODE_PATH='C:\Users\nangua\.workbuddy\binaries\node\workspace\node_modules'
node .\two-window-acceptance.mjs --self-check --out .\self-check.json

# 应用级两窗口验收（需要宿主就位；本工具不启动宿主）
$env:ZHKU_ACCEPTANCE_USERNAME='<测试账号>'   # 口令只从环境变量读取，绝不打印、不落盘
$env:ZHKU_ACCEPTANCE_PASSWORD='<测试口令>'
node .\two-window-acceptance.mjs --base-url http://127.0.0.1:<宿主端口> --out .\attached.json
```

`playwright-core` 装在托管 node 工作区（本机 1.63.0）；ESM 下用 `createRequire` + `NODE_PATH` 解析，浏览器用系统 Edge（`channel: 'msedge'`），不下载浏览器。

## 3. 机制级自检结果（真实 Edge，2026-10-16）

`self-check.json`，两次运行均 5/5 通过（exit 0）：

| 断言 | 含义 | 结果 |
| --- | --- | --- |
| `twoTabsShareChannel` | **同一浏览器上下文的两个标签页共享 BroadcastChannel**（跨窗口身份撤销所依赖的前提） | true |
| `noEcho` | 接收端收到广播后不回投（对应 `identity-bus` 的回声抑制） | true |
| `receiverDidNotAmplify` | 接收端未放大消息数 | true |
| `browserExitVerified` | 启动后新增的 msedge PID 在关闭后**全部消失** | true（`edgePidsLeaked: []`） |
| `fixturePortReleased` | 夹具端口 8151 释放 | true |

其他记录：`externalRequests: []`、`startedHost: false`、`providerCalls: 0`、`databaseCalls: 0`。

**边界**：这是**浏览器机制级**证明，**不含应用集成**——没有登录、没有任务结果、没有迟到响应、没有真实身份与资料。不得据此声称两窗口验收通过。

## 4. 退出核对协议（本工具遵守的规则）

- **只看命令返回/BUILD SUCCESS 不算退出**。本工具在启动浏览器前记录 msedge PID 基线，关闭后等待至多 10s，要求**"启动后新出现的 PID 全部消失"**（`edgePidsLeaked` 为空）。
- **绝对进程计数不可用作证据**：本机实测同一台机器上 msedge 进程数在 10 → 4 → 12 之间自然波动（用户自有 Edge 在工作），所以计数变化无法归因。只有"启动后新增 PID 的差集"是可归因的。
- 本工具**不触碰用户既有的 Edge 进程**（不按进程名批量终止）。

## 5. 应用级断言清单（宿主就位后执行，当前全部待验证）

1. A、B 两标签页**同一测试身份**登录，均落在学生查证页。
2. A 提交合成问题并取得**已发布结果**（`#decision-title` 可见）。
3. B 执行**退出登录**。
4. A 的已发布结果被**清空**（`#decision-title` 计数为 0），并显示"登录状态已在其他窗口变更，请重新登录。"
5. A 在观察窗口（5s）内**迟到响应不得复活**旧结果。
6. 未获后端确认身份前 **A 不能再次提交**（查证输入区不出现）。
7. A 经**后端确认的新身份**（重新登录成功）后才能继续查证。
8. 记录两侧页面、接口结果、源码 SHA、合同版本、模式与**资源退出**；宿主自身的退出由宿主方按其稳定退出方式核对。

## 6. 不可表述

- ❌ 不得据 `self-check.json` 声称"两窗口已验证""真实身份/资料已验证""整链通过"。
- ✅ 可以说：两窗口验收工具已就绪且**附加式**（不会自行启动宿主）；跨标签页 BroadcastChannel 共享与"接收端不回投"已在系统 Edge 上机制级验证；工具自身的浏览器退出与端口释放按 PID 差集获证。
