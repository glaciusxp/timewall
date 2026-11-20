param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Serial,
    [switch]$BuildOnly
)
$ErrorActionPreference='Stop'
if(!$Sdk -or !$Jdk){throw 'Set ANDROID_HOME and JAVA_HOME (JDK 17)'}
$project=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$output=Join-Path $project 'build\tls-verification'
$classes=Join-Path $project 'build\intermediates\javac\release\compileReleaseJavaWithJavac\classes'
$android=Join-Path $Sdk 'platforms\android-37.0\android.jar'
$tools=Join-Path $Sdk 'build-tools\36.0.0'
$adb=Join-Path $Sdk 'platform-tools\adb.exe'
$deviceArgs=if($Serial){@('-s',$Serial)}else{@()}
New-Item -ItemType Directory -Force -Path "$output\classes","$output\dex" | Out-Null
& "$Jdk\bin\javac.exe" -encoding UTF-8 --release 8 -cp "$android;$classes" -d "$output\classes" "$PSScriptRoot\TlsProbe.java"
if($LASTEXITCODE){throw 'javac failed'}
& "$Jdk\bin\jar.exe" cf "$output\classes.jar" -C "$output\classes" .
$env:JAVA_HOME=$Jdk
& "$tools\d8.bat" --min-api 19 --lib $android --classpath $classes --output "$output\dex" "$output\classes.jar"
if($LASTEXITCODE){throw 'd8 failed'}
& "$tools\aapt2.exe" link -o "$output\unsigned.apk" --manifest "$PSScriptRoot\AndroidManifest.xml" -I $android
if($LASTEXITCODE){throw 'aapt2 failed'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[System.IO.Compression.ZipFile]::Open("$output\unsigned.apk",'Update')
try {[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,"$output\dex\classes.dex",'classes.dex') | Out-Null}finally{$archive.Dispose()}
& "$tools\zipalign.exe" -f 4 "$output\unsigned.apk" "$output\aligned.apk"
$key=if($env:TIMEWALL_KEYSTORE){$env:TIMEWALL_KEYSTORE}else{Join-Path $project '.signing\timewall.jks'}
$alias=if($env:TIMEWALL_KEY_ALIAS){$env:TIMEWALL_KEY_ALIAS}else{'androiddebugkey'}
if(!$env:TIMEWALL_STORE_PASSWORD){$env:TIMEWALL_STORE_PASSWORD='android'}
if(!$env:TIMEWALL_KEY_PASSWORD){$env:TIMEWALL_KEY_PASSWORD='android'}
& "$Jdk\bin\java.exe" -jar "$tools\lib\apksigner.jar" sign --ks $key --ks-key-alias $alias --ks-pass env:TIMEWALL_STORE_PASSWORD --key-pass env:TIMEWALL_KEY_PASSWORD --out "$output\probe.apk" "$output\aligned.apk"
if($LASTEXITCODE){throw 'Signing failed'}
if($BuildOnly){return}
try {
    & $adb @deviceArgs install -r "$output\probe.apk"
    if($LASTEXITCODE){throw 'Probe installation failed'}
    foreach($mode in @('cold','core','ui')){
        & $adb @deviceArgs shell am instrument -w -e mode $mode it.timewall.audit/it.timewall.audit.TlsProbe | Tee-Object -FilePath "$output\$mode.txt"
        if($LASTEXITCODE){throw "Instrumentation failed: $mode"}
        $result=Get-Content -LiteralPath "$output\$mode.txt" -Raw
        if($result -match '(?m)^FAIL ' -or $result -match 'INSTRUMENTATION_FAILED|Process crashed' -or $result -notmatch '(?m)^PASS '){throw "Audit checks failed: $mode"}
    }
} finally {
    # Remove only the disposable helper; never uninstall or clear the target app.
    & $adb @deviceArgs uninstall it.timewall.audit
}
