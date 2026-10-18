#requires -Version 7.0
param([string]$Launcher=(Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'knowledge/scripts/start-rule-agent-http-check.ps1'))
$ErrorActionPreference='Stop'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($Launcher,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'launcher parse failed'}
$functionAst=$ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-HttpOwnedChild'},$true)[0]
. ([scriptblock]::Create($functionAst.Extent.Text))
$fixture=Join-Path $env:TEMP ('zhku-http-owned-child-'+[guid]::NewGuid().ToString('N'))
$root=Join-Path $fixture 'working root';$installed=Join-Path $fixture 'installed tools'
New-Item -ItemType Directory -Path $root,$installed|Out-Null
$utf8=[Text.UTF8Encoding]::new($false);$launchLog=Join-Path $fixture 'child.log'
$previousPath=$env:Path
try{
 [IO.File]::WriteAllText((Join-Path $installed 'fixture-command.cmd'),"@echo off`r`necho %~dp0`r`n",[Text.ASCIIEncoding]::new())
 $env:Path=$installed+';'+$previousPath
 $result=Invoke-HttpOwnedChild -Arguments @('fixture-command.cmd') -Timeout 5
 if($result.Trim().TrimEnd('\') -ne $installed){throw 'PATH-resolved CMD shim used the working directory instead of its executable directory'}
 Write-Output 'Owned child PATH shim location: PASS; no real DB/model/Maven/npm commands.'
}finally{
 $env:Path=$previousPath
 $resolved=[IO.Path]::GetFullPath($fixture);$temp=[IO.Path]::GetFullPath($env:TEMP).TrimEnd('\')+'\'
 if(!$resolved.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase)){throw 'test fixture cleanup outside temp boundary'}
 Remove-Item -LiteralPath $resolved -Recurse -Force
}