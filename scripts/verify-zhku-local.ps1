$ErrorActionPreference = 'Stop'

# 只读取被忽略的本机环境文件，验证过程不会打印数据库密码或上传真实资料。
$envFile = 'infra/.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "缺少 $envFile，请先复制 infra/.env.example 并设置本机数据库密码。"
}

$passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }
if ([string]::IsNullOrWhiteSpace($passwordLine)) {
    throw 'infra/.env 中缺少 ZHKU_DB_PASSWORD。'
}
$env:ZHKU_DB_PASSWORD = $passwordLine -replace '^ZHKU_DB_PASSWORD=', ''

$containerStatus = docker inspect --format '{{.State.Health.Status}}' zhkuagent-postgres 2>$null
if ($LASTEXITCODE -ne 0 -or $containerStatus -ne 'healthy') {
    throw '本机 PostgreSQL 尚未就绪。请先启动 Docker Desktop，再执行 docker compose --env-file infra/.env -f infra/docker-compose.yml up -d。'
}

$testClasses = @(
    'KnowledgeSubmissionStatusTest',
    'KnowledgeGovernanceSchemaTest',
    'LocalKnowledgeFileStoreTest',
    'KnowledgeSubmissionServiceTest',
    'CandidateReviewServiceTest',
    'KnowledgeGovernancePublisherTest',
    'ZhkuLocalAdminControllerTest',
    'ZhkuLocalAdminApplicationTest',
    'ZhkuLocalAdminPageControllerTest',
    'ZhkuLocalAdminPageResourceTest',
    'ZhkuLocalPreviewPageControllerTest',
    'ZhkuLocalPreviewPageResourceTest',
    'ZhkuLocalPreviewControllerTest',
    'ZhkuLocalPreviewConfigurationTest',
    'ZhkuLocalPreviewApplicationTest'
) -join ','

& .\mvnw.cmd "-Dtest=$testClasses" test
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host '仲园 Agent 本机资料治理与问答验证已通过。'
