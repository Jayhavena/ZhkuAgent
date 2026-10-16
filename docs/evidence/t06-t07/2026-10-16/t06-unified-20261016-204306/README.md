# T06 统一受控验收脚本（本轮通过）

> 时间 2026-10-16 20:43。对应交接文档 [T06-HANDOFF.md](../t06-handoff/T06-HANDOFF.md) 第 3 节第 3 项
> 「统一受控启动脚本（含普通用户终端 / PowerShell 5.1 GBK 边界与中文编码）」——**该项本轮完成并通过**。
> 全部为本地提交，未 push／PR／标签／部署／合并。

## 0. 一句话状态

新增 `scripts/t06-controlled-acceptance.ps1`：把 T06 三档验收固化的启动参数（一次性临时库每轮重建、
随机端口避开 54329、证据目录、受控启动显式开关、清除宿主注入的 `SERVER__PORT`）收成一条命令。
**最终完整运行（`-Scenario all`）三场景全部通过：各 1 项测试、0 失败 0 错误 0 跳过，脚本退出码 0。**
脚本开发过程实测出并修掉 **5 个脚本自身缺陷**（见 §4），全部保留在案。

## 1. 脚本能力

| 场景 | 内容 | 依赖 |
| --- | --- | --- |
| `existing` | 既有 `RuleAgentTaskHttpTest`（HTTP 契约面：校验/幂等/终态/退出，身份与资料为替身，零数据库） | 无（不建容器） |
| `chain` | `RuleAgentSimulatedChainTempDbHttpTest` 直连方法（真实临时库身份 + 真实已审核资料 + 真实回环 HTTP） | Docker |
| `proxy` | 同测试类代理方法（仓库 `nginx.conf` 经官方镜像模板机制起真实 nginx，归档代理实际响应） | Docker + Python |
| `all` | 依次跑 existing → chain → proxy，任一失败即停 | 视场景 |

公共机制：证据目录每次运行按时间戳新建（`t06-unified-<戳>\<场景>`，不覆盖历史）；maven 前摘除
`SERVER__PORT`（宿主自身占用该端口，属结构性冲突）；口令只经环境变量进子进程（未设置时现场生成
随机口令，`-D` 系统属性会被 fail-fast 拒绝）；临时库容器按名精确回收，`-KeepContainer` 可保留事后检查；
场景结束核对证据文件是否落盘——**测试被假设跳过时会指出缺证据，跳过不算通过**。

## 2. 最终完整运行（本目录即该次证据）

| 项 | 值 |
| --- | --- |
| 运行 | `t06-unified-20261016-204306`，`-Scenario all`，退出码 **0** |
| existing | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`（8.696s）；证据 [`existing/http-exchanges.json`](existing/http-exchanges.json)（25319 字节，46 条交换） |
| chain | 同计数（10.34s）；临时库 `zhku-t06-tmpdb-20261016-204306-1`（127.0.0.1:56884），18 个迁移至 v22，空库证明通过；证据 [`chain/simulated-chain-tempdb.json`](chain/simulated-chain-tempdb.json)（8902 字节） |
| proxy | 同计数（9.786s）；nginx `zhku-t06-nginx-20261016-204306`（127.0.0.1:18082 → 仓库 nginx.conf → host.docker.internal:18083 → 宿主转发 PID 53736 → 后端 127.0.0.1:18081）；证据 [`proxy/proxied-chain.json`](proxy/proxied-chain.json)（7136 字节） |
| 控制台全记录 | [`run.log`](run.log)（脚本 `[t06]` 生命周期行 + maven 输出，0 条错误记录） |
| 资源退出 | 两个临时库容器、nginx 容器按名回收；宿主转发按 PID 终止；无残留 java/python 进程；18081–18083 无监听 |

复现：

```powershell
cd C:/Users/nangua/.codex/worktrees/t06-controlled-launch/ZhkuAgent
.\scripts\t06-controlled-acceptance.ps1 -Scenario all *>&1 | Out-File run.log -Encoding utf8
```

## 3. 记账边界（脚本不改变任何一层的性质）

- `chain`/`proxy`：真实临时库 + 真实已审核资料 + 真实 HTTP；**决策段是宿主显式模拟执行器，模型尝试 0、
  供应商调用 0**，不得读作在线可用。
- `existing`：真实 HTTP 服务器/安全链/TaskStore，**身份仓储是 Mockito 替身、资料是合成 fixture、
  `databaseCalls: 0`**，属 HTTP 契约面证据。
- 脚本判据只有 maven 退出码 + 证据落盘；**在线链路未运行**。

## 4. 脚本开发失败轨迹（5 个脚本缺陷，实测暴露后修复；非产品代码问题）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | surefire 报 `No tests matching pattern ... were executed!` | 传了不存在的属性名 `-DfailIfNoSpecifiedTests` | 干脆不传：匹配不到必须硬失败，静默成功+缺证据最易把"没跑"读成"通过" |
| 2 | 同一命令在 PowerShell 下"无匹配"、bash 下正常 | maven 以**调用方 cwd** 为反应堆根；主工作区没有本分支的测试源码 | `Set-Location $repoRoot`（脚本所在 worktree）后再跑 maven |
| 3 | "maven 退出码"变成一长串日志 | `& $mvnw` 的 stdout 进了函数返回值 | `\| Out-Host` 隔离 |
| 4 | 输出里漂着裸 `True` 和容器名 | `Wait-Until`/`Start-ProxyScaffold`/`Test-EvidenceLanded` 的返回值泄露出函数 | 条件输出收进变量、删掉无人用的 return、调用点 `\| Out-Null` |
| 5 | `all` 场景在 Mockito 警告处**整个脚本终止**（`NativeCommandError`） | maven/surefire 把警告写 stderr；PS 5.1 包装成 ErrorRecord，脚本级 `$ErrorActionPreference='Stop'` 使其终止；**只在输出被管道重定向时才现形**，单独跑场景不触发 | maven 调用期间 EAP 临时降 `Continue` + stderr 记录转纯文本（`2>&1` 单独不够） |

另记一次**非回归**：用 bash 复刻 round-3 命令时测试报 `FileNotFoundException`——原因是我传的证据目录
不存在，46 条 HTTP 断言全部通过后才在写盘步骤失败。教训：证据目录必须先建（脚本已 `New-Item -Force`）。

## 5. PowerShell 5.1 / GBK 边界处理

- 文件为 **UTF-8 带 BOM**（本机 PS 5.1 按 GBK 读无 BOM 的 .ps1，中文会乱码）；行尾 CRLF，与既有
  `scripts/*.ps1` 一致。
- 不用 PS 7 专有运算符（`&&`/`||`/`??`/`?.`），条件链用 `if ($?)`/分号。
- 原生命令（maven/docker）的 stderr 处理见 §4 第 5 条；日志留档用 `Out-File -Encoding utf8`
（PS 5.1 的 `*>` 重定向产出 UTF-16，读取不便）。
- 不需要管理员权限：`netsh interface portproxy` 需提权，故代理容器可达性用宿主 Python 转发解决
（脚手架在 `$env:TEMP` 生成，不入库，按 PID 终止）。

## 6. 未验证范围（状态不变）

- 在线分支真机（非 T06 授权）；浏览器整体验收；多实例/重启运行时行为。
- 脚本只在**本机**验证过；`-DockerHostIp` 等参数在其它机器的 Docker Desktop 上可能不同。
- ~~真实库上的取消/容量/终态矩阵尚未搬到真实层（下一项，进行中）~~ → **已完成**（2026-10-16 21:16）：
  见 [`../t06-cancel-capacity-01/`](../t06-cancel-capacity-01/) 与脚本级复跑
  [`../t06-unified-20261016-211949/`](../t06-unified-20261016-211949/)；chain 场景现已收两个方法。
