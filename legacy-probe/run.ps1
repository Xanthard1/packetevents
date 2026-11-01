param(
    [ValidateSet('1.7.2', '1.7.5', '1.7.9', '1.7.10')][string]$Server = '1.7.10',
    [int]$Seconds = 20,
    [string[]]$Extra = @(),
    [string]$PacketEvents = "$PSScriptRoot\..\build\libs\packetevents-spigot-2.14.0.jar"
)
$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$pe = (Resolve-Path $PacketEvents).Path
$probe = & "$PSScriptRoot\build.ps1"
$protocol = if ($Server -in '1.7.2', '1.7.5') { 4 } else { 5 }
$ports = @{ '1.7.2' = 25572; '1.7.5' = 25575; '1.7.9' = 25579; '1.7.10' = 25570 }
$plugins = @($pe, $probe) + $Extra
$job = Start-Job -ScriptBlock {
    param($workspace, $server, $plugins, $seconds)
    & "$workspace\scripts\legacy-server.ps1" -Server $server -Plugins $plugins -Keep -Seconds $seconds
} -ArgumentList $workspace, $Server, $plugins, ($Seconds + 15)
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $ports[$Server]); $client.Close(); break } catch { Start-Sleep -Seconds 1 }
}
Start-Sleep -Seconds 2
$java = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$second = Start-Process $java -ArgumentList "`"$PSScriptRoot\bot\LegacyBot.java`"", '127.0.0.1', $ports[$Server], $protocol, $Seconds, 'SecondBot' `
        -NoNewWindow -PassThru -RedirectStandardOutput "$PSScriptRoot\build\second-bot.txt"
& $java "$PSScriptRoot\bot\LegacyBot.java" 127.0.0.1 $ports[$Server] $protocol $Seconds LegacyBot
$second.WaitForExit()
Get-Content "$PSScriptRoot\build\second-bot.txt"
Wait-Job $job | Out-Null
Receive-Job $job | Out-String | Set-Content "$PSScriptRoot\build\server-$Server.log"
"$PSScriptRoot\build\server-$Server.log"
