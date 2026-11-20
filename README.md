# TimeWall

Due applicazioni Android **indipendenti**, compatibili con Android 4.4.2 (API 19):

| Progetto | Codice Java | Application ID conservato |
| --- | --- | --- |
| `timewall-app` | `it.timewall.app.*` | `it.timewall.dashboard` |
| `timewall-diagnostics` | `it.timewall.diagnostics.*` | `it.timewall.diagnostics` |

Aprire in Android Studio la cartella del singolo progetto. Nessun modulo condiviso,
collegamento simbolico o dipendenza dai sorgenti dell'altra applicazione.
Le piccole implementazioni TLS sono intenzionalmente duplicate.

Prerequisiti: JDK 17, Android SDK Platform 37.0 e Build Tools 36.0.0.
Gradle 9.8.0 è incluso tramite wrapper con checksum; Android Gradle Plugin 9.4.1.
Il bytecode usa Java 8 e la versione minima resta API 19. La dashboard usa target 36;
la diagnostica indipendente conserva target 28.

```powershell
cd timewall-app
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

La build release richiede la chiave storica, disponibile localmente nella cartella
ignorata `.signing` di ciascun progetto. Le chiavi **non sono incluse negli ZIP**.
Configurazione alternativa: `TIMEWALL_KEYSTORE` (percorso assoluto),
`TIMEWALL_STORE_PASSWORD`, `TIMEWALL_KEY_ALIAS`, `TIMEWALL_KEY_PASSWORD`.
I test JVM non richiedono un tablet, credenziali, calendari personali o chiamate API.

Dal livello principale:

```powershell
.\build.ps1 -Dashboard   # dashboard, test, lint e artefatti
.\build.ps1 -All         # entrambe le applicazioni
```

Usare `-UpdateLocks` soltanto per aggiornare intenzionalmente i dependency lock.

L'output consegnabile viene scritto in `build/`. Lo script non installa e non avvia
applicazioni. I dettagli tecnici sono nella documentazione dei due progetti.

La versione precedente alla riorganizzazione è conservata localmente in
`build/baseline-0.93.zip`; non è un secondo albero di sorgenti attivo.
