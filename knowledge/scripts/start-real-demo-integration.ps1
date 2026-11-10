#requires -Version 7.0
param(
    [ValidateSet('Prepare','Verify','Assembly','Live')][string]$Operation='Prepare',
    [string]$AssetsRoot,
    [Parameter(Mandatory)][string]$BatchDirectory,
    [string]$PackageSha256,
    [string]$ApprovedSha256,
    [string]$ApprovedBudgetRmb,
    [string]$AuthorizationId,
    [ValidateRange(1024,65535)][int]$DatabasePort=55527,
    [string]$HandbookPdf
)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if(!$AssetsRoot){$AssetsRoot=$root}
$git=(Get-Command git.exe -ErrorAction Stop).Source
$gitLocation=Join-Path $root '.git'
if(Test-Path -LiteralPath $gitLocation -PathType Leaf){
    $pointer=(Get-Content -Raw -LiteralPath $gitLocation).Trim()
    if($pointer -notmatch '^gitdir: (.+)$'){throw '无法确定原索引位置'}
    $gitLocation=[IO.Path]::GetFullPath((Join-Path $root $Matches[1]))
}
$originalIndex=Join-Path $gitLocation 'index'
$originalHash=(Get-FileHash -LiteralPath $originalIndex -Algorithm SHA256).Hash
$savedIndex=$env:GIT_INDEX_FILE;$savedLocks=$env:GIT_OPTIONAL_LOCKS
$index=Join-Path ([IO.Path]::GetTempPath()) ('zhku-real-demo-'+[guid]::NewGuid().ToString('N')+'.index')
$exitCode=1
try{
    # HEAD已前进而原索引属于其他任务；独立索引只用于冻结真实输入，绝不修正或重暂存原索引。
    $env:GIT_INDEX_FILE=$index;$env:GIT_OPTIONAL_LOCKS='0'
    & $git -C $root read-tree HEAD
    if($LASTEXITCODE -ne 0){throw '独立输入索引初始化失败'}
    $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'start-rule-agent-three-question-test.ps1'),
        '-Operation',$Operation,'-Profile','E02','-PlanFile','knowledge/src/test/resources/rule-agent-e02-integration-plan.json',
        '-AssetsRoot',$AssetsRoot,'-BatchDirectory',$BatchDirectory,'-DatabasePort',[string]$DatabasePort)
    foreach($item in @(@('PackageSha256',$PackageSha256),@('ApprovedSha256',$ApprovedSha256),@('ApprovedBudgetRmb',$ApprovedBudgetRmb),
        @('AuthorizationId',$AuthorizationId),@('HandbookPdf',$HandbookPdf))){if($item[1]){$arguments+=@("-$($item[0])",[string]$item[1])}}
    & (Join-Path $PSHOME 'pwsh.exe') @arguments
    $exitCode=$LASTEXITCODE
}finally{
    $env:GIT_INDEX_FILE=$savedIndex;$env:GIT_OPTIONAL_LOCKS=$savedLocks
    if((Get-FileHash -LiteralPath $originalIndex -Algorithm SHA256).Hash -cne $originalHash){throw '原索引发生变化，停止后续操作'}
}
Write-Host "Original Git index preserved. Independent input index: $index"
exit $exitCode
