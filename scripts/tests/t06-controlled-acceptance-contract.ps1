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
    $script:LastDatabaseImportCount = 0
    $script:EveryDatabaseCommandReimported = $true
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
    }
    if ($arguments[0] -eq 'exec' -and $arguments[2] -eq 'psql') { return '0' }
}
function Get-FreeLoopbackPort { return 56123 }
try {
    New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts') -Force *> $null
    # 凭证存储是外部边界：替身禁止读取本机密钥，计数检查每次数据库操作是否重新导入。
    $credentialStub = @'
param([switch]$RequireDatabase)
$script:CredentialImports++
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
                $removals = @($script:DockerCalls | Where-Object { $_[0] -eq 'rm' -and $_[2] -like 'zhku-t06-tmpdb-*' })
                Assert-Contract ($removals.Count -ge 2) '缺证据后未清理临时库'
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
