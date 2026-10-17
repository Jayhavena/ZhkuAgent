#requires -Version 7.0
param([switch]$Assembly,[ValidateRange(1024,65535)][int]$DatabasePort=55519)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $root 'scripts/Invoke-ZhkuOwnedProcess.ps1')
$importer=Join-Path $root 'scripts/Import-ZhkuCredentials.ps1'
$java=Get-Command java.exe -ErrorAction Stop
$jar=Join-Path $root 'knowledge/target/knowledge-0.1.0-rc.1-controlled.jar'
$env:ZHKU_RULE_AGENT_ROOT=$root
# 本地验收入口显式撤去模型凭证和授权；即使原会话有值，也不能转入在线分支。
foreach($name in @('DASHSCOPE_API_KEY','ZHKU_RULE_AGENT_AUTHORIZATION_ID','ZHKU_RULE_AGENT_MODE')){Remove-Item -LiteralPath "Env:\$name" -ErrorAction SilentlyContinue}
$arguments=@('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dloader.main=com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost','-cp',$jar,'org.springframework.boot.loader.launch.PropertiesLauncher')
$checked=Invoke-ZhkuOwnedProcess -Executable $java.Source -Arguments ($arguments+@('--CheckRuntimeAssets')) -TimeoutSeconds 20
$checked.output
if($checked.exitCode-ne 0-or $checked.timedOut-or !$checked.resourcesClosed){throw '运行资产检查失败；未创建临时库'}
if(!$Assembly){exit 0}
if($DatabasePort-eq 54329){throw '拒绝演示库端口'}
$docker=Get-Command docker.exe -ErrorAction Stop
& $importer -RequireDatabase
$secrets=@($env:ZHKU_DB_PASSWORD)
$env:POSTGRES_PASSWORD=$env:ZHKU_DB_PASSWORD
$env:ZHKU_DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/zhkuagent_rulecheck_e2e"
$container='zhku-runtime-assets-'+[guid]::NewGuid().ToString('N')
function Docker([string[]]$argumentsForDocker){
 & $importer -RequireDatabase
 $r=Invoke-ZhkuOwnedProcess -Executable $docker.Source -Arguments $argumentsForDocker -TimeoutSeconds 12 -Secrets $secrets
 if($r.exitCode-ne 0-or $r.timedOut-or !$r.resourcesClosed){throw '本次临时库操作失败'}
 return $r
}
try {
 $null=Docker @('run','--rm','-d','--pull','never','--name',$container,'--publish',"127.0.0.1:${DatabasePort}:5432",'--env','POSTGRES_PASSWORD','--env','POSTGRES_USER=zhkuagent','--env','POSTGRES_DB=zhkuagent_rulecheck_e2e','pgvector/pgvector:0.8.6-pg16-bookworm')
 $ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(30)
 while([DateTime]::UtcNow-lt $deadline){try{$null=Docker @('exec',$container,'pg_isready','-U','zhkuagent','-d','zhkuagent_rulecheck_e2e');$ready=$true;break}catch{Start-Sleep -Milliseconds 200}}
 if(!$ready){throw '专用临时库未就绪'}
 & $importer -RequireDatabase
 $result=Invoke-ZhkuOwnedProcess -Executable $java.Source -Arguments ($arguments+@('--CheckRuntimeAssembly')) -TimeoutSeconds 60 -Secrets $secrets
 $result.output;$result.errorOutput
 if($result.exitCode-ne 0-or $result.timedOut-or !$result.resourcesClosed){throw '本地运行装配未通过'}
} finally {
 # 只清理本轮随机容器；原演示库和其他容器不在范围内。
 $null=Docker @('rm','-f',$container)
}
