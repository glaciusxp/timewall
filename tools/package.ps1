param([string[]]$Projects = @('timewall-app', 'timewall-diagnostics'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$workspace = Split-Path $PSScriptRoot
$output = Join-Path $workspace 'build'
New-Item -ItemType Directory -Path $output -Force | Out-Null
foreach ($project in $Projects) {
    if ($project -notin @('timewall-app', 'timewall-diagnostics')) { throw 'Unknown project' }
    $source = Join-Path $workspace $project
    $name = if ($project -eq 'timewall-app') { 'TimeWall-Dashboard' } else { 'TimeWall-Diagnostica' }
    $apk = Join-Path $source "build\outputs\apk\release\$project-release.apk"
    if (!(Test-Path -LiteralPath $apk)) { throw "Build the project first: $project" }
    Copy-Item -LiteralPath $apk -Destination (Join-Path $output "$name.apk") -Force
    $archivePath = Join-Path $output "$name-Sorgenti.zip"
    $stream = [System.IO.File]::Open($archivePath, [System.IO.FileMode]::Create)
    $archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        # Allowlist excludes signing keys, device data, caches and local settings.
        foreach ($entry in @('src','design','docs','verification','gradle','README.md','build.gradle','settings.gradle',
                             'gradle.properties','gradle.lockfile','gradlew','gradlew.bat','.gitignore')) {
            $path = Join-Path $source $entry
            if (!(Test-Path -LiteralPath $path)) { continue }
            $files = if (Test-Path -LiteralPath $path -PathType Container) {
                Get-ChildItem -LiteralPath $path -Recurse -File
            } else { Get-Item -LiteralPath $path }
            foreach ($file in $files) {
                $relative = $file.FullName.Substring($source.Length + 1).Replace('\','/')
                [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                    $archive, $file.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
            }
        }
    } finally { $archive.Dispose(); $stream.Dispose() }
    Get-Item -LiteralPath (Join-Path $output "$name.apk"), $archivePath | Select-Object Name, Length
}
