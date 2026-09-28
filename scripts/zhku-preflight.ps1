# zhku-preflight.ps1 - make the local demo environment ready (run this after a reboot).
#
# 为什么需要它：本机 Docker Desktop 不会随开机自启，而 knowledge 模块的测试与本机预览服务
# 都依赖共享演示库（127.0.0.1:54329）。忘记启动 Docker 时会跑出几十个 "Connection refused"
# 错误，看起来像代码回归——本脚本把"环境是否就绪"变成一条命令的事。
#
# Usage（必须用点号调用，环境变量才会落进当前会话）：
#   . .\scripts\zhku-preflight.ps1                 # 只检查；未就绪则报错并给指引
#   . .\scripts\zhku-preflight.ps1 -StartDocker    # 顺便拉起 Docker Desktop 并等待就绪
#
# 检查顺序：① 继承本机会话凭证 → ② docker 守护进程 → ③ 容器 → ④ 端口 54329
param(
    [switch]$StartDocker,
    [int]$TimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'
$container = 'zhkuagent-postgres'
& (Join-Path $PSScriptRoot 'Import-ZhkuCredentials.ps1') -UseDatabase -UseDashScope

function Test-DockerDaemon {
    # docker CLI 对未运行的守护进程会返回非 0，用它的退出码判断最省事
    docker ps *> $null
    return ($LASTEXITCODE -eq 0)
}

function Wait-Until([scriptblock]$Condition, [string]$What) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (& $Condition) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

# --- ① docker 守护进程 ---
if (-not (Test-DockerDaemon)) {
    if (-not $StartDocker) {
        throw "Docker 守护进程未运行。请启动 Docker Desktop，或用：. .\scripts\zhku-preflight.ps1 -StartDocker"
    }
    Write-Host '[preflight] 正在启动 Docker Desktop…'
    Start-Process 'Docker Desktop' | Out-Null
    if (-not (Wait-Until { Test-DockerDaemon } 'docker daemon')) {
        throw "等待 Docker 守护进程超时（${TimeoutSeconds}s）。请手动确认 Docker Desktop 已启动。"
    }
}
Write-Host '[preflight] Docker 守护进程 OK'

# --- ② 容器 ---
$running = (docker inspect -f '{{.State.Running}}' $container 2>$null)
if ($running -ne 'true') {
    Write-Host "[preflight] 启动容器 $container …"
    docker start $container | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "docker start $container 失败，请手动检查容器是否存在。" }
}

# --- ③ 健康 + 端口 ---
$healthy = Wait-Until { (docker inspect -f '{{.State.Health.Status}}' $container 2>$null) -eq 'healthy' } 'healthy'
if (-not $healthy) {
    throw "容器 $container 未在 ${TimeoutSeconds}s 内变为 healthy，请查看：docker logs $container"
}

$tcp = New-Object System.Net.Sockets.TcpClient
try {
    $tcp.Connect('127.0.0.1', 54329)
    Write-Host '[preflight] 54329 端口 OK'
} catch {
    throw '54329 未监听：容器虽 healthy 但端口未就绪，请稍后重试或检查端口映射。'
} finally {
    $tcp.Dispose()
}

# --- ④ 会话凭证状态（脚本开头已按进程、用户、系统顺序读取，不能清空进程值）---
if ([string]::IsNullOrWhiteSpace($env:ZHKU_DB_PASSWORD)) {
    Write-Warning '[preflight] 当前进程尚无 ZHKU_DB_PASSWORD；本机启动脚本可回退读取数据目录的 infra/.env。'
} else {
    Write-Host '[preflight] 当前会话可继承 ZHKU_DB_PASSWORD（值不回显）'
}

Write-Host @'
[preflight] 环境就绪。接下来：
  · 跑全量回归：  .\mvnw.cmd -pl knowledge test
  · 起预览服务：  .\mvnw.cmd -pl knowledge '-Dspring-boot.run.main-class=com.zhku.knowledge.web.ZhkuLocalPreviewApplication' spring-boot:run
                  （必须带 -pl knowledge；随后浏览器打开 http://127.0.0.1:8123/api/zhku/local-preview）
'@
