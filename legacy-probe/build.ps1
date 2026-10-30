param([string]$PacketEvents = "$PSScriptRoot\..\build\libs\packetevents-spigot-2.14.0.jar")
$ErrorActionPreference = 'Stop'
$jdk = 'C:\Program Files\Java\jdk-17\bin'
$bukkit = Join-Path $env:USERPROFILE '.m2\repository\org\bukkit\craftbukkit\1.7.10-R0.1-SNAPSHOT\craftbukkit-1.7.10-R0.1-SNAPSHOT.jar'
$out = Join-Path $PSScriptRoot 'build'
Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes" | Out-Null
$sources = @(Get-ChildItem "$PSScriptRoot\plugin\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& "$jdk\javac.exe" --release 8 -nowarn -cp "$bukkit;$PacketEvents" -d "$out\classes" @sources
if ($LASTEXITCODE -ne 0) { throw 'probe compile failed' }
Copy-Item "$PSScriptRoot\plugin\plugin.yml" "$out\classes"
& "$jdk\jar.exe" cf "$out\LegacyProbe.jar" -C "$out\classes" .
if ($LASTEXITCODE -ne 0) { throw 'probe jar failed' }
"$out\LegacyProbe.jar"
