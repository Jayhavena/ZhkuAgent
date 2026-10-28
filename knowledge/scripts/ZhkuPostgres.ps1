# Shared implementation; compatible with Windows PowerShell 5.1.
$script:ZhkuRepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $script:ZhkuRepoRoot 'scripts/ZhkuDocker.ps1')

function Get-ZhkuRemainingSeconds {
    param($Clock, [int]$TimeoutSeconds)
    $remaining = $TimeoutSeconds - $Clock.Elapsed.TotalSeconds
    if ($remaining -le 0) { throw 'PostgreSQL readiness timed out; no volumes were deleted.' }
    return [int][Math]::Ceiling($remaining)
}

function Get-ZhkuLocalValue {
    param([string]$Name)
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $value = [Environment]::GetEnvironmentVariable($Name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    }
    return $null
}

function Get-ZhkuDbConfiguration {
    param([string]$DataRoot, [int]$Port = 0)
    # 每次入口重新读取凭证；禁止从容器 inspect 的旧环境反推真实口令。
    & (Join-Path $script:ZhkuRepoRoot 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
    $legacy = @{}
    if ($DataRoot) {
        $envFile = Join-Path $DataRoot 'infra/.env'
        if (Test-Path -LiteralPath $envFile -PathType Leaf) {
            foreach ($line in [IO.File]::ReadAllLines($envFile)) {
                if ($line -match '^\s*(ZHKU_DB_(?:NAME|USER|PORT|PASSWORD))\s*=(.*)$') {
                    $key = $Matches[1]; $value = $Matches[2].Trim()
                    if ($legacy.ContainsKey($key)) { throw 'Duplicate database setting in legacy infra/.env.' }
                    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                        $value = $value.Substring(1, $value.Length - 2)
                    }
                    $legacy[$key] = $value
                }
            }
        }
    }
    $values = @{}
    foreach ($key in @('ZHKU_DB_NAME', 'ZHKU_DB_USER', 'ZHKU_DB_PORT', 'ZHKU_DB_PASSWORD')) {
        $value = Get-ZhkuLocalValue $key
        if ([string]::IsNullOrWhiteSpace($value)) { $value = $legacy[$key] }
        $values[$key] = $value
    }
    # 这些非秘密默认值与既有本机 Java 入口一致；密码绝不生成或替换。
    if (-not $values.ZHKU_DB_NAME) { $values.ZHKU_DB_NAME = 'zhkuagent' }
    if (-not $values.ZHKU_DB_USER) { $values.ZHKU_DB_USER = 'zhkuagent' }
    if (-not $values.ZHKU_DB_PORT) { $values.ZHKU_DB_PORT = '54329' }
    if ($Port -gt 0) { $values.ZHKU_DB_PORT = [string]$Port }
    $number = 0
    if (-not [int]::TryParse($values.ZHKU_DB_PORT, [ref]$number) -or $number -lt 1024 -or $number -gt 65535) {
        throw 'ZHKU_DB_PORT must be an explicit port between 1024 and 65535.'
    }
    foreach ($key in @('ZHKU_DB_NAME', 'ZHKU_DB_USER')) {
        if ($values[$key] -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') { throw 'Invalid local database name or user.' }
    }
    if ([string]::IsNullOrWhiteSpace($values.ZHKU_DB_PASSWORD)) {
        throw 'ZHKU_DB_PASSWORD unavailable in Process/User/Machine; use -DataRoot only for an existing legacy infra/.env. No password was generated.'
    }
    return $values
}

function Invoke-ZhkuDbDocker {
    param([string[]]$Arguments, $Clock, [int]$TimeoutSeconds)
    $limit = [Math]::Min(15, (Get-ZhkuRemainingSeconds $Clock $TimeoutSeconds))
    $result = Invoke-ZhkuDocker -Arguments $Arguments -TimeoutSeconds $limit
    if ($result.ExitCode -ne 0) {
        # Docker 错误可能包含环境或连接细节；不回显原输出，也不输出完整 inspect。
        throw "Local Docker operation failed ($($Arguments[0])); no volumes were deleted. Check Docker access, port conflicts and container ownership."
    }
    return $result.Output
}

function Get-ZhkuPostgresContainer {
    param($Clock, [int]$TimeoutSeconds)
    $raw = Invoke-ZhkuDbDocker -Arguments @('container', 'inspect', 'zhkuagent-postgres') -Clock $Clock -TimeoutSeconds $TimeoutSeconds
    try { $items = @($raw | ConvertFrom-Json) } catch { throw 'Unreadable PostgreSQL container metadata.' }
    if ($items.Count -ne 1) { throw 'Expected exactly one existing PostgreSQL container; no new database will be created.' }
    return $items[0]
}

function Assert-ZhkuPostgresOwnership {
    param($Container)
    if ($Container.Config.Labels.'com.docker.compose.project' -ne 'zhkuagent' -or
        $Container.Config.Labels.'com.docker.compose.service' -ne 'postgres' -or
        $Container.Config.Image -ne 'pgvector/pgvector:0.8.6-pg16-bookworm') {
        throw 'Refusing an unrecognized container/project/image; no changes made.'
    }
    $data = @($Container.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
    if ($data.Count -ne 1 -or $data[0].Type -ne 'volume' -or
        $data[0].Name -ne 'zhkuagent_zhkuagent_postgres_data') {
        throw 'Refusing an unexpected database volume; preserve it and resolve identity manually.'
    }
    if (@($Container.Mounts | Where-Object { $_.Destination -ne '/var/lib/postgresql/data' -and
            $_.Destination -ne '/docker-entrypoint-initdb.d/001-enable-pgvector.sql' }).Count -gt 0) {
        throw 'Refusing unexpected additional mounts; no changes made.'
    }
}

function ConvertTo-ZhkuBindPath {
    param([string]$Path)
    $normalized = $Path.Replace('\', '/').TrimEnd('/')
    if ($normalized -match '^/run/desktop/mnt/host/([a-zA-Z])/(.+)$') {
        return ($Matches[1] + ':/' + $Matches[2])
    }
    return $normalized
}

function Test-ZhkuPublishedMapping {
    param($Container, $Configuration)
    $published = @($Container.NetworkSettings.Ports.'5432/tcp')
    return ($published.Count -eq 1 -and $published[0].HostIp -eq '127.0.0.1' -and
        $published[0].HostPort -eq $Configuration.ZHKU_DB_PORT)
}

function Get-ZhkuPostgresDrift {
    param($Container, $Configuration)
    $bindings = @($Container.HostConfig.PortBindings.'5432/tcp')
    if ($bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or
        $bindings[0].HostPort -ne $Configuration.ZHKU_DB_PORT) { return 'port mapping' }
    $init = @($Container.Mounts | Where-Object { $_.Destination -eq '/docker-entrypoint-initdb.d/001-enable-pgvector.sql' })
    $expected = Join-Path $script:ZhkuRepoRoot 'knowledge/infra/init/001-enable-pgvector.sql'
    if (-not (Test-Path -LiteralPath $expected -PathType Leaf)) { throw 'Current SQL init file is missing or is a directory.' }
    if ($init.Count -ne 1 -or $init[0].Type -ne 'bind' -or $init[0].RW -ne $false -or
        (ConvertTo-ZhkuBindPath $init[0].Source) -ine (ConvertTo-ZhkuBindPath $expected)) {
        return 'SQL init bind path'
    }
    $envMap = @{}
    foreach ($entry in $Container.Config.Env) {
        $pair = $entry -split '=', 2
        if ($pair.Count -eq 2) { $envMap[$pair[0]] = $pair[1] }
    }
    if ($envMap.POSTGRES_USER -ne $Configuration.ZHKU_DB_USER -or
        $envMap.POSTGRES_DB -ne $Configuration.ZHKU_DB_NAME) { return 'database environment' }
    if ($Container.HostConfig.RestartPolicy.Name -ne 'unless-stopped') { return 'restart policy' }
    return $null
}

function Test-ZhkuPostgresTcp {
    param([int]$Port, [int]$TimeoutMilliseconds = 500)
    $client = New-Object System.Net.Sockets.TcpClient
    $pending = $null
    try {
        $pending = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne($TimeoutMilliseconds)) { return $false }
        $client.EndConnect($pending)
        return $client.Connected
    } catch { return $false } finally {
        if ($pending) { $pending.AsyncWaitHandle.Close() }
        $client.Close()
    }
}

function Test-ZhkuPostgresAuthentication {
    param($Container, $Configuration, $Clock, [int]$TimeoutSeconds)
    $savedPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    $published = @($Container.NetworkSettings.Ports.'5432/tcp')
    $probePort = 0
    if ($published.Count -ne 1 -or -not [int]::TryParse([string]$published[0].HostPort, [ref]$probePort) -or
        $probePort -lt 1024 -or $probePort -gt 65535) { throw 'Published database port is unavailable for authentication.' }
    $arguments = @('exec', '--env', 'PGPASSWORD', '--env',
        'PGOPTIONS=-c default_transaction_read_only=on', '--env', 'PGCONNECT_TIMEOUT=3',
        $Container.Id, 'psql', '-h', 'host.docker.internal', '-p', [string]$probePort,
        '-U', $Configuration.ZHKU_DB_USER, '-d', $Configuration.ZHKU_DB_NAME,
        '--no-password', '-X', '-A', '-t', '-c', 'SELECT 1')
    try {
        # 走宿主发布端口，避免容器 loopback 的 trust 规则导致错误口令也通过。
        # 反向探针必须明确收到口令认证失败；网络故障不算口令保护验收成功。
        $env:PGPASSWORD = 'negative-probe-' + [guid]::NewGuid().ToString('N')
        $negative = Invoke-ZhkuDocker -Arguments $arguments -TimeoutSeconds ([Math]::Min(15, (Get-ZhkuRemainingSeconds $Clock $TimeoutSeconds)))
        if ($negative.ExitCode -eq 0 -or $negative.Output -notmatch 'password authentication failed') {
            throw 'Published endpoint did not prove password enforcement; authentication check failed closed.'
        }
        # 正向只读认证：口令通过继承环境传给 exec；命令行不携带口令值。
        $env:PGPASSWORD = $Configuration.ZHKU_DB_PASSWORD
        $result = Invoke-ZhkuDbDocker -Arguments $arguments -Clock $Clock -TimeoutSeconds $TimeoutSeconds
        if ($result.Trim() -ne '1') { throw 'Read-only database authentication probe failed.' }
    } finally { [Environment]::SetEnvironmentVariable('PGPASSWORD', $savedPassword, 'Process') }
}

function Ensure-ZhkuPostgres {
    param([int]$Port = 0, [string]$DataRoot, [int]$TimeoutSeconds = 180,
        [int]$PollIntervalMilliseconds = 500, [switch]$DockerOnly,
        [switch]$RepairConfiguration, [switch]$CheckOnly)
    if ($DockerOnly -and $RepairConfiguration) { throw 'DockerOnly cannot repair database configuration.' }
    if ($CheckOnly -and $RepairConfiguration) { throw 'CheckOnly cannot repair database configuration.' }
    $clock = [Diagnostics.Stopwatch]::StartNew()
    if ($DockerOnly) {
        Ensure-ZhkuDocker -TimeoutSeconds $TimeoutSeconds -PollIntervalMilliseconds $PollIntervalMilliseconds -CheckOnly:$CheckOnly
        Write-Output 'Docker Linux engine ready (shared database untouched).'
        return
    }
    $saved = @{}
    foreach ($key in @('ZHKU_DB_PASSWORD', 'ZHKU_DB_USER', 'ZHKU_DB_NAME', 'ZHKU_DB_PORT', 'COMPOSE_DISABLE_ENV_FILE')) {
        $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    }
    $mutex = $null; $locked = $false
    try {
        $config = Get-ZhkuDbConfiguration -DataRoot $DataRoot -Port $Port
        Ensure-ZhkuDocker -TimeoutSeconds (Get-ZhkuRemainingSeconds $clock $TimeoutSeconds) `
            -PollIntervalMilliseconds $PollIntervalMilliseconds -CheckOnly:$CheckOnly
        # 跨 Agent 串行化同一容器的恢复，避免两个 compose 同时重建。
        $mutex = New-Object Threading.Mutex($false, 'Local\ZhkuAgent.Postgres.Readiness')
        try { $locked = $mutex.WaitOne((Get-ZhkuRemainingSeconds $clock $TimeoutSeconds) * 1000) }
        catch [Threading.AbandonedMutexException] { $locked = $true }
        if (-not $locked) { throw 'Another PostgreSQL readiness operation still owns the lock.' }
        $container = Get-ZhkuPostgresContainer $clock $TimeoutSeconds
        Assert-ZhkuPostgresOwnership $container
        $drift = Get-ZhkuPostgresDrift $container $config
        if ($drift) {
            if (-not $RepairConfiguration) {
                throw "PostgreSQL configuration drift ($drift). Run ensure-zhku-postgres.cmd -RepairConfiguration; existing volume is preserved."
            }
            # 已运行的旧容器先验证真实账号；POSTGRES_* 不会重置既有卷中的用户或密码。
            if (-not $container.State.Running) {
                # 身份标签不能证明卷已初始化；未认证时重建可能把空卷当成原库初始化。
                throw 'Refusing to repair a stopped drifted container without prior authentication; preserve the volume and recover the original configuration first.'
            }
            Test-ZhkuPostgresAuthentication $container $config $clock $TimeoutSeconds
            $volume = Invoke-ZhkuDbDocker -Arguments @('volume', 'inspect', 'zhkuagent_zhkuagent_postgres_data') -Clock $clock -TimeoutSeconds $TimeoutSeconds
            $known = @($volume | ConvertFrom-Json)
            if ($known.Count -ne 1 -or $known[0].Name -ne 'zhkuagent_zhkuagent_postgres_data' -or
                $known[0].Driver -ne 'local' -or $known[0].Labels.'com.docker.compose.project' -ne 'zhkuagent' -or
                $known[0].Labels.'com.docker.compose.volume' -ne 'zhkuagent_postgres_data') {
                throw 'Original volume unavailable; refusing to create a replacement database.'
            }
            foreach ($key in $config.Keys) { [Environment]::SetEnvironmentVariable($key, $config[$key], 'Process') }
            $env:COMPOSE_DISABLE_ENV_FILE = 'true'
            $infra = Join-Path $script:ZhkuRepoRoot 'knowledge/infra'
            Write-Host '[readiness] Repairing container configuration; keeping the verified existing data volume.'
            Invoke-ZhkuDbDocker -Arguments @('compose', '--project-name', 'zhkuagent', '--project-directory', $infra,
                '-f', (Join-Path $infra 'docker-compose.yml'), 'up', '-d', '--no-deps', '--pull', 'never',
                '--force-recreate', 'postgres') -Clock $clock -TimeoutSeconds $TimeoutSeconds | Out-Null
            $container = Get-ZhkuPostgresContainer $clock $TimeoutSeconds
            Assert-ZhkuPostgresOwnership $container
            if (Get-ZhkuPostgresDrift $container $config) { throw 'Configuration repair did not converge.' }
        }
        if (-not $container.State.Running) {
            if ($CheckOnly) { throw 'PostgreSQL container is stopped (CheckOnly).' }
            Invoke-ZhkuDbDocker -Arguments @('start', $container.Id) -Clock $clock -TimeoutSeconds $TimeoutSeconds | Out-Null
        }
        do {
            $state = Get-ZhkuPostgresContainer $clock $TimeoutSeconds
            if ($state.Id -ne $container.Id) { throw 'Container identity changed concurrently; refusing to continue.' }
            Assert-ZhkuPostgresOwnership $state
            if (Get-ZhkuPostgresDrift $state $config) { throw 'Container configuration changed concurrently.' }
            if ($state.State.Health.Status -eq 'unhealthy' -or $state.State.Dead -or $state.State.OOMKilled) {
                throw 'PostgreSQL is unhealthy/dead/OOM-killed; no destructive recovery attempted.'
            }
            if (-not $state.State.Health) { throw 'PostgreSQL healthcheck is missing.' }
            $remaining = Get-ZhkuRemainingSeconds $clock $TimeoutSeconds
            if ($state.State.Running -and $state.State.Health.Status -eq 'healthy' -and
                -not (Test-ZhkuPublishedMapping $state $config)) {
                throw 'Actual published port differs from the expected loopback mapping.'
            }
            if ($state.State.Running -and $state.State.Health.Status -eq 'healthy' -and
                (Test-ZhkuPostgresTcp -Port ([int]$config.ZHKU_DB_PORT) -TimeoutMilliseconds ([Math]::Min(500, $remaining * 1000)))) {
                Test-ZhkuPostgresAuthentication $state $config $clock $TimeoutSeconds
                Write-Output "PostgreSQL ready: container=zhkuagent-postgres port=$($config.ZHKU_DB_PORT) volume=zhkuagent_zhkuagent_postgres_data authenticated=read-only"
                return
            }
            if ($CheckOnly) { throw 'PostgreSQL is not ready (CheckOnly).' }
            $null = Get-ZhkuRemainingSeconds $clock $TimeoutSeconds
            $sleepBudget = [Math]::Max(1, [int][Math]::Floor(($TimeoutSeconds - $clock.Elapsed.TotalSeconds) * 1000))
            Start-Sleep -Milliseconds ([Math]::Min($PollIntervalMilliseconds, $sleepBudget))
        } while ($true)
    } finally {
        if ($locked) { $mutex.ReleaseMutex() }
        if ($mutex) { $mutex.Dispose() }
        foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
    }
}
