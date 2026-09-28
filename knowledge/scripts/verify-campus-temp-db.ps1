param(
    [int]$Port = 55432,
    [string]$MavenRepo = (Join-Path $env:USERPROFILE '.m2/repository')
)

$ErrorActionPreference = 'Stop'
if ($Port -lt 1024 -or $Port -gt 65535 -or $Port -eq 54329) {
    throw '请使用不与演示库冲突的本机临时端口。'
}

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$databaseUrl = "jdbc:postgresql://127.0.0.1:$Port/zhkuagent_e2e"
$containerName = 'zhku-campus-e2e-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$databaseSecret = [Convert]::ToHexString(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
$containerStarted = $false
$originalDbPassword = [Environment]::GetEnvironmentVariable('ZHKU_DB_PASSWORD', 'Process')
$originalDbUrl = [Environment]::GetEnvironmentVariable('ZHKU_DB_URL', 'Process')
$originalDbUser = [Environment]::GetEnvironmentVariable('ZHKU_DB_USER', 'Process')

try {
    # 每次创建无卷、自动删除的独立库；不能沿用演示库或把随机密码写入仓库。
    docker run --rm -d --name $containerName --publish "127.0.0.1:${Port}:5432" `
        --env "POSTGRES_PASSWORD=$databaseSecret" --env 'POSTGRES_USER=zhkuagent' `
        --env 'POSTGRES_DB=zhkuagent_e2e' 'pgvector/pgvector:0.8.6-pg16-bookworm' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '无法启动专用临时 PostgreSQL 容器。' }
    $containerStarted = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        docker exec $containerName pg_isready -U zhkuagent -d zhkuagent_e2e 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw '临时数据库未能在 30 秒内就绪。' }

    $env:ZHKU_DB_PASSWORD = $databaseSecret
    $env:ZHKU_DB_URL = $databaseUrl
    $env:ZHKU_DB_USER = 'zhkuagent'
    $maven = Join-Path $repoRoot 'mvnw.cmd'
    $common = @(
        "-Dmaven.repo.local=$MavenRepo", '-pl', 'knowledge', '-am',
        '-Dsurefire.failIfNoSpecifiedTests=false', "-Dzhku.db.url=$databaseUrl",
        '-Dzhku.db.user=zhkuagent'
    )
    & $maven @common '-Dtest=KnowledgeSchemaTest' test
    if ($LASTEXITCODE -ne 0) { throw '临时库结构迁移测试失败。' }

    $tests = 'JdbcRetrievalIndexingTest,JdbcCandidateRetrieverTest,' +
        'JdbcCreditLimitEvidenceFinderIntegrationTest,RetrievalAcceptanceEvalTest,' +
        'CampusCreditAgentJdbcEndToEndTest'
    & $maven @common "-Dtest=$tests" test
    if ($LASTEXITCODE -ne 0) { throw '校园 Agent 临时库端到端测试失败。' }
    Write-Output '临时库端到端测试完成；模型选择使用可控替身，未调用真实供应商。'
} finally {
    if ($containerStarted) { docker stop $containerName | Out-Null }
    [Environment]::SetEnvironmentVariable('ZHKU_DB_PASSWORD', $originalDbPassword, 'Process')
    [Environment]::SetEnvironmentVariable('ZHKU_DB_URL', $originalDbUrl, 'Process')
    [Environment]::SetEnvironmentVariable('ZHKU_DB_USER', $originalDbUser, 'Process')
}
