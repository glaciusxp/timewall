# TimeWall — contesto e direttive di progetto

Usa questo file come contesto iniziale per lavorare in `C:\workspace\android-apps\timewall`. Il codice e la documentazione tecnica indicata sotto descrivono lo stato corrente. Inizia da `git status --short` e `git diff`, preservando tutte le modifiche locali. Questa fotografia del lavoro risale al 24 novembre 2026.

## Scopo e vincoli

- TimeWall Dashboard è un pannello Android da tenere consultabile a colpo d'occhio su un Asus Transformer Pad TF303CL con Android 4.4.2 (API 19), soprattutto in orizzontale Full HD. Mantieni questa compatibilità e verifica sul tablet le modifiche che dipendono da layout, TLS o API Android.
- `timewall-app` è la Dashboard, namespace Java `it.timewall.app`, application ID `it.timewall.dashboard`. `timewall-diagnostics` è un'app indipendente, application ID `it.timewall.diagnostics`. Ognuna ha il suo wrapper Gradle. Tieni separati i sorgenti e le dipendenze. La duplicazione delle piccole implementazioni TLS è intenzionale.
- Dashboard in Java con bytecode Java 8 e UI Android nativa. JDK 17 per la build, Gradle wrapper 9.8.0 con checksum, AGP 9.4.1, SDK Platform 37.0, Build Tools 36.0.0. Dashboard con minSdk 19 e target 36. Diagnostica con target 28. Leggi le versioni attuali dai file Gradle prima di intervenirvi.
- Il codice Dashboard è sotto `timewall-app/src/main/java/it/timewall/app`, suddiviso tra `configuration`, `data`, `domain`, `localization`, `platform` e `ui`. Riusa la struttura esistente e mantieni allineate le risorse nelle nove lingue supportate.

## Calendari e interfaccia

- Accetta URL iCal/ICS pubblici raggiungibili senza autenticazione, inclusi link non indicizzati condivisi dall'utente. Il supporto è indipendente dal provider. Elimina dalla Dashboard riferimenti, esempi e assunzioni specifiche di Google Calendar. I riferimenti al canale di distribuzione Google Play e alle coordinate delle dipendenze di build hanno un ruolo distinto.
- La Free supporta un calendario. La completa supporta fino a 20 calendari con nome e colore. Se un profilo storico supera il limite, conserva tutti i feed per consentirne modifica e rimozione e aggiorna solo i primi 20. Tratta gli URL privati di condivisione e il contenuto iCal come dati riservati. Mantieni l'aggiornamento manuale, quello automatico ogni cinque minuti e la cache locale, inclusa la possibilità di svuotarla. Prima di cambiare persistenza o aggiornamenti, leggi `timewall-app/docs/calendar-cache.md`.
- La Dashboard mostra orologio con secondi e data, meteo nella completa e Agenda con le sezioni Oggi e Prossimi eventi. Conserva la gerarchia visiva esistente, l'indicatore dell'ora corrente, i colori dei calendari e il riepilogo sotto l'orologio con gli eventi di tutta la giornata e quello in corso o imminente.
- Il messaggio «Nessun calendario configurato. Aggiungi calendario» deve avere più spazio sopra rispetto all'header. Il working tree corrente imposta 16 dp di padding superiore in quello stato. Il collaudo sul TF303CL è stato confermato dall’utente.
- Nella completa conserva tema e tipografia configurabili. Tra i temi già richiesti figurano Ardesia e Azzurro, scelto come predefinito, e Avorio e Petrolio, con varianti chiare e scure.

## Varianti Dashboard

- Un solo modulo Gradle. La variante completa (`complete`) mantiene l’ID storico `it.timewall.dashboard`. Le due Free usano gli ID di collaudo `it.timewall.dashboard.playfree` e `it.timewall.dashboard.sitefree`. Definisci ID e certificati di distribuzione prima del primo upload.
- Entrambe le Free includono orologio e data con formati e secondi configurabili, Agenda con un feed iCal, aggiornamento automatico ogni cinque minuti e manuale, cache locale, selezione della lingua, Roboto predefinito, Roboto Condensed, Roboto Slab e Shantell Sans, temi Ardesia e Azzurro chiaro e scuro. Il filtro Agenda è nascosto. Senza meteo, orologio e data sono centrati in entrambi gli assi nel quadrante superiore sinistro. Le due metà della colonna sinistra hanno la stessa altezza.
- Nella Free Play (`playFree`), le opzioni Pro sono anteprime inattive. Badge nelle singole voci bloccate di font e tema, accanto al pulsante di aggiunta dopo il primo calendario e accanto allo sfondo disabilitato. La scheda Meteo è navigabile, con badge in sidebar e nel titolo e campi inattivi. Il selettore font usa il badge senza una nota aggiuntiva. Gli SVG sorgente sono in `design/icons`; le risorse runtime compatibili con KitKat sono in `src/playFree/res`.
- Nella Free del sito (`siteFree`), le anteprime Pro sono assenti. Il pacchetto website esclude gli asset Pro e usa R8 per rimuovere il codice esclusivo inutilizzato. Per la completa usa `assembleCompleteRelease`; per Play usa `bundlePlayFreeRelease` o `assemblePlayFreeRelease` per ADB; per il sito usa `assembleSiteFreeWebsite`.
- `SettingsStore` conserva profili Free e completo in SharedPreferences distinte. Il file storico è il profilo completo. Inizializza un profilo mancante una sola volta copiando i valori compatibili, incluso il primo calendario valido per la Free. Mantieni entrambi i profili e la cache iCal separata. La copia richiede lo stesso spazio dati Android e va collaudata con aggiornamento a pari ID e firma. La disinstallazione elimina i dati locali.
- Pro è una proposta a pagamento con prezzo e modalità di vendita da definire. La variante completa serve per sviluppo e collaudo.

## Meteo, privacy e sito

- Open-Meteo è il provider meteo. Mantieni il credito nelle impostazioni del meteo e in Informazioni e Licenze. La Dashboard deve restare senza la riga aggiuntiva «Dati meteo: Open-Meteo – CC BY 4.0» sotto il meteo. La sezione di `timewall-app/docs/privacy-notice.md` che descrive quel credito sulla Dashboard documenta uno stato precedente e va letta alla luce di questa direttiva.
- Mantieni isolata la compatibilità TLS per KitKat e la sanitizzazione dei log meteo. Consulta `timewall-app/docs/tls-kitkat.md` e `tls-verification-0.99.md` per i limiti verificati sul dispositivo. Preserva le verifiche di certificato e hostname.
- Info comprende un'informativa privacy consultabile offline e localizzata. La sorgente è `localization/PrivacyNotice.java` con le risorse stringa. Usa `privacy-data-review.md` per verificare la corrispondenza tra documentazione e trattamento effettivo dei dati. Il materiale per lo store è in `store-assets`.
- Il sito vetrina è in `C:\workspace\android-apps\timewall-website`, directory sorella separata. Per il sito parti dal suo README e dal suo codice. I vecchi ZIP sono copie di distribuzione, non alberi di sorgenti attivi. Prima di collegare l'informativa dell'app a una pagina pubblica, verifica l'URL effettivamente pubblicato. Il sito è bilingue IT/EN e mantiene lo stile Ardesia e Azzurro. Presenta TimeWall come dashboard da scrivania che mette in primo piano l’evento in corso o imminente e dà un nuovo uso ai tablet datati. Il confronto Free/Pro deve rispecchiare le funzioni effettive e indicare Pro come edizione futura con prezzo da definire.

## Build, installazione e ripresa

- Dopo ogni modifica all'app, compila e installa sul TF303CL tutte e tre le varianti della Dashboard via ADB con i rispettivi ID: completa, Free Play e Free sito. Usa gli APK dei task `assembleCompleteRelease`, `assemblePlayFreeRelease` e `assembleSiteFreeWebsite`. Il collaudo sul dispositivo resta all'utente, salvo richiesta esplicita.

- Da questa radice usa `.uild.ps1 -Dashboard` per test JVM, lint, build release e pacchetti della Dashboard completa. Usa `.uild.ps1 -All` quando servono entrambi i progetti. Usa `-UpdateLocks` solo per aggiornare intenzionalmente i lock.
- Per build e installazione sul tablet collegato via ADB usa `.uild_and_install_dashboard.ps1`. Lo script compila la Dashboard e installa `build\TimeWall-Dashboard.apk` con `adb install -r` solo dopo il successo della build. `GRADLE_USER_HOME` è già configurato come variabile utente Windows.
- Preserva la firma storica della release e la firma v1 necessaria su KitKat. La chiave è configurata in locale, tramite `.signing` o le variabili indicate nel README. Mantieni chiavi e credenziali fuori da Git e dagli ZIP.
- La Dashboard è alla versione `0.103` (`versionCode 103`). Le due Free e la completa sono state installate sul TF303CL. L’utente ha confermato il collaudo sul tablet; il test su Android recente è in corso a cura dell’utente.
- Restano da verificare la migrazione tra profili, gli ID e le firme definitive, il test chiuso eventualmente richiesto da Play Console, l’informativa Free e il suo URL pubblico, Sicurezza dei dati, scheda e materiali store. Mantieni configurazioni e modifiche locali; il commit `1337b8d` resta il riferimento della cache.
- Per approfondire, leggi il README principale e `timewall-app/docs/architecture.md`, `android-compatibility.md`, `testing.md` e `artwork-provenance.md` a seconda dell'intervento.
