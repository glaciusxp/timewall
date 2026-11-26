$ErrorActionPreference = 'Stop'

$project = 'C:\workspace\android-apps\timewall\timewall-app'
$keystore = 'C:\workspace\android-apps\timewall-service\timewall-play-upload.p12'
$wrapper = Join-Path $project 'gradlew.bat'

foreach ($path in @($wrapper, $keystore)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "File mancante: $path"
    }
}

$password = Read-Host 'Password del keystore Play' -AsSecureString
$variables = @('TIMEWALL_KEYSTORE', 'TIMEWALL_KEY_ALIAS', 'TIMEWALL_STORE_PASSWORD', 'TIMEWALL_KEY_PASSWORD')
$previous = @{}
foreach ($name in $variables) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

Push-Location -LiteralPath $project
try {
    $env:TIMEWALL_KEYSTORE = $keystore
    $env:TIMEWALL_KEY_ALIAS = 'timewall-upload'
    $env:TIMEWALL_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password
    $env:TIMEWALL_KEY_PASSWORD = $env:TIMEWALL_STORE_PASSWORD

    & $wrapper --no-daemon bundlePlayFreeRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Build del bundle fallita (codice $LASTEXITCODE)."
    }

    Get-Item -LiteralPath (Join-Path $project 'build\outputs\bundle\playFreeRelease\timewall-app-playFree-release.aab') |
        Select-Object -ExpandProperty FullName
} finally {
    foreach ($name in $variables) {
        if ($null -eq $previous[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            Set-Item -LiteralPath "Env:$name" -Value $previous[$name]
        }
    }
    $password.Dispose()
    Pop-Location
}