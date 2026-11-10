$helperPath = Join-Path $PSScriptRoot 'ZhkuDocker.ps1'
. $helperPath

Describe 'Zhku Docker engine readiness' {
    BeforeEach {
        $script:engineCalls = 0
        Mock Resolve-ZhkuDockerExecutable { 'C:\Docker\docker.exe' }
        Mock Resolve-ZhkuDockerDesktopExecutable { 'C:\Docker\Docker Desktop.exe' }
        Mock Test-Path { $true }
        Mock Start-Process { }
        Mock Start-Sleep { }
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 0; Output = '"linux"' } }
    }

    It 'loads the engine helpers without accessing Docker' {
        Test-Path -LiteralPath $helperPath | Should Be $true
        (Get-Command Ensure-ZhkuDocker -ErrorAction SilentlyContinue) -ne $null | Should Be $true
    }

    It 'does not launch Desktop when the Linux engine is ready' {
        @(Ensure-ZhkuDocker -TimeoutSeconds 1).Count | Should Be 0
        Assert-MockCalled Start-Process -Times 0 -Exactly -Scope It
    }

    It 'launches Desktop once and polls until Linux readiness' {
        Mock Invoke-ZhkuDocker {
            $script:engineCalls++
            if ($script:engineCalls -lt 3) { return [pscustomobject]@{ ExitCode = 1; Output = 'engine unavailable' } }
            [pscustomobject]@{ ExitCode = 0; Output = '"linux"' }
        }
        Ensure-ZhkuDocker -TimeoutSeconds 1 -PollIntervalMilliseconds 1
        Assert-MockCalled Start-Process -Times 1 -Exactly -Scope It -ParameterFilter { $FilePath -eq 'C:\Docker\Docker Desktop.exe' }
    }

    It 'does not launch Desktop in check-only mode' {
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 1; Output = 'engine unavailable' } }
        { Ensure-ZhkuDocker -CheckOnly -TimeoutSeconds 1 } | Should Throw 'CheckOnly'
        Assert-MockCalled Start-Process -Times 0 -Exactly -Scope It
    }

    It 'fails access denial immediately without launch or polling' {
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 1; Output = 'permission denied SYNTHETIC_PRIVATE_MARKER' } }
        { Ensure-ZhkuDocker -TimeoutSeconds 1 } | Should Throw 'access denied'
        Assert-MockCalled Invoke-ZhkuDocker -Times 1 -Exactly -Scope It
        Assert-MockCalled Start-Process -Times 0 -Exactly -Scope It
        Assert-MockCalled Start-Sleep -Times 0 -Exactly -Scope It
    }

    It 'rejects Windows containers instead of claiming readiness' {
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 0; Output = '"windows"' } }
        { Ensure-ZhkuDocker -TimeoutSeconds 1 } | Should Throw 'Linux containers'
        Assert-MockCalled Start-Process -Times 0 -Exactly -Scope It
    }

    It 'does not swallow malformed successful engine metadata' {
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 0; Output = 'SYNTHETIC_PRIVATE_MARKER' } }
        { Ensure-ZhkuDocker -TimeoutSeconds 1 } | Should Throw 'invalid operating-system metadata'
    }

    It 'fails without launching when the CLI is missing' {
        Mock Resolve-ZhkuDockerExecutable { throw 'Docker CLI executable was not found.' }
        { Ensure-ZhkuDocker -TimeoutSeconds 1 } | Should Throw 'not found'
        Assert-MockCalled Invoke-ZhkuDocker -Times 0 -Exactly -Scope It
        Assert-MockCalled Start-Process -Times 0 -Exactly -Scope It
    }

    It 'fails without repeated launch when Desktop is missing' {
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 1; Output = 'engine unavailable' } }
        Mock Resolve-ZhkuDockerDesktopExecutable { throw 'Docker Desktop executable was not found.' }
        { Ensure-ZhkuDocker -TimeoutSeconds 1 } | Should Throw 'not found'
        Assert-MockCalled Start-Process -Times 0 -Exactly -Scope It
    }

    It 'checks the resolved Desktop path again before launch' {
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 1; Output = 'engine unavailable' } }
        Mock Test-Path { $false }
        { Ensure-ZhkuDocker -TimeoutSeconds 1 } | Should Throw 'no longer available'
        Assert-MockCalled Start-Process -Times 0 -Exactly -Scope It
    }

    It 'honors a single readiness deadline and launches only once' {
        Mock Invoke-ZhkuDocker { [pscustomobject]@{ ExitCode = 1; Output = 'engine unavailable' } }
        $clock = [Diagnostics.Stopwatch]::StartNew()
        { Ensure-ZhkuDocker -TimeoutSeconds 1 -PollIntervalMilliseconds 1 } | Should Throw 'time budget'
        $clock.Elapsed.TotalSeconds -lt 3 | Should Be $true
        Assert-MockCalled Start-Process -Times 1 -Exactly -Scope It
    }
}

Describe 'Zhku Docker fixed local context' {
    BeforeEach {
        Mock Resolve-ZhkuDockerExecutable { 'C:\Docker\docker.exe' }
        Mock Invoke-ZhkuDockerProcess {
            if ($Arguments -contains 'context') { return [pscustomobject]@{ ExitCode = 0; Output = '"npipe:////./pipe/dockerDesktopLinuxEngine"' } }
            [pscustomobject]@{ ExitCode = 0; Output = 'fixture result' }
        }
    }

    It 'validates a local endpoint then forces desktop-linux on the operation' {
        (Invoke-ZhkuDocker -Arguments @('info')).Output | Should Be 'fixture result'
        Assert-MockCalled Invoke-ZhkuDockerProcess -Times 2 -Exactly -Scope It
        Assert-MockCalled Invoke-ZhkuDockerProcess -Times 1 -Exactly -Scope It -ParameterFilter { $Arguments[0] -eq '--context' -and $Arguments[1] -eq 'desktop-linux' -and $Arguments[2] -eq 'info' }
    }

    It 'rejects remote context endpoints before the requested operation' {
        Mock Invoke-ZhkuDockerProcess { [pscustomobject]@{ ExitCode = 0; Output = '"tcp://remote.example:2375"' } }
        { Invoke-ZhkuDocker -Arguments @('info') } | Should Throw 'remote engines are not permitted'
        Assert-MockCalled Invoke-ZhkuDockerProcess -Times 1 -Exactly -Scope It
    }

    It 'rejects caller endpoint overrides before starting any process' {
        { Invoke-ZhkuDocker -Arguments @('--host=tcp://remote.example:2375', 'info') } | Should Throw 'endpoint overrides'
        Assert-MockCalled Invoke-ZhkuDockerProcess -Times 0 -Exactly -Scope It
    }

    It 'does not include raw CLI output in access-denied exceptions' {
        Mock Invoke-ZhkuDockerProcess { [pscustomobject]@{ ExitCode = 1; Output = 'permission denied SYNTHETIC_PRIVATE_MARKER' } }
        $message = ''
        try { Invoke-ZhkuDocker -Arguments @('info') } catch { $message = $_.Exception.Message }
        $message | Should Match 'access denied'
        $message | Should Not Match 'SYNTHETIC_PRIVATE_MARKER'
    }

    It 'returns a sanitized unavailable result when the local context is not registered' {
        Mock Invoke-ZhkuDockerProcess { [pscustomobject]@{ ExitCode = 1; Output = 'missing context SYNTHETIC_PRIVATE_MARKER' } }
        $result = Invoke-ZhkuDocker -Arguments @('info')
        $result.ExitCode | Should Be 1
        $result.Output | Should Not Match 'SYNTHETIC_PRIVATE_MARKER'
    }
}

Describe 'Zhku Windows native argument encoding' {
    It 'quotes an empty argument exactly' { ConvertTo-ZhkuWindowsArgument '' | Should Be '""' }
    It 'quotes whitespace exactly' { ConvertTo-ZhkuWindowsArgument 'two words' | Should Be '"two words"' }
    It 'escapes an embedded quote exactly' { ConvertTo-ZhkuWindowsArgument 'a"b' | Should Be '"a\"b"' }
    It 'doubles a trailing backslash inside quotes exactly' { ConvertTo-ZhkuWindowsArgument 'C:\space path\' | Should Be '"C:\space path\\"' }
    It 'does not double unquoted backslashes' { ConvertTo-ZhkuWindowsArgument 'C:\plain\' | Should Be 'C:\plain\' }
}

Describe 'Zhku bounded native process boundary without Docker' {
    BeforeEach {
        $script:fakeCli = Join-Path $TestDrive ('fixture CLI 空格 ' + [guid]::NewGuid().ToString('N') + '.exe')
        $typeName = 'ZhkuDockerFixture' + [guid]::NewGuid().ToString('N')
        $source = 'public class ' + $typeName + ' { public static int Main(string[] args) { System.Console.OutputEncoding = new System.Text.UTF8Encoding(false); if (args.Length == 1 && args[0] == "__sleep__") { System.Threading.Thread.Sleep(5000); return 0; } foreach (string s in args) { System.Console.WriteLine(System.Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes(s))); } return 0; } }'
        Add-Type -TypeDefinition $source -OutputAssembly $script:fakeCli -OutputType ConsoleApplication
    }

    It 'roundtrips spaces quotes empty values unicode and trailing backslashes' {
        [IO.File]::Exists($script:fakeCli) | Should Be $true
        $values = @('simple', 'two words', '', '中文目录', 'quote"inside', 'C:\space path\', '\\server\share\', 'a\"b')
        try {
            $result = Invoke-ZhkuDockerProcess -Executable $script:fakeCli -Arguments $values -TimeoutSeconds 5
        } catch {
            if ($_.Exception.Message -match 'native error code: 5\.') {
                Set-TestInconclusive -Message 'UNAVAILABLE: host denied execution of the generated native fixture (code 5); native roundtrip is not verified.'
            }
            throw
        }
        $result.ExitCode | Should Be 0
        $decoded = @($result.Output -split '\r?\n' | ForEach-Object { [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($_)) })
        $decoded.Count | Should Be $values.Count
        for ($index = 0; $index -lt $values.Count; $index++) { $decoded[$index] | Should Be $values[$index] }
    }

    It 'bounds a hung owned executable without Docker access' {
        $clock = [Diagnostics.Stopwatch]::StartNew()
        $failure = ''
        try {
            Invoke-ZhkuDockerProcess -Executable $script:fakeCli -Arguments @('__sleep__') -TimeoutSeconds 1 | Out-Null
        } catch {
            if ($_.Exception.Message -match 'native error code: 5\.') {
                Set-TestInconclusive -Message 'UNAVAILABLE: host denied execution of the generated native fixture (code 5); native timeout is not verified.'
            }
            $failure = $_.Exception.Message
        }
        $failure | Should Match 'timed out'
        $clock.Elapsed.TotalSeconds -lt 3 | Should Be $true
    }
}
