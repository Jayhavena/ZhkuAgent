$scriptPath = Join-Path $PSScriptRoot 'ensure-zhku-postgres.ps1'

Describe 'ensure-zhku-postgres' {
    BeforeEach {
        $global:mockContainerExists = $true
        $global:mockContainerRunning = $false
        $global:mockDockerCalls = [System.Collections.Generic.List[string]]::new()
        function global:docker.exe {
            param([Parameter(ValueFromRemainingArguments = $true)][string[]]$DockerArguments)

            $global:mockDockerCalls.Add(($DockerArguments -join ' '))
            $global:LASTEXITCODE = 0
            switch ($DockerArguments[0]) {
                'info' { return '26.1.0' }
                'inspect' {
                    if (-not $global:mockContainerExists) {
                        $global:LASTEXITCODE = 1
                        Write-Error 'No such container'
                        return
                    }
                    if ($global:mockContainerRunning) {
                        return 'true|healthy'
                    }
                    return 'false|none'
                }
                'start' {
                    $global:mockContainerRunning = $true
                    return 'zhkuagent-postgres'
                }
                default { throw "未预期的 Docker 命令：$($DockerArguments -join ' ')" }
            }
        }
        Mock -CommandName Test-NetConnection -MockWith { $true }
        Mock -CommandName Start-Sleep -MockWith { }
    }

    AfterEach {
        Remove-Item 'Function:\global:docker.exe' -ErrorAction SilentlyContinue
        Remove-Variable mockContainerExists, mockContainerRunning, mockDockerCalls -Scope Global -ErrorAction SilentlyContinue
    }

    It 'starts the existing stopped container and waits for the database port' {
        (Test-Path -LiteralPath $scriptPath) | Should Be $true

        & $scriptPath -TimeoutSeconds 1 -PollIntervalMilliseconds 1 | Should Match 'ready'

        ($global:mockDockerCalls -contains 'start zhkuagent-postgres') | Should Be $true
        Assert-MockCalled -CommandName Test-NetConnection -Times 1 -Exactly
    }

    It 'does not restart a healthy existing container' {
        $global:mockContainerRunning = $true
        (Test-Path -LiteralPath $scriptPath) | Should Be $true

        & $scriptPath -TimeoutSeconds 1 -PollIntervalMilliseconds 1 | Should Match 'ready'

        ($global:mockDockerCalls -contains 'start zhkuagent-postgres') | Should Be $false
    }

    It 'fails without creating resources when the expected container does not exist' {
        $global:mockContainerExists = $false
        (Test-Path -LiteralPath $scriptPath) | Should Be $true

        { & $scriptPath -TimeoutSeconds 1 -PollIntervalMilliseconds 1 } |
                Should Throw 'No such container'

        ($global:mockDockerCalls -contains 'start zhkuagent-postgres') | Should Be $false
    }
}
