$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
$utf8=[Text.UTF8Encoding]::new($false)
$paths=@(
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentTaskStore.java',
 'knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentTaskStoreTest.java',
 'knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentContract.java',
 'agent/frontend/src/api/rule-agent-contract.js',
 'knowledge/scripts/verify-rule-check-refactor.ps1',
 'docs/design/rule-agent-task-store.md','docs/evidence/rule-agent-progress.md',
 'docs/evidence/rule-agent-task-store/2026-10-13/t04-store-red-01/results.json',
 'docs/evidence/rule-agent-task-store/2026-10-13/t04-store-red-01/xml/TEST-com.zhku.knowledge.ruleagent.RuleAgentTaskStoreTest.xml',
 'docs/evidence/rule-agent-task-store/2026-10-13/t04-store-green-01/results.json',
 'docs/evidence/rule-agent-task-store/2026-10-13/t04-store-green-01/xml/TEST-com.zhku.knowledge.ruleagent.RuleAgentTaskStoreTest.xml'
)
foreach($phase in @('t04-01','t04-02','t04-final-unit-01','t04-final-unit-02','t04-final-unit-03')){
 $directory=Join-Path $root "docs/evidence/rule-agent-task-store/2026-10-13/$phase"
 $paths+=Get-ChildItem -LiteralPath $directory -Recurse -File|Where-Object {
   $_.Extension -in @('.md','.json','.log','.xml','.ps1','.mjs') -and
   $_.Name -notin @('source-before.json','source-after.json','scope-manifest.json','secret-review.json','commit-receipt.json','staged-review.json')
 }|ForEach-Object {[IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/')}
}
$paths=@($paths|Sort-Object -Unique)
$configured=@()
foreach($name in @('ZHKU_DB_PASSWORD','DASHSCOPE_API_KEY')){
 $found=$false
 foreach($scope in @('Process','User','Machine')){try{$value=[Environment]::GetEnvironmentVariable($name,$scope);if($value){$configured+=@{name=$name;value=$value};$found=$true}}catch{}}
 if(!$found){throw "秘密检查当前无法读取$name"}
}
$blocked=@();$files=@()
foreach($path in $paths){
 $full=Join-Path $root $path;$content=[IO.File]::ReadAllText($full)
 foreach($item in $configured){if($content.Contains($item.value)){$blocked+=@{path=$path;variable=$item.name}}}
 if($content -match '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----' -or $content -match 'sk-[A-Za-z0-9_-]{16,}'){$blocked+=@{path=$path;variable='PATTERN'}}
 $files+=@{path=$path;sha256=(Get-FileHash -LiteralPath $full).Hash.ToLowerInvariant()}
}
$verification=Get-Content (Join-Path $root 'docs/evidence/rule-agent-task-store/2026-10-13/t04-final-unit-03/results.json') -Raw|ConvertFrom-Json
if(!$verification.passed-or $verification.sourceDrift-ne 0-or !$verification.resourcesVerified){throw '最终单元验证未通过'}
$source=Get-Content (Join-Path $root 'docs/evidence/rule-agent-task-store/2026-10-13/t04-final-unit-03/source-after.json') -Raw|ConvertFrom-Json
foreach($file in $files|Where-Object {$_.path -like 'knowledge/*'}){
 $observed=@($source|Where-Object {$_.path-eq $file.path})
 if($observed.Count-ne 1-or $observed[0].sha256-ne $file.sha256){throw "源码与验收身份不一致：$($file.path)"}
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'scope-manifest.json'),(@{files=$files;fileCount=$files.Count;baseHead=(git -c core.excludesfile= -C $root rev-parse HEAD);scope='T04_INTERNAL_TASK_STORE'}|ConvertTo-Json -Depth 6),$utf8)
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'secret-review.json'),(@{freshConfiguredValueScan=$true;passed=($blocked.Count-eq 0);blocked=$blocked;valuesNeverPrinted=$true;publicationApproved=$false}|ConvertTo-Json -Depth 6),$utf8)
if($blocked.Count){throw '存在未审查的敏感匹配；值未打印'}
[IO.File]::WriteAllBytes((Join-Path $PSScriptRoot 'pathspec.bin'),$utf8.GetBytes(($paths -join "`0")+"`0"))
@{fileCount=$files.Count;passed=$true}|ConvertTo-Json -Compress
