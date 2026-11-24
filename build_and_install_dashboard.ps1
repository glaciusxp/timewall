$ErrorActionPreference = 'Stop'

Push-Location $PSScriptRoot
try {
    & .\build.ps1 -Dashboard
    adb install -r .\build\TimeWall-Dashboard.apk
    if ($LASTEXITCODE -ne 0) {
        throw "ADB installation failed ($LASTEXITCODE)"
    }
} finally {
    Pop-Location
}
