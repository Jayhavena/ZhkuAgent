param(
    [int]$Port = 55433,
    [string]$Tests = 'KnowledgeSchemaTest',
    [string]$MavenRepo = (Join-Path $env:USERPROFILE '.m2/repository')
)
$ErrorActionPreference = 'Stop'
if ($Port -lt 1024 -or $Port -gt 65535 -or $Port -eq 54329) {
    throw '拒绝演示库或无效临时端口'
}
$allowed = @(
    'KnowledgeSchemaTest', 'CampusRunArchiveSchemaIntegrationTest',
    'CampusRunArchiveOwnerIntegrationTest', 'CampusRunHistoryVisibilityIntegrationTest',
    'CampusRunArchiveStoreIntegrationTest', 'CampusRunArchiveBudgetTest',
    'CampusRunArchiveConfigurationIntegrationTest', 'CampusRunArchiveProcessRestartTest'
)
if ($Tests -notmatch '^[A-Za-z][A-Za-z0-9]*(,[A-Za-z][A-Za-z0-9]*)*$') {
    throw '只允许明确测试类名'
}
$selected = @($Tests.Split(','))
if (@($selected | Where-Object { $_ -notin $allowed }).Count -gt 0 -or
    @($selected | Select-Object -Unique).Count -ne $selected.Count) {
    throw '测试选择不在非真实模型白名单内'
}
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$databaseUrl = "jdbc:postgresql://127.0.0.1:$Port/zhkuagent_run_archive_e2e"
$containerName = 'zhku-run-archive-e2e-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$names = @('POSTGRES_PASSWORD', 'ZHKU_DB_PASSWORD', 'ZHKU_DB_URL', 'ZHKU_DB_USER')
$saved = @{}
foreach ($name in $names) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$containerStarted = $false
try {
    # 每次检查调用方环境；本次验收仍只使用新建临时库的随机口令，不复用原演示库。
    & (Join-Path $repoRoot 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
    $secret = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
    $env:POSTGRES_PASSWORD = $secret
    docker run --rm -d --name $containerName --publish "127.0.0.1:${Port}:5432" `
        --env 'POSTGRES_PASSWORD' --env 'POSTGRES_USER=zhkuagent' `
        --env 'POSTGRES_DB=zhkuagent_run_archive_e2e' 'pgvector/pgvector:0.8.6-pg16-bookworm' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw '无法启动专用临时容器'
    }
    $containerStarted = $true
    if ($null -eq $saved['POSTGRES_PASSWORD']) {
        Remove-Item Env:POSTGRES_PASSWORD -ErrorAction SilentlyContinue
    } else {
        [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', $saved['POSTGRES_PASSWORD'], 'Process')
    }
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $containerName pg_isready -U zhkuagent -d zhkuagent_run_archive_e2e 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true;
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        throw '临时数据库未就绪'
    }
    $env:ZHKU_DB_PASSWORD = $secret;
    $env:ZHKU_DB_URL = $databaseUrl;
    $env:ZHKU_DB_USER = 'zhkuagent'
    # 秘密只按环境名继承，不拼入 Maven 或 Docker 参数，也不输出异常中的连接细节。
    & (Join-Path $repoRoot 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
    $started = [DateTime]::UtcNow
    $arguments = @(
        "-Dmaven.repo.local=$MavenRepo", '-pl', 'knowledge', '-am', "-Dtest=$Tests",
        '-Dsurefire.failIfNoSpecifiedTests=false', "-Dzhku.db.url=$databaseUrl", '-Dzhku.db.user=zhkuagent', 'test'
    )
    & (Join-Path $repoRoot 'mvnw.cmd') @arguments
    if ($LASTEXITCODE -ne 0) {
        throw '归档临时库测试失败'
    }
    foreach ($class in $selected) {
        $reports = @(Get-ChildItem (Join-Path $repoRoot 'knowledge/target/surefire-reports') -Filter "TEST-*.$class.xml")
        if ($reports.Count -ne 1 -or $reports[0].LastWriteTimeUtc -lt $started) {
            throw "缺少本轮报告：$class"
        }
        [xml]$report = Get-Content -LiteralPath $reports[0].FullName
        $suite = $report.testsuite
        if ([int]$suite.tests -lt 1 -or [int]$suite.failures -ne 0 -or
            [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) {
            throw "测试为空、失败或跳过：$class"
        }
        Write-Output "本轮 $class : tests=$($suite.tests), failures=0, errors=0, skipped=0"
    }
    & (Join-Path $repoRoot 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
    docker exec $containerName psql -U zhkuagent -d zhkuagent_run_archive_e2e -Atc 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL'
    if ($LASTEXITCODE -ne 0) {
        throw '无法读取迁移版本'
    }
    Write-Output '专用临时库验收完成；无真实模型调用。'
} finally {
    # 验收或报告检查失败也要关闭临时容器，并恢复调用方原来的环境值。
    if ($containerStarted) {
        docker stop $containerName | Out-Null
    }
    foreach ($name in $names) {
        if ($null -eq $saved[$name]) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
        }
    }
}
