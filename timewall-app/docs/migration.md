# Compatibilità della riorganizzazione 0.94

- Application ID invariato: `it.timewall.dashboard`; nessuna disinstallazione.
- Chiave locale storica riusata, senza generazione automatica di una nuova chiave.
- La classe diventa `it.timewall.app.ui.dashboard.DashboardActivity`, mantenendo
  `it.timewall.diagnostics.DashboardActivity` come alias launcher.
- `SettingsStore.FILE_NAME` conserva il nome completo della vecchia Activity:
  `it.timewall.diagnostics.DashboardActivity`. Non usare un nuovo `getPreferences`
  implicito dopo aver rinominato l'Activity: selezionerebbe un archivio differente.
- Nessuna modifica delle chiavi, dei valori JSON dei calendari o dei nomi font.
- Gli ID temi restano 3–15, ora espliciti anziché dipendenti dall'ordine dell'enum.
  I valori ritirati o sconosciuti mantengono il fallback Ardesia e Azzurro · Scuro.
- Le palette, le traduzioni e le risorse runtime sono conservate; i colori dei
  calendari non dipendono dalle palette dei temi.
- La cache rimane nell'area dati dell'app, `calendar-` + SHA-256 dell'URL, con
  scrittura atomica. Non viene cancellata né rinominata durante l'aggiornamento.
- Gli URI persistenti degli sfondi restano validi perché l'identità dell'app resta
  invariata e viene usato `adb install -r`, senza reset dei dati.

Prima dell'installazione confrontare il certificato SHA-256 dell'APK installato e
di quello nuovo usando `apksigner verify --print-certs`; verificare anche firma v1
per Android 4.4, minSdk, application ID e versione crescente.
Non eseguire `uninstall` o `pm clear` per superare un'eventuale incompatibilità.
