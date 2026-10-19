# M1-02 安全执行准备结果

日期：2026-10-19（Asia/Shanghai）。结论：**准备工具与离线探针已交付，M1-02 尚未满足全部放行条件；M1-03 不放行。** 未执行完整 chain，未创建容器/卷，未连接或写入 PostgreSQL，未调用模型供应商。`-Mode Chain` 当前在任何外部命令前硬拒绝，验证该拒绝不等于执行 chain。

## 已解决的问题及证据

1. **历史报告隔离已实测。** Maven Surefire 的 `reportsDirectory` 文档没有为其声明 `surefire.reportsDirectory` 用户属性。本机实测传入 `-Dsurefire.reportsDirectory=<独立目录>` 后该目录未生成，XML/TXT 仍写入默认 `knowledge/target/surefire-reports`。因此采用每批完整构建副本，绝不在候选工作树运行 Maven。原工作树 25 份 XML/TXT 的前后摘要保持 `02B205617EC29DDD5F6E7139136DC8637CE26616572556B70EC098604165C855`。报告保留类、方法、测试总数、失败、错误、跳过及原始 Maven 退出码。参见 [Surefire 参数说明](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html)。
2. **离线可编译已实测。** 使用已缓存、与 wrapper 配置对应的 Maven 3.9.9，直接调用已存在的分发文件，不让 wrapper 在缓存缺失时下载；所有 Maven 命令携带 `-o -B -ntp`。候选 main/test 源码通过 `test-compile`。纯合同探针 `RuleAgentContractTest` 共 7 项，失败 0、错误 0、跳过 0，退出 0。该探针没有数据库、HTTP 服务或供应商依赖，不能代替 chain。
3. **进程监督机制已局部实测。** 子进程先等待门闩，加入 Windows Job 后才启动目标命令；Job 记录本轮 PID，并设置关闭句柄终止本轮后代。独立探针覆盖成功 0、原始失败 23、超时 124、后代进程退出、3000 行日志不丢尾行、特殊字符与等长秘密脱敏、敏感参数拒绝。超时尾日志收集限时并记录 `tailComplete`，不使用无界 `ReadToEnd`。没有按 Java 进程名清理。
4. **本机镜像缓存已只读确认。** `pgvector/pgvector:0.8.6-pg16-bookworm` 对应 `sha256:ccc6e83d6e35e931dc7c5def2022729d5a6c370318d099181995567ff1fb4d6b`，镜像数据挂载点为 `/var/lib/postgresql/data`。未拉取镜像。草案运行路径固定该 ID 并携带 `--pull=never`，缓存消失即失败。参见 [Docker pull 策略](https://github.com/docker/cli/blob/master/docs/reference/commandline/container_run.md)。
5. **证据与身份独立保存。** 每轮路径为 `%LOCALAPPDATA%\ZhkuAgent\controlled-acceptance\m1-<mode>-<时间>-<GUID>`。完整日志、构建副本和探针报告留在该目录；本仓库只追加最小审查证据。记录 HEAD、704 个候选源码/脚本文件和 22 个冻结资料输入，包括 B02/B03 回执、topic、overlay 以及外部 PDF 的 SHA256。PDF 只读计算摘要，未复制或提交整本文件。没有修改已有证据。

## 源码身份和验证边界

执行时 HEAD：`58690f28e512bd03325ba49815dacd808bd0157b`，分支 `codex/rule-agent-integration-20261018`。该工作树已有大量未提交成果，实际验收身份是逐文件摘要，不能只用 HEAD 代表源码。

最终准备源码摘要：`BC4DACE6FAC27EE818E2F38F48BE62A5CBB95439D49F122515D39AC93EE05C4F`。

冻结输入摘要：`DA7FA313D92FD4228C17CCF3A0E651964B4E3ECAF4EB7B3CAB5540F24A5E81E4`。

最小证据：`docs/evidence/m1-02/2026-10-19/prepare-final/`。完整最终轮：`C:\Users\nangua\AppData\Local\ZhkuAgent\controlled-acceptance\m1-prepare-20261019-222440-8f42ff00a96a4ef6b385bb62740d8049`。该轮通过普通用户注册表 PATH 重建、Windows PowerShell 5.1 入口和会话 UTF-8 设置，未使用 Codex 注入 PATH 证明用户环境。

独立静态审查发现并修复特殊字符脱敏、等长秘密去重及无界尾日志读取问题；最终复核无阻断 Prepare 交付的 P1/P2。探索轮还保留了初版日志排空过慢触发超时、工具修改中身份变化触发退出 3 的失败记录；未覆盖或删除原始记录，均不算业务验收结果。

本会话未定位到用户所述 M0/M1-01 的原始冻结清单，不能宣称已与那两个阶段逐文件比对。本轮检查证明所记录输入在准备验收前后不变；后续还需用前阶段基线对齐。提交新增准备文件后 HEAD 会改变，执行前必须重新生成并审阅身份文件。

本地检查点只保存本任务新增准备文件与最小证据，不吸收工作区已有的未提交业务源码。因此它不是完整候选源码的恢复提交；复核本轮构建还需原候选工作区或外部构建副本，并核对逐文件摘要。原始证据按字节保留（含 CRLF 和 Maven 原生空白），空白检查只对新工具/说明的可维护文本作通过判定。

## 尚未解决的风险与放行条件

- Docker 草案虽然有唯一批次标签、预写意图、容器 ID/名称/镜像/挂载核对、专用命名卷与退出检查，**真实创建成功、创建失败和超时后的发现/回收均未实测**。不能称容器和卷自动回收已验收。
- 外层 PowerShell 文件遍历、摘要计算和部分系统查询仍是同步操作，截止时间在操作之间检查；父进程强杀或系统 I/O 卡死的收尾尚未验证，不能声称具有严格的整轮墙钟硬上限。
- Windows Job 只能终止本轮子进程，Docker 守护进程管理的容器不会随 Job 自动退出；硬中断可能留下资源。必须保留资源账本并人工核对，不能宣称已清理。
- 当前只验证了准备模式的纯合同探针，未验证 chain 两个方法、两份 chain JSON、真实 HTTP 结束与数据库关闭；这些结果不能提前填写为成功。失败时模型计数缺证据则保留 `null/not-run`，不伪造 0。
- 模拟模型保障依据现有测试显式 `SIMULATED_EXECUTOR_BEAN`、启动模式 `SIMULATED`、0 模型尝试和两份结果的零调用判据，子进程清除供应商 Key 及可能注入配置的选项。没有安装全机防火墙或宣称具备操作系统级断网隔离。

因此八项标准中，历史报告、新证据目录、缓存离线和本轮输入不变已验证；真实资源生命周期、严格停止边界、与前阶段身份对齐仍有缺口。继续准备时应先验证专用资源的成功/失败/超时回收，不得直接解除锁运行 chain。

## 文件和差异

全部为新增文件：`tools/m1-02/Invoke-SafeChain.ps1`（准备入口及锁定的执行草案）、`Safety.ps1`（Job/截止时间/脱敏/命令记录）、`Worker.ps1`（门闩和原始退出码传递）、`Test-Safety.ps1`（无数据库探针），以及本目录计划/结果和新的最小证据目录。

**既有 `scripts/t06-controlled-acceptance.ps1`、业务源码、测试断言、模型协议、数据库守卫、POM、历史证据均未修改。** 新执行草案沿用原 chain 的两个方法、数据库名和资料根；仅构建流程加入 `-am` 保证 runtime 与 knowledge 同源码编译，允许 runtime 无匹配测试后，另行强制核对 knowledge 类的两个方法、2/0/0/0，不能把 reactor 空跑记为成功。该草案仍锁定、未获得真实 chain 验收。

## 命令、时限与停止方法

可重复执行的仅为准备命令（普通 Windows PowerShell 5.1 入口，无需全局 PATH 改动）：

```powershell
$p = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
if (!(Test-Path -LiteralPath $p)) { throw 'PowerShell 7 路径不存在，停止并重新定位' }
[Console]::InputEncoding = [Console]::OutputEncoding = $OutputEncoding = New-Object Text.UTF8Encoding($false)
& $p -NoProfile -File 'C:\Users\nangua\.codex\worktrees\rule-agent-integration\ZhkuAgent\tools\m1-02\Invoke-SafeChain.ps1' -Mode Prepare -TimeoutSeconds 900
$LASTEXITCODE
```

**M1-03 准确执行命令：当前无可放行命令。** `-Mode Chain` 会先写失败结果并退出 1，不创建资源。解除锁和执行 chain 需要下一步指令及上述缺口的验证；不能建议直接运行原脚本绕过本锁。

计划预算为业务阶段 900 秒加收尾 120 秒（17 分钟）；每个子命令另有较短上限。该预算不等于已证明的墙钟硬上限。准备探针约 1–2 分钟，具体实测秒数在 `result.json`。

正常停止：在本轮输出的证据目录中创建空文件 `STOP`；监督器在检查点识别为退出 130，保留日志并进入资源收尾。不要把关窗口或按进程名杀 Java 当作安全停止。当前 Chain 锁定，这一停止方案仍须在真实资源生命周期中验证。

## 成功、失败和超时记录约定

| 结果 | 记录与判据 |
| --- | --- |
| 成功 | 测试退出 0，准确目标方法 2/0/0/0，两份 chain JSON 齐备且模式/模型调用符合约定，源码/输入/历史报告不变，Job 空、HTTP 已退出、容器/卷/数据库端口核对退出。任一缺项不成功。 |
| 失败 | 保留原始子命令和 Maven 退出码、完整脱敏日志、已有 XML/TXT/JSON，提取第一份 Surefire failure/error 堆栈与最后一条 `Caused by`；构建前失败无 Java 堆栈时明确 null，并保留脚本失败位置。禁止自动下载、重试业务或修改断言凑通过。 |
| 超时/停止 | 监督器分别记录 124/130 与原生退出状态，Job 只终止本轮进程树，记录尾日志是否完整；进入有限收尾，未确认的资源如实记为失败并交人工核对。 |

故障停止条件：身份变化、缓存缺失、镜像/挂载布局变化、凭证不可读、端口越界、测试缺失/跳过、任一测试失败、供应商计数非零或缺失、资源所有权不匹配、退出未确认，均停止，不进入下一场景。

## 人工恢复边界

执行草案在创建前写 `resource-intent.json`，成功检查后写 `resource-owner.json`，删除前写 `resource-before-removal.json`。只核对账本里的批次、容器 ID、名称、镜像 ID、端口及挂载卷；卷只有本轮精确名称且批次标签一致才可删除。容器先按已核对 ID 删除，再按已核对名称删除专用卷；卷在用或所有权不明就停止并保留记录。不要保存完整 Docker inspect（Env 含密码），不要 prune，不操作 54329，不按 PostgreSQL/Java 名称批量清理。

本轮没有创建可供清理的数据库容器或卷。外部临时构建副本和全部探索日志暂保留，不自动删除；恢复源码只需保留原工作区并撤回这次新增准备文件，不涉及数据库恢复或历史改写。
