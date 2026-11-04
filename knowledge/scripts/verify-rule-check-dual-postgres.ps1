#requires -Version 7.0
# 仅用于 RuleCheckDualPostgresRememberMeRestartTest；不得传入已有数据库或容器。
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $root 'scripts/ZhkuDocker.ps1')
$importer = Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$wrapper = Join-Path $root 'mvnw.cmd'
$testClass = 'RuleCheckDualPostgresRememberMeRestartTest'
$testPath = Join-Path $root "knowledge/src/test/java/com/zhku/knowledge/retrieval/$testClass.java"
$runId = [guid]::NewGuid().ToString('N')
$names = @("zhku-dual-rule-$runId", "zhku-dual-identity-$runId")
$ids = [Collections.Generic.List[string]]::new()
$namesAttempted = [Collections.Generic.List[string]]::new()
$variables = @('ZHKU_DB_PASSWORD','POSTGRES_PASSWORD','ZHKU_DB_URL','ZHKU_DB_USER',
    'ZHKU_RULECHECK_IDENTITY_DB_USER','ZHKU_RULECHECK_IDENTITY_DB_PASSWORD',
    'ZHKU_TEST_IDENTITY_DB_URL','ZHKU_TEST_DUAL_POSTGRES_DISPOSABLE','DASHSCOPE_API_KEY',
    'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS',
    'SPRING_APPLICATION_JSON')
$saved = @{}
foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$success = $false
$cleanup = $true
$stage = 'preflight'
try {
    if (!(Test-Path -LiteralPath $testPath -PathType Leaf) -or !(Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'Missing test or Maven wrapper.' }
    # 按项目约定逐次检查现有凭据；验收实例改用独立随机口令，避免传播演示库密钥。
    & $importer -UseDatabase
    $env:ZHKU_DB_PASSWORD = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $identityPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    foreach ($name in @('DASHSCOPE_API_KEY','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS','SPRING_APPLICATION_JSON')) {
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
    }
    # 只唤醒本机 Linux 引擎；不得启动或修复共享演示容器。
    & (Join-Path $root 'knowledge/scripts/ensure-zhku-postgres.cmd') -DockerOnly | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker readiness failed.' }
    $databases = @('zhkuagent_rulecheck_e2e','zhkuagent_rulecheck_identity')
    $passwords = @($env:ZHKU_DB_PASSWORD, $identityPassword)
    $ports = @()
    for ($i = 0; $i -lt 2; $i++) {
        $stage = "container-$i"
        $env:POSTGRES_PASSWORD = $passwords[$i]
        $namesAttempted.Add($names[$i])
        # tmpfs 不使用任何旧卷；唯一名称与返回 ID 限定清理对象。随机端口由 Docker 原子分配。
        $created = Invoke-ZhkuDocker -Arguments @('create','--rm','--name',$names[$i],
            '--label',"zhku.dual.run=$runId",'--pull','never','--publish','127.0.0.1::5432',
            '--tmpfs','/var/lib/postgresql/data','--env','POSTGRES_PASSWORD',
            '--env','POSTGRES_USER=zhkuagent','--env',"POSTGRES_DB=$($databases[$i])",
            'pgvector/pgvector:0.8.6-pg16-bookworm') -TimeoutSeconds 30
        if ($created.ExitCode -ne 0 -or $created.Output -notmatch '^[a-f0-9]{64}$') { throw 'Container creation not confirmed.' }
        $id = $created.Output
        $ids.Add($id)
        $started = Invoke-ZhkuDocker -Arguments @('start',$id) -TimeoutSeconds 30
        if ($started.ExitCode -ne 0) { throw 'Container start failed.' }
        $mapping = Invoke-ZhkuDocker -Arguments @('port',$id,'5432/tcp') -TimeoutSeconds 15
        if ($mapping.ExitCode -ne 0 -or $mapping.Output -notmatch '^127\.0\.0\.1:([0-9]{4,5})$') { throw 'Unexpected published port.' }
        $port = [int]$Matches[1]
        if ($port -lt 1024 -or $port -gt 65535 -or $port -eq 54329 -or $port -in $ports) { throw 'Unsafe or duplicate published port.' }
        $ports += $port
        $ready = $false
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            $probe = Invoke-ZhkuDocker -Arguments @('exec',$id,'pg_isready','-U','zhkuagent','-d',$databases[$i]) -TimeoutSeconds 10
            if ($probe.ExitCode -eq 0) { $ready = $true; break }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw 'Temporary database readiness timeout.' }
    }
    $stage = 'test'
    $env:ZHKU_DB_URL = "jdbc:postgresql://127.0.0.1:$($ports[0])/$($databases[0])"
    $env:ZHKU_DB_USER = 'zhkuagent'
    $env:ZHKU_TEST_IDENTITY_DB_URL = "jdbc:postgresql://127.0.0.1:$($ports[1])/$($databases[1])"
    $env:ZHKU_RULECHECK_IDENTITY_DB_USER = 'zhkuagent'
    $env:ZHKU_RULECHECK_IDENTITY_DB_PASSWORD = $identityPassword
    $env:ZHKU_TEST_DUAL_POSTGRES_DISPOSABLE = 'yes'
    & $importer -RequireDatabase
    # Surefire 的 reportsDirectory 系统属性并不生效；校验默认报告的时间戳，避免旧报告冒充本次结果。
    $report = Join-Path $root "knowledge/target/surefire-reports/TEST-com.zhku.knowledge.retrieval.$testClass.xml"
    $previousWrite = if (Test-Path -LiteralPath $report) { (Get-Item -LiteralPath $report).LastWriteTimeUtc } else { [DateTime]::MinValue }
    $stage = 'maven'
    Push-Location $root
    try {
        & $wrapper '-o' '-Dfile.encoding=UTF-8' '-pl' 'knowledge' '-am' "-Dtest=$testClass" `
            '-Dsurefire.failIfNoSpecifiedTests=false' `
            "-Dzhku.db.url=$env:ZHKU_DB_URL" 'test' *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Maven test failed.' }
    } finally { Pop-Location }
    $stage = 'report'
    if (!(Test-Path -LiteralPath $report -PathType Leaf) -or (Get-Item -LiteralPath $report).LastWriteTimeUtc -le $previousWrite) { throw 'Fresh test report missing.' }
    [xml]$xml = Get-Content -LiteralPath $report -Raw
    if ($xml.testsuite.name -cne "com.zhku.knowledge.retrieval.$testClass" -or
        [int]$xml.testsuite.tests -ne 1 -or [int]$xml.testsuite.failures -ne 0 -or
        [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) { throw 'Test report did not prove one passing test.' }
    $success = $true
} catch {
    # 不输出原始异常、Docker 输出或 Maven 输出，避免意外泄露密钥。
    Write-Output ('DUAL_POSTGRES_FAILED stage=' + $stage + ' type=' + $_.Exception.GetType().Name)
} finally {
    foreach ($id in $ids) {
        try {
            $removed = Invoke-ZhkuDocker -Arguments @('rm','-f',$id) -TimeoutSeconds 30
            if ($removed.ExitCode -ne 0) { $cleanup = $false }
        } catch { $cleanup = $false }
    }
    # create 超时可能已在 daemon 生效但未返回 ID；绝不按猜测名称删除。
    if ($namesAttempted.Count -ne $ids.Count) { $cleanup = $false }
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    if (-not $cleanup) { Write-Output ('DUAL_POSTGRES_CLEANUP_UNCONFIRMED run=' + $runId) }
}
if ($success -and $cleanup) { Write-Output 'DUAL_POSTGRES_VERIFIED tests=1'; exit 0 }
exit 1
