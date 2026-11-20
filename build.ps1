param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [switch]$Dashboard,
    [switch]$All,
    [switch]$UpdateLocks
)
$ErrorActionPreference = 'Stop'
if ($Sdk) { $env:ANDROID_HOME = (Resolve-Path -LiteralPath $Sdk).Path }
if ($Jdk) { $env:JAVA_HOME = (Resolve-Path -LiteralPath $Jdk).Path }
$projects = if ($All) { @('timewall-app', 'timewall-diagnostics') }
            elseif ($Dashboard) { @('timewall-app') } else { @('timewall-diagnostics') }
foreach ($project in $projects) {
    $projectPath = Join-Path $PSScriptRoot $project
    $arguments = @('--no-daemon', '--console=plain')
    if ($UpdateLocks) { $arguments += '--write-locks' }
    if ($env:HTTPS_PROXY) {
        $proxyAddress = [uri]$env:HTTPS_PROXY
        if ($proxyAddress.Scheme -eq 'http') {
            $arguments += "-Dhttps.proxyHost=$($proxyAddress.Host)", "-Dhttps.proxyPort=$($proxyAddress.Port)"
            $arguments += "-Dhttp.proxyHost=$($proxyAddress.Host)", "-Dhttp.proxyPort=$($proxyAddress.Port)"
        }
    }
    & (Join-Path $projectPath 'gradlew.bat') -p $projectPath @arguments testDebugUnitTest lintRelease checkJavaFormat assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed for $project ($LASTEXITCODE)" }
}
& (Join-Path $PSScriptRoot 'tools\package.ps1') -Projects $projects
