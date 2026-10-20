#requires -Version 7.0
param([string]$ReportPath,[string]$ExpectedIdentity='')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$ps51=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$actualExt=$env:PATHEXT
$normalPath=[Environment]::ExpandEnvironmentVariables((@([Environment]::GetEnvironmentVariable('Path','Machine'),[Environment]::GetEnvironmentVariable('Path','User'))|Where-Object {$_}) -join ';')
$temp=Join-Path ([IO.Path]::GetTempPath()) ('zhku-terminal-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($temp)
$relay=Join-Path $temp 'relay.cmd'
[IO.File]::WriteAllText($relay,"@echo off`r`n""%SystemRoot%\System32\cmd.exe"" /d /c ""echo ZHKU_NATIVE_29&exit /b 29""`r`nexit /b %errorlevel%`r`n",[Text.Encoding]::ASCII)
function Invoke-Probe([string]$Code,[bool]$WithPathExt=$true) {
    $info=[Diagnostics.ProcessStartInfo]::new($ps51)
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    $info.Environment['PATH']=$normalPath
    if($WithPathExt){$info.Environment['PATHEXT']=$actualExt}else{[void]$info.Environment.Remove('PATHEXT')}
    foreach($key in @($info.Environment.Keys)){if($key -match '(?i)dashscope|openai|anthropic|api.?key|^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|MAVEN_ARGS|MAVEN_OPTS)$|^(LOADER_|SPRING_|SERVER_|ZHKU_RAG_|ZHKU_RULECHECK_|ZHKU_RULE_AGENT_)'){[void]$info.Environment.Remove($key)}}
    foreach($arg in @('-NoProfile','-NonInteractive','-EncodedCommand',[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Code)))){[void]$info.ArgumentList.Add($arg)}
    $p=[Diagnostics.Process]::Start($info);$out=$p.StandardOutput.ReadToEndAsync();$err=$p.StandardError.ReadToEndAsync()
    try {if(!$p.WaitForExit(30000)){$p.Kill($true);throw 'Terminal probe timeout'};return @{exit=$p.ExitCode;output=$out.GetAwaiter().GetResult();error=$err.GetAwaiter().GetResult()}}finally{$p.Dispose()}
}
# 这是验收控制器的门禁，防止重现历史的空 LASTEXITCODE→0；不冒充业务启动器的拒绝行为。
$guard=@'
$ErrorActionPreference='Stop'
[Console]::InputEncoding=[Console]::OutputEncoding=$OutputEncoding=New-Object Text.UTF8Encoding($false)
if(!$env:PATHEXT -or '.EXE' -notin ($env:PATHEXT -split ';') -or '.CMD' -notin ($env:PATHEXT -split ';')){[Console]::Error.WriteLine('ZHKU_PATHEXT_REQUIRED');exit 64}
$global:LASTEXITCODE=$null
'@
$cases=@();$failure=$null
try {
    foreach($entry in @(@{name='native-exe';code='& "$env:SystemRoot\System32\cmd.exe" /d /c ''echo ZHKU_NATIVE_29&exit /b 29'''},@{name='cmd-relay';code=('& '''+$relay.Replace("'","''")+'''')})) {
        $r=Invoke-Probe ($guard+"`n"+$entry.code+"`nif(`$null -eq `$LASTEXITCODE){exit 65};exit `$LASTEXITCODE")
        if($r.exit -ne 29 -or $r.output -notmatch '(?m)^ZHKU_NATIVE_29\r?$'){throw ('Native boundary failed: '+$entry.name+'; exit='+$r.exit)}
        $cases+=$entry.name
    }
    $missing=Invoke-Probe ($guard+"`nexit 0") $false
    if($missing.exit -ne 64 -or $missing.error -notmatch 'ZHKU_PATHEXT_REQUIRED'){throw 'Missing PATHEXT controller guard failed'}
    $cases+='controller-rejects-missing-pathext'
    $launcher=Join-Path $root 'knowledge/scripts/start-rule-agent-simulated-demo.cmd'
    $identityArg=if($ExpectedIdentity){" -ExpectedIdentity '"+$ExpectedIdentity.Replace("'","''")+"'"}else{''}
    $check=Invoke-Probe ($guard+"`n& '"+$launcher.Replace("'","''")+"' -Action CheckEnvironment"+$identityArg+"`nif(`$null -eq `$LASTEXITCODE){exit 65};exit `$LASTEXITCODE")
    if($check.exit -ne 0){throw ('CheckEnvironment failed: exit='+$check.exit)}
    $receipt=$check.output|ConvertFrom-Json
    if($receipt.mode -cne 'SIMULATED' -or $receipt.databaseOperations -ne 0 -or $receipt.providerCalls -ne 0 -or $receipt.executionAuthorized){throw 'CheckEnvironment receipt mismatch'}
    $cases+='existing-launcher-check-environment'
    $identityPath=if($ExpectedIdentity){$ExpectedIdentity}else{Join-Path $root 'docs/preparation/m1-04a/identity.json'}
    $ancestor=Get-Content -Raw -LiteralPath $identityPath|ConvertFrom-Json
    $ancestor.head=(& git -C $root rev-parse ($ancestor.head+'^'))
    $ancestorPath=Join-Path $temp 'ancestor.json'
    [IO.File]::WriteAllText($ancestorPath,($ancestor|ConvertTo-Json -Depth 20))
    $command=$guard+"`n& '"+$launcher.Replace("'","''")+"' -Action CheckEnvironment -ExpectedIdentity '"+$ancestorPath.Replace("'","''")+"'`nif(`$null -eq `$LASTEXITCODE){exit 65};exit `$LASTEXITCODE"
    $same=Invoke-Probe $command
    if($same.exit -ne 0){throw 'Verified identical source rejected after local checkpoint'}
    $cases+='ancestor-with-identical-source-accepted'
    $ancestor.source[0].sha256='0'*64
    [IO.File]::WriteAllText($ancestorPath,($ancestor|ConvertTo-Json -Depth 20))
    $changed=Invoke-Probe $command
    if($changed.exit -eq 0){throw 'Changed source identity accepted'}
    $cases+='ancestor-does-not-bypass-source-digest'
}catch{$failure=$_.Exception.Message}
$report=[ordered]@{passed=($null -eq $failure);contracts=$cases;failure=$failure;databaseOperations=0;providerCalls=0;ordinaryPersistentPath=$true;parent='Windows PowerShell 5.1';missingPathExtScope='controller guard only'}
if($ReportPath){[IO.File]::WriteAllText($ReportPath,($report|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))}
$report|ConvertTo-Json -Depth 5
if($failure){exit 1}
