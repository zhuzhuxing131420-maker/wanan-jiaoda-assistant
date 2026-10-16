$ErrorActionPreference = 'Stop'
$env:ANDROID_HOME = 'D:\AndroidDev\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = 'D:\AndroidDev\gradle-cache'
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"

# Java does not automatically use the HTTP(S) proxy environment variables.
$taskProxyOptions = @()
foreach ($taskProtocol in @('http', 'https')) {
    $taskProxyValue = [Environment]::GetEnvironmentVariable($taskProtocol.ToUpper() + '_PROXY')
    if ($taskProxyValue) {
        $taskProxy = [uri]$taskProxyValue
        if ($taskProxy.Host -and $taskProxy.Port -gt 0) {
            $taskProxyOptions += "-D$taskProtocol.proxyHost=$($taskProxy.Host)"
            $taskProxyOptions += "-D$taskProtocol.proxyPort=$($taskProxy.Port)"
        }
    }
}
if ($taskProxyOptions.Count -gt 0) {
    $taskProxyOptions += '-Dhttp.nonProxyHosts=localhost|127.*|dl.google.com|maven.google.com|repo.maven.apache.org|repo1.maven.org|plugins.gradle.org|plugins-artifacts.gradle.org'
    $env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS, ($taskProxyOptions -join ' ')) | Where-Object { $_ }) -join ' '
}
