# Migrazione Android della dashboard 0.95

La dashboard usa `targetSdk 36`, `minSdk 19`, `compileSdk 37.0` e Java 8.
La diagnostica resta indipendente e non viene modificata da questa migrazione.
Nessun font, colore, chiave delle preferenze o formato della cache viene cambiato.
La firma resta quella già usata sul tablet; questa build non configura ancora la
firma di produzione di Google Play e non costituisce una certificazione Play.

## Indietro

Da Android 13 viene usato `OnBackInvokedDispatcher`, con una callback a priorità
normale registrata soltanto quando sono aperte le impostazioni. Il gesto richiede
la stessa uscita del pulsante dell'header: le modifiche non salvate continuano a
richiedere conferma. Il pulsante Indietro della dashboard resta gestito dal sistema.
Chiusura e distruzione rimuovono la callback senza duplicare registrazioni.

Su Android precedenti resta `onBackPressed()`. I tipi introdotti nell'API 33 sono
isolati in `Api33BackNavigation`, istanziata soltanto dopo un controllo della versione:
la classe eseguita su KitKat non espone quei tipi nelle proprie firme.
Il manifest abilita la navigazione moderna, anziché disattivarla per evitare la migrazione.

## Barre di sistema e tastiera

Su Android 15+ il contenitore comune di dashboard e impostazioni riceve gli inset
di barre visibili, cutout e IME. Il padding di sicurezza si somma al padding base
una volta sola; riapertura della tastiera e ridimensionamenti non lo accumulano.
I padding interni dei quadranti restano separati. Il listener continua a propagare
gli inset ai figli, senza consumarli arbitrariamente.

La vecchia gestione fullscreen resta invariata fino ad Android 14 compreso.
Le API moderne sono isolate in `Api35WindowCompatibility` e non vengono chiamate
su KitKat. Le popup native continuano a gestire la propria finestra.

## Orientamento e limiti di questo passaggio

Per conservare la dashboard orizzontale si usa sulla sua Activity la proprietà
`android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY=true`, prevista
da Android come opt-out temporaneo per le app con target 36 sui display grandi.
Non è un opt-out dall'edge-to-edge o da Indietro, già migrati.

La proprietà non varrà più quando l'app passerà al target 37: prima di quel passo
serviranno layout realmente adattabili a verticale e finestre strette, insieme
al ripristino completo della bozza delle impostazioni durante una ricreazione.
Il layout 60/40 continua a usare pesi proporzionali, ma questo passaggio non lo
trasforma in un'interfaccia universale per tutti i telefoni e le finestre.

## Altri cambiamenti di target già valutati

- File e immagini usano directory private e Storage Access Framework, senza file URI
  o permessi generali della memoria; non è richiesta una migrazione a tali permessi.
- Non esistono servizi foreground, sveglie esatte, PendingIntent o notifiche di sistema.
  Il suono è locale alla dashboard attiva: non viene aggiunto POST_NOTIFICATIONS.
- Activity e alias launcher dichiarano già `exported` esplicitamente.
- Il provider TLS legacy viene cercato solo su API 19–20, quindi le restrizioni di
  visibilità dei package moderne non richiedono una query globale dei pacchetti.
- Nessuna dipendenza runtime nuova impone una versione minima superiore a 19.

## Verifica

Eseguire `gradlew.bat testDebugUnitTest lintRelease checkJavaFormat assembleRelease`.
Verificare nell'APK risultante minSdk 19, targetSdk 36, firma storica e versionCode 95.
Le verifiche UI moderne richiedono un emulatore o un dispositivo eseguibile; i test JVM
non simulano il dispatcher Back né il sistema di insets Android.

Sul tablet dell'utente installare con `adb install -r`, senza cancellare i dati e
senza avviare l'app o effettuare controlli successivi all'installazione.

Riferimenti: https://developer.android.com/about/versions/16/behavior-changes-16
e https://developer.android.com/develop/ui/views/layout/edge-to-edge.
