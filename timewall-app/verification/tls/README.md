# Device TLS regression checks

The helper instruments the installed production APK using the same signing key.
It changes no calendar or setting, never exports preference values, and uses fixed
public weather inputs plus a public Google holidays calendar for parallel checks.
The UI check launches the real dashboard: its normal configured refreshes may run.
It also creates a disposable weather controller using only the helper's preferences
to exercise provider-load failure, old-data retention and retry. No system package
is disabled and no device network setting is changed.

Build/install the corresponding official release first. On Windows with JDK 17,
Android platform 37.0 and build-tools 36.0.0:

```powershell
$env:JAVA_HOME = 'path-to-jdk-17'
$env:ANDROID_HOME = 'path-to-android-sdk'
.\verification\tls\run.ps1 -Serial 'device-serial'
```

Use TIMEWALL_KEYSTORE, TIMEWALL_KEY_ALIAS, TIMEWALL_STORE_PASSWORD and
TIMEWALL_KEY_PASSWORD for a different signing configuration. Historical defaults
match this project's local builds. Keys are not in this source ZIP.

Results go to `build/tls-verification/{cold,core,ui}.txt`, outside source archives.
`cold` runs concurrent forecast/geocoding/calendar paths in a fresh process;
`core` verifies TLS, global state, HTTP failures and safe logging;
`ui` verifies actual dashboard behavior and disposable failure handling.
The script removes its temporary instrumentation package in finally. It never
uninstalls or clears the official app. No screenshots are taken.

HTTP errors/timeouts/body bounds use in-memory HTTPS doubles; live TLS checks
use the real provider. The probe uses reflection for private controller/transport
checks so it adds no test hooks to production code. JSONException retaining a
synthetic payload is expected: WeatherLog must not emit that payload or forward
the Throwable to Android's logger.

This is a regression test, not a certification of the legacy native TLS library.
Play Services support for KitKat ended in 2023. An incompatible provider must fail
closed. Tests on other Android versions remain separate work.
