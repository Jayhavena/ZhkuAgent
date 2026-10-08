param([Parameter(Mandatory)][ValidateSet('before','after')][string]$Stage)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../../../../')).Path
$output = Join-Path $PSScriptRoot ('browser-' + $Stage)
if (Test-Path -LiteralPath $output) { throw '浏览器批次已存在，不覆盖' }
New-Item -ItemType Directory -Path $output | Out-Null
$container = 'zhku-rulecheck-r2-' + $Stage + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$dbUrl = 'jdbc:postgresql://127.0.0.1:55439/zhkuagent_rulecheck_e2e'
$serviceUrl = 'http://127.0.0.1:18124/api/zhku/local-admin'
$importer = Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$server = $null
$ownedContainer = $false
$secret = $null
$failure = $null
function Refresh-DatabaseCredential { & $importer -RequireDatabase }
function Save-Json($value, $path) {
    [IO.File]::WriteAllText($path, ($value | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
}
try {
    foreach ($port in @(55439,18124)) {
        $socket = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
        try { $socket.Start() } finally { $socket.Stop() }
    }
    Refresh-DatabaseCredential
    # 读取已配置凭证只用于确认环境；临时库和服务实际使用随机临时口令，不触达原库。
    $secret = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $env:ZHKU_DB_PASSWORD = $secret
    $env:POSTGRES_PASSWORD = $secret
    $env:DASHSCOPE_API_KEY = $null
    Refresh-DatabaseCredential
    & docker run --rm -d --name $container --pull never --publish '127.0.0.1:55439:5432' --env POSTGRES_PASSWORD --env POSTGRES_USER=zhkuagent --env POSTGRES_DB=zhkuagent_rulecheck_e2e pgvector/pgvector:0.8.6-pg16-bookworm | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '专用临时容器启动失败' }
    $ownedContainer = $true
    for ($i=0; $i -lt 30; $i++) {
        Refresh-DatabaseCredential
        & docker exec $container pg_isready -U zhkuagent -d zhkuagent_rulecheck_e2e 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        if ($i -eq 29) { throw '临时数据库启动超时' }
        Start-Sleep -Seconds 1
    }
    $runtimeCpFile = Join-Path $root 'knowledge/target/manual-demo/classpath.txt'
    $runtimeCp = ((Get-Content -LiteralPath $runtimeCpFile -Raw).Trim() -split ';' | Where-Object { Test-Path -LiteralPath $_ }) -join ';'
    Refresh-DatabaseCredential
    $start = [Diagnostics.ProcessStartInfo]::new((Get-Command java.exe).Source)
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WorkingDirectory = $root
    foreach ($argument in @('-Dfile.encoding=UTF-8', "-Dzhku.db.url=$dbUrl", '-Dzhku.db.user=zhkuagent', '-cp', $runtimeCp, 'com.zhku.knowledge.governance.web.ZhkuLocalAdminApplication', '--server.port=18124', '--server.address=127.0.0.1', "--zhku.local-admin.storage-directory=$output/storage")) {
        $start.ArgumentList.Add($argument)
    }
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $server = [Diagnostics.Process]::Start($start)
    $stdout = $server.StandardOutput.ReadToEndAsync()
    $stderr = $server.StandardError.ReadToEndAsync()
    for ($i=0; $i -lt 45; $i++) {
        if ($server.HasExited) { throw '管理服务启动退出' }
        try {
            $response = Invoke-WebRequest "$serviceUrl/submissions" -TimeoutSec 2
            if ($response.StatusCode -eq 200) { break }
        } catch { if ($i -eq 44) { throw '管理服务未就绪' } }
        Start-Sleep -Seconds 1
    }
    Refresh-DatabaseCredential
    Get-Content (Join-Path $PSScriptRoot 'fixtures.sql') -Raw | & docker exec -i $container psql -v ON_ERROR_STOP=1 -U zhkuagent -d zhkuagent_rulecheck_e2e | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '合成夹具写入失败' }
    $detail = Invoke-WebRequest "$serviceUrl/submissions/10000000-0000-0000-0000-000000000001"
    [IO.File]::WriteAllText((Join-Path $output 'detail-initial.json'), $detail.Content, [Text.UTF8Encoding]::new($false))
    $html = Invoke-WebRequest $serviceUrl
    [IO.File]::WriteAllText((Join-Path $output 'served-page.html'), $html.Content, [Text.UTF8Encoding]::new($false))
    Refresh-DatabaseCredential
    $migration = & docker exec $container psql -U zhkuagent -d zhkuagent_rulecheck_e2e -Atc 'SELECT max(version::integer) FROM flyway_schema_history WHERE success'
    if ($LASTEXITCODE -ne 0) { throw '迁移核验失败' }
    Save-Json ([ordered]@{ready=$true;stage=$Stage;serviceUrl=$serviceUrl;databaseUrl=$dbUrl;container=$container;javaPid=$server.Id;synthetic=$true;migration=$migration;providerAllowed=$false;fixtureSource='SQL合成审核候选；不是本轮PDF导入';classpathSource=$runtimeCpFile;classpathSha256=(Get-FileHash $runtimeCpFile).Hash.ToLowerInvariant()}) (Join-Path $output 'ready.json')
    Write-Output "R2_BROWSER_READY stage=$Stage url=$serviceUrl"
    # 通过本批停止标记保留服务，便于工具间真实浏览器操作；退出时仅清理自己创建的资源。
    while (!(Test-Path -LiteralPath (Join-Path $output 'stop.request'))) {
        if ($server.HasExited) { throw '管理服务意外退出' }
        Start-Sleep -Seconds 1
    }
    $finalDetail = Invoke-WebRequest "$serviceUrl/submissions/10000000-0000-0000-0000-000000000001"
    [IO.File]::WriteAllText((Join-Path $output 'detail-final.json'), $finalDetail.Content, [Text.UTF8Encoding]::new($false))
    Refresh-DatabaseCredential
    $auditSql = "SELECT json_build_object('events',(SELECT json_agg(json_build_object('action',action_type,'candidateId',candidate_id)) FROM knowledge_review_event),'publishedSources',(SELECT json_agg(json_build_object('id',id,'status',publication_status,'visibility',visibility_scope,'shareConfirmed',sharing_rights_confirmed)) FROM knowledge_source),'chunkCount',(SELECT count(*) FROM knowledge_chunk));"
    $audit = & docker exec $container psql -U zhkuagent -d zhkuagent_rulecheck_e2e -Atc $auditSql
    if ($LASTEXITCODE -ne 0) { throw '审查与发布终态读取失败' }
    [IO.File]::WriteAllText((Join-Path $output 'database-final.json'), ($audit -join ''), [Text.UTF8Encoding]::new($false))
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($server -and !$server.HasExited) { $server.Kill($true); $server.WaitForExit(10000) | Out-Null }
    if ($server) {
        $combined = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($secret) { $combined = $combined.Replace($secret, '[REDACTED]') }
        [IO.File]::WriteAllText((Join-Path $output 'service.log'), $combined, [Text.UTF8Encoding]::new($false))
    }
    $connections = $null
    $containerExited = !$ownedContainer
    if ($ownedContainer) {
        Refresh-DatabaseCredential
        $connections = & docker exec $container psql -U zhkuagent -d zhkuagent_rulecheck_e2e -Atc "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend' AND pid<>pg_backend_pid()"
        if ($LASTEXITCODE -ne 0) { $failure = '临时数据库连接退出检查失败' }
        Refresh-DatabaseCredential
        & docker stop --time 10 $container 2>$null | Out-Null
        Refresh-DatabaseCredential
        $remaining = @(& docker ps -a --filter "name=^/$container$" --format '{{.Names}}')
        $containerExited = $LASTEXITCODE -eq 0 -and $remaining.Count -eq 0
    }
    Save-Json ([ordered]@{stage=$Stage;passed=(!$failure -and $containerExited -and $connections -eq '0');failure=$failure;javaExited=(!$server -or $server.HasExited);containerExited=$containerExited;connectionsAfterService=$connections;databaseUrl=$dbUrl;providerAllowed=$false}) (Join-Path $output 'cleanup.json')
}
if ($failure) { throw $failure }
