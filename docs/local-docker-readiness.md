# Windows 本机 Docker / PostgreSQL 就绪与恢复

## Agent 的执行顺序

用户于 2026-10-28 授权本项目 Agent 按需启动已安装的 Docker Desktop 和本项目已有数据库容器。这个授权不改变 DSH 沙箱、Windows ACL 或 UAC；遇到访问拒绝仍遵守宿主审批，不能修改系统权限来绕过。

从当前仓库执行以下入口，路径随当前 worktree 解析，不依赖调用者工作目录或 `pwsh` 是否在 PATH：

```powershell
# 临时数据库测试、演示：只确保 Docker 引擎可用，不操作共享演示库。
.\knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly

# 确实需要既有共享演示库：启动 Docker / 已有容器并验证固定映射、健康、只读认证。
.\knowledge\scripts\ensure-zhku-postgres.cmd

# 只有端口、SQL bind、非秘密容器环境或 restart policy 漂移时执行。
.\knowledge\scripts\ensure-zhku-postgres.cmd -RepairConfiguration

# 审查模式：不启动 Desktop/容器、不重建；仍会进行只读连接探针。
.\knowledge\scripts\ensure-zhku-postgres.cmd -CheckOnly
```

旧入口 `scripts/zhku-preflight.ps1` 已委托同一实现；默认自动启动，旧 `-StartDocker` 参数继续兼容。希望把既有凭证继承到自己的 PowerShell 会话时使用点号调用：

```powershell
. .\scripts\zhku-preflight.ps1
```

完成标准：Linux 引擎就绪、原数据卷身份匹配、容器 `healthy`、指定回环端口可连接、通过原用户名/库名/口令的只读 `SELECT 1`。认证经 `host.docker.internal` 和实际宿主发布端口，必须先确认错误口令被明确拒绝，再验证原口令；不使用可能命中 `trust` 的容器内部回环连接。只有 `healthy` 不代表应用凭证正确。脚本不运行迁移、不导入业务数据、不访问模型；数据库写入验收继续使用专用临时库。

## 自动恢复做什么

1. 查找实际存在的 `docker.exe`；固定本机 `desktop-linux` context，核对其 endpoint 为 Docker Desktop Linux 的 Windows 命名管道，不切换全局 context，不连接远程引擎。
2. 引擎未就绪时查找实际安装的 Docker Desktop 可执行文件，启动一次并限时等待。已就绪不重复打开；访问拒绝立即失败，而不是反复启动。
3. 数据库模式只接受 Compose 项目 `zhkuagent` / 服务 `postgres` / 固定镜像和已有命名卷 `zhkuagent_zhkuagent_postgres_data`。容器缺失、卷身份不符时停止，不自动创建替代空库。
4. 普通模式只启动已存在的容器；漂移给出具体类别。修复模式在核对原卷仍存在后，以当前 worktree 的 Compose 重建配置，并使用 `--pull never`，不自动升级镜像。旧容器必须正在运行并先通过只读认证；已停止且存在漂移的容器拒绝自动重建，防止把未初始化卷误当成原库。此例外需要保留原卷、核对原配置后单独恢复。
5. 重建后再次核对卷、映射、挂载、健康和认证；失败仍保留原卷，不盲目回滚或清理。不同 Agent 对共享容器的恢复使用同一互斥锁。

默认总预算 180 秒，可用 `-TimeoutSeconds` 调整。CLI 子进程和 TCP 探针各有限时；只终止当前调用拥有的超时 CLI，不终止 Docker Desktop 或其他任务。Docker 服务端可能在 CLI 超时后完成已收到的启动/重建请求，应再次只读检查，不能把超时直接当作未发生任何操作。

## 稳定配置与凭证

- 非秘密默认值与既有 Java 本机入口一致：数据库 `zhkuagent`、用户 `zhkuagent`、回环端口 `54329`。缺变量不再静默分配随机宿主端口。
- 每次入口重新按 Process → User → Machine 读取 `ZHKU_DB_*`。口令绝不生成、回显、写文件或拼进命令参数；不会从容器 inspect 的旧环境恢复口令。
- 如果既有数据目录仅有旧 `infra/.env`，可显式传 `-DataRoot '已有数据根目录'` 作为兼容回退。环境变量优先；不搜索其他 worktree、备份或任意凭证文件。不会要求用户再次提供已配置的密钥。
- `-Port` 或 `ZHKU_DB_PORT` 可以指定非默认端口；要与应用 URL 一致。改变 `POSTGRES_USER` / `POSTGRES_DB` / `POSTGRES_PASSWORD` **不会**修改已初始化卷中的真实用户、库或口令；认证失败必须检查原配置，不能重置数据。
- 脚本仅在当前子进程注入 Compose / psql 环境，退出恢复原值。`-DockerOnly` 不加载数据库凭证。

Compose 使用 `restart: unless-stopped`，引擎恢复后可恢复非人工停止的容器；它不能启动 Docker Desktop，也不能修复旧 bind。SQL 使用长格式只读 bind 并设置 `create_host_path: false`，源文件缺失时明确失败，避免创建同名目录。保留初始化 SQL 是为了兼容空卷首次建库；恢复既有卷时 PostgreSQL 不会重跑它。

## 电脑重启后

不必更改全局开机自启设置：Agent 进入依赖 Docker 的任务时先执行统一入口，会在当前已登录 Windows 用户会话按需启动 Desktop。它不是开机服务，不会在无人登录时唤起桌面应用。

以下情况会准确报错，不会假装恢复成功：Desktop 未安装、Windows 容器模式、WSL/虚拟化故障、Windows/DSH 权限拒绝、卷身份不符、原口令无法读取或认证失败、端口被其他程序占用。自动启动授权不包含安装软件、管理员提权、关闭其他进程或改防火墙。

## 本次现场与验证边界

用户提供的现场已确认当前 SQL bind 指向 `main`、原卷为 `zhkuagent_zhkuagent_postgres_data`、健康状态为 `healthy`；但此前缺三个非秘密变量，宿主端口漂移为 `23031`。应由修复入口核对后恢复预期配置，不能只因 healthy 就宣称应用已恢复。

离线回归命令（Windows 自带 PowerShell 5.1 / 本机 Pester 3.4）：

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Script ./scripts/ZhkuDocker.Tests.ps1,./knowledge/scripts/ensure-zhku-postgres.Tests.ps1 -EnableExit"
```

替身测试验证分支与保护条件，不等同于真实关机/重启验收。真实恢复结果与限制记录在 [本轮验收](evidence/local-docker-readiness/2026-10-28.md)。

## 恢复与排错

脚本从不执行 `down -v`、删除卷、prune 或重置密码。若修复未成功，原数据仍保存在同一命名卷中；停止进一步变更，先检查当前容器身份、卷和端口。回退代码用本任务提交的父版本；回退代码不自动修改容器或数据卷。不要把移除旧 worktree 之前的容器配置当作可恢复备份。

安全的 PowerShell 挂载查看命令（没有嵌套模板引号）：

```powershell
docker inspect zhkuagent-postgres --format '{{json .Mounts}}' |
    ConvertFrom-Json | Format-Table Type, Name, Source, Destination -AutoSize
```

不要贴完整 `docker inspect`、Compose 渲染配置、环境变量列表或未脱敏日志，它们可能包含秘密。
