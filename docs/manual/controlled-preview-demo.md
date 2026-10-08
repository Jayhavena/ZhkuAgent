# 4A＋3B 人工演示

本指南供当前本机受控原型人工验收。控制台菜单发出**真实HTTP请求**，服务是既有 `ControlledPreviewApplication`；认证、CSRF、归属、资料许可和JDBC使用真实实现。模型仅为 `src/test` 中的确定性替身，政策和课程均为合成数据，不能证明真实模型或真实学生效果。

没有新增前端、生产假模型开关、治理API或业务。不要用旧匿名页面判断受控入口的权限。

## 启动

在仓库根目录打开 Windows PowerShell、PowerShell 7 或CMD，确认 Docker 已启动、其他会话已保存并暂停写入，且没有并行Maven或此前未关闭的演示。

```powershell
Set-Location 'D:\alldesk\Projects\ZhkuAgent'
.\knowledge\scripts\start-controlled-manual-demo.cmd
```

默认使用专用临时库端口 `55438`；HTTP随机分配可用端口，只绑定 `127.0.0.1`。显示 `服务已就绪 PID=... URL=...` 后输入菜单命令。脚本先执行1项classpath导出/编译检查，这不是产品验收。

包装入口会依次查找PATH、标准安装目录及本机Codex内置PowerShell 7，进程内启动，不修改全局PATH或执行策略。当前机器没有 `C:\Program Files\PowerShell\7\pwsh.exe`，但Codex内置版本存在；不能把Codex里的 `Get-Command pwsh` 成功当作用户终端可执行。出现“pwsh无法识别”时请使用上面的 `.cmd`，无须先安装软件。可传 `-CheckEnvironment` 做无数据库/模型/构建副作用的检查。

### Windows PowerShell 5.1 中文乱码

如果已启动，请先输入 `q` 正常退出，然后在原终端复制以下**整段**再启动：

```powershell
$demoUtf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $demoUtf8
[Console]::OutputEncoding = $demoUtf8
$OutputEncoding = $demoUtf8
& "D:\alldesk\Projects\ZhkuAgent\knowledge\scripts\start-controlled-manual-demo.cmd"
```

外层5.1可能在启动子进程前已选择GBK解码器；子进程里的chcp或file.encoding不能修复已经误解码的字符，所以需在调用前同步外层三项编码。只影响当前会话，不修改注册表、全局PATH或默认系统语言。包装入口临时切换代码页并恢复，内层PS7和Java固定UTF-8。`-CheckEnvironment`中的三个codePage应为65001，encodingSample应显示“中文菜单：登录、退出”。旧的乱码行不会自动恢复。

本机已核对Java21、PowerShell7和Maven3.9.15。Maven默认路径 `D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd`，其他机器可用 `-Maven` 指定现有安装；不新增依赖或下载新的技术栈。若端口占用，可传 `-DatabasePort 55439`，不能使用 `54329`。菜单默认总上限30分钟，可用 `-MaxMinutes 10` 缩短。

随机临时数据库密码仅通过环境传递。三名合成账号的密码、登录Cookie、CSRF只在Java内存中保存，不打印、不写报告；菜单替你完成正常框架登录，不绕过认证。此入口不需要供应商API Key，不启动真实模型。不要在演示过程中同时运行另一套验收。

## 菜单

每次输入一条命令并回车，不要一次粘贴整个长流程。

| 命令 | 动作 |
| --- | --- |
| `a` / `b` / `m` | 切换A、B、MAINTAINER的独立Cookie客户端；不会自动登录 |
| `1` | 以当前合成账号走框架登录及CSRF流程 |
| `2` | 提交固定学分请求，并在最多10秒内轮询终态 |
| `3` / `4` | 查询 / 取消最近Run |
| `5` / `6` | 本人历史列表 / 最近Run历史详情 |
| `7` | 创建当前账号的业务Session |
| `8` | 发送“选修课挂科了可以重修吗？” |
| `9` | 发送“专业选修课”，使用最近确认的revision |
| `10` / `11` | 查询 / 取消最近Session |
| `12` | 故意使用revision=0补答，观察冲突 |
| `13` | 停止并重新启动真实服务进程，保留本次数据库 |
| `14` / `15` | 撤回 / 恢复本次合成学分资料 |
| `16` / `17` | 撤回 / 恢复本次合成重修资料 |
| `h` / `q` | 帮助 / 退出并清理 |

菜单显示实际HTTP状态和JSON响应。A/B/M共用“最近Run/Session ID”，方便直接对同一资源做越权对照；这是客户端变量，不是服务器权限。身份始终由服务器登录决定。撤回/恢复是演示宿主对两个合成UUID的准备动作，标注“非HTTP接口”；MAINTAINER本身仍无治理能力。

## 五组最明显的人工体验

### 1. 成功学分＋账号隔离

1. 初始A，输入 `1` 登录，再输入 `2`。
2. 请求固定包含ART01公共选修1.5学分、HIS02公共选修2学分，合成上限4学分。先看到202受理，再看到 `SUCCEEDED`、`terminated=true`、`VISIBLE` 和3.5学分答案/合成出处。
3. 输入 `5` 查看A的历史。
4. 输入 `b`、`1`、`3`、`4`、`6`：B查不到、不能取消A的Run，也读不到历史详情，返回404；`5` 的B历史为空。
5. 输入 `m`、`1`、`3`、`5`：维护者返回403。再输入 `a`。

替身任务通常立即完成，因此对已完成Run输入 `4` 不能当作“运行中取消”验收。明显的取消体验使用第4组；运行中Run取消/超时仍有独立自动化验收证据。

### 2. 重启后继续澄清

1. A已登录，依次输入 `7`、`8`、`10`。
2. 看到 `NEED_CLARIFICATION`，Session `pendingIntent=ELECTIVE_RETAKE_AFTER_FAILURE`、`revision=1`，记下 `expiresAt`。
3. 输入 `13`。新旧服务PID不同，旧PID先退出；旧Cookie探测返回401。输入 `10` 仍应401，不会替你自动登录。
4. 输入 `1` 重新登录，再输入 `10`：仍是同一Session、revision1和原expiresAt。
5. 输入 `9`：得到重修 `ANSWER` 和合成政策来源；`10` 显示revision2、pendingIntent已清空。

整个流程须在业务Session当前10分钟TTL内完成。重启不延长TTL。恢复仅限澄清控制状态，不保存完整聊天记录、不自动续跑Run、不关联Run和Session。

### 3. 已确认历史＋资料撤回

1. 第1组完成后，A输入 `14` 撤回学分合成资料，再输入 `3`：执行仍是 `SUCCEEDED`，但 `resultVisibility=HIDDEN`，answer/source为null。
2. 输入 `6`：历史正文也隐藏，不能用归档绕过当前资料许可。
3. 输入 `15` 恢复资料，再输入 `6`：合成答案重新可见。
4. 输入 `13`、`1`，再输入 `3`、`6`：旧在线Run返回404，确认历史仍为 `FINALIZED` 且可读。这里没有恢复在线Run或重放模型。

### 4. 版本冲突＋取消不复活

1. A输入 `7`、`8`：待澄清revision1。
2. 输入 `12`：旧revision0请求返回409 `SESSION_STATE_CHANGED`，没有覆盖确认状态。
3. 输入 `11`：Session取消，revision增加，expiresAt不延长。
4. 输入 `9`：409 `SESSION_CANCELLED`，不会复活。下一段问题须显式用 `7` 新建。

### 5. 证据不足时不返回旧答案

1. A输入 `7`、`8`，再输入 `16` 撤回合成重修资料。
2. 输入 `9`：返回 `EVIDENCE_UNAVAILABLE`，answer为空，没有可展示出处，提示需要人工确认。
3. 输入 `17` 恢复合成资料。学分路径也可输入 `14`、`2`，观察新Run `INCOMPLETE/HANDOFF/INSUFFICIENT_EVIDENCE`，最后 `15` 恢复。

## 请求形状

菜单调用前缀是启动时显示的 `http://127.0.0.1:<随机端口>/api/zhku/controlled-preview/v1`。下面只展示业务请求，不提供或落盘登录密码、Cookie、CSRF。若改用其他HTTP客户端，必须自己维护独立客户端登录状态，先GET `/auth/csrf`，POST表单 `/auth/login`，登录后重新取得CSRF并用于写请求。

```http
POST /runs
Content-Type: application/json

{"question":"这两门虚构公共选修课学分合计以及每学期上限是多少？","courses":[{"courseId":"ART01","category":"公共选修课","credits":1.5},{"courseId":"HIS02","category":"公共选修课","credits":2}]}
```

```http
POST /conversations
Content-Type: application/json

{}
```

```http
POST /conversations/<sessionId>/messages
Content-Type: application/json

{"message":"选修课挂科了可以重修吗？","expectedRevision":0}
```

```http
POST /conversations/<sessionId>/messages
Content-Type: application/json

{"message":"专业选修课","expectedRevision":1}
```

演示账号的随机密码不对外展示，因此现成菜单是首选。另配外部客户端账号属于独立准备，不要从日志提取密码或Cookie。

## 退出与验证

输入 `q` 正常退出，或输入流EOF/达到上限。脚本会检查服务进程、业务线程、监听及数据库连接退出，再停止并删除**本次随机名称的临时容器**。下次启动是全新的演示库；不会清除原54329演示库，不会改旧迁移或生产配置。

建议用 `q`，不要关闭终端或强杀进程。异常中断是清理失败诊断，不把Future取消或强杀当作正常验收。若脚本报告资源未确认退出，lease保持ACTIVE，先查看本轮记录，不再启动另一套Maven；不能直接改成NONE。

本轮结果位置为 `docs/evidence/manual-demo/2026-10-05/<时间-随机ID>/`，包含选类、源码哈希、脱敏XML、迁移版本、资源退出和结果。没有保存完整手动HTTP响应或模型Prompt。

重新运行自动验证（串行执行，正常退出后再运行下一条）：

```powershell
.\knowledge\scripts\start-controlled-manual-demo.cmd -Verify
.\knowledge\scripts\start-controlled-manual-demo.cmd -Smoke
```

`-Verify` 运行新增菜单组合及既有相关真库/HTTP/跨PID回归；`-Smoke` 自动发送一段固定菜单序列，检查实际启动脚本和最终清理，是演示冒烟，不当作人工用户反馈。只运行默认命令才由人逐项操作。

当前验收和未验证项见 [本任务证据](../evidence/manual-demo/2026-10-05/README.md)，既有 [4A＋3B整合核验](../evidence/milestone-4a/2026-10-05/A/20-4a-3b-integration-review.md)。真实模型、非本机部署、真实用户效果、完整聊天历史、自动续跑与不可变政策证据均不在本任务中。
