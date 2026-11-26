# TimeWall Dashboard 0.95

Progetto Android autonomo, Java 8 / JDK 17, minSdk 19, targetSdk 36.
Importare questa directory direttamente in Android Studio.

```powershell
.\gradlew.bat testCompleteDebugUnitTest lintCompleteRelease assembleCompleteRelease
```

`checkJavaFormat` verifica lo stile Java; `formatJava` lo applica esplicitamente.

SDK richiesto: Platform 37.0, Build Tools 36.0.0. Specificare `ANDROID_HOME` oppure
un `local.properties` locale. Il wrapper Gradle 9.9.2 verifica il checksum della
distribuzione. Il plugin Android è fissato alla versione 9.4.1.

Per firmare con la chiave precedente, conservare `.signing/timewall.jks` oppure
impostare `TIMEWALL_KEYSTORE`, `TIMEWALL_STORE_PASSWORD`, `TIMEWALL_KEY_ALIAS` e
`TIMEWALL_KEY_PASSWORD`. Il valore di fallback corrisponde alla chiave debug storica
del progetto; nessuna nuova chiave viene generata automaticamente. Chiavi e
configurazioni locali sono escluse dai sorgenti consegnati.

APK: `build/outputs/apk/complete/release/timewall-app-complete-release.apk`.
Test: `build/reports/tests/testCompleteDebugUnitTest/index.html`.
Lint: `build/reports/lint-results-completeRelease.html`.

La diagnostica non viene compilata né impacchettata con questa applicazione.
Vedere `docs/architecture.md`, `docs/tls-kitkat.md`, `docs/testing.md` e
`docs/migration.md` e `docs/android-compatibility.md`. La versione 0.95 migra
il target Android mantenendo le API minime e il layout orizzontale esistente.

## Varianti gratuite in collaudo

- Google Play: bundlePlayFreeRelease produce l'AAB. Per il collaudo ADB usare
  assemblePlayFreeRelease. L'ID Play è it.timewall.app.
- Sito: assembleSiteFreeWebsite produce l'APK release e rimuove il codice
  inutilizzato tramite R8. L'ID Play è it.timewall.dashboard.sitefree.
- La variante complete mantiene l'ID storico e lo script di installazione
  dalla radice del repository.

La prima apertura di un profilo mancante copia le preferenze comuni accessibili
nello stesso spazio dati. La Free deriva il primo calendario valido, oltre a lingua,
formati data/ora, font e tema compatibili. Con gli ID di prova distinti, Android
isola le installazioni. La disinstallazione rimuove le configurazioni locali.
