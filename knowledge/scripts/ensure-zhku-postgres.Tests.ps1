. (Join-Path $PSScriptRoot 'ZhkuPostgres.ps1')

Describe 'PostgreSQL readiness and data-preserving repair' {
    BeforeEach {
        $global:dbCalls = New-Object 'System.Collections.Generic.List[string]'
        $global:dbConfig = @{ ZHKU_DB_NAME='zhkuagent'; ZHKU_DB_USER='zhkuagent'; ZHKU_DB_PORT='54329'; ZHKU_DB_PASSWORD='fixture-only-not-a-credential' }
        $global:dbFixture = [pscustomobject]@{
            Id='fixture-id'; Config=[pscustomobject]@{
                Image='pgvector/pgvector:0.8.6-pg16-bookworm'
                Labels=@{'com.docker.compose.project'='zhkuagent'; 'com.docker.compose.service'='postgres'}
                Env=@('POSTGRES_USER=zhkuagent', 'POSTGRES_DB=zhkuagent')
            }
            HostConfig=[pscustomobject]@{
                RestartPolicy=[pscustomobject]@{Name='unless-stopped'}
                PortBindings=@{'5432/tcp'=@([pscustomobject]@{HostIp='127.0.0.1';HostPort='54329'})}
            }
            Mounts=@(
                [pscustomobject]@{Type='volume';Name='zhkuagent_zhkuagent_postgres_data';Destination='/var/lib/postgresql/data'},
                [pscustomobject]@{Type='bind';Source=(Join-Path $script:ZhkuRepoRoot 'knowledge/infra/init/001-enable-pgvector.sql');Destination='/docker-entrypoint-initdb.d/001-enable-pgvector.sql';RW=$false}
            )
            NetworkSettings=[pscustomobject]@{Ports=@{'5432/tcp'=@([pscustomobject]@{HostIp='127.0.0.1';HostPort='54329'})}}
            State=[pscustomobject]@{Running=$true;Dead=$false;OOMKilled=$false;Health=[pscustomobject]@{Status='healthy'}}
        }
        $global:dbMissing=$false; $global:dbAuthFail=$false; $global:dbVolumeMissing=$false
        $global:dbTrust=$false; $global:dbNegativeNetworkFailure=$false
        Mock Ensure-ZhkuDocker {}
        Mock Get-ZhkuDbConfiguration { $global:dbConfig }
        Mock Test-ZhkuPostgresTcp { $true }
        Mock Invoke-ZhkuDocker {
            param($Arguments, $TimeoutSeconds)
            $global:dbCalls.Add(($Arguments -join ' '))
            switch ($Arguments[0]) {
                'container' {
                    if ($global:dbMissing) { return [pscustomobject]@{ExitCode=1;Output='sensitive daemon output'} }
                    return [pscustomobject]@{ExitCode=0;Output=(ConvertTo-Json -InputObject @($global:dbFixture) -Depth 12 -Compress)}
                }
                'start' { $global:dbFixture.State.Running=$true; return [pscustomobject]@{ExitCode=0;Output='fixture-id'} }
                'volume' {
                    if ($global:dbVolumeMissing) { return [pscustomobject]@{ExitCode=1;Output='missing volume'} }
                    return [pscustomobject]@{ExitCode=0;Output='[{"Name":"zhkuagent_zhkuagent_postgres_data","Driver":"local","Labels":{"com.docker.compose.project":"zhkuagent","com.docker.compose.volume":"zhkuagent_postgres_data"}}]'}
                }
                'compose' {
                    $global:dbFixture.HostConfig.PortBindings.'5432/tcp'[0].HostPort='54329'
                    $global:dbFixture.HostConfig.RestartPolicy.Name='unless-stopped'
                    $global:dbFixture.Config.Env=@('POSTGRES_USER=zhkuagent','POSTGRES_DB=zhkuagent')
                    $global:dbFixture.Mounts[1].Source=Join-Path $script:ZhkuRepoRoot 'knowledge/infra/init/001-enable-pgvector.sql'
                    $global:dbFixture.State.Running=$true
                    return [pscustomobject]@{ExitCode=0;Output='recreated'}
                }
                'exec' {
                    if ($env:PGPASSWORD -like 'negative-probe-*') {
                        if ($global:dbNegativeNetworkFailure) { return [pscustomobject]@{ExitCode=1;Output='connection refused'} }
                        if ($global:dbTrust) { return [pscustomobject]@{ExitCode=0;Output='1'} }
                        return [pscustomobject]@{ExitCode=2;Output='FATAL: password authentication failed for user zhkuagent'}
                    }
                    if ($global:dbAuthFail) { return [pscustomobject]@{ExitCode=1;Output='fixture-only-not-a-credential'} }
                    return [pscustomobject]@{ExitCode=0;Output='1'}
                }
                default { throw 'Unexpected Docker mutation in test.' }
            }
        }
    }
    AfterEach {
        Remove-Variable dbCalls,dbConfig,dbFixture,dbMissing,dbAuthFail,dbVolumeMissing,dbTrust,dbNegativeNetworkFailure -Scope Global -ErrorAction SilentlyContinue
    }
    It 'does not restart a healthy container and authenticates read-only' {
        Ensure-ZhkuPostgres | Should Match 'authenticated=read-only'
        @($global:dbCalls | Where-Object { $_ -match '^(start|compose|volume)' }).Count | Should Be 0
        ($global:dbCalls -join "`n") | Should Match 'default_transaction_read_only=on'
        ($global:dbCalls -join "`n") | Should Not Match 'fixture-only-not-a-credential'
    }
    It 'rejects trust authentication even if SELECT succeeds with any password' {
        $global:dbTrust=$true
        { Ensure-ZhkuPostgres } | Should Throw 'did not prove password enforcement'
        @($global:dbCalls | Where-Object { $_ -match '^exec' }).Count | Should Be 1
    }
    It 'does not confuse network failure with a rejected wrong password' {
        $global:dbNegativeNetworkFailure=$true
        { Ensure-ZhkuPostgres } | Should Throw 'did not prove password enforcement'
    }
    It 'authenticates through the actual published host endpoint instead of container loopback' {
        Ensure-ZhkuPostgres | Should Match 'authenticated=read-only'
        $probes=@($global:dbCalls | Where-Object { $_ -match '^exec' })
        $probes.Count | Should Be 2
        foreach ($probe in $probes) { $probe | Should Match '-h host.docker.internal -p 54329' }
        ($probes -join "`n") | Should Not Match 'negative-probe-'
    }
    It 'starts an existing stopped container by ID once' {
        $global:dbFixture.State.Running=$false
        Ensure-ZhkuPostgres | Should Match 'ready'
        @($global:dbCalls | Where-Object { $_ -eq 'start fixture-id' }).Count | Should Be 1
    }
    It 'does not touch the database in DockerOnly mode' {
        Ensure-ZhkuPostgres -DockerOnly | Should Match 'untouched'
        $global:dbCalls.Count | Should Be 0
        Assert-MockCalled Get-ZhkuDbConfiguration -Times 0 -Exactly -Scope It
    }
    It 'does not start a stopped container in CheckOnly mode' {
        $global:dbFixture.State.Running=$false
        { Ensure-ZhkuPostgres -CheckOnly } | Should Throw 'stopped'
        @($global:dbCalls | Where-Object { $_ -match '^start' }).Count | Should Be 0
    }
    It 'does not create a database when the expected container is missing' {
        $global:dbMissing=$true
        { Ensure-ZhkuPostgres } | Should Throw 'Local Docker operation failed'
        @($global:dbCalls | Where-Object { $_ -match '^(start|compose|volume|exec)' }).Count | Should Be 0
    }
    It 'rejects a foreign project before any start or repair' {
        $global:dbFixture.Config.Labels['com.docker.compose.project']='foreign'
        { Ensure-ZhkuPostgres -RepairConfiguration } | Should Throw 'unrecognized'
        $global:dbCalls.Count | Should Be 1
    }
    It 'refuses an unexpected data volume even with repair enabled' {
        $global:dbFixture.Mounts[0].Name='other-data'
        { Ensure-ZhkuPostgres -RepairConfiguration } | Should Throw 'unexpected database volume'
        $global:dbCalls.Count | Should Be 1
    }
    It 'reports random port drift rather than accepting an unrelated listener' {
        $global:dbFixture.HostConfig.PortBindings.'5432/tcp'[0].HostPort='23031'
        { Ensure-ZhkuPostgres } | Should Throw 'port mapping'
        Assert-MockCalled Test-ZhkuPostgresTcp -Times 0 -Exactly -Scope It
    }
    It 'rejects a public binding instead of treating it as local' {
        $global:dbFixture.HostConfig.PortBindings.'5432/tcp'[0].HostIp='0.0.0.0'
        { Ensure-ZhkuPostgres } | Should Throw 'port mapping'
    }
    It 'reports an obsolete worktree SQL bind without starting it' {
        $global:dbFixture.Mounts[1].Source='D:/removed-worktree/001-enable-pgvector.sql'
        $global:dbFixture.State.Running=$false
        { Ensure-ZhkuPostgres } | Should Throw 'SQL init bind path'
        @($global:dbCalls | Where-Object { $_ -match '^start' }).Count | Should Be 0
    }
    It 'repairs drift with the existing volume and fixed Compose project only' {
        $global:dbFixture.HostConfig.PortBindings.'5432/tcp'[0].HostPort='23031'
        $originalVolume=$global:dbFixture.Mounts[0].Name
        Ensure-ZhkuPostgres -RepairConfiguration | Should Match 'port=54329'
        $global:dbFixture.Mounts[0].Name | Should Be $originalVolume
        @($global:dbCalls | Where-Object { $_ -match '^compose' }).Count | Should Be 1
        ($global:dbCalls -join "`n") | Should Match '--project-name zhkuagent'
        ($global:dbCalls -join "`n") | Should Match '--pull never'
        ($global:dbCalls -join "`n") | Should Not Match 'down|volume rm|container rm|system prune'
    }
    It 'fails before recreation if existing volume is unavailable' {
        $global:dbFixture.HostConfig.RestartPolicy.Name='no'; $global:dbVolumeMissing=$true
        { Ensure-ZhkuPostgres -RepairConfiguration } | Should Throw
        @($global:dbCalls | Where-Object { $_ -match '^compose' }).Count | Should Be 0
    }
    It 'fails before recreation if existing database authentication fails' {
        $global:dbFixture.HostConfig.RestartPolicy.Name='no'; $global:dbAuthFail=$true
        { Ensure-ZhkuPostgres -RepairConfiguration } | Should Throw 'Local Docker operation failed'
        @($global:dbCalls | Where-Object { $_ -match '^compose' }).Count | Should Be 0
    }
    It 'does not expose a secret or leave PGPASSWORD in the caller on failure' {
        $before=[Environment]::GetEnvironmentVariable('PGPASSWORD','Process')
        $global:dbAuthFail=$true
        try { Ensure-ZhkuPostgres } catch { $_.Exception.Message | Should Not Match 'fixture-only-not-a-credential' }
        [Environment]::GetEnvironmentVariable('PGPASSWORD','Process') | Should Be $before
    }
    It 'rejects unhealthy status immediately' {
        $global:dbFixture.State.Health.Status='unhealthy'
        { Ensure-ZhkuPostgres } | Should Throw 'unhealthy'
    }
    It 'enforces a finite wait when the correct host port is closed' {
        Mock Test-ZhkuPostgresTcp { $false }
        $clock=[Diagnostics.Stopwatch]::StartNew()
        { Ensure-ZhkuPostgres -TimeoutSeconds 1 -PollIntervalMilliseconds 1 } | Should Throw 'timed out'
        ($clock.Elapsed.TotalSeconds -lt 4) | Should Be $true
    }
    It 'does not initialize a stopped drifted container during repair' {
        $global:dbFixture.State.Running=$false
        $global:dbFixture.HostConfig.RestartPolicy.Name='no'
        { Ensure-ZhkuPostgres -RepairConfiguration } | Should Throw 'without prior authentication'
        @($global:dbCalls | Where-Object { $_ -match '^(compose|start)' }).Count | Should Be 0
    }
    It 'rejects an actual port mapping that disagrees with HostConfig' {
        $global:dbFixture.NetworkSettings.Ports.'5432/tcp'[0].HostPort='23031'
        { Ensure-ZhkuPostgres } | Should Throw 'Actual published port'
        Assert-MockCalled Test-ZhkuPostgresTcp -Times 0 -Exactly -Scope It
    }
    It 'normalizes only the known local Docker Desktop bind path form' {
        (ConvertTo-ZhkuBindPath '/run/desktop/mnt/host/d/alldesk/file.sql') | Should Be 'd:/alldesk/file.sql'
        (ConvertTo-ZhkuBindPath 'D:\alldesk\file.sql') | Should Be 'D:/alldesk/file.sql'
        (ConvertTo-ZhkuBindPath '/remote/d/alldesk/file.sql') | Should Be '/remote/d/alldesk/file.sql'
    }
    It 'rejects a foreign volume label before recreation' {
        $global:dbFixture.HostConfig.RestartPolicy.Name='no'
        Mock Invoke-ZhkuDocker {
            param($Arguments)
            if ($Arguments[0] -eq 'volume') { return [pscustomobject]@{ExitCode=0;Output='[{"Name":"zhkuagent_zhkuagent_postgres_data","Driver":"local","Labels":{"com.docker.compose.project":"foreign"}}]'} }
            if ($Arguments[0] -eq 'exec') {
                if ($env:PGPASSWORD -like 'negative-probe-*') { return [pscustomobject]@{ExitCode=2;Output='FATAL: password authentication failed for user zhkuagent'} }
                return [pscustomobject]@{ExitCode=0;Output='1'}
            }
            if ($Arguments[0] -eq 'container') { return [pscustomobject]@{ExitCode=0;Output=(ConvertTo-Json -InputObject @($global:dbFixture) -Depth 12 -Compress)} }
            throw 'Unexpected mutation in foreign volume test.'
        }
        { Ensure-ZhkuPostgres -RepairConfiguration } | Should Throw 'Original volume unavailable'
    }
    It 'does not combine check-only and repair' {
        { Ensure-ZhkuPostgres -CheckOnly -RepairConfiguration } | Should Throw 'cannot repair'
        $global:dbCalls.Count | Should Be 0
    }
}
