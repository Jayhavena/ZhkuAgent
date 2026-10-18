# 行为合同：只替换外部边界，不连接真实 Docker、数据库或供应商。
param([string]$AcceptanceScript = (Join-Path (Split-Path $PSScriptRoot -Parent) 't06-controlled-acceptance.ps1'))
$ErrorActionPreference = 'Stop'
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($AcceptanceScript, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw '验收脚本存在语法错误' }
$lastFunction = -1
for ($index = 0; $index -lt $ast.EndBlock.Statements.Count; $index++) {
    $statement = $ast.EndBlock.Statements[$index]
    if ($statement -is [Management.Automation.Language.FunctionDefinitionAst]) {
        . ([scriptblock]::Create($statement.Extent.Text))
        $lastFunction = $index
    }
}
# 保留真实场景编排；仅去掉最终 exit，由测试读取同一个 overall，避免终止合同测试宿主。
$tail = @()
for ($index = $lastFunction + 1; $index -lt $ast.EndBlock.Statements.Count; $index++) {
    if ($ast.EndBlock.Statements[$index] -isnot [Management.Automation.Language.ExitStatementAst]) { $tail += $ast.EndBlock.Statements[$index].Extent.Text }
}
$orchestration = [scriptblock]::Create($tail -join [Environment]::NewLine)
$productionMaven = ${function:Invoke-MavenAcceptance}
$productionStartProxy = ${function:Start-ProxyScaffold}
$productionStopProxy = ${function:Stop-ProxyScaffold}
$fixture = Join-Path $env:TEMP ('zhku-t06-contract-' + [guid]::NewGuid().ToString('N'))
$trackedNames = @('ZHKU_DB_PASSWORD', 'POSTGRES_PASSWORD', 'SERVER__PORT', 'T06_CONTRACT_CREDENTIAL', 'T06_CONTRACT_MAVEN_EXIT', 'T06_CONTRACT_REQUIRE_DB')
$saved = @{}
foreach ($name in $trackedNames) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$script:Failures = New-Object System.Collections.ArrayList
$script:Passed = 0
function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { $script:FailureReason = $Message; throw $Message }
}
function Invoke-Case([string]$Name, [scriptblock]$Body) {
    $script:FailureReason = $null
    try { & $Body 6>$null; $script:Passed++; Write-Host "[PASS] $Name" }
    catch {
        # 不展开参数、调用栈或环境，失败路径也不能输出原有凭证。
        [void]$script:Failures.Add($Name)
        if ($script:FailureReason) { Write-Host "[FAIL] ${Name}: $script:FailureReason" }
        else { Write-Host "[FAIL] ${Name}: unexpected $($_.Exception.GetType().Name)" }
    }
}
function Reset-Boundaries {
    $script:CredentialImports = 0
    $script:DockerCalls = New-Object System.Collections.ArrayList
    $script:DockerRunArgs = @()
    $script:DockerRunHadPassword = $false
    $script:DockerRunExit = 0
    $script:NginxRunExit = 0
    $script:LastDatabaseImportCount = 0
    $script:EveryDatabaseCommandReimported = $true
    $script:CredentialFailureAt = 0
    $script:PsqlTableCount = '0'
    $script:DockerRemoveExit = 0
    $script:DockerRemoveFailurePrefix = ''
    $script:MavenBoundaryFailure = $null
    $script:MavenBoundaryCalls = 0
    $script:ContainerSeq = 0
    $script:Containers = New-Object System.Collections.ArrayList
    $script:TempPassword = $null
    $script:RunStamp = 'contract'
    $script:RelayProcess = $null
    $script:KeepContainer = $false
    $script:TimeoutSeconds = 1
    $script:AssetsRoot = $fixture
    $script:EvidenceRoot = Join-Path $fixture ([guid]::NewGuid().ToString('N'))
    $script:repoRoot = $fixture
    $script:BackendPort = 18081
    $script:PublicPort = 18082
    $script:RelayPort = 18083
    $env:ZHKU_DB_PASSWORD = 'contract-process-only'
    $env:T06_CONTRACT_CREDENTIAL = 'contract-process-only'
    $env:T06_CONTRACT_MAVEN_EXIT = '0'
    $env:T06_CONTRACT_REQUIRE_DB = '0'
    $env:POSTGRES_PASSWORD = 'contract-previous-postgres'
}
function docker {
    $arguments = @($args)
    [void]$script:DockerCalls.Add($arguments)
    $global:LASTEXITCODE = 0
    if ($arguments[0] -eq 'run' -or $arguments[0] -eq 'exec') {
        if ($script:CredentialImports -le $script:LastDatabaseImportCount) { $script:EveryDatabaseCommandReimported = $false }
        $script:LastDatabaseImportCount = $script:CredentialImports
    }
    if ($arguments[0] -eq 'run') {
        $script:DockerRunArgs = $arguments
        $script:DockerRunHadPassword = ($env:POSTGRES_PASSWORD -eq $env:T06_CONTRACT_CREDENTIAL)
        $global:LASTEXITCODE = $script:DockerRunExit
        if ($arguments -contains 'nginx:1.27-alpine') { $global:LASTEXITCODE = $script:NginxRunExit }
    }
    if ($arguments[0] -eq 'rm' -and ($script:DockerRemoveFailurePrefix -eq '' -or $arguments[2] -like ($script:DockerRemoveFailurePrefix + '*'))) { $global:LASTEXITCODE = $script:DockerRemoveExit }
    if ($arguments[0] -eq 'exec' -and $arguments[2] -eq 'psql') { return $script:PsqlTableCount }
}
function Get-FreeLoopbackPort { return 56123 }
function Get-StartedContractContainer([string]$Prefix) {
    foreach ($call in $script:DockerCalls) {
        if ($call[0] -eq 'run') {
            $nameIndex = [array]::IndexOf($call, '--name')
            if ($nameIndex -ge 0 -and $call[$nameIndex + 1] -like ($Prefix + '*')) { return $call[$nameIndex + 1] }
        }
    }
    return $null
}
function Test-RemovedAfterCreation([string]$Container) {
    $created = $false
    foreach ($call in $script:DockerCalls) {
        if ($call[0] -eq 'run' -and $call -contains $Container) { $created = $true }
        if ($created -and $call[0] -eq 'rm' -and $call[2] -eq $Container) { return $true }
    }
    return $false
}
try {
    New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts') -Force *> $null
    # 凭证存储是外部边界：替身禁止读取本机密钥，计数检查每次数据库操作是否重新导入。
    $credentialStub = @'
param([switch]$RequireDatabase)
$script:CredentialImports++
if ($script:CredentialFailureAt -eq $script:CredentialImports) { throw 'contract credential import failed' }
if ([string]::IsNullOrWhiteSpace($env:T06_CONTRACT_CREDENTIAL)) { throw 'contract credential unavailable' }
$env:ZHKU_DB_PASSWORD = $env:T06_CONTRACT_CREDENTIAL
'@
    [IO.File]::WriteAllText((Join-Path $fixture 'scripts/Import-ZhkuCredentials.ps1'), $credentialStub, (New-Object Text.UTF8Encoding($true)))
    $mavenStub = @'
@echo off
if "%T06_CONTRACT_REQUIRE_DB%"=="1" if not defined ZHKU_DB_PASSWORD exit /b 98
exit /b %T06_CONTRACT_MAVEN_EXIT%
'@
    [IO.File]::WriteAllText((Join-Path $fixture 'mvnw.cmd'), ($mavenStub -replace '\r?\n', [Environment]::NewLine), (New-Object Text.ASCIIEncoding))
    Invoke-Case '缺凭证时先失败，不能自造口令或创建数据库' {
        Reset-Boundaries
        [Environment]::SetEnvironmentVariable('ZHKU_DB_PASSWORD', $null, 'Process')
        [Environment]::SetEnvironmentVariable('T06_CONTRACT_CREDENTIAL', $null, 'Process')
        $blocked = $false
        try { $null = New-TempDatabase } catch { $blocked = $true }
        Assert-Contract ($blocked -and $script:DockerCalls.Count -eq 0) '缺凭证必须在 Docker 操作之前失败'
    }
    Invoke-Case 'Docker 口令仅通过环境传入并还原宿主环境' {
        Reset-Boundaries
        $null = New-TempDatabase
        Assert-Contract $script:DockerRunHadPassword 'Docker 子进程未继承口令'
        Assert-Contract (-not (@($script:DockerRunArgs | Where-Object { $_ -like 'POSTGRES_PASSWORD=*' }).Count)) '口令出现在参数中'
        Assert-Contract ($script:DockerRunArgs -contains 'POSTGRES_PASSWORD') '缺少按名称继承口令'
        Assert-Contract ($env:POSTGRES_PASSWORD -eq 'contract-previous-postgres') '宿主环境未还原'
    }
    Invoke-Case '临时数据库只发布到回环地址' {
        Reset-Boundaries
        $null = New-TempDatabase
        $publishIndex = [array]::IndexOf($script:DockerRunArgs, '-p')
        Assert-Contract ($publishIndex -ge 0 -and $script:DockerRunArgs[$publishIndex + 1] -eq '127.0.0.1:56123:5432') '临时库暴露到非回环地址'
    }
    Invoke-Case 'Docker 创建失败也还原宿主口令环境' {
        Reset-Boundaries
        $script:DockerRunExit = 1
        $blocked = $false
        try { $null = New-TempDatabase 3>$null } catch { $blocked = $true }
        Assert-Contract ($blocked -and $env:POSTGRES_PASSWORD -eq 'contract-previous-postgres') 'Docker 失败后宿主环境被污染'
    }
    Invoke-Case '建库、就绪检查和空库查询各自重新导入凭证' {
        Reset-Boundaries
        $null = New-TempDatabase
        Assert-Contract ($script:CredentialImports -ge 3 -and $script:EveryDatabaseCommandReimported) '数据库操作复用了缓存凭证'
    }
    Invoke-Case '创建前不删除任何既有容器' {
        Reset-Boundaries
        $null = New-TempDatabase
        $created = $false
        foreach ($call in $script:DockerCalls) {
            if ($call[0] -eq 'run') { $created = $true }
            Assert-Contract ($created -or $call[0] -ne 'rm') '尚未创建本次容器就执行了删除'
        }
    }
    foreach ($initializationFailure in @('readiness', 'credential', 'nonempty')) {
        $script:InitializationFailure = $initializationFailure
        Invoke-Case "临时库初始化 $initializationFailure 失败即使 KeepContainer 也回收" {
            Reset-Boundaries
            $KeepContainer = $true
            if ($script:InitializationFailure -eq 'readiness') { $TimeoutSeconds = 0 }
            if ($script:InitializationFailure -eq 'credential') { $script:CredentialFailureAt = 3 }
            if ($script:InitializationFailure -eq 'nonempty') { $script:PsqlTableCount = '1' }
            $blocked = $false
            try { $null = New-TempDatabase } catch { $blocked = $true }
            $owned = Get-StartedContractContainer 'zhku-t06-tmpdb-'
            Assert-Contract ($blocked -and $owned -and (Test-RemovedAfterCreation $owned)) '初始化失败留下了临时库'
        }
    }
    Invoke-Case '初始化与清理同时失败保留原始失败并尝试回收' {
        Reset-Boundaries
        $script:CredentialFailureAt = 3
        $script:DockerRemoveExit = 1
        $primary = ''
        try { $null = New-TempDatabase 3>$null } catch { $primary = $_.Exception.Message }
        $owned = Get-StartedContractContainer 'zhku-t06-tmpdb-'
        Assert-Contract ($primary -eq 'contract credential import failed' -and (Test-RemovedAfterCreation $owned)) '原始失败丢失或未尝试清理'
    }
    Invoke-Case '成功初始化的 KeepContainer 仍可用于调试保留' {
        Reset-Boundaries
        $KeepContainer = $true
        $database = New-TempDatabase
        Remove-TempDatabase -Container $database.Container
        Assert-Contract (-not (Test-RemovedAfterCreation $database.Container)) '成功的调试保留被错误回收'
    }
    Invoke-Case '临时库回收拒绝未由本轮登记的容器' {
        Reset-Boundaries
        $blocked = $false
        try { Remove-TempDatabase -Container 'unrelated-existing-database' } catch { $blocked = $true }
        Assert-Contract ($blocked -and $script:DockerCalls.Count -eq 0) '未登记容器被清理'
    }
    Invoke-Case '转发进程清理失败仍逐个尝试自有 nginx' {
        Reset-Boundaries
        $script:RelayProcess = [pscustomobject]@{ Id = 2147000001; HasExited = $false }
        $script:StoppedPids = New-Object System.Collections.ArrayList
        [void]$script:Containers.Add('zhku-t06-nginx-contract-first')
        [void]$script:Containers.Add('zhku-t06-nginx-contract-second')
        [void]$script:Containers.Add('unrelated-container-must-not-be-removed')
        function Stop-Process { param($Id, [switch]$Force) [void]$script:StoppedPids.Add($Id); throw 'contract stop failed' }
        $blocked = $false
        try { Stop-ProxyScaffold 3>$null } catch { $blocked = $true }
        $removed = @($script:DockerCalls | Where-Object { $_[0] -eq 'rm' })
        Assert-Contract ($blocked -and $script:StoppedPids.Count -eq 1 -and $script:StoppedPids[0] -eq 2147000001 -and $removed.Count -eq 2) '首个清理失败中断了其他自有资源回收'
        Assert-Contract (@($removed | Where-Object { $_[2] -eq 'unrelated-container-must-not-be-removed' }).Count -eq 0) '触及了非本脚手架资源'
    }
    Invoke-Case 'nginx 删除失败不得记录已回收且必须失败' {
        Reset-Boundaries
        $script:DockerRemoveExit = 1
        [void]$script:Containers.Add('zhku-t06-nginx-contract-failed-remove')
        $blocked = $false
        $messages = @()
        try { $messages = @(Stop-ProxyScaffold 3>$null 6>&1) } catch { $blocked = $true }
        Assert-Contract ($blocked -and @($messages | Where-Object { $_.ToString() -like '*已回收*' }).Count -eq 0) '清理失败被冒报为已回收'
    }
    Invoke-Case '数据库 Maven 每轮重读并还原凭证、端口及目录' {
        Reset-Boundaries
        $env:ZHKU_DB_PASSWORD = 'contract-original-parent'
        $env:SERVER__PORT = '12345'
        $env:T06_CONTRACT_REQUIRE_DB = '1'
        $before = (Get-Location).Path
        $code = Invoke-MavenAcceptance -TestFilter 'Fixture' -EvidenceDir $fixture -DbUrl 'jdbc:postgresql://127.0.0.1:56123/zhkuagent_rulecheck_e2e'
        Assert-Contract ($code -eq 0 -and $script:CredentialImports -eq 1) 'Maven 未按本次环境重读凭证'
        Assert-Contract ($env:ZHKU_DB_PASSWORD -eq 'contract-original-parent' -and $env:SERVER__PORT -eq '12345' -and (Get-Location).Path -eq $before) 'Maven 未还原宿主状态'
    }
    Invoke-Case 'existing Maven 不要求数据库凭证' {
        Reset-Boundaries
        $code = Invoke-MavenAcceptance -TestFilter 'Fixture' -EvidenceDir $fixture
        Assert-Contract ($code -eq 0 -and $script:CredentialImports -eq 0) '无数据库场景误读凭证'
    }
    Invoke-Case 'Maven 非零退出仍还原环境与目录' {
        Reset-Boundaries
        $env:ZHKU_DB_PASSWORD = 'contract-original-parent'
        $env:SERVER__PORT = '12345'
        $before = (Get-Location).Path
        $env:T06_CONTRACT_MAVEN_EXIT = '17'
        $code = Invoke-MavenAcceptance -TestFilter 'Fixture' -EvidenceDir $fixture -DbUrl 'jdbc:postgresql://127.0.0.1:56123/zhkuagent_rulecheck_e2e'
        Assert-Contract ($code -eq 17 -and $env:ZHKU_DB_PASSWORD -eq 'contract-original-parent' -and $env:SERVER__PORT -eq '12345' -and (Get-Location).Path -eq $before) 'Maven 失败后宿主状态被污染'
    }
    Invoke-Case '代理显式非回环地址在启动任何进程或容器前拒绝' {
        Reset-Boundaries
        $script:ProxyProcessStarts = 0
        function Test-TcpListener { param($TargetHost, $Port) return $false }
        function Start-Process {
            param($FilePath, $ArgumentList, $WindowStyle, [switch]$PassThru)
            $script:ProxyProcessStarts++
            throw 'contract forbidden process start'
        }
        foreach ($address in @('0.0.0.0', '::1', 'localhost', '192.168.65.254')) {
            $blocked = $false
            try { Start-ProxyScaffold -Backend 0 -Public 0 -Relay 0 -RelayBindAddress $address } catch { $blocked = $true }
            Assert-Contract ($blocked -and $script:ProxyProcessStarts -eq 0 -and $script:DockerCalls.Count -eq 0) '非回环配置未在启动前拒绝'
        }
    }
    Invoke-Case '代理默认生成代码实际绑定回环 socket' {
        Reset-Boundaries
        $script:ProxyProcessStarts = 0
        $script:ProbeScript = Join-Path $fixture 'relay-bind-probe.py'
        $script:BindRecord = Join-Path $fixture 'relay-bind-record.json'
        # 非回环地址在系统 bind 前截断，避免 RED 测试自身暴露服务；回环地址则执行真实 OS bind。
        $pythonProbe = @'
import json
import runpy
import socket
import sys

OriginalSocket = socket.socket

class ProbeSocket(OriginalSocket):
    def bind(self, address):
        result = {"requestedHost": address[0], "bound": False}
        if address[0] == "127.0.0.1":
            super().bind(address)
            result["bound"] = True
            result["boundHost"] = self.getsockname()[0]
        with open(sys.argv[2], "w", encoding="utf-8") as record:
            json.dump(result, record)
        if not result["bound"]:
            raise SystemExit(0)

    def listen(self, backlog):
        self.close()
        raise SystemExit(0)

socket.socket = ProbeSocket
runpy.run_path(sys.argv[1], run_name="__main__")
'@
        [IO.File]::WriteAllText($script:ProbeScript, $pythonProbe, (New-Object Text.UTF8Encoding($false)))
        function Test-TcpListener { param($TargetHost, $Port) return $false }
        function Start-Process {
            param($FilePath, $ArgumentList, $WindowStyle, [switch]$PassThru)
            $script:ProxyProcessStarts++
            & $FilePath $script:ProbeScript $ArgumentList $script:BindRecord
            if ($LASTEXITCODE -ne 0) { throw 'contract Python probe failed' }
            # 在生成代码完成 bind 后停止，不能继续启动真实代理或 Docker。
            throw 'contract relay probe complete'
        }
        $originalTemp = $env:TEMP
        try {
            $env:TEMP = $fixture
            try { Start-ProxyScaffold -Backend 0 -Public 0 -Relay 0 } catch { }
        } finally { $env:TEMP = $originalTemp }
        Assert-Contract ($script:ProxyProcessStarts -eq 1 -and (Test-Path -LiteralPath $script:BindRecord)) '转发代码没有执行 socket 边界探针'
        $binding = Get-Content -LiteralPath $script:BindRecord -Raw -Encoding UTF8 | ConvertFrom-Json
        Assert-Contract ($binding.bound -eq $true -and $binding.requestedHost -eq '127.0.0.1' -and $binding.boundHost -eq '127.0.0.1') '默认转发没有实际绑定回环'
        Assert-Contract ($script:DockerCalls.Count -eq 0) 'socket 探针误启动了容器'
    }
    # 场景测试替换 Maven 外部执行及代理拓扑；证据判断、编排和清理仍运行生产函数。
    function Invoke-MavenAcceptance {
        param($TestFilter, $EvidenceDir, $DbUrl, $AssetsRootForTests, $ExtraArgs)
        $script:MavenBoundaryCalls++
        if ($script:MavenBoundaryFailure) { throw $script:MavenBoundaryFailure }
        if ($script:CreateEvidence) {
            foreach ($file in @('http-exchanges.json', 'simulated-chain-tempdb.json', 'cancel-capacity-terminal.json', 'proxied-chain.json')) { [IO.File]::WriteAllText((Join-Path $EvidenceDir $file), '{}') }
        }
        return $script:BoundaryMavenExit
    }
    function Start-ProxyScaffold { param($Backend, $Public, $Relay) }
    function Stop-ProxyScaffold { }
    foreach ($caseScenario in @('existing', 'chain', 'proxy')) {
        $script:CaseScenario = $caseScenario
        Invoke-Case "$caseScenario 缺证据必须失败且临时库已回收" {
            Reset-Boundaries
            $Scenario = $script:CaseScenario
            $script:CreateEvidence = $false
            $script:BoundaryMavenExit = 0
            . $orchestration 3>$null 6>$null
            Assert-Contract ($overall -ne 0) '缺证据被冒认为成功'
            if ($Scenario -ne 'existing') {
                $owned = Get-StartedContractContainer 'zhku-t06-tmpdb-'
                Assert-Contract (Test-RemovedAfterCreation $owned) '缺证据后未清理临时库'
            }
        }
    }
    Invoke-Case '完整证据与 Maven 成功返回成功' {
        Reset-Boundaries
        $Scenario = 'all'
        $script:CreateEvidence = $true
        $script:BoundaryMavenExit = 0
        . $orchestration 3>$null 6>$null
        Assert-Contract ($overall -eq 0) '完整证据未通过'
    }
    Invoke-Case 'Maven 原失败码保留且 all 停止后续场景' {
        Reset-Boundaries
        $Scenario = 'all'
        $script:CreateEvidence = $false
        $script:BoundaryMavenExit = 17
        . $orchestration 3>$null 6>$null
        Assert-Contract ($overall -eq 17 -and $script:DockerCalls.Count -eq 0) '原失败码丢失或继续后续场景'
    }
    foreach ($proxyFailureStage in @('relay-readiness', 'template', 'nginx-run', 'nginx-readiness', 'nginx-readiness-and-cleanup')) {
        $script:ProxyFailureStage = $proxyFailureStage
        Invoke-Case "代理初始化 $proxyFailureStage 失败回收全部自有资源" {
            Reset-Boundaries
            $Scenario = 'proxy'
            $script:StoppedPids = New-Object System.Collections.ArrayList
            $templateDirectory = Join-Path $fixture 'agent/frontend'
            New-Item -ItemType Directory -Path $templateDirectory -Force *> $null
            $templatePath = Join-Path $templateDirectory 'nginx.conf'
            if ($script:ProxyFailureStage -eq 'template') {
                Remove-Item -LiteralPath $templatePath -ErrorAction SilentlyContinue
            } else { [IO.File]::WriteAllText($templatePath, '# contract template') }
            if ($script:ProxyFailureStage -eq 'nginx-readiness-and-cleanup') { $script:DockerRemoveExit = 1 }
            if ($script:ProxyFailureStage -eq 'nginx-run') { $script:NginxRunExit = 1 }
            function Test-TcpListener { param($TargetHost, $Port) return $false }
            function Start-Process { param($FilePath, $ArgumentList, $WindowStyle, [switch]$PassThru) return [pscustomobject]@{ Id = 2147000002; HasExited = $false } }
            function Stop-Process {
                param($Id, [switch]$Force)
                [void]$script:StoppedPids.Add($Id)
                if ($script:ProxyFailureStage -eq 'nginx-readiness-and-cleanup') { throw 'contract stop failed' }
            }
            function Wait-Until {
                param([scriptblock]$Condition, [string]$What, [int]$Seconds)
                if ($What -eq '临时库就绪') { $null = & $Condition; return }
                if (($What -like '宿主转发端口*' -and $script:ProxyFailureStage -eq 'relay-readiness') -or ($What -like 'nginx 发布端口*' -and $script:ProxyFailureStage -like 'nginx-readiness*')) { throw 'contract proxy readiness failed' }
            }
            function Start-ProxyScaffold { param($Backend, $Public, $Relay) & $productionStartProxy -Backend $Backend -Public $Public -Relay $Relay }
            function Stop-ProxyScaffold { & $productionStopProxy }
            $primary = ''
            $originalTemp = $env:TEMP
            try { $env:TEMP = $fixture; try { . $orchestration 3>$null 6>$null } catch { $primary = $_.Exception.Message } }
            finally { $env:TEMP = $originalTemp }
            if ($script:ProxyFailureStage -eq 'template') { Assert-Contract ($primary -like '未找到仓库 nginx 配置*') '模板失败原始原因丢失' }
            elseif ($script:ProxyFailureStage -eq 'nginx-run') { Assert-Contract ($primary -like 'nginx 容器启动失败*') '容器启动失败原始原因丢失' }
            else { Assert-Contract ($primary -eq 'contract proxy readiness failed') '原始代理失败被清理异常覆盖' }
            $ownedDatabase = Get-StartedContractContainer 'zhku-t06-tmpdb-'
            Assert-Contract ($script:StoppedPids.Count -eq 1 -and $script:StoppedPids[0] -eq 2147000002 -and (Test-RemovedAfterCreation $ownedDatabase)) '代理启动失败后转发进程或临时库未回收'
            if ($script:ProxyFailureStage -like 'nginx-*') {
                $ownedNginx = Get-StartedContractContainer 'zhku-t06-nginx-'
                Assert-Contract ($ownedNginx -and (Test-RemovedAfterCreation $ownedNginx)) '代理启动失败后 nginx 未回收'
            }
            Assert-Contract ($script:MavenBoundaryCalls -eq 0) '代理初始化失败后仍调用了 Maven'
        }
    }
    foreach ($mavenExit in @(0, 17)) {
        $script:CleanupCaseMavenExit = $mavenExit
        Invoke-Case "临时库清理失败影响验收结果并保留 Maven $mavenExit 原码" {
            Reset-Boundaries
            $Scenario = 'chain'
            $script:CreateEvidence = $true
            $script:BoundaryMavenExit = $script:CleanupCaseMavenExit
            $script:DockerRemoveExit = 1
            . $orchestration 3>$null 6>$null
            $wanted = 1
            if ($script:CleanupCaseMavenExit -ne 0) { $wanted = $script:CleanupCaseMavenExit }
            Assert-Contract ($overall -eq $wanted) '资源清理失败被当成通过或覆盖原码'
        }
    }
    Invoke-Case '业务异常与清理异常同时发生仍保留业务异常' {
        Reset-Boundaries
        $Scenario = 'chain'
        $script:MavenBoundaryFailure = 'contract original business failure'
        $script:DockerRemoveExit = 1
        $primary = ''
        try { . $orchestration 3>$null 6>$null } catch { $primary = $_.Exception.Message }
        $owned = Get-StartedContractContainer 'zhku-t06-tmpdb-'
        Assert-Contract ($primary -eq 'contract original business failure' -and (Test-RemovedAfterCreation $owned)) '业务异常被清理异常覆盖或未回收'
    }
    Invoke-Case '正常代理验收后 nginx 清理失败必须使整体失败' {
        Reset-Boundaries
        $Scenario = 'proxy'
        $script:CreateEvidence = $true
        $script:BoundaryMavenExit = 0
        $script:DockerRemoveExit = 1
        $script:DockerRemoveFailurePrefix = 'zhku-t06-nginx-'
        $script:StoppedPids = New-Object System.Collections.ArrayList
        function Start-ProxyScaffold {
            param($Backend, $Public, $Relay)
            $script:RelayProcess = [pscustomobject]@{ Id = 2147000003; HasExited = $false }
            [void]$script:Containers.Add('zhku-t06-nginx-contract-cleanup')
        }
        function Stop-ProxyScaffold { & $productionStopProxy }
        function Stop-Process { param($Id, [switch]$Force) [void]$script:StoppedPids.Add($Id) }
        . $orchestration 3>$null 6>$null
        $owned = Get-StartedContractContainer 'zhku-t06-tmpdb-'
        Assert-Contract ($overall -ne 0 -and $script:StoppedPids.Count -eq 1 -and (Test-RemovedAfterCreation $owned)) '代理清理失败被当成成功或跳过数据库回收'
    }
} finally {
    foreach ($name in $trackedNames) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    if (Test-Path -LiteralPath $fixture) {
        $resolvedFixture = (Resolve-Path -LiteralPath $fixture).Path
        $resolvedTemp = (Resolve-Path -LiteralPath $env:TEMP).Path.TrimEnd('\') + '\'
        if (-not $resolvedFixture.StartsWith($resolvedTemp, [StringComparison]::OrdinalIgnoreCase)) { throw '临时目录边界异常，拒绝清理' }
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
Write-Host ('T06 contract: {0} passed, {1} failed; Docker/DB/provider calls: 0 real.' -f $script:Passed, $script:Failures.Count)
if ($script:Failures.Count -gt 0) { exit 1 }
exit 0
