#requires -Version 5.1
<#
.SYNOPSIS
    长期持久身份库（正式账号）本地启动器：身份库用命名卷，规则库仍是一次性临时库。

.DESCRIPTION
    设计取舍（不放这些取舍，后面的人会照着改动把"重启后账号还在"这件事弄丢）：

    1) 身份库必须用【命名 Docker 卷】（zhkuagent-rulecheck-identity-data）。
       账号表 preview_identity、持久登录 token 表 preview_persistent_login 都写在 /var/lib/postgresql/data。
       若像规则库那样用 --tmpfs，容器一删数据立刻消失，"长期账号"就是假的；命名卷让容器可随时删除重建而数据仍在本机卷里。
       因此 Stop 只停不删身份库容器，卷永不自动删除（本脚本没有任何删除身份库卷的代码路径）。

    2) 规则库仍是一次性临时库（容器名 zhkuagent-rulecheck-rules-<随机>，--tmpfs）。
       规则库只承载可由 Flyway 重新初始化的规则/条款索引，重建零成本；用 tmpfs 保证每次启动都是干净状态，
       避免上一轮演示残留的脏数据影响判定。它不参与任何身份/会话数据。

    3) 身份库用户名/密码随机生成一次后写入 tmp/rule-check-identity/state.json。
       容器的 POSTGRES_USER/POSTGRES_PASSWORD 在创建时固化，重建容器若换密码就再也登不进旧卷里的数据目录，
       所以必须复用同一套凭据；状态文件位于仓库根 .gitignore 已忽略的 tmp/ 下，只在本机落地，绝不打印密码。

    4) 绝不触碰 127.0.0.1:54329 的 zhkuagent-postgres（用户共享演示库）。
       本脚本用的两个库都是自己起的隔离实例，端口必须避开 54329；身份库与规则库端口也必须互不相同，
       否则宿主 RuleCheckPreviewConfiguration 会直接拒绝启动。

    5) 宿主进程由"后台监管进程"持有（-Action Supervise，内部使用）。
       宿主用标准输入读 STOP 才优雅退出，而启动器自身要立刻返回并把终端交还用户；
       一旦启动器进程退出，被它继承的 stdin 管道会关闭，宿主读到 EOF 就会自行退出。
       所以 Start 会派生一个隐藏的 PowerShell 监管进程抱住宿主的 stdin，Stop 通过放置 stop.request 让它写下 STOP。
       监管进程若被杀，宿主 stdin 同样关闭并自行退出，不会留下无法停止的孤儿。

    6) Stop 策略（帮助文本一并声明）：向 stdin 写 STOP → 等宿主退出 → 删除规则库临时容器 →
       docker stop 身份库容器（保留容器与卷，端口全部释放）。绝不 docker rm 身份库容器，绝不删除卷。

.PARAMETER Action
    Check / Start / Stop / Restart / Status；Supervise 为内部监管模式，不要手工调用。

.PARAMETER IdentityPort
    身份库回环发布端口（默认 55440）。

.PARAMETER RulePort
    规则库回环发布端口（默认 55441）。

.PARAMETER BackendPort
    宿主 HTTP 端口（默认 58130）。

.PARAMETER SkipBuild
    不构建；jar 缺失或落后于 knowledge/src/main、knowledge/pom.xml 时直接失败，绝不静默使用旧包。

.PARAMETER BuildOnly
    只执行离线打包然后退出。

.EXAMPLE
    knowledge\scripts\start-persistent-rule-check.cmd -Action Check
.EXAMPLE
    knowledge\scripts\start-persistent-rule-check.cmd
.EXAMPLE
    knowledge\scripts\start-persistent-rule-check.cmd -Action Stop
#>
param(
    [ValidateSet('Check', 'Start', 'Stop', 'Restart', 'Status', 'Supervise')]
    [string]$Action = 'Start',
    [ValidateRange(1024, 65535)][int]$IdentityPort = 55440,
    [ValidateRange(1024, 65535)][int]$RulePort = 55441,
    [ValidateRange(1024, 65535)][int]$BackendPort = 58130,
    [switch]$SkipBuild,
    [switch]$BuildOnly,
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,31}$')][string]$InstanceName,
    [string]$HandbookPdf = '',
    [string]$AssetsRoot = ''
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$jarPath = Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-persistent.jar'
$stateDir = Join-Path $root 'tmp/rule-check-identity'
# 每个试用实例独享状态、容器及卷，避免新 checkout 接管既有账号数据。
if ($InstanceName) { $stateDir += '-' + $InstanceName }
$statePath = Join-Path $stateDir 'state.json'
$outLog = Join-Path $stateDir 'host-out.log'
$errLog = Join-Path $stateDir 'host-err.log'
$supervisorLog = Join-Path $stateDir 'supervisor.log'
$readyFile = Join-Path $stateDir 'ready.json'
$readyFailFile = Join-Path $stateDir 'ready-fail.json'
$stoppedFile = Join-Path $stateDir 'stopped.json'
$stopRequest = Join-Path $stateDir 'stop.request'

$script:stage = 'init'
$image = 'pgvector/pgvector:0.8.6-pg16-bookworm'
$identityDbName = 'zhkuagent_rulecheck_identity'
$ruleDbName = 'zhkuagent_rulecheck_e2e'
$ruleDbUser = 'zhkuagent'
$identityContainerName = 'zhkuagent-rulecheck-identity'
$identityVolumeName = 'zhkuagent-rulecheck-identity-data'
if ($InstanceName) {
    $identityContainerName += '-' + $InstanceName
    $identityVolumeName += '-' + $InstanceName
}
$hostReadySeconds = 120

. (Join-Path $root 'scripts/ZhkuDocker.ps1')

# ---------- 通用工具 ----------

function Test-PortFree {
    param([int]$Port)
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
    try { $listener.Start(); return $true } catch { return $false } finally { try { $listener.Stop() } catch { } }
}

function Test-PortListening {
    param([int]$Port)
    $client = New-Object Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        if (-not $task.Wait(400)) { return $false }
        return $client.Connected
    } catch { return $false } finally { $client.Dispose() }
}

function Write-JsonFile {
    param([string]$Path, $Value)
    $temp = $Path + '.tmp'
    [IO.File]::WriteAllText($temp, ($Value | ConvertTo-Json -Depth 8), $utf8)
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Force }
    Move-Item -LiteralPath $temp -Destination $Path
}

function Read-JsonFile {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    try { return (Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop) } catch { return $null }
}

function ConvertTo-StateTable {
    param($Source)
    $table = [ordered]@{}
    if ($Source) { foreach ($property in $Source.PSObject.Properties) { $table[$property.Name] = $property.Value } }
    return $table
}

function Read-State {
    # 空 OrderedDictionary 在 PowerShell 里是 truthy，若把"没有文件"也转成空表，
    # 调用方 if (-not $state) 就永远不成立，Stop/Status 会误判成"已有状态"。
    $raw = Read-JsonFile $statePath
    if (-not $raw) { return $null }
    return (ConvertTo-StateTable $raw)
}

function Save-State {
    param($Table)
    $Table['updatedAt'] = (Get-Date).ToString('o')
    Write-JsonFile $statePath $Table
}

# 任何进入终端/日志的文本都先过这里：密码、token、含密码的 JDBC URL 一律不出现。
function ConvertTo-SafeText {
    param([string]$Text)
    if (-not $Text) { return '' }
    $safe = $Text
    foreach ($secret in @($script:knownSecrets)) {
        if ($secret -and $secret.Length -ge 6) { $safe = $safe.Replace($secret, '***') }
    }
    $safe = [regex]::Replace($safe, '(?i)(password|pwd|passwd)\s*=\s*[^&\s;]+', '$1=***')
    $safe = [regex]::Replace($safe, '(?i)postgresql://[^/\s]+:[^@\s]+@', 'postgresql://***@')
    $safe = $safe -replace '\s+', ' '
    if ($safe.Length -gt 240) { $safe = $safe.Substring(0, 240) + '…' }
    return $safe.Trim()
}

$script:knownSecrets = @()

function Get-JavaPath {
    $command = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command -and (Test-Path -LiteralPath $command.Source -PathType Leaf)) { return $command.Source }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    return $null
}

function New-RandomSecret {
    param([int]$Bytes = 24)
    $buffer = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return ([Convert]::ToBase64String($buffer) -replace '[^A-Za-z0-9]', '')
}

function Invoke-Docker {
    param([string[]]$Arguments, [int]$TimeoutSeconds = 30)
    return (Invoke-ZhkuDocker -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds)
}

function Get-ContainerInfo {
    param([string]$Name)
    $result = Invoke-Docker @('inspect', $Name) 20
    if ($result.ExitCode -ne 0) { return $null }
    $parsed = @($result.Output | ConvertFrom-Json)
    if ($parsed.Count -lt 1) { return $null }
    $container = $parsed[0]
    $user = $null; $password = $null; $database = $null
    foreach ($entry in @($container.Config.Env)) {
        if ($entry -like 'POSTGRES_USER=*') { $user = $entry.Substring('POSTGRES_USER='.Length) }
        elseif ($entry -like 'POSTGRES_PASSWORD=*') { $password = $entry.Substring('POSTGRES_PASSWORD='.Length) }
        elseif ($entry -like 'POSTGRES_DB=*') { $database = $entry.Substring('POSTGRES_DB='.Length) }
    }
    $hostPort = $null
    try {
        $bindings = @($container.HostConfig.PortBindings.'5432/tcp')
        if ($bindings.Count -gt 0 -and $bindings[0]) { $hostPort = [int]$bindings[0].HostPort }
    } catch { }
    $volume = $null
    foreach ($mount in @($container.Mounts)) {
        if ($mount -and $mount.Destination -eq '/var/lib/postgresql/data') { $volume = $mount.Name }
    }
    return [pscustomobject]@{
        Name = $Name
        Running = [bool]$container.State.Running
        ExitCode = $container.State.ExitCode
        HostPort = $hostPort
        User = $user
        Password = $password
        Database = $database
        Volume = $volume
        Image = $container.Config.Image
    }
}

function Test-VolumeExists {
    param([string]$Name)
    $result = Invoke-Docker @('volume', 'inspect', $Name) 15
    return ($result.ExitCode -eq 0)
}

# 用 pg_isready 轮询，不靠固定 sleep：容器起来到真正能接受连接的时间在不同机器上差别很大。
function Wait-DatabaseReady {
    param([string]$Container, [string]$User, [string]$Database, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $probe = Invoke-Docker @('exec', $Container, 'pg_isready', '-U', $User, '-d', $Database) 15
        if ($probe.ExitCode -eq 0) { return $true }
        Start-Sleep -Milliseconds 300
    }
    return $false
}

function Test-JarFresh {
    # jar 必须比 knowledge/src/main 与 knowledge/pom.xml 都新，否则就是旧包，不能拿来冒充当前实现。
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { return $false }
    $jarTime = (Get-Item -LiteralPath $jarPath).LastWriteTimeUtc
    $inputs = @()
    $sourceRoot = Join-Path $root 'knowledge/src/main'
    if (Test-Path -LiteralPath $sourceRoot) { $inputs += @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File) }
    $pom = Join-Path $root 'knowledge/pom.xml'
    if (Test-Path -LiteralPath $pom) { $inputs += (Get-Item -LiteralPath $pom) }
    foreach ($item in $inputs) { if ($item.LastWriteTimeUtc -gt $jarTime) { return $false } }
    return $true
}

function Invoke-MavenBuild {
    $script:stage = 'build'
    $maven = Join-Path $root 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $maven -PathType Leaf)) { throw 'mvnw.cmd 不存在，无法构建持久宿主 jar' }
    Push-Location $root
    try {
        # 两处坑：'a' + 'b' 不加括号会把 '+' 当独立参数传给 mvnw；而 -Dfile.encoding 经 cmd 包装后
        # 会被拆成 '.encoding=UTF-8' 触发 LifecyclePhaseNotFoundException。编码改走 MAVEN_OPTS。
        $repoArg = '-Dmaven.repo.local=' + (Join-Path $env:USERPROFILE '.m2/repository')
        $previousMavenOpts = $env:MAVEN_OPTS
        $env:MAVEN_OPTS = ((@($previousMavenOpts, '-Dfile.encoding=UTF-8') | Where-Object { $_ }) -join ' ')
        & $maven -o $repoArg -pl knowledge -am -DskipTests package
        $code = $LASTEXITCODE
    } finally {
        $env:MAVEN_OPTS = $previousMavenOpts
        Pop-Location
    }
    if ($code -ne 0) { throw ('离线打包失败（mvnw.cmd -o -pl knowledge -am -DskipTests package），退出码 ' + $code + '；不启动旧包') }
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) { throw '打包结束但持久宿主 jar 不存在' }
    if (-not (Test-JarFresh)) { throw '打包结束但 jar 仍落后于源码，检查构建产物路径' }
    return $code
}

# ---------- Check ----------

function Invoke-Check {
    $script:stage = 'check'
    $report = [ordered]@{
        action = 'Check'
        root = $root
        jar = [ordered]@{ path = $jarPath; exists = (Test-Path -LiteralPath $jarPath -PathType Leaf) }
        docker = [ordered]@{ cliAvailable = $false; engineReady = $false; imagePresent = $false; image = $image }
        ports = @()
        identity = [ordered]@{ container = $identityContainerName; volume = $identityVolumeName; containerExists = $false; containerRunning = $false; volumeExists = $false; credentialsReadable = $false }
        state = [ordered]@{ path = $statePath; exists = (Test-Path -LiteralPath $statePath -PathType Leaf) }
        gitIgnored = $false
    }
    if ($report.jar.exists) {
        $item = Get-Item -LiteralPath $jarPath
        $report.jar.length = $item.Length
        $report.jar.lastWriteTime = $item.LastWriteTime.ToString('o')
        $report.jar.upToDate = (Test-JarFresh)
    }
    foreach ($pair in @(@{ name = 'identity'; port = $IdentityPort }, @{ name = 'rule'; port = $RulePort }, @{ name = 'backend'; port = $BackendPort })) {
        $report.ports += [ordered]@{ name = $pair.name; port = $pair.port; free = (Test-PortFree $pair.port); listening = (Test-PortListening $pair.port) }
    }
    try {
        [void](Resolve-ZhkuDockerExecutable)
        $report.docker.cliAvailable = $true
        try { Ensure-ZhkuDocker -TimeoutSeconds 15 -CheckOnly; $report.docker.engineReady = $true } catch { $report.docker.engineReady = $false }
        if ($report.docker.engineReady) {
            $images = Invoke-Docker @('image', 'inspect', $image) 20
            $report.docker.imagePresent = ($images.ExitCode -eq 0)
            $report.identity.volumeExists = (Test-VolumeExists $identityVolumeName)
            $container = Get-ContainerInfo $identityContainerName
            if ($container) {
                $report.identity.containerExists = $true
                $report.identity.containerRunning = $container.Running
            }
            $rules = Invoke-Docker @('ps', '--filter', 'name=zhkuagent-rulecheck-rules-', '--format', '{{.Names}}') 20
            if ($rules.ExitCode -eq 0 -and $rules.Output) { $report.rulesContainers = @($rules.Output -split "`n" | Where-Object { $_.Trim() }) }
        }
    } catch {
        $report.docker.error = ConvertTo-SafeText $_.Exception.Message
    }
    $state = Read-State
    if ($state) {
        # 身份库凭据可读 = 状态文件里用户名/密码都在，且与容器实际环境一致；不一致时后续 Start 会明确拒绝。
        $report.identity.credentialsReadable = [bool]($state['identityDbUser'] -and $state['identityDbPassword'])
        $report.state.identityDbUser = $state['identityDbUser']
        $report.state.hostPid = $state['hostPid']
        $report.state.ruleContainer = $state['ruleContainer']
        if ($report.identity.credentialsReadable) { $script:knownSecrets += [string]$state['identityDbPassword'] }
    }
    $ignore = & git -C $root check-ignore -q 'tmp/rule-check-identity/state.json' 2>$null
    $report.gitIgnored = ($LASTEXITCODE -eq 0)
    Write-Output ($report | ConvertTo-Json -Depth 8)
    $script:exitCode = 0
}

# ---------- Start ----------

function New-RuleContainerName {
    return ('zhkuagent-rulecheck-rules-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
}

function Invoke-Start {
    $script:stage = 'preflight'
    if ($IdentityPort -eq 54329 -or $RulePort -eq 54329 -or $BackendPort -eq 54329) { throw '端口 54329 是用户共享演示库，禁止使用' }
    if ($IdentityPort -eq $RulePort -or $IdentityPort -eq $BackendPort -or $RulePort -eq $BackendPort) { throw '身份库、规则库、宿主端口必须互不相同' }

    $java = Get-JavaPath
    if (-not $java) { throw '找不到 java.exe；本机需要 JDK 21' }

    if (-not (Test-JarFresh)) {
        if ($SkipBuild) { throw 'jar 缺失或落后于 knowledge/src/main；先去 mvnw.cmd -o -pl knowledge -am -DskipTests package，或去掉 -SkipBuild' }
        [void](Invoke-MavenBuild)
    }

    $state = Read-State
    if ($state) {
        if ($state['identityDbPassword']) { $script:knownSecrets += [string]$state['identityDbPassword'] }
        if ($state['rulePassword']) { $script:knownSecrets += [string]$state['rulePassword'] }
        $existingPid = $state['hostPid']
        if ($existingPid) {
            $existing = Get-Process -Id ([int]$existingPid) -ErrorAction SilentlyContinue
            if ($existing -and $existing.ProcessName -eq 'java') {
                throw ('已有持久宿主在运行（PID ' + $existingPid + '），请先 -Action Stop；本脚本不会静默杀进程')
            }
        }
    }

    if (-not (Test-PortFree $BackendPort)) { throw ('宿主端口 ' + $BackendPort + ' 已被占用') }

    $script:stage = 'docker'
    Ensure-ZhkuDocker -TimeoutSeconds 120
    $imageInfo = Invoke-Docker @('image', 'inspect', $image) 20
    if ($imageInfo.ExitCode -ne 0) { throw ('本机缺少镜像 ' + $image + '；请先 docker pull（本脚本不联网拉取）') }

    # ---- 身份库：命名卷 + 保留容器 ----
    $script:stage = 'identity'
    if (-not (Test-VolumeExists $identityVolumeName)) {
        $created = Invoke-Docker @('volume', 'create', $identityVolumeName) 20
        if ($created.ExitCode -ne 0) { throw '创建身份库命名卷失败' }
    }
    $identityUser = $null; $identityPassword = $null
    if ($state -and $state['identityDbUser'] -and $state['identityDbPassword']) {
        $identityUser = [string]$state['identityDbUser']
        $identityPassword = [string]$state['identityDbPassword']
    } else {
        $identityUser = 'zhku_id_' + [guid]::NewGuid().ToString('N').Substring(0, 8)
        $identityPassword = New-RandomSecret 24
    }
    if ($identityUser -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') { throw '身份库用户名不符合宿主校验规则' }
    $script:knownSecrets += $identityPassword

    $identityContainer = Get-ContainerInfo $identityContainerName
    if ($identityContainer) {
        # 容器已存在：必须与状态文件凭据、卷、端口一致，否则旧卷里的账号将无法用新凭据登录。
        if ($state -and $state['identityDbUser'] -and $identityContainer.User -and $identityContainer.User -ne $identityUser) { throw '身份库容器实际用户名与状态文件不一致；不要删卷，先人工核对 tmp/rule-check-identity/state.json' }
        if ($state -and $state['identityDbPassword'] -and $identityContainer.Password -and $identityContainer.Password -ne $identityPassword) { throw '身份库容器实际密码与状态文件不一致；删容器会保留卷但账号将不可登录，请先人工核对' }
        if ($identityContainer.Volume -ne $identityVolumeName) { throw '身份库容器未挂载预期命名卷；拒绝启动以免写入错误数据目录' }
        if ($identityContainer.Database -ne $identityDbName) { throw '身份库容器 POSTGRES_DB 与预期不一致' }
        if ($identityContainer.HostPort -and $identityContainer.HostPort -ne $IdentityPort) { throw ('身份库容器已发布到 ' + $identityContainer.HostPort + '，与本次 -IdentityPort ' + $IdentityPort + ' 不一致；请沿用原端口或先自行删除该容器') }
        if (-not $identityContainer.Running) {
            $started = Invoke-Docker @('start', $identityContainerName) 40
            if ($started.ExitCode -ne 0) { throw '身份库容器启动失败' }
        }
    } else {
        # 只有需要新建容器时才要求端口空闲；已存在的容器自己就占着这个端口，不能把它判成冲突。
        if (-not (Test-PortFree $IdentityPort)) { throw ('身份库端口 ' + $IdentityPort + ' 已被占用') }
        # 容器的 POSTGRES_* 只在创建时生效；密码用 docker 自身环境传入，不出现在命令行。
        $previousDbPassword = $env:POSTGRES_PASSWORD
        try {
            $env:POSTGRES_PASSWORD = $identityPassword
            $created = Invoke-Docker @('run', '-d', '--pull', 'never', '--name', $identityContainerName,
                '--publish', ('127.0.0.1:' + $IdentityPort + ':5432'),
                '--mount', ('type=volume,source=' + $identityVolumeName + ',target=/var/lib/postgresql/data'),
                '--env', ('POSTGRES_USER=' + $identityUser), '--env', ('POSTGRES_DB=' + $identityDbName),
                '--env', 'POSTGRES_PASSWORD', $image) 60
        } finally { $env:POSTGRES_PASSWORD = $previousDbPassword }
        if ($created.ExitCode -ne 0) { throw ('身份库容器创建失败：' + (ConvertTo-SafeText $created.Output)) }
    }
    if (-not (Wait-DatabaseReady $identityContainerName $identityUser $identityDbName 90)) { throw '身份库 90 秒内未就绪' }

    # 容器一旦创建，POSTGRES_* 就固化了；凭据必须立刻落盘，否则中途失败会让下次启动对不上旧卷里的数据目录。
    if (-not (Test-Path -LiteralPath $stateDir)) { New-Item -ItemType Directory -Path $stateDir -Force | Out-Null }
    $state = ConvertTo-StateTable (Read-JsonFile $statePath)
    $state['identityPort'] = $IdentityPort
    $state['identityContainer'] = $identityContainerName
    $state['identityVolume'] = $identityVolumeName
    $state['identityDbName'] = $identityDbName
    $state['identityDbUser'] = $identityUser
    $state['identityDbPassword'] = $identityPassword
    $state['lastAction'] = 'Start'
    Save-State $state

    # ---- 规则库：一次性临时容器 + tmpfs ----
    $script:stage = 'rules'
    if (-not (Test-PortFree $RulePort)) { throw ('规则库端口 ' + $RulePort + ' 已被占用') }
    $rulePassword = New-RandomSecret 24
    $script:knownSecrets += $rulePassword
    $ruleContainer = New-RuleContainerName
    $script:ruleContainer = $ruleContainer
    $previousDbPassword = $env:POSTGRES_PASSWORD
    try {
        $env:POSTGRES_PASSWORD = $rulePassword
        $created = Invoke-Docker @('run', '-d', '--pull', 'never', '--name', $ruleContainer,
            '--publish', ('127.0.0.1:' + $RulePort + ':5432'),
            '--tmpfs', '/var/lib/postgresql/data',
            '--env', ('POSTGRES_USER=' + $ruleDbUser), '--env', ('POSTGRES_DB=' + $ruleDbName),
            '--env', 'POSTGRES_PASSWORD', $image) 60
    } finally { $env:POSTGRES_PASSWORD = $previousDbPassword }
    if ($created.ExitCode -ne 0) { throw ('规则库临时容器创建失败：' + (ConvertTo-SafeText $created.Output)) }
    if (-not (Wait-DatabaseReady $ruleContainer $ruleDbUser $ruleDbName 90)) { throw '规则库 90 秒内未就绪' }
    $script:startSucceeded = $false

    # ---- 落状态并派生监管进程 ----
    $script:stage = 'host'
    if (-not (Test-Path -LiteralPath $stateDir)) { New-Item -ItemType Directory -Path $stateDir -Force | Out-Null }
    foreach ($stale in @($readyFile, $readyFailFile, $stoppedFile, $stopRequest)) {
        if (Test-Path -LiteralPath $stale) { Remove-Item -LiteralPath $stale -Force -ErrorAction SilentlyContinue }
    }
    $state = ConvertTo-StateTable (Read-JsonFile $statePath)
    $state['version'] = 1
    $state['identityPort'] = $IdentityPort
    $state['rulePort'] = $RulePort
    $state['backendPort'] = $BackendPort
    $state['identityContainer'] = $identityContainerName
    $state['identityVolume'] = $identityVolumeName
    $state['identityDbName'] = $identityDbName
    $state['identityDbUser'] = $identityUser
    $state['identityDbPassword'] = $identityPassword
    $state['ruleContainer'] = $ruleContainer
    $state['rulePassword'] = $rulePassword
    $state['hostJar'] = $jarPath
    $state['handbookPdf'] = $HandbookPdf
    # 源码从固定提交构建，审核原件可在独立只读目录；原有指纹/资格校验仍在连接前执行。
    $state['assetsRoot'] = if ($AssetsRoot) { (Resolve-Path -LiteralPath $AssetsRoot).Path } else { $root }
    $state['hostPid'] = $null
    $state['supervisorPid'] = $null
    $state['lastAction'] = 'Start'
    Save-State $state

    $powerShellPath = Join-Path $PSHOME 'powershell.exe'
    if (-not (Test-Path -LiteralPath $powerShellPath -PathType Leaf)) {
        $command = Get-Command powershell.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $command) { throw '找不到 powershell.exe，无法派生监管进程' }
        $powerShellPath = $command.Source
    }
    $quotedScript = '"' + $PSCommandPath + '"'
    $argumentLine = '-NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ' + $quotedScript + ' -Action Supervise'
    if ($InstanceName) { $argumentLine += ' -InstanceName ' + $InstanceName }
    $supervisor = Start-Process -FilePath $powerShellPath -ArgumentList $argumentLine -WindowStyle Hidden -PassThru
    $script:supervisorPid = $supervisor.Id

    $deadline = (Get-Date).AddSeconds($hostReadySeconds + 15)
    $ready = $null
    while ((Get-Date) -lt $deadline) {
        $ready = Read-JsonFile $readyFile
        if ($ready) { break }
        $failure = Read-JsonFile $readyFailFile
        if ($failure) { break }
        if ($supervisor.HasExited) { break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $ready) {
        $script:stage = 'host'
        $lines = @()
        if (Test-Path -LiteralPath $outLog) { $lines = @(Get-Content -LiteralPath $outLog -Tail 40 -ErrorAction SilentlyContinue) }
        foreach ($line in $lines) { Write-Host $line }
        $reason = 'supervisor 未在死线内报告就绪'
        $failure = Read-JsonFile $readyFailFile
        if ($failure -and $failure.reason) { $reason = [string]$failure.reason }
        elseif ($supervisor.HasExited) { $reason = '监管进程提前退出，退出码 ' + $supervisor.ExitCode }
        throw $reason
    }
    if ($ready.PSObject.Properties['failure']) { throw ([string]$ready.failure) }

    $state['hostPid'] = [int]$ready.hostPid
    $state['supervisorPid'] = [int]$supervisor.Id
    $state['readyAt'] = (Get-Date).ToString('o')
    Save-State $state
    $script:startSucceeded = $true
    $script:stage = 'ready'
    $port = $BackendPort
    if (-not (Test-PortListening $port)) { throw '宿主声称就绪但端口未监听' }
    Write-Output ('PERSISTENT_RULECHECK_READY backend=http://127.0.0.1:' + $port + ' identityDbPort=' + $IdentityPort + ' ruleDbPort=' + $RulePort)
    $script:exitCode = 0
}

# ---------- Stop ----------

function Invoke-Stop {
    $script:stage = 'stop'
    $state = Read-State
    if (-not $state) {
        Write-Output 'PERSISTENT_RULECHECK_STOPPED already-stopped（没有本地状态文件）'
        $script:exitCode = 0
        return
    }
    if ($state['identityDbPassword']) { $script:knownSecrets += [string]$state['identityDbPassword'] }
    if ($state['rulePassword']) { $script:knownSecrets += [string]$state['rulePassword'] }

    $hostPid = $state['hostPid']
    $hostAlive = $false
    if ($hostPid) {
        $process = Get-Process -Id ([int]$hostPid) -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -eq 'java') { $hostAlive = $true }
    }
    if ($hostAlive) {
        # 监管进程持有宿主 stdin；若它已死，宿主的 stdin 也早已关闭并自行退出，所以"宿主活着"即意味着控制通道可用。
        if (Test-Path -LiteralPath $stoppedFile) { Remove-Item -LiteralPath $stoppedFile -Force -ErrorAction SilentlyContinue }
        if (Test-Path -LiteralPath $stopRequest) { Remove-Item -LiteralPath $stopRequest -Force -ErrorAction SilentlyContinue }
        [IO.File]::WriteAllText($stopRequest, 'STOP', $utf8)
        $deadline = (Get-Date).AddSeconds(60)
        while ((Get-Date) -lt $deadline) {
            if (Test-Path -LiteralPath $stoppedFile) { break }
            if (-not (Get-Process -Id ([int]$hostPid) -ErrorAction SilentlyContinue)) { break }
            Start-Sleep -Milliseconds 200
        }
        $aliveAfter = Get-Process -Id ([int]$hostPid) -ErrorAction SilentlyContinue
        if ($aliveAfter) {
            throw ('宿主未在 60 秒内响应 STOP（PID ' + $hostPid + '）；不静默强杀，请检查 tmp/rule-check-identity/host-out.log')
        }
        if (-not (Test-Path -LiteralPath $stoppedFile)) {
            # 进程消失了却没写出 stopped.json：说明监管进程异常，宿主是被 EOF 收尾的；端口仍需复核。
            $stoppedRecord = Read-JsonFile $stoppedFile
        }
        if (Test-PortListening $BackendPort) { throw ('宿主进程已退出但端口 ' + $BackendPort + ' 仍在监听') }
    }

    $script:stage = 'cleanup'
    $ruleContainer = $state['ruleContainer']
    if ($ruleContainer -and $ruleContainer -like 'zhkuagent-rulecheck-rules-*') {
        [void](Invoke-Docker @('rm', '-f', $ruleContainer) 40)
    }
    # 身份库容器只停不删：容器里的 POSTGRES_* 与卷配对，删了虽然卷还在，但状态文件/容器凭据一致性的复核就失去依据。
    $identity = Get-ContainerInfo $identityContainerName
    if ($identity -and $identity.Running) {
        $stopResult = Invoke-Docker @('stop', $identityContainerName) 90
        if ($stopResult.ExitCode -ne 0) { throw '身份库容器停止失败（容器与卷均保留）' }
    }
    foreach ($stale in @($stopRequest, $readyFile, $readyFailFile)) {
        if (Test-Path -LiteralPath $stale) { Remove-Item -LiteralPath $stale -Force -ErrorAction SilentlyContinue }
    }
    $state['hostPid'] = $null
    $state['supervisorPid'] = $null
    $state['ruleContainer'] = $null
    $state['rulePassword'] = $null
    $state['lastAction'] = 'Stop'
    Save-State $state
    Write-Output ('PERSISTENT_RULECHECK_STOPPED host=exited rulesContainer=removed identityContainer=stopped identityVolume=' + $identityVolumeName)
    $script:exitCode = 0
}

# ---------- Status ----------

function Invoke-Status {
    $script:stage = 'status'
    $state = Read-State
    if ($state -and $state['identityDbPassword']) { $script:knownSecrets += [string]$state['identityDbPassword'] }
    $hostPid = $null; $hostAlive = $false; $supervisorAlive = $false
    if ($state) {
        $hostPid = $state['hostPid']
        if ($hostPid) {
            $process = Get-Process -Id ([int]$hostPid) -ErrorAction SilentlyContinue
            $hostAlive = [bool]($process -and $process.ProcessName -eq 'java')
        }
        if ($state['supervisorPid']) {
            $supervisorAlive = [bool](Get-Process -Id ([int]$state['supervisorPid']) -ErrorAction SilentlyContinue)
        }
    }
    $identity = $null; $volumeExists = $false; $rulesContainers = @()
    $dockerReady = $false
    try {
        Ensure-ZhkuDocker -TimeoutSeconds 15 -CheckOnly
        $dockerReady = $true
        $identity = Get-ContainerInfo $identityContainerName
        $volumeExists = Test-VolumeExists $identityVolumeName
        $rules = Invoke-Docker @('ps', '--filter', 'name=zhkuagent-rulecheck-rules-', '--format', '{{.Names}}') 20
        if ($rules.ExitCode -eq 0 -and $rules.Output) { $rulesContainers = @($rules.Output -split "`n" | Where-Object { $_.Trim() }) }
    } catch { }
    $report = [ordered]@{
        action = 'Status'
        stateExists = [bool]$state
        host = [ordered]@{ pid = $hostPid; alive = $hostAlive; supervisorPid = $(if ($state) { $state['supervisorPid'] } else { $null }); supervisorAlive = $supervisorAlive; backendPort = $BackendPort; listening = (Test-PortListening $BackendPort) }
        identity = [ordered]@{
            container = $identityContainerName
            exists = [bool]$identity
            running = $(if ($identity) { $identity.Running } else { $false })
            volume = $identityVolumeName
            volumeExists = $volumeExists
            publishedPort = $(if ($identity) { $identity.HostPort } else { $null })
        }
        rules = [ordered]@{ port = $RulePort; listening = (Test-PortListening $RulePort); containers = $rulesContainers }
        dockerReady = $dockerReady
    }
    Write-Output ($report | ConvertTo-Json -Depth 6)
    $script:exitCode = 0
}

# ---------- Supervise（内部：抱住宿主 stdin，落日志，等 stop.request） ----------

function Invoke-Supervisor {
    $script:stage = 'supervise'
    $state = Read-State
    if (-not $state) { throw '监管进程缺少状态文件' }
    $log = New-Object IO.StreamWriter($supervisorLog, $false, $utf8)
    $log.AutoFlush = $true
    $stdoutWriter = New-Object IO.StreamWriter($outLog, $false, $utf8)
    $stdoutWriter.AutoFlush = $true
    $process = $null
    $stopped = $false
    try {
        $log.WriteLine(('supervisor start ' + (Get-Date).ToString('o') + ' pid=' + $PID))
        $java = Get-JavaPath
        if (-not $java) { throw '监管进程找不到 java.exe' }
        $jar = [string]$state['hostJar']
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw '持久宿主 jar 不存在' }
        $info = New-Object Diagnostics.ProcessStartInfo
        $info.FileName = $java
        $info.Arguments = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "' + $jar + '"'
        if ($state['handbookPdf']) {
            $pdf = (Resolve-Path -LiteralPath ([string]$state['handbookPdf'])).Path
            if ($pdf.Contains('"')) { throw 'PDF路径含非法引号' }
            $info.Arguments = '-Dzhku.t06.handbookPdf="' + $pdf + '" ' + $info.Arguments
        }
        $info.WorkingDirectory = $root
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardInput = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        # Windows PowerShell 5.1（.NET Framework）的 ProcessStartInfo 没有 StandardInputEncoding，
        # 设了会直接抛异常让监管进程起不来；STOP 只写 ASCII，用默认编码即可。
        $info.StandardOutputEncoding = $utf8
        $info.StandardErrorEncoding = $utf8
        $identityPort = [int]$state['identityPort']
        $rulePort = [int]$state['rulePort']
        $backendPort = [int]$state['backendPort']
        $variables = $info.EnvironmentVariables
        $variables['ZHKU_RULECHECK_ROOT'] = if ($state['assetsRoot']) { [string]$state['assetsRoot'] } else { $root }
        $variables['ZHKU_RULECHECK_PORT'] = [string]$backendPort
        $variables['ZHKU_DB_URL'] = 'jdbc:postgresql://127.0.0.1:' + $rulePort + '/' + $ruleDbName
        $variables['ZHKU_DB_PASSWORD'] = [string]$state['rulePassword']
        $variables['ZHKU_RULECHECK_IDENTITY_DB_URL'] = 'jdbc:postgresql://127.0.0.1:' + $identityPort + '/' + $identityDbName
        $variables['ZHKU_RULECHECK_IDENTITY_DB_USER'] = [string]$state['identityDbUser']
        $variables['ZHKU_RULECHECK_IDENTITY_DB_PASSWORD'] = [string]$state['identityDbPassword']
        # 任务回放目录一旦存在会切到 TASK_PLAN_REPLAY，与"持久身份库"验收模式无关，这里显式清掉。
        foreach ($name in @('ZHKU_RULECHECK_TASK_BATCH_DIRECTORY', 'ZHKU_RULECHECK_TASK_PACKAGE_SHA256', 'ZHKU_RULECHECK_TASK_SUMMARY_SHA256', 'DASHSCOPE_API_KEY')) {
            [void]$variables.Remove($name)
        }
        $process = [Diagnostics.Process]::Start($info)
        $log.WriteLine(('host pid=' + $process.Id))
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $ring = New-Object 'System.Collections.Generic.Queue[string]'
        $readTask = $process.StandardOutput.ReadLineAsync()
        $sawReady = $false
        $stopSent = $false
        $stopDeadline = [DateTime]::MaxValue
        $deadline = (Get-Date).AddSeconds($hostReadySeconds)
        while (-not $process.HasExited) {
            if ($readTask -and $readTask.IsCompleted) {
                $line = $null
                try { $line = $readTask.Result } catch { $line = $null }
                if ($null -eq $line) { $readTask = $null }
                else {
                    $stdoutWriter.WriteLine($line)
                    $ring.Enqueue($line)
                    while ($ring.Count -gt 40) { [void]$ring.Dequeue() }
                    if ($line -like 'RULECHECK_HOST_READY*') { $sawReady = $true }
                    $readTask = $process.StandardOutput.ReadLineAsync()
                }
            }
            if (-not $sawReady) {
                if ((Get-Date) -gt $deadline) {
                    $log.WriteLine('host ready timeout')
                    try { $process.Kill($true) } catch { try { $process.Kill() } catch { } }
                    [void]$process.WaitForExit(5000)
                    break
                }
            } else {
                if ($stopped -eq $false) {
                    $stopped = $true
                    $ready = [ordered]@{
                        ready = $true
                        hostPid = $process.Id
                        startedAt = (Get-Date).ToString('o')
                        backendPort = $backendPort
                        identityDbPort = $identityPort
                        ruleDbPort = $rulePort
                        identityContainer = $identityContainerName
                        ruleContainer = [string]$state['ruleContainer']
                    }
                    Write-JsonFile $readyFile $ready
                    $log.WriteLine('ready reported')
                }
                if (-not $stopSent -and (Test-Path -LiteralPath $stopRequest)) {
                    try {
                        $process.StandardInput.WriteLine('STOP')
                        $process.StandardInput.Flush()
                        $stopSent = $true
                        $stopDeadline = (Get-Date).AddSeconds(60)
                        $log.WriteLine('STOP sent')
                    } catch { $log.WriteLine(('STOP 写入失败：' + $_.Exception.Message)) }
                }
                if ($stopSent -and (Get-Date) -gt $stopDeadline) {
                    $log.WriteLine('host 未在 60 秒内响应 STOP')
                    break
                }
            }
            Start-Sleep -Milliseconds 100
        }
        # 收紧尾部输出：子进程退出后读干净，保证 host-out.log 完整。
        $tailDeadline = (Get-Date).AddSeconds(5)
        while ($readTask -and (Get-Date) -lt $tailDeadline) {
            if ($readTask.IsCompleted) {
                $line = $null
                try { $line = $readTask.Result } catch { $line = $null }
                if ($null -eq $line) { break }
                $stdoutWriter.WriteLine($line)
                $readTask = $process.StandardOutput.ReadLineAsync()
            } else { Start-Sleep -Milliseconds 50 }
        }
        [void]$process.WaitForExit(5000)
        $outcome = [ordered]@{ stopped = $process.HasExited; hostExitCode = $(if ($process.HasExited) { $process.ExitCode } else { $null }); stopRequested = $stopSent; stoppedAt = (Get-Date).ToString('o') }
        if (-not $sawReady) {
            $lastLines = @($ring.ToArray())
            $outcome['reason'] = 'host 未在 ' + $hostReadySeconds + ' 秒内输出就绪标志'
            $outcome['lastLines'] = $lastLines
            Write-JsonFile $readyFailFile $outcome
            $log.WriteLine('host not ready; exit=' + $(if ($process.HasExited) { $process.ExitCode } else { 'running' }))
        } else {
            Write-JsonFile $stoppedFile $outcome
            $log.WriteLine('host exited=' + $process.HasExited)
        }
        try { [IO.File]::WriteAllText($errLog, $stderrTask.Result, $utf8) } catch { }
    } catch {
        $log.WriteLine(('supervisor error: ' + $_.Exception.Message))
        try { [IO.File]::WriteAllText($readyFailFile, ((@{ failure = (ConvertTo-SafeText $_.Exception.Message) } | ConvertTo-Json)), $utf8) } catch { }
        throw
    } finally {
        if ($process) { $process.Dispose() }
        $stdoutWriter.Dispose()
        $log.Dispose()
    }
    $script:exitCode = 0
}

# ---------- 调度 ----------

try {
    if ($BuildOnly) {
        [void](Invoke-MavenBuild)
        Write-Output ('PERSISTENT_RULECHECK_BUILD_OK jar=' + $jarPath)
        exit 0
    }
    $script:exitCode = 0
    switch ($Action) {
        'Check' { Invoke-Check }
        'Start' { Invoke-Start }
        'Stop' { Invoke-Stop }
        'Restart' {
            Invoke-Stop
            if ($script:exitCode -eq 0) { Invoke-Start }
        }
        'Status' { Invoke-Status }
        'Supervise' { Invoke-Supervisor }
    }
    exit $script:exitCode
} catch {
    $reason = ConvertTo-SafeText $_.Exception.Message
    if (-not $reason) { $reason = 'unknown' }
    Write-Output ('PERSISTENT_RULECHECK_FAILED stage=' + $script:stage + ' reason=' + $reason)
    exit 1
} finally {
    # 只回收本次自己创建、且启动失败时已无意义的资源；身份库容器与卷始终保留。
    if ($script:ruleContainer -and -not $script:startSucceeded) {
        $existing = Get-ContainerInfo $script:ruleContainer
        if ($existing) { [void](Invoke-Docker @('rm', '-f', $script:ruleContainer) 40) }
    }
}
