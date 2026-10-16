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
#   · 判据只有 maven 退出码；"命令返回"不等于"JVM 退出"（surefire 分叉 JVM 可能残留，
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
$script:RunStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$script:AssetsRoot = (Resolve-Path $AssetsRoot).Path
$script:TempPassword = $env:ZHKU_DB_PASSWORD
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
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw '未找到 docker 命令：请先安装并启动 Docker Desktop，或执行 . .\scripts\zhku-preflight.ps1 -StartDocker'
    }
    if (-not (Test-DockerDaemon)) {
        throw 'Docker 守护进程未运行。请启动 Docker Desktop，或执行 . .\scripts\zhku-preflight.ps1 -StartDocker'
    }
    if ([string]::IsNullOrWhiteSpace($script:TempPassword)) {
        # 一次性容器口令现场生成，只经环境变量传给 maven 子进程（-D 系统属性会被 fail-fast 拒绝）
        $alphabet = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $buffer = New-Object byte[] 32
        $rng.GetBytes($buffer)
        $rng.Dispose()
        $script:TempPassword = -join ($buffer | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
    }
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $script:ContainerSeq++
        $container = "zhku-t06-tmpdb-$script:RunStamp-$script:ContainerSeq"
        $port = Get-FreeLoopbackPort
        docker rm -f $container *> $null
        # 口令会进入容器 env（docker inspect 可见）：一次性容器、随机口令，可接受
        docker run -d --name $container `
            -e 'POSTGRES_USER=zhkuagent' `
            -e "POSTGRES_PASSWORD=$script:TempPassword" `
            -e 'POSTGRES_DB=zhkuagent_rulecheck_e2e' `
            -p "${port}:5432" `
            'pgvector/pgvector:0.8.6-pg16-bookworm' *> $null
        if ($LASTEXITCODE -eq 0) {
            [void]$script:Containers.Add($container)
            Write-Host "[t06] 一次性临时库 $container（127.0.0.1:${port}/zhkuagent_rulecheck_e2e）"
            Wait-Until { docker exec $container pg_isready -U zhkuagent -d zhkuagent_rulecheck_e2e *> $null; return ($LASTEXITCODE -eq 0) } '临时库就绪' $TimeoutSeconds
            $tables = (docker exec $container psql -U zhkuagent -d zhkuagent_rulecheck_e2e -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema')") -join ''
            if ($tables.Trim() -ne '0') {
                throw "临时库不是空库（$($tables.Trim()) 张表）：一次性库每轮必须重建，不得复用。"
            }
            return @{ Container = $container; Port = $port; Url = "jdbc:postgresql://127.0.0.1:${port}/zhkuagent_rulecheck_e2e" }
        }
        docker rm -f $container *> $null
        Write-Warning "[t06] docker run 失败（第 $attempt 次，可能端口被抢占），换端口重试"
    }
    throw '临时库容器创建失败（3 次尝试）；请检查 docker 与镜像 pgvector/pgvector:0.8.6-pg16-bookworm'
}

function Remove-TempDatabase {
    param([string]$Container)
    if (-not $Container) { return }
    if ($KeepContainer) {
        Write-Host "[t06] -KeepContainer：保留 $Container 供事后检查（用后请手动 docker rm -f）"
        return
    }
    docker rm -f $Container *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[t06] 已回收临时库容器 $Container"
    } else {
        Write-Warning "[t06] 回收 $Container 失败，请手动 docker rm -f $Container"
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
        # 口令只经环境变量进入子进程；-D 系统属性会被 fail-fast 拒绝
        $env:ZHKU_DB_PASSWORD = $script:TempPassword
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
    param([int]$Backend, [int]$Public, [int]$Relay)
    foreach ($p in @($Backend, $Public, $Relay)) {
        if (Test-TcpListener -TargetHost '127.0.0.1' -Port $p) {
            throw "代理场景端口 $p 已被占用：请用 -BackendPort/-PublicPort/-RelayPort 换端口。"
        }
    }
    $python = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $python) { $python = (Get-Command py -ErrorAction SilentlyContinue).Source }
    if (-not $python) { throw '代理脚手架需要 python（未找到 python / py）。' }

    # 宿主侧 TCP 转发（脚手架，不入库）：受控后端按设计只绑回环，而只有绑 0.0.0.0 的宿主监听
    # 才能被 Docker 容器访问；nginx 到后端的代理路径本身仍是仓库 nginx.conf 原样。
    $relayFile = Join-Path $env:TEMP "t06-proxy-relay-$script:RunStamp.py"
    $relaySource = @'
"""T06 代理贯通用的宿主侧 TCP 转发（验收脚手架，由 t06-controlled-acceptance.ps1 生成，不入库）。

存在原因（本机 Docker Desktop 实测）：
1. 受控查证后端按设计只绑 127.0.0.1（LocalPreviewBindingGuard 精确要求回环字面量），不能改；
2. Docker Desktop 里 host.docker.internal 解析到 192.168.65.254，只有绑定 0.0.0.0 的宿主监听
   才能被容器访问（绑 127.0.0.1 或 WLAN 地址均 Connection refused）。
因此用本转发在 0.0.0.0 上接收容器来的连接，再转回宿主回环。nginx 用的仍是仓库自带 nginx.conf
的真实代理路径；本转发不削弱任何绑定约束，也不做鉴权或重放。验收结束按 PID 终止。
"""
import socket
import threading

LISTEN = ("0.0.0.0", __RELAY_PORT__)
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
    $relaySource = $relaySource -replace '__RELAY_PORT__', $Relay -replace '__BACKEND_PORT__', $Backend
    Set-Content -Path $relayFile -Value $relaySource -Encoding UTF8
    $script:RelayProcess = Start-Process -FilePath $python -ArgumentList $relayFile -WindowStyle Hidden -PassThru
    Wait-Until { Test-TcpListener -TargetHost '127.0.0.1' -Port $Relay } "宿主转发端口 $Relay" 30
    Write-Host "[t06] 宿主转发已就绪（PID $($script:RelayProcess.Id)，脚本 $relayFile）"

    $nginx = "zhku-t06-nginx-$script:RunStamp"
    docker rm -f $nginx *> $null
    $template = Join-Path $repoRoot 'agent\frontend\nginx.conf'
    if (-not (Test-Path $template)) { throw "未找到仓库 nginx 配置：$template" }
    # 官方镜像模板机制：挂到 /etc/nginx/templates/*.template，envsubst 用环境变量替换后生成 conf.d 配置
    docker run -d --name $nginx `
        --add-host "host.docker.internal:$DockerHostIp" `
        -e "ZHKU_CONTROLLED_BACKEND_PORT=$Relay" `
        -p "127.0.0.1:${Public}:80" `
        -v "${template}:/etc/nginx/templates/default.conf.template:ro" `
        'nginx:1.27-alpine' *> $null
    if ($LASTEXITCODE -ne 0) { throw "nginx 容器启动失败；排查：docker logs $nginx" }
    [void]$script:Containers.Add($nginx)
    Wait-Until { Test-TcpListener -TargetHost '127.0.0.1' -Port $Public } "nginx 发布端口 $Public" 60
    $generated = (docker exec $nginx cat /etc/nginx/conf.d/default.conf 2>$null) -join "`n"
    if ($generated -and ($generated -notmatch [regex]::Escape([string]$Relay))) {
        throw "nginx 生成的配置里没有出现后端端口 $Relay：模板替换可能未生效；排查：docker logs $nginx"
    }
    Write-Host "[t06] nginx 已就绪（$nginx：127.0.0.1:$Public -> 仓库 nginx.conf -> host.docker.internal:$Relay -> 后端 127.0.0.1:$Backend）"
    # 不 return 容器名：调用方不使用它，return 值会泄露出函数输出流；容器已登记在 $script:Containers
}

function Stop-ProxyScaffold {
    if ($script:RelayProcess -and -not $script:RelayProcess.HasExited) {
        Stop-Process -Id $script:RelayProcess.Id -Force
        Write-Host "[t06] 宿主转发已终止（PID $($script:RelayProcess.Id)）"
    }
    foreach ($container in @($script:Containers)) {
        if ($container -like 'zhku-t06-nginx-*') {
            docker rm -f $container *> $null
            Write-Host "[t06] nginx 容器已回收（$container）"
        }
    }
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
            Test-EvidenceLanded -EvidenceDir $evidenceDir -ExpectedFiles 'http-exchanges.json' | Out-Null
        }
        'chain' {
            $db = New-TempDatabase
            try {
                $evidenceDir = Join-Path $evidenceRunRoot 'chain'
                New-Item -ItemType Directory -Path $evidenceDir -Force *> $null
                $overall = Invoke-MavenAcceptance `
                    -TestFilter 'RuleAgentSimulatedChainTempDbHttpTest#realIdentityAndReviewedAssetsOverActualHttpWithCsrfIdempotencyAndIsolation+realTempDbCancelCapacityAndTerminalStatesOverActualHttp' `
                    -EvidenceDir $evidenceDir -DbUrl $db.Url -AssetsRootForTests $script:AssetsRoot
                Test-EvidenceLanded -EvidenceDir $evidenceDir -ExpectedFiles 'simulated-chain-tempdb.json','cancel-capacity-terminal.json' | Out-Null
            } finally {
                Remove-TempDatabase -Container $db.Container
            }
        }
        'proxy' {
            $db = New-TempDatabase
            try {
                Start-ProxyScaffold -Backend $BackendPort -Public $PublicPort -Relay $RelayPort
                try {
                    $evidenceDir = Join-Path $evidenceRunRoot 'proxy'
                    New-Item -ItemType Directory -Path $evidenceDir -Force *> $null
                    $extra = @{ 'zhku.proxy.backendPort' = $BackendPort; 'zhku.proxy.publicPort' = $PublicPort; 'zhku.proxy.bindAddress' = '127.0.0.1' }
                    $overall = Invoke-MavenAcceptance `
                        -TestFilter 'RuleAgentSimulatedChainTempDbHttpTest#sameChainThroughRepoNginxProxyWithActualProxiedResponses' `
                        -EvidenceDir $evidenceDir -DbUrl $db.Url -AssetsRootForTests $script:AssetsRoot -ExtraArgs $extra
                    Test-EvidenceLanded -EvidenceDir $evidenceDir -ExpectedFiles 'proxied-chain.json' | Out-Null
                } finally {
                    Stop-ProxyScaffold
                }
            } finally {
                Remove-TempDatabase -Container $db.Container
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
Write-Host "  maven 退出码：$overall（0=通过）"
Write-Host '  记账边界：chain/proxy 决策段为宿主模拟执行器（模型尝试 0、供应商 0）；existing 为 HTTP 契约面（databaseCalls=0）。'
if ($overall -ne 0) {
    Write-Host '  结论：未通过。失败轨迹以 maven 输出与证据目录为准，不得表述为通过。'
} else {
    Write-Host '  结论：本轮各场景 maven 判定通过；在线链路未运行，不得读作在线可用。'
}
exit $overall
