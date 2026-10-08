function Get-ZhkuNodeToolchain {
    $nodeCommand=Get-Command node.exe -ErrorAction SilentlyContinue
    $directories=@()
    if($nodeCommand){$directories+=Split-Path -Parent $nodeCommand.Source}
    $directories+=Join-Path $env:ProgramFiles 'nodejs'
    foreach($directory in $directories | Select-Object -Unique){
        $node=Join-Path $directory 'node.exe'; $npm=Join-Path $directory 'npm.cmd'
        if((Test-Path -LiteralPath $node -PathType Leaf) -and (Test-Path -LiteralPath $npm -PathType Leaf)){
            # npm.cmd仍会调用node；只找到npm文件而不补本次PATH，会在普通终端构建时失败。
            return [pscustomobject]@{Directory=$directory;Node=$node;Npm=$npm}
        }
    }
    throw '未找到包含node.exe与npm.cmd的已安装Node工具，请安装Node或配置本机PATH；不自动下载。'
}
