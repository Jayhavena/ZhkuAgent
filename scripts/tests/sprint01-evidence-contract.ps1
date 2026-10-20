#requires -Version 7.0
$ErrorActionPreference='Stop'
$file=Join-Path $PSScriptRoot '../../tools/sprint01/Invoke-SimulatedQuestionLoop.ps1'
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'Acceptance script parse failed'}
$function=$ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Redact'},$true)
if(!$function){throw 'Evidence redactor missing'}
# 仅载入纯脱敏函数，不运行控制器、启动器或任何资源操作。
. ([scriptblock]::Create($function.Extent.Text))
$sample=@{exchanges=@(@{path='/tasks/fixture/originals/synthetic-proof';actual=@{token='synthetic-csrf';originalToken='synthetic-proof';continuationToken='synthetic-continuation';quote='保留可核验的规则正文'}})}
Redact $sample
$json=$sample|ConvertTo-Json -Depth 10
if($json -match 'synthetic-(proof|csrf|continuation)' -or $json -notmatch '保留可核验的规则正文'){throw 'Token field/path redaction contract failed'}
Write-Output '{"passed":true,"contracts":["token-fields-and-original-url-redacted-with-content-preserved"],"databaseOperations":0,"providerCalls":0}'
