# t06-controlled-acceptance.ps1 - T06 受控验收一键脚本（真实临时库 + 真实身份 + 真实 HTTP）
#
# 为什么需要它：T06 三档验收各固定了一组启动参数——一次性临时库（每轮重建、端口避开 54329）、
# 证据目录、受控启动显式开关位、清除宿主注入的 SERVER__PORT。这些参数散落在命令历史里，
# 换个会话就凑不齐；本脚本把它们收成一条命令，并保留"每轮重建"这条硬约束
# （迁移后的库不再是空库，复用会让测试的"空库证明"断言失败，看起来像代码回归）。
#
# 用法（PowerShell 5.1，普通用户终端，无需管理员）：
#   .\scripts\t06-controlled-acceptance.ps1 -Scenario existing  # 既有 RuleAgentTaskHttpTest（无 Docker 依赖）
#   .\scripts\t06-controlled-acceptance.ps1 -Scenario chain     # SIMULATED 直连整链：两个方法（身份/资料/幂等主链 + 取消/容量/终态矩阵），自动建一次性临时库
#   .\scripts\t06-controlled-acceptance.ps1 -Scenario proxy     # 经仓库 nginx.conf 的代理贯通（需要 Docker + Python）
#   .\scripts\t06-controlled-acceptance.ps1 -Scenario all       # 依次跑 existing -> chain -> proxy，任一失败即停
#
# 建议把控制台输出留档（容器名/端口/PID 只存在于控制台；PS 5.1 的 *> 重定向产出 UTF-16，
# 读取不便，用 Out-File 显式指定 utf8）：
#   .\scripts\t06-controlled-acceptance.ps1 -Scenario all *>&1 | Out-File run.log -Encoding utf8
# 事后检查用：-KeepContainer 保留临时库容器（用后手动 docker rm -f）。
#
# 记账边界（脚本只负责"跑真"，不负责"冒领"）：
#   · chain / proxy 的决策段是宿主显式模拟执行器：模型尝试 0、供应商调用 0，不得读作在线可用；
#   · existing 的身份仓储是 Mockito 替身、资料是合成 fixture：databaseCalls=0，属 HTTP 契约面证据；
#   · 判据为 maven 退出码、全部预期证据落盘及自有资源处置；"命令返回"不等于"JVM 退出"（surefire 分叉 JVM 可能残留，
#     禁止按进程名批量终止，只能按 PID + 命令行核实）；
#   · 测试因假设未满足而跳过时脚本会指出证据文件缺失——跳过不算通过。
#
# 编码：本文件必须是 UTF-8 带 BOM——本机 PowerShell 5.1 按 GBK 读无 BOM 的 .ps1，中文会变乱码。
param(
    # existing=既有 HTTP 契约测试；chain=真实库直连整链；proxy=仓库 nginx 代理贯通；all=依次跑前三者
    [ValidateSet('existing', 'chain', 'proxy', 'all')]
    [string]$Scenario = 'chain',
    # 证据根目录；每次运行在其下建 t06-unified-<时间戳>\<场景> 子目录，不覆盖历史证据
    [string]$EvidenceRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) 'docs\evidence\t06-t07\2026-10-16'),
    # 已审核资料根：必须是主工作目录（只读）；用 worktree 当根会被批准指纹校验拒绝
    [string]$AssetsRoot = 'D:\alldesk\Projects\ZhkuAgent',
    # 代理场景端口（默认值取自 2026-10-16 代理贯通轮；被占用时换值，不要复用别人的监听）
    [int]$BackendPort = 18081,
    [int]$PublicPort = 18082,
    [int]$RelayPort = 18083,
    # Docker Desktop 的 host.docker.internal 地址（本机实测 192.168.65.254；IPv6 解析会抖动，固定 IPv4）
    [string]$DockerHostIp = '192.168.65.254',
    [switch]$KeepContainer,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
# 同秒并行验收也必须隔离转发脚本与证据目录，避免互相覆盖或误读另一轮结果。
$script:RunStamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$script:AssetsRoot = (Resolve-Path $AssetsRoot).Path
$script:Containers = New-Object System.Collections.ArrayList
$script:RelayProcess = $null
# 同一运行内多个场景各建一个临时库：序号保证容器名唯一（否则 -KeepContainer 时会撞名）
$script:ContainerSeq = 0

function Test-DockerDaemon {
    docker ps *> $null
    return ($LASTEXITCODE -eq 0)
}

function Wait-Until([scriptblock]$Condition, [string]$What, [int]$Seconds = 120) {
    # 条件输出必须收进变量：直接 & 调用会把条件的布尔值泄露出函数，污染调用方输出流
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $ready = & $Condition
        if ($ready) { return }
        Start-Sleep -Seconds 2
    }
    throw "等待 ${What} 超时（${Seconds}s）"
}

function Test-TcpListener {
    param([string]$TargetHost, [int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect($TargetHost, $Port)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-FreeLoopbackPort {
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $candidate = Get-Random -Minimum 55000 -Maximum 57000
        if ($candidate -eq 54329) { continue }
        $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $candidate)
        try { $listener.Start(); $listener.Stop(); return $candidate } catch { }
    }
    throw '无法在 55000-56999 范围内找到空闲回环端口'
}

function New-TempDatabase {
    # 一次性临时库：每轮重建。迁移后库不再是空库，复用会让"空库证明"断言失败——
    # 那不是代码回归，是库没重建。端口随机取并避开演示库 54329。
    # 缺凭证不能自行生成：否则会绕过本机统一凭证来源，把环境继承问题误记为验收成功。
    . (Join-Path $repoRoot 'scripts\Import-ZhkuCredentials.ps1') -RequireDatabase
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw '未找到 docker 命令：请先安装并启动 Docker Desktop，或执行 . .\scripts\zhku-preflight.ps1 -StartDocker'
    }
    if (-not (Test-DockerDaemon)) {
        throw 'Docker 守护进程未运行。请启动 Docker Desktop，或执行 . .\scripts\zhku-preflight.ps1 -StartDocker'
    }
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $script:ContainerSeq++
        # 随机后缀隔离本轮所有权；不能先删除一个可能已被其他运行占用的同名容器。
        $container = "zhku-t06-tmpdb-$script:RunStamp-$script:ContainerSeq-$([guid]::NewGuid().ToString('N'))"
        $port = Get-FreeLoopbackPort
        . (Join-Path $repoRoot 'scripts\Import-ZhkuCredentials.ps1') -RequireDatabase
        $previousPostgresPassword = [Environment]::GetEnvironmentVariable('POSTGRES_PASSWORD', 'Process')
        try {
            # Docker 只接收环境变量名，避免口令进入命令行；失败或重试也必须还原调用方环境。
            $env:POSTGRES_PASSWORD = $env:ZHKU_DB_PASSWORD
            docker run -d --name $container `
                -e 'POSTGRES_USER=zhkuagent' `
                -e 'POSTGRES_PASSWORD' `
                -e 'POSTGRES_DB=zhkuagent_rulecheck_e2e' `
                -p "127.0.0.1:${port}:5432" `
                'pgvector/pgvector:0.8.6-pg16-bookworm' *> $null
            $dockerExitCode = $LASTEXITCODE
        } finally {
            [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', $previousPostgresPassword, 'Process')
        }
        if ($dockerExitCode -eq 0) {
            [void]$script:Containers.Add($container)
            Write-Host "[t06] 一次性临时库 $container（127.0.0.1:${port}/zhkuagent_rulecheck_e2e）"
            try {
                Wait-Until {
                    . (Join-Path $repoRoot 'scripts\Import-ZhkuCredentials.ps1') -RequireDatabase
                    docker exec $container pg_isready -U zhkuagent -d zhkuagent_rulecheck_e2e *> $null
                    return ($LASTEXITCODE -eq 0)
                } '临时库就绪' $TimeoutSeconds
                . (Join-Path $repoRoot 'scripts\Import-ZhkuCredentials.ps1') -RequireDatabase
                $tables = (docker exec $container psql -U zhkuagent -d zhkuagent_rulecheck_e2e -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema')") -join ''
                if ($tables.Trim() -ne '0') {
                    throw "临时库不是空库（$($tables.Trim()) 张表）：一次性库每轮必须重建，不得复用。"
                }
                return @{ Container = $container; Port = $port; Url = "jdbc:postgresql://127.0.0.1:${port}/zhkuagent_rulecheck_e2e" }
            } catch {
                $initializationFailure = $_
                # 尚未返回 db 时调用方没有可回收对象；初始化失败必须在这里清理，不能按调试保留处理。
                try { Remove-TempDatabase -Container $container -InitializationFailed }
                catch { Write-Warning "[t06] 初始化失败且临时库未确认回收：$container" }
                throw $initializationFailure
            }
        }
        docker rm -f $container *> $null
        if ($LASTEXITCODE -ne 0) { throw "[t06] 失败启动的自有临时库未确认回收：$container；停止重试。" }
        Write-Warning "[t06] docker run 失败（第 $attempt 次，可能端口被抢占），换端口重试"
    }
    throw '临时库容器创建失败（3 次尝试）；请检查 docker 与镜像 pgvector/pgvector:0.8.6-pg16-bookworm'
}

function Remove-TempDatabase {
    param([string]$Container, [switch]$InitializationFailed)
    if (-not $Container) { return }
    if (-not $script:Containers.Contains($Container)) { throw "拒绝回收本轮未登记的容器：$Container" }
    if ($KeepContainer -and -not $InitializationFailed) {
        Write-Host "[t06] -KeepContainer：保留 $Container 供事后检查（用后请手动 docker rm -f）"
        return
    }
    docker rm -f $Container *> $null
    if ($LASTEXITCODE -eq 0) {
        [void]$script:Containers.Remove($Container)
        Write-Host "[t06] 已回收临时库容器 $Container"
    } else {
        throw "[t06] 回收 $Container 失败，不能记为已回收；请手动核对本次容器。"
    }
}

function Invoke-MavenAcceptance {
    param(
        [string]$TestFilter,
        [string]$EvidenceDir,
        [string]$DbUrl,
        [string]$AssetsRootForTests,
        [hashtable]$ExtraArgs = @{}
    )
    $mvnw = Join-Path $repoRoot 'mvnw.cmd'
    # 不传 failIfNoSpecifiedTests：模式匹配不到测试时必须硬失败（方法名写错、切错仓库都属于脚本缺陷），
    # 静默成功 + 缺证据文件的组合反而容易把"没跑"读成"通过"。
    $arguments = @('-pl', 'knowledge', "-Dtest=$TestFilter", "-Dzhku.rulecheck.evidenceDir=$EvidenceDir")
    if ($DbUrl) { $arguments += "-Dzhku.db.url=$DbUrl" }
    if ($AssetsRootForTests) { $arguments += "-Dzhku.t06.root=$AssetsRootForTests" }
    foreach ($pair in $ExtraArgs.GetEnumerator()) { $arguments += "-D$($pair.Key)=$($pair.Value)" }
    $arguments += 'test'

    $previousPassword = $env:ZHKU_DB_PASSWORD
    $injectedPort = [System.Environment]::GetEnvironmentVariable('SERVER__PORT', 'Process')
    $previousLocation = Get-Location
    try {
        # 仅真实库场景重读统一凭证；existing 无数据库，不能要求额外口令。
        if ($DbUrl) { . (Join-Path $repoRoot 'scripts\Import-ZhkuCredentials.ps1') -RequireDatabase }
        # 摘掉宿主注入的 SERVER__PORT：否则 Spring 宽松绑定会拿它覆盖测试内的随机端口，
        # 而该端口常被宿主自身占用（结构性冲突，不是代码回归）
        [System.Environment]::SetEnvironmentVariable('SERVER__PORT', $null, 'Process')
        # maven 以调用方 cwd 为反应堆根：必须显式切到脚本所在 worktree，否则 -pl knowledge
        # 会解析到终端当前目录的仓库（那里可能没有本分支的测试源码，表现为"无匹配"静默跳过）
        Set-Location $repoRoot
        Write-Host "[t06] maven: mvnw.cmd $($arguments -join ' ')"
        # 两条都必须有，缺一条就会在特定调用方式下挂掉：
        # ① EAP 临时降为 Continue：maven/surefire 会把警告写进 stderr，PS 5.1 会把原生 stderr
        #    包装成 NativeCommandError；脚本级 $ErrorActionPreference='Stop' 会让它终止整个脚本
        #    （Mockito 自附着提示即可触发，且只在输出被管道重定向时才现形，极易漏测）。
        # ② 2>&1：把 stderr 合并进可见输出，否则警告会以红色错误记录刷屏。
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            # 原生 stderr 记录先转纯文本：否则 Mockito 自附着提示会以红色 NativeCommandError
            # 块刷屏——在一次"通过"的运行里长得像失败，事后读日志极易误判
            & $mvnw @arguments 2>&1 |
                ForEach-Object { if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.ToString() } else { $_ } } |
                Out-Host
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        return $LASTEXITCODE
    } finally {
        Set-Location $previousLocation
        [System.Environment]::SetEnvironmentVariable('SERVER__PORT', $injectedPort, 'Process')
        if ($previousPassword) { $env:ZHKU_DB_PASSWORD = $previousPassword } else { [System.Environment]::SetEnvironmentVariable('ZHKU_DB_PASSWORD', $null, 'Process') }
    }
}

function Test-EvidenceLanded {
    param([string]$EvidenceDir, [string[]]$ExpectedFiles)
    $missing = @()
    foreach ($file in $ExpectedFiles) {
        $path = Join-Path $EvidenceDir $file
        if (Test-Path $path) {
            Write-Host "[t06] 证据已归档：$path（$((Get-Item $path).Length) 字节）"
        } else {
            $missing += $file
        }
    }
    if ($missing.Count -gt 0) {
        Write-Warning "[t06] 未找到证据文件 $($missing -join '、')：测试可能被假设跳过（跳过不算通过）或未写证据，请核对 maven 输出。"
        return $false
    }
    return $true
}

function Start-ProxyScaffold {
    param(
        [int]$Backend, [int]$Public, [int]$Relay,
        # 只接受回环字面量，参数绑定阶段拒绝越界配置，避免先启动进程后才发现暴露到局域网。
        [ValidateSet('127.0.0.1')]
        [string]$RelayBindAddress = '127.0.0.1'
    )
    foreach ($p in @($Backend, $Public, $Relay)) {
        if (Test-TcpListener -TargetHost '127.0.0.1' -Port $p) {
            throw "代理场景端口 $p 已被占用：请用 -BackendPort/-PublicPort/-RelayPort 换端口。"
        }
    }
    $python = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $python) { $python = (Get-Command py -ErrorAction SilentlyContinue).Source }
    if (-not $python) { throw '代理脚手架需要 python（未找到 python / py）。' }

    # 宿主侧 TCP 转发只绑回环：2026-10-18 已实测 Docker 容器可访问宿主回环，
    # 无需暴露到局域网；nginx 到后端的代理路径仍使用仓库 nginx.conf。
    $relayFile = Join-Path $env:TEMP "t06-proxy-relay-$script:RunStamp.py"
    $relaySource = @'
"""T06 代理贯通用的宿主侧 TCP 转发（验收脚手架，由 t06-controlled-acceptance.ps1 生成，不入库）。

边界说明（本机 Docker Desktop 于 2026-10-18 重新验证）：
1. 受控查证后端按设计只绑 127.0.0.1（LocalPreviewBindingGuard 精确要求回环字面量），不能改；
2. 容器经默认 host.docker.internal 或显式 192.168.65.254 均可访问宿主回环，因此转发也只监听回环。
本转发保留独立代理验收端口，再转到受控后端；nginx 使用仓库自带 nginx.conf 的真实代理路径。
脚手架不能为了容器访问而扩大宿主监听范围，也不做鉴权或重放。验收结束按 PID 终止。
"""
import socket
import threading

LISTEN = ("__RELAY_BIND_ADDRESS__", __RELAY_PORT__)
TARGET = ("127.0.0.1", __BACKEND_PORT__)


def pipe(source, sink):
    try:
        while True:
            data = source.recv(65536)
            if not data:
                break
            sink.sendall(data)
    except Exception:
        pass
    finally:
        for stream in (source, sink):
            try:
                stream.close()
            except Exception:
                pass


def handle(client):
    try:
        upstream = socket.create_connection(TARGET, timeout=5)
    except Exception:
        try:
            client.close()
        except Exception:
            pass
        return
    threading.Thread(target=pipe, args=(client, upstream), daemon=True).start()
    threading.Thread(target=pipe, args=(upstream, client), daemon=True).start()


server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(LISTEN)
server.listen(128)
print("relay-up %s:%s -> %s:%s" % (LISTEN[0], LISTEN[1], TARGET[0], TARGET[1]), flush=True)
while True:
    connection, _ = server.accept()
    threading.Thread(target=handle, args=(connection,), daemon=True).start()
'@
    $relaySource = $relaySource -replace '__RELAY_PORT__', $Relay -replace '__BACKEND_PORT__', $Backend -replace '__RELAY_BIND_ADDRESS__', $RelayBindAddress
    Set-Content -Path $relayFile -Value $relaySource -Encoding UTF8
    $script:RelayProcess = Start-Process -FilePath $python -ArgumentList $relayFile -WindowStyle Hidden -PassThru
    Wait-Until { Test-TcpListener -TargetHost '127.0.0.1' -Port $Relay } "宿主转发端口 $Relay" 30
    Write-Host "[t06] 宿主转发已就绪（PID $($script:RelayProcess.Id)，脚本 $relayFile）"

    $nginx = "zhku-t06-nginx-$script:RunStamp-$([guid]::NewGuid().ToString('N'))"
    $template = Join-Path $repoRoot 'agent\frontend\nginx.conf'
    if (-not (Test-Path $template)) { throw "未找到仓库 nginx 配置：$template" }
    # 官方镜像模板机制：挂到 /etc/nginx/templates/*.template，envsubst 用环境变量替换后生成 conf.d 配置
    # 先登记本轮随机名称，启动失败也可能已经留下容器；调用方 finally 必须能尝试回收。
    [void]$script:Containers.Add($nginx)
    docker run -d --name $nginx `
        --add-host "host.docker.internal:$DockerHostIp" `
        -e "ZHKU_CONTROLLED_BACKEND_PORT=$Relay" `
        -p "127.0.0.1:${Public}:80" `
        -v "${template}:/etc/nginx/templates/default.conf.template:ro" `
        'nginx:1.27-alpine' *> $null
    if ($LASTEXITCODE -ne 0) { throw "nginx 容器启动失败；排查：docker logs $nginx" }
    Wait-Until { Test-TcpListener -TargetHost '127.0.0.1' -Port $Public } "nginx 发布端口 $Public" 60
    $generated = (docker exec $nginx cat /etc/nginx/conf.d/default.conf 2>$null) -join "`n"
    if ($generated -and ($generated -notmatch [regex]::Escape([string]$Relay))) {
        throw "nginx 生成的配置里没有出现后端端口 $Relay：模板替换可能未生效；排查：docker logs $nginx"
    }
    Write-Host "[t06] nginx 已就绪（$nginx：127.0.0.1:$Public -> 仓库 nginx.conf -> host.docker.internal:$Relay -> 后端 127.0.0.1:$Backend）"
    # 不 return 容器名：调用方不使用它，return 值会泄露出函数输出流；容器已登记在 $script:Containers
}

function Stop-ProxyScaffold {
    $cleanupFailed = $false
    if ($script:RelayProcess -and -not $script:RelayProcess.HasExited) {
        try {
            Stop-Process -Id $script:RelayProcess.Id -Force
            Write-Host "[t06] 宿主转发已终止（PID $($script:RelayProcess.Id)）"
            $script:RelayProcess = $null
        } catch {
            $cleanupFailed = $true
            Write-Warning "[t06] 宿主转发未确认终止（PID $($script:RelayProcess.Id)）"
        }
    }
    # 单个资源失败不能阻断其余自有资源回收；只遍历本轮登记的 nginx，不按进程名或全局容器列表清理。
    foreach ($container in @($script:Containers)) {
        if ($container -like 'zhku-t06-nginx-*') {
            try {
                docker rm -f $container *> $null
                if ($LASTEXITCODE -ne 0) { throw '本次容器回收失败' }
                [void]$script:Containers.Remove($container)
                Write-Host "[t06] nginx 容器已回收（$container）"
            } catch {
                $cleanupFailed = $true
                Write-Warning "[t06] nginx 容器未确认回收（$container）"
            }
        }
    }
    if ($cleanupFailed) { throw '[t06] 代理自有资源未全部确认回收，本轮不能记为通过。' }
}

if ($Scenario -eq 'all') { $plan = @('existing', 'chain', 'proxy') } else { $plan = @($Scenario) }
$evidenceRunRoot = Join-Path $EvidenceRoot "t06-unified-$script:RunStamp"
$overall = 0

foreach ($name in $plan) {
    Write-Host ''
    Write-Host "[t06] ===== 场景 $name ====="
    switch ($name) {
        'existing' {
            # 既有 HTTP 契约测试：随机回环端口 + 真实安全链/TaskStore；身份与资料是替身，零数据库
            $evidenceDir = Join-Path $evidenceRunRoot 'existing'
            New-Item -ItemType Directory -Path $evidenceDir -Force *> $null
            $overall = Invoke-MavenAcceptance -TestFilter 'RuleAgentTaskHttpTest' -EvidenceDir $evidenceDir
            if (-not (Test-EvidenceLanded -EvidenceDir $evidenceDir -ExpectedFiles 'http-exchanges.json')) {
                if ($overall -eq 0) { $overall = 1 }
            }
        }
        'chain' {
            $db = New-TempDatabase
            try {
                $evidenceDir = Join-Path $evidenceRunRoot 'chain'
                New-Item -ItemType Directory -Path $evidenceDir -Force *> $null
                $overall = Invoke-MavenAcceptance `
                    -TestFilter 'RuleAgentSimulatedChainTempDbHttpTest#realIdentityAndReviewedAssetsOverActualHttpWithCsrfIdempotencyAndIsolation+realTempDbCancelCapacityAndTerminalStatesOverActualHttp' `
                    -EvidenceDir $evidenceDir -DbUrl $db.Url -AssetsRootForTests $script:AssetsRoot
                if (-not (Test-EvidenceLanded -EvidenceDir $evidenceDir -ExpectedFiles 'simulated-chain-tempdb.json','cancel-capacity-terminal.json')) {
                    if ($overall -eq 0) { $overall = 1 }
                }
            } finally {
                # 清理失败改变验收结果，但不能遮蔽已有业务异常或原 Maven 失败码。
                try { Remove-TempDatabase -Container $db.Container }
                catch {
                    if ($overall -eq 0) { $overall = 1 }
                    Write-Warning "[t06] 临时库未确认回收：$($db.Container)，本轮未通过。"
                }
            }
        }
        'proxy' {
            $db = New-TempDatabase
            try {
                try {
                    Start-ProxyScaffold -Backend $BackendPort -Public $PublicPort -Relay $RelayPort
                    $evidenceDir = Join-Path $evidenceRunRoot 'proxy'
                    New-Item -ItemType Directory -Path $evidenceDir -Force *> $null
                    $extra = @{ 'zhku.proxy.backendPort' = $BackendPort; 'zhku.proxy.publicPort' = $PublicPort; 'zhku.proxy.bindAddress' = '127.0.0.1' }
                    $overall = Invoke-MavenAcceptance `
                        -TestFilter 'RuleAgentSimulatedChainTempDbHttpTest#sameChainThroughRepoNginxProxyWithActualProxiedResponses' `
                        -EvidenceDir $evidenceDir -DbUrl $db.Url -AssetsRootForTests $script:AssetsRoot -ExtraArgs $extra
                    if (-not (Test-EvidenceLanded -EvidenceDir $evidenceDir -ExpectedFiles 'proxied-chain.json')) {
                        if ($overall -eq 0) { $overall = 1 }
                    }
                } finally {
                    try { Stop-ProxyScaffold }
                    catch {
                        if ($overall -eq 0) { $overall = 1 }
                        Write-Warning '[t06] 代理资源未确认完全回收，本轮未通过。'
                    }
                }
            } finally {
                try { Remove-TempDatabase -Container $db.Container }
                catch {
                    if ($overall -eq 0) { $overall = 1 }
                    Write-Warning "[t06] 临时库未确认回收：$($db.Container)，本轮未通过。"
                }
            }
        }
    }
    if ($overall -ne 0) {
        Write-Host "[t06] 场景 $name 未通过（退出码 $overall），按约定停止后续场景。"
        break
    }
}

Write-Host ''
Write-Host '[t06] ===== 汇总 ====='
Write-Host "  场景：$($plan -join ' -> ')"
Write-Host "  证据目录：$evidenceRunRoot"
Write-Host "  验收退出码：$overall（0=maven 成功、证据齐备且自有资源处置符合约定）"
Write-Host '  记账边界：chain/proxy 决策段为宿主模拟执行器（模型尝试 0、供应商 0）；existing 为 HTTP 契约面（databaseCalls=0）。'
if ($overall -ne 0) {
    Write-Host '  结论：未通过。失败轨迹以 maven 输出与证据目录为准，不得表述为通过。'
} else {
    Write-Host '  结论：本轮各场景 maven 成功、证据齐备且自有资源处置符合约定；在线链路未运行，不得读作在线可用。'
}
exit $overall
