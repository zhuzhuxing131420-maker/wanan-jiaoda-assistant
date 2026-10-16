. "$PSScriptRoot\dev-env.ps1"
$taskProject = Join-Path $PSScriptRoot 'WanAnJiaoDa-Handoff\source\WanAnJiaoDa'
$taskLink = 'D:\AndroidDev\projects\WanAnJiaoDa'
if (-not (Test-Path -LiteralPath $taskLink)) {
    New-Item -ItemType Directory -Force -Path 'D:\AndroidDev\projects' | Out-Null
    New-Item -ItemType Junction -Path $taskLink -Target $taskProject | Out-Null
}
$taskLinkItem = Get-Item -LiteralPath $taskLink
if ($taskLinkItem.LinkType -ne 'Junction' -or
    -not [IO.Path]::GetFullPath($taskLinkItem.Target).Equals([IO.Path]::GetFullPath($taskProject), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The Android project link points to a different workspace.'
}
Push-Location $taskLink
try {
    & .\gradlew.bat assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)." }
    $taskApk = Join-Path $taskProject 'app\build\outputs\apk\debug\app-debug.apk'
    New-Item -ItemType Directory -Force -Path 'D:\AndroidDev\output' | Out-Null
    Copy-Item -LiteralPath $taskApk -Destination 'D:\AndroidDev\output\WanAnJiaoDa-debug.apk' -Force
    Write-Output $taskApk
    Write-Output 'D:\AndroidDev\output\WanAnJiaoDa-debug.apk'
} finally {
    Pop-Location
}
