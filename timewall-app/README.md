# TimeWall Dashboard 0.95

Progetto Android autonomo, Java 8 / JDK 17, minSdk 19, targetSdk 36.
Importare questa directory direttamente in Android Studio.

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

`checkJavaFormat` verifica lo stile Java; `formatJava` lo applica esplicitamente.

SDK richiesto: Platform 37.0, Build Tools 36.0.0. Specificare `ANDROID_HOME` oppure
un `local.properties` locale. Il wrapper Gradle 9.8.0 verifica il checksum della
distribuzione. Il plugin Android è fissato alla versione 9.4.1.

Per firmare con la chiave precedente, conservare `.signing/timewall.jks` oppure
impostare `TIMEWALL_KEYSTORE`, `TIMEWALL_STORE_PASSWORD`, `TIMEWALL_KEY_ALIAS` e
`TIMEWALL_KEY_PASSWORD`. Il valore di fallback corrisponde alla chiave debug storica
del progetto; nessuna nuova chiave viene generata automaticamente. Chiavi e
configurazioni locali sono escluse dai sorgenti consegnati.

APK: `build/outputs/apk/release/timewall-app-release.apk`.
Test: `build/reports/tests/testDebugUnitTest/index.html`.
Lint: `build/reports/lint-results-release.html`.

La diagnostica non viene compilata né impacchettata con questa applicazione.
Vedere `docs/architecture.md`, `docs/tls-kitkat.md`, `docs/testing.md` e
`docs/migration.md` e `docs/android-compatibility.md`. La versione 0.95 migra
il target Android mantenendo le API minime e il layout orizzontale esistente.
