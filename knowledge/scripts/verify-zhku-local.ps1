param(
    [Parameter(Mandatory = $true)][string]$DatabaseUrl,
    [Parameter(Mandatory = $true)][string]$DataRoot,
    [string]$DatabaseUser = 'zhkuagent'
)
$ErrorActionPreference = 'Stop'
# 验收必须指定专用测试库；禁止沿用原演示库默认地址。
if ($DatabaseUrl -notmatch '^jdbc:postgresql://127\.0\.0\.1:\d+/' -or $DatabaseUrl -match ':54329/') { throw '请指定独立临时数据库地址，不能使用原知识库。' }
if ([string]::IsNullOrWhiteSpace($env:ZHKU_DB_PASSWORD)) { throw '请先通过当前环境设置测试库密码。' }
$moduleRoot = Split-Path $PSScriptRoot -Parent
$repoRoot = Split-Path $moduleRoot -Parent
$runtimeRoot = (Resolve-Path -LiteralPath $DataRoot).Path
$tests = (Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'src/test/java') -Recurse -Filter '*Test.java' | ForEach-Object BaseName) -join ','
& (Join-Path $repoRoot 'mvnw.cmd') '-f' (Join-Path $moduleRoot 'pom.xml') "-Dtest=$tests" "-Dzhku.db.url=$DatabaseUrl" "-Dzhku.db.user=$DatabaseUser" "-Dzhku.data-root=$runtimeRoot" test
if ($LASTEXITCODE -ne 0) { throw '知识库验收失败。' }
