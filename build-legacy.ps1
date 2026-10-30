param([string[]]$Tasks = @(':spigot:shadowJar'))
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
$env:PATH = "C:\Program Files\Git\cmd;$env:PATH"
$toolchains = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot,C:\Program Files\Java\jdk-21.0.12,C:\Program Files\Java\jdk1.8.0_202'
& "$PSScriptRoot\gradlew.bat" @Tasks "-Porg.gradle.java.installations.paths=$toolchains" --console=plain
if ($LASTEXITCODE -ne 0) { throw "gradle $($Tasks -join ' ') failed" }
