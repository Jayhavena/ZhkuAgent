param(
    [switch]$UseDatabase,
    [switch]$UseDashScope,
    [switch]$RequireDatabase,
    [switch]$RequireDashScope
)

$ErrorActionPreference = 'Stop'

$credentialNames = @()
if ($UseDatabase -or $RequireDatabase) { $credentialNames += 'ZHKU_DB_PASSWORD' }
if ($UseDashScope -or $RequireDashScope) { $credentialNames += 'DASHSCOPE_API_KEY' }
foreach ($credentialName in $credentialNames) {
    $credentialValue = [Environment]::GetEnvironmentVariable($credentialName, 'Process')
    if ([string]::IsNullOrWhiteSpace($credentialValue)) {
        foreach ($credentialScope in @('User', 'Machine')) {
            $credentialValue = [Environment]::GetEnvironmentVariable($credentialName, $credentialScope)
            if (-not [string]::IsNullOrWhiteSpace($credentialValue)) {
                # 只注入当前进程供子进程继承；不能覆盖调用方显式设置的测试凭证。
                [Environment]::SetEnvironmentVariable($credentialName, $credentialValue, 'Process')
                break
            }
        }
    }

    $required = ($credentialName -eq 'ZHKU_DB_PASSWORD' -and $RequireDatabase) -or
        ($credentialName -eq 'DASHSCOPE_API_KEY' -and $RequireDashScope)
    if ($required -and [string]::IsNullOrWhiteSpace($credentialValue)) {
        throw "当前进程及 Windows 用户/系统环境均无法读取 $credentialName；请检查启动器是否继承了已配置的凭证。"
    }
}
