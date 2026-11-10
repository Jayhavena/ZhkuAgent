# 兼容 Windows PowerShell 5.1；引擎检查不读取数据库凭证，避免扩大秘密访问边界。
function Resolve-ZhkuDockerExecutable {
    $command = Get-Command docker.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command -and (Test-Path -LiteralPath $command.Source -PathType Leaf)) { return $command.Source }
    if ($env:ProgramFiles) {
        $candidate = Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin/docker.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    throw 'Docker CLI executable was not found; install Docker Desktop or repair its installation.'
}

function Resolve-ZhkuDockerDesktopExecutable {
    foreach ($base in @($env:ProgramFiles, $env:LOCALAPPDATA)) {
        if ($base) {
            foreach ($relative in @('Docker/Docker/Docker Desktop.exe', 'Docker/Docker Desktop.exe', 'Programs/Docker/Docker/Docker Desktop.exe')) {
                $candidate = Join-Path $base $relative
                if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
            }
        }
    }
    throw 'Docker Desktop executable was not found; install or start Docker Desktop manually.'
}

function ConvertTo-ZhkuWindowsArgument {
    param([AllowEmptyString()][string]$Value)
    # .NET Framework 没有 ArgumentList；按 Windows CRT 规则转义，防止空格、引号和末尾反斜杠改变参数。
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
    $builder = New-Object Text.StringBuilder
    [void]$builder.Append('"')
    $slashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') { $slashes++; continue }
        if ($character -eq '"') {
            [void]$builder.Append(('\' * (2 * $slashes + 1)))
        } elseif ($slashes) {
            [void]$builder.Append(('\' * $slashes))
        }
        [void]$builder.Append($character)
        $slashes = 0
    }
    if ($slashes) { [void]$builder.Append(('\' * (2 * $slashes))) }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Invoke-ZhkuDockerProcess {
    param([string]$Executable, [string[]]$Arguments, [ValidateRange(0.001, 600)][double]$TimeoutSeconds = 10)
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Executable
    $info.Arguments = (($Arguments | ForEach-Object { ConvertTo-ZhkuWindowsArgument $_ }) -join ' ')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
    # 只清理本次子进程的端点覆盖；固定本机 context，既不改用户全局配置，也不误连远程引擎。
    foreach ($name in @('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH')) {
        $info.EnvironmentVariables.Remove($name)
    }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        try { [void]$process.Start() } catch {
            $nativeCode = $_.Exception.InnerException.NativeErrorCode
            throw ('Docker CLI could not be started; native error code: ' + $nativeCode + '.')
        }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            # 只回收本次创建的 CLI；关闭 Docker Desktop 或 daemon 会破坏用户其他任务。
            try { $process.Kill() } catch { }
            [void]$process.WaitForExit(500)
            throw 'Docker CLI operation timed out.'
        }
        if (-not [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($stdout, $stderr), 500)) {
            throw 'Docker CLI output did not close within its time budget.'
        }
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = ($stdout.Result + [Environment]::NewLine + $stderr.Result).Trim() }
    } finally {
        $process.Dispose()
    }
}

function Invoke-ZhkuDocker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments, [ValidateRange(1, 600)][int]$TimeoutSeconds = 10)
    foreach ($argument in $Arguments) {
        if ($argument -match '^(--context|--host|--config)(=|$)' -or $argument -cmatch '^-H') {
            throw 'Docker endpoint overrides are not permitted by the local readiness helper.'
        }
    }
    $executable = Resolve-ZhkuDockerExecutable
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $context = Invoke-ZhkuDockerProcess -Executable $executable -Arguments @('--context', 'desktop-linux', 'context', 'inspect', 'desktop-linux', '--format', '{{json .Endpoints.docker.Host}}') -TimeoutSeconds $TimeoutSeconds
    if ($context.ExitCode -ne 0) {
        if ($context.Output -match '(?i)access is denied|permission denied|access denied') { throw 'Docker engine access denied; check Docker Desktop permissions for this Windows user.' }
        return [pscustomobject]@{ ExitCode = 1; Output = 'Local Docker Desktop Linux context is unavailable.' }
    }
    try { $endpoint = $context.Output | ConvertFrom-Json -ErrorAction Stop } catch { throw 'Docker Desktop context returned invalid endpoint metadata.' }
    if ($endpoint -isnot [string] -or $endpoint -notmatch '^npipe:/{4}\./pipe/dockerDesktopLinuxEngine$') {
        throw 'Docker Desktop Linux context is not the expected local Windows named pipe; remote engines are not permitted.'
    }
    $remaining = $TimeoutSeconds - $clock.Elapsed.TotalSeconds
    if ($remaining -le 0) { throw 'Docker CLI operation timed out.' }
    $result = Invoke-ZhkuDockerProcess -Executable $executable -Arguments (@('--context', 'desktop-linux') + $Arguments) -TimeoutSeconds $remaining
    if ($result.ExitCode -ne 0 -and $result.Output -match '(?i)access is denied|permission denied|access denied') {
        throw 'Docker engine access denied; check Docker Desktop permissions for this Windows user.'
    }
    return $result
}

function Ensure-ZhkuDocker {
    param([ValidateRange(1, 600)][int]$TimeoutSeconds = 120,
        [ValidateRange(1, 5000)][int]$PollIntervalMilliseconds = 500, [switch]$CheckOnly)
    [void](Resolve-ZhkuDockerExecutable)
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $launched = $false
    do {
        $remaining = $TimeoutSeconds - $clock.Elapsed.TotalSeconds
        if ($remaining -le 0) { break }
        $result = Invoke-ZhkuDocker -Arguments @('info', '--format', '{{json .OSType}}') -TimeoutSeconds ([int][Math]::Ceiling([Math]::Min([double]10, [double]$remaining)))
        if ($result.ExitCode -eq 0) {
            try { $osType = $result.Output | ConvertFrom-Json -ErrorAction Stop } catch { throw 'Docker engine returned invalid operating-system metadata.' }
            if ($osType -cne 'linux') { throw 'Docker Desktop must be configured to use Linux containers.' }
            return
        }
        if ($result.Output -match '(?i)access is denied|permission denied|access denied') { throw 'Docker engine access denied; check Docker Desktop permissions for this Windows user.' }
        if ($CheckOnly) { throw 'Local Docker Desktop Linux engine is not ready; CheckOnly did not start it.' }
        if (-not $launched) {
            $desktop = Resolve-ZhkuDockerDesktopExecutable
            # 启动前复核实际文件，避免依赖失效安装路径或不明确的应用别名。
            if (-not (Test-Path -LiteralPath $desktop -PathType Leaf)) { throw 'Docker Desktop executable is no longer available.' }
            try { Start-Process -FilePath $desktop -ErrorAction Stop | Out-Null } catch { throw 'Docker Desktop could not be started for this Windows user.' }
            $launched = $true
        }
        $remainingMilliseconds = [int][Math]::Floor(($TimeoutSeconds - $clock.Elapsed.TotalSeconds) * 1000)
        if ($remainingMilliseconds -gt 0) { Start-Sleep -Milliseconds ([Math]::Min($PollIntervalMilliseconds, $remainingMilliseconds)) }
    } while ($clock.Elapsed.TotalSeconds -lt $TimeoutSeconds)
    throw 'Local Docker Desktop Linux engine did not become ready within its time budget.'
}
