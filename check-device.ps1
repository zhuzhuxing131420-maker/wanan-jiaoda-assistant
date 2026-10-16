. "$PSScriptRoot\dev-env.ps1"
& "$env:ANDROID_HOME\platform-tools\adb.exe" start-server
if ($LASTEXITCODE -ne 0) { throw 'ADB server failed to start.' }
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
if ($LASTEXITCODE -ne 0) { throw 'ADB device check failed.' }
