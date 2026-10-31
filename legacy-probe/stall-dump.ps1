param([string[]]$Extra = @(), [int]$Runs = 8, [switch]$NoProbe)
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$pe = (Resolve-Path "$PSScriptRoot\..\build\libs\packetevents-spigot-2.14.0.jar").Path
$probe = & "$PSScriptRoot\build.ps1"
$java = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$jstack = 'C:\Program Files\Java\jdk1.8.0_202\bin\jstack.exe'
$bot = "$PSScriptRoot\bot\LegacyBot.java"
for ($run = 1; $run -le $Runs; $run++) {
    $job = Start-Job -ScriptBlock {
        param($workspace, $plugins)
        & "$workspace\scripts\legacy-server.ps1" -Server 1.7.10 -Plugins $plugins -Keep -Seconds 12
    } -ArgumentList $workspace, ($(if ($NoProbe) { @($pe) } else { @($pe, $probe) }) + $Extra)
    $deadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $deadline) {
        $client = New-Object System.Net.Sockets.TcpClient
        try { $client.Connect('127.0.0.1', 25570); $client.Close(); break } catch { Start-Sleep -Seconds 1 }
    }
    Start-Sleep -Seconds 2
    $second = Start-Process $java -ArgumentList "`"$bot`"", '127.0.0.1', '25570', '5', '6', 'SecondBot' -NoNewWindow -PassThru `
            -RedirectStandardOutput "$env:TEMP\stall-b2.txt" -RedirectStandardError "$env:TEMP\stall-b2err.txt"
    $first = Start-Process $java -ArgumentList "`"$bot`"", '127.0.0.1', '25570', '5', '6', 'LegacyBot' -NoNewWindow -PassThru `
            -RedirectStandardOutput "$env:TEMP\stall-b1.txt" -RedirectStandardError "$env:TEMP\stall-b1err.txt"
    Start-Sleep -Seconds 4
    $server = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*craftbukkit-1.7.10*' } | Select-Object -First 1
    $dump = if ($server) { & $jstack $server.ProcessId 2>&1 | Out-String } else { 'no server process' }
    $first.WaitForExit(); $second.WaitForExit()
    Wait-Job $job | Out-Null; Remove-Job $job
    $logins = @(Select-String -Path 'C:\Development\servers\legacy-1.7\1.7.10\console.log' -Pattern 'logged in with entity').Count
    "run $run logins=$logins"
    if ($logins -lt 2) {
        $dump | Set-Content "$PSScriptRoot\build\stall-dump.txt"
        "$PSScriptRoot\build\stall-dump.txt"
        return
    }
}
