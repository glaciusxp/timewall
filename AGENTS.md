# TimeWall — contesto e direttive di progetto

Usa questo file come contesto iniziale per lavorare in `C:\workspace\android-apps\timewall`. Il codice e la documentazione tecnica indicata sotto descrivono lo stato corrente. Inizia da `git status --short` e `git diff`, preservando tutte le modifiche locali. Questa fotografia del lavoro risale al 24 novembre 2026.

## Scopo e vincoli

- TimeWall Dashboard è un pannello Android da tenere consultabile a colpo d'occhio su un Asus Transformer Pad TF303CL con Android 4.4.2 (API 19), soprattutto in orizzontale Full HD. Mantieni questa compatibilità e verifica sul tablet le modifiche che dipendono da layout, TLS o API Android.
- `timewall-app` è la Dashboard, namespace Java `it.timewall.app`, application ID `it.timewall.dashboard`. `timewall-diagnostics` è un'app indipendente, application ID `it.timewall.diagnostics`. Ognuna ha il suo wrapper Gradle. Tieni separati i sorgenti e le dipendenze. La duplicazione delle piccole implementazioni TLS è intenzionale.
- Dashboard in Java con bytecode Java 8 e UI Android nativa. JDK 17 per la build, Gradle wrapper 9.9.2 con checksum, AGP 9.4.1, SDK Platform 37.0, Build Tools 36.0.0. Dashboard con minSdk 19 e target 36. Diagnostica con target 28. Leggi le versioni attuali dai file Gradle prima di intervenirvi.
- Il codice Dashboard è sotto `timewall-app/src/main/java/it/timewall/app`, suddiviso tra `configuration`, `data`, `domain`, `localization`, `platform` e `ui`. Riusa la struttura esistente e mantieni allineate le risorse nelle nove lingue supportate.

## Calendari e interfaccia

- Accetta URL iCal/ICS pubblici raggiungibili senza autenticazione, inclusi link non indicizzati condivisi dall'utente. Il supporto è indipendente dal provider. Elimina dalla Dashboard riferimenti, esempi e assunzioni specifiche di Google Calendar. I riferimenti al canale di distribuzione Google Play e alle coordinate delle dipendenze di build hanno un ruolo distinto.
- TimeWall supporta fino a 20 calendari con nome e colore. Se un profilo storico supera il limite, conserva tutti i feed per consentirne modifica e rimozione e aggiorna solo i primi 20. Tratta gli URL privati di condivisione e il contenuto iCal come dati riservati. Mantieni l'aggiornamento manuale, quello automatico ogni cinque minuti e la cache locale, inclusa la possibilità di svuotarla. Prima di cambiare persistenza o aggiornamenti, leggi `timewall-app/docs/calendar-cache.md`.
- La Dashboard mostra orologio con secondi e data, meteo e Agenda con le sezioni Oggi e Prossimi eventi. Conserva la gerarchia visiva esistente, l'indicatore dell'ora corrente, i colori dei calendari e il quadrante «In primo piano» sotto l’orologio, condiviso con What’s next: evento in corso oppure prossimo evento di oggi. Gli eventi di tutta la giornata restano nell’Agenda.
- Il messaggio «Nessun calendario configurato. Aggiungi calendario» deve avere più spazio sopra rispetto all'header. Il working tree corrente imposta 16 dp di padding superiore in quello stato. Il collaudo sul TF303CL è stato confermato dall’utente.
- Nella completa conserva tema e tipografia configurabili. Tra i temi già richiesti figurano Ardesia e Azzurro, scelto come predefinito, e Avorio e Petrolio, con varianti chiare e scure.

## Varianti Dashboard

- TimeWall è un’unica app completa e gratuita. Tutti i canali offrono le stesse funzioni, inclusi i quattro widget, il meteo, tutti i font e i temi.
- I flavor Gradle identificano i tre canali: `complete` (`it.timewall.dashboard`), `playFree` (`it.timewall.app`) e `siteFree` (`it.timewall.dashboard.sitefree`). Il package Play è stato confermato in Console. Configura la firma di upload prima del primo caricamento.
- `SettingsStore` riapre il file già usato dal relativo package, incluso il suffisso storico `.free` dei canali Play e sito. Android isola i dati tra package. Preserva la firma del sito per gli aggiornamenti in-place.
- Usa `assembleCompleteRelease`, `assemblePlayFreeRelease` e `assembleSiteFreeWebsite` per ADB. Usa `bundlePlayFreeRelease` per l’AAB. Il rilascio dell’APK del sito passa dal tag `site-vVERSION`, mentre i tag `site-vVERSION-test` collaudano solo build e firma.

## Meteo, privacy e sito

- Open-Meteo è il provider meteo. Mantieni il credito nelle impostazioni del meteo e in Informazioni e Licenze. La Dashboard deve restare senza la riga aggiuntiva «Dati meteo: Open-Meteo – CC BY 4.0» sotto il meteo. La sezione di `timewall-app/docs/privacy-notice.md` che descrive quel credito sulla Dashboard documenta uno stato precedente e va letta alla luce di questa direttiva.
- Mantieni isolata la compatibilità TLS per KitKat e la sanitizzazione dei log meteo. Consulta `timewall-app/docs/tls-kitkat.md` e `tls-verification-0.99.md` per i limiti verificati sul dispositivo. Preserva le verifiche di certificato e hostname.
- Info comprende un'informativa privacy consultabile offline e localizzata. La sorgente è `localization/PrivacyNotice.java` con le risorse stringa. Usa `privacy-data-review.md` per verificare la corrispondenza tra documentazione e trattamento effettivo dei dati. Il materiale per lo store è in `store-assets`.
- Il sito vetrina è in `C:\workspace\android-apps\timewall-website`, directory sorella separata. Per il sito parti dal suo README e dal suo codice. I vecchi ZIP sono copie di distribuzione, non alberi di sorgenti attivi. Prima di collegare l'informativa dell'app a una pagina pubblica, verifica l'URL effettivamente pubblicato. Il sito è bilingue IT/EN e mantiene lo stile Ardesia e Azzurro. Presenta TimeWall come dashboard da scrivania che mette in primo piano l’evento in corso o imminente e dà un nuovo uso ai tablet datati. Il sito presenta TimeWall come app unica gratuita e open source, con codice MIT e attribuzioni separate per gli asset di terzi.

## Build, installazione e ripresa

- Dopo ogni modifica all'app, compila e installa sul TF303CL tutte e tre le varianti della Dashboard via ADB con i rispettivi ID: sviluppo, Play e sito. Usa gli APK dei task `assembleCompleteRelease`, `assemblePlayFreeRelease` e `assembleSiteFreeWebsite`. Il collaudo sul dispositivo resta all'utente, salvo richiesta esplicita.

- Da questa radice usa `.uild.ps1 -Dashboard` per test JVM, lint, build release e pacchetti della Dashboard completa. Usa `.uild.ps1 -All` quando servono entrambi i progetti. Usa `-UpdateLocks` solo per aggiornare intenzionalmente i lock.
- Per build e installazione sul tablet collegato via ADB usa `.uild_and_install_dashboard.ps1`. Lo script compila la Dashboard e installa `build\TimeWall-Dashboard.apk` con `adb install -r` solo dopo il successo della build. `GRADLE_USER_HOME` è già configurato come variabile utente Windows.
- Preserva la firma storica della release e la firma v1 necessaria su KitKat. La chiave è configurata in locale, tramite `.signing` o le variabili indicate nel README. Mantieni chiavi e credenziali fuori da Git e dagli ZIP.
- La Dashboard in preparazione è alla versione `1.0.beta-1` (`versionCode 114`). Per i fix del test chiuso incrementa il progressivo dopo beta e il versionCode.
- Restano da verificare l’aggiornamento in-place, l’ID e la firma Play, il test chiuso eventualmente richiesto da Play Console, Sicurezza dei dati, scheda e materiali store. Mantieni configurazioni e modifiche locali; il commit `1337b8d` resta il riferimento della cache.
- Per approfondire, leggi il README principale e `timewall-app/docs/architecture.md`, `android-compatibility.md`, `testing.md` e `artwork-provenance.md` a seconda dell'intervento.

## Idee future

- Valutare un'opzione «Proteggi con password» per cifrare l'intero file di backup delle impostazioni della Dashboard. Il ripristino deve funzionare anche dopo una reinstallazione o su un altro dispositivo. Questa è una proposta per il futuro.
