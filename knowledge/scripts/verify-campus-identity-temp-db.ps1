param(
    [ValidateRange(1024,65535)][int]$Port=55435,
    [string]$Tests='ControlledCampusHttpAcceptanceTest,ControlledCampusIdentityProcessTest,LegacyPreviewSecurityCompatibilityTest,ControlledPreviewConfigurationTest',
    [string]$MavenRepo='C:/Users/nangua/.m2/repository',
    [ValidateSet(19,20)][int]$ExpectedMigration=19
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if($Port -eq 54329){throw '拒绝演示库端口'}
$allowed=@('TrustedActorTest','PreviewSecurityConfigurationTest','PreviewIdentityBudgetTest',
    'PreviewIdentitySchemaIntegrationTest','PreviewIdentityRepositoryIntegrationTest',
    'CampusCreditAgentOwnershipTest','CampusRunOwnershipIntegrationTest','CampusRunAccessServiceTest',
    'ControlledPreviewConfigurationTest','LegacyPreviewSecurityCompatibilityTest',
    'ControlledCampusHttpAcceptanceTest','ControlledCampusIdentityProcessTest')
$selected=@($Tests.Split(',')|ForEach-Object{$_.Trim()})
if(-not $selected.Count -or @($selected|Where-Object{$_ -notin $allowed}).Count -or
    @($selected|Select-Object -Unique).Count -ne $selected.Count){throw '只接受明确的4A非供应商测试类，不能重复或加入任意参数'}
foreach($class in $selected){
    $files=@(rg --files (Join-Path $root 'knowledge/src/test/java')|Where-Object{[IO.Path]::GetFileName($_) -eq "$class.java"})
    if($files.Count -ne 1){throw '测试源码必须唯一存在；不能依赖没有测试的成功退出'}
}
$maven=(Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if(-not $maven -and (Test-Path 'D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd')){
    $maven='D:/Software/Maven/apache-maven-3.9.15/bin/mvn.cmd'
}
if(-not $maven){throw '未找到Maven；请配置PATH'}
$container='zhku-4a-identity-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$database='zhkuagent_run_archive_e2e'
$environmentNames=@('POSTGRES_PASSWORD','ZHKU_DB_PASSWORD','ZHKU_DB_URL','ZHKU_DB_USER')
$saved=@{}
foreach($name in $environmentNames){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$started=$false
$secret=$null
$exitCode=0
try{
    # 逐次重查凭据来源，但专用验收仍生成随机口令；不复用原演示库或volume。
    & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
    $secret=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18))
    $env:POSTGRES_PASSWORD=$secret
    docker run --rm -d --name $container --publish "127.0.0.1:${Port}:5432" --env POSTGRES_PASSWORD `
        --env POSTGRES_USER=zhkuagent --env "POSTGRES_DB=$database" pgvector/pgvector:0.8.6-pg16-bookworm | Out-Null
    if($LASTEXITCODE -ne 0){throw '专用临时容器未启动'}
    $started=$true
    [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD',$saved['POSTGRES_PASSWORD'],'Process')
    $ready=$false
    for($attempt=0;$attempt -lt 30;$attempt++){
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
        docker exec $container pg_isready -U zhkuagent -d $database 2>$null | Out-Null
        if($LASTEXITCODE -eq 0){$ready=$true;break}
        Start-Sleep -Seconds 1
    }
    if(-not $ready){throw '临时库30秒内未就绪'}
    $env:ZHKU_DB_PASSWORD=$secret
    $env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$Port/$database"
    $env:ZHKU_DB_USER='zhkuagent'
    & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
    Push-Location $root
    try{
        $arguments=@('-e',"-Dmaven.repo.local=$MavenRepo",'-Dfile.encoding=UTF-8','-pl','knowledge','-am',
            "-Dtest=$($selected -join ',')",'-Dsurefire.failIfNoSpecifiedTests=false',
            "-Dzhku.db.url=$env:ZHKU_DB_URL",'-Dzhku.db.user=zhkuagent','test')
        # 密码不进入参数/仓库；失败日志也先清除随机密码与BCrypt载荷再交给证据采集器。
        & $maven @arguments 2>&1 | ForEach-Object {
            $_.ToString().Replace($secret,'[REDACTED]') -replace '\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}','[REDACTED_HASH]'
        }
        if($LASTEXITCODE -ne 0){throw '4A测试失败；保留实际XML，不跳过失败'}
    }finally{Pop-Location}
    & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -RequireDatabase
    $version=@(docker exec $container psql -U zhkuagent -d $database -Atc 'SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL')
    if($LASTEXITCODE -ne 0 -or $version[-1] -ne [string]$ExpectedMigration){throw '临时库最高迁移与显式阶段不符'}
    Write-Output "4A_TEMP_DB_VERIFIED migration=$ExpectedMigration selected=$($selected.Count) provider=false"
}catch{
    $exitCode=1
    # 未知工具错误可能携参数；只给有限异常类型，具体测试事实由XML保存。
    Write-Output ('4A_TEMP_DB_FAILED type='+$_.Exception.GetType().Name)
}finally{
    if($started){
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
        docker stop --time 10 $container | Out-Null
        if($LASTEXITCODE -ne 0){$exitCode=1;Write-Output '4A_TEMP_DB_CLOSE_UNCONFIRMED'}
        & (Join-Path $root 'scripts/Import-ZhkuCredentials.ps1') -UseDatabase
        $remaining=@(docker ps -a --filter "name=^/$container$" --format '{{.Names}}')
        if($LASTEXITCODE -ne 0 -or $remaining.Count){$exitCode=1;Write-Output '4A_TEMP_DB_REMAINING_UNCONFIRMED'}
    }
    foreach($name in $environmentNames){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
    $secret=$null
}
exit $exitCode
