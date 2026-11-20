# TimeWall Diagnostica 1.11

Applicazione indipendente dalla dashboard: sorgenti, risorse, wrapper e suite
di test propri. Non aggiungere dipendenze a `timewall-app`.

Requisiti: JDK 17, Android SDK Platform 37.0, Build Tools 36.0.0.
Gradle 9.8.0 con checksum e Android Gradle Plugin 9.4.1 fissati.
Java 8, minSdk 19, targetSdk 28.

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

`checkJavaFormat` controlla lo stile Java AOSP; `formatJava` lo applica.

I test verificano il dump integrale degli eventi iCal e l'adattatore TLS senza
accesso alla rete. Le prove reali dei provider restano pulsanti manuali dell'app.
Le URL segrete non devono essere inserite in fixture o log persistenti.

APK: `build/outputs/apk/release/timewall-diagnostics-release.apk`.
Per la firma usare la chiave storica in `.signing/timewall.jks`, esclusa dagli ZIP,
oppure `TIMEWALL_KEYSTORE`, `TIMEWALL_STORE_PASSWORD`, `TIMEWALL_KEY_ALIAS`,
`TIMEWALL_KEY_PASSWORD`. I test non richiedono la chiave.

Package: `ui` per la schermata, `calendar` per la lettura raw dei blocchi VEVENT,
`network` per le prove dei provider e certificati, `platform.tls` per la copia
locale della compatibilità TLS. L'Activity storica è mantenuta come alias del
launcher e le preferenze continuano a usare il file `DiagnosticActivity`.

La compatibilità Open-Meteo su KitKat dipende dal provider di Google Play Services
installato sul dispositivo e dalla root ISRG Root X1 inclusa. Il codice riflessivo
del provider è documentato in `docs/tls-kitkat.md` e non disabilita la verifica
del certificato o del nome host nelle richieste meteo.
