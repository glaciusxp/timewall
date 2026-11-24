# TimeWall — Sicurezza dei dati per Google Play

Preparato il 20 novembre 2026 per TimeWall 0.101, package `it.timewall.dashboard`.
Titolare: Andrea Persano. Contatto: andreapersano@gmail.com.

Documento di compilazione: non è il testo della privacy policy e non è una dichiarazione già inviata a Google. Le risposte sotto sono proposte basate sui sorgenti correnti e sulle informative pubbliche dei fornitori. Dove la classificazione dipende dal comportamento dei server è indicata una verifica ancora necessaria. Il questionario deve coprire tutte le versioni distribuite nei canali interessati.

## 1. Risposte generali proposte

| Domanda / area | Risposta proposta | Motivazione e limiti |
|---|---|---|
| L'app raccoglie o condivide dati compresi nelle categorie del modulo? | **Sì** | Ricerca località e richieste meteo inviano dati a Open-Meteo. L'assenza di un backend TimeWall non significa che nessun dato esca dal dispositivo. |
| I dati sono cifrati durante il trasferimento? | **Sì** | I client usano HTTPS e verificano certificati e hostname. Gli URL dei calendari devono essere HTTPS; i redirect vengono rifiutati. Questo non significa cifratura end-to-end o cifratura applicativa della cache. |
| L'app consente di creare un account? | **No** | Non esiste un account TimeWall. Gli eventuali account dei fornitori di calendari sono esterni all'app. |
| Cancellazione account / relativo URL | **Non applicabile al comportamento attuale** | Non si crea un account TimeWall. Non inserire un indirizzo fittizio. |
| Gli utenti possono richiedere la cancellazione dei dati raccolti? | **Da completare prima dell'invio** | È possibile eliminare i dati locali, ma ciò non cancella i log dei fornitori. Non rispondere Sì solo per la presenza del pulsante cache o dell'email: occorre una procedura effettiva per i dati raccolti fuori dal dispositivo. Confermare con Open-Meteo; in assenza di una procedura verificata non dichiararla disponibile. |
| Revisione indipendente della sicurezza | **No** | I test del progetto non sono una certificazione o una revisione indipendente riconosciuta da Google. |
| Informativa privacy | **Pagina pubblica ancora da pubblicare** | L'informativa in-app esiste, ma l'URL esterno è ancora da definire. Inserire l'URL reale anche nell'app e in Play Console. |

## 2. Dati da selezionare: proposta da validare

“Raccolta” nel modulo riguarda la trasmissione fuori dal dispositivo, anche a terzi. La colonna condivisione è una proposta prudenziale: non sostituisce la verifica delle eccezioni Google per azioni esplicite dell'utente o fornitori che agiscono per conto dello sviluppatore.

| Categoria Google | Raccolta proposta | Condivisione proposta | Obbligatorietà | Elaborazione effimera | Finalità proposta |
|---|---|---|---|---|---|
| Posizione → Posizione approssimativa | **Sì**, per la località meteo scelta manualmente | **Sì, salvo eccezione documentata** | Facoltativa | **No** | Funzionalità dell'app; verificare eventuale uso tecnico del provider |
| Attività nell'app → Cronologia delle ricerche nell'app | **Sì**, per il testo cercato nel geocoding | **Sì, salvo eccezione documentata** | Facoltativa | **Non dichiarare Sì senza conferma** | Funzionalità dell'app |
| ID del dispositivo o altri ID | **Da chiarire per IP e metadati tecnici** | Da chiarire | Dipende dal flusso | Non presumere effimero | Funzionamento, sicurezza/prevenzione abusi, se confermati |
| Informazioni personali → ID utente / indirizzo email | **Da verificare per il contenuto degli URL iCal** | Da verificare, inclusa l'eccezione per trasferimento richiesto dall'utente | Configurazione calendario facoltativa | Dipende dal server | Accesso al calendario richiesto dall'utente |

### Posizione: cosa viene realmente inviato

TimeWall non richiede il permesso posizione e non legge GPS o posizione di rete. Invia le coordinate della località selezionata nei risultati del geocoding; quella località può anche non essere dove si trova l'utente. La proposta “approssimativa” si riferisce a una località/città, non al numero di decimali delle coordinate. Google distingue la precisione in base all'area rappresentata: verificare che il servizio e i risultati selezionabili mantengano questo significato. Se si introduce la posizione effettiva precisa dell'utente, la classificazione va aggiornata.

Open-Meteo dichiara per le API gratuite log tecnici che possono includere IP e coordinate e una conservazione di 90 giorni. Per questo il flusso meteo non va presentato come necessariamente effimero. La durata specifica delle query geocoding va confermata: in attesa, non usare l'eccezione “effimero”.

### Condivisione: decisione da documentare

Open-Meteo si descrive come titolare indipendente. Non è quindi corretto presumere automaticamente l'eccezione riservata ai responsabili/fornitori che trattano dati soltanto per conto dello sviluppatore. Google prevede anche eccezioni per trasferimenti specifici richiesti e ragionevolmente attesi dall'utente: se si decide di applicarle, documentare il flusso concreto e l'informazione mostrata prima della ricerca/abilitazione, e motivare l'esclusione. Senza questa verifica, la proposta qui adottata è dichiarare la condivisione dei dati meteo/ricerca.

### IP, identificativi e URL dei calendari

Non sono presenti advertising ID, identificativi TimeWall o analytics SDK. L'IP arriva comunque ai server contattati. Non equivale automaticamente a un advertising ID: la categoria Google dipende dall'uso effettivo del fornitore. Chiarire se viene usato come identificatore, per dedurre posizione o per diagnostica/sicurezza.

Un URL iCal può contenere email, ID calendario o token segreti. Non dichiarare in assoluto che questi elementi non vengono mai trasmessi: il server configurato riceve l'URL richiesto. TimeWall non estrae tali dati per creare profili e non li inoltra a un proprio backend. Valutare l'URL e il trasferimento specifico senza trattare un URL segreto come un dato innocuo o come un login TimeWall.

## 3. Dati che restano locali nell'implementazione corrente

| Dati / categoria | Comportamento verificato nel client | Conseguenza proposta per il modulo |
|---|---|---|
| Calendario → Eventi del calendario | Download e lettura locale di iCal. Nessun upload degli eventi da TimeWall ad altri server. | Non dichiarare raccolta degli eventi solo perché vengono scaricati e mostrati. Riesaminare se viene aggiunta sincronizzazione/upload. |
| Immagini, file e documenti | Lo sfondo viene selezionato con il selettore Android e letto localmente. | Nessuna raccolta di immagini/file da parte di TimeWall. |
| Tema, font, lingua, filtri e impostazioni | Preferenze locali, senza invio a TimeWall. | Nessuna raccolta di queste preferenze nel comportamento corrente. |
| Diagnostica e crash | Nessun crash reporting o invio log nell'app. | Nessuna raccolta diagnostica TimeWall; separatamente chiarire i log dei server esterni. |
| Dati finanziari, salute, contatti, messaggi | Nessuna funzione di acquisizione o invio dedicata. | Non selezionare senza un flusso effettivo. Un eventuale contenuto sensibile scritto da terzi dentro un evento rimane nella copia locale iCal. |

L'email di assistenza può essere aperta con un'app esterna. I messaggi inviati volontariamente al titolare sono un trattamento distinto descritto nell'informativa; TimeWall non legge la casella email o il contenuto del messaggio.

## 4. Facoltatività, finalità e cancellazione

- L'app è utilizzabile come orologio/agenda senza meteo: posizione meteo e ricerca sono facoltative. La ricerca esplicita nei settings può contattare il geocoding anche se il meteo della dashboard è disabilitato.
- Non risultano pubblicità, marketing, profili pubblicitari, account TimeWall o analytics nell'app. Non estendere questa osservazione ai server esterni senza verificarli.
- La cache contiene il file iCal completo, anche campi non mostrati. È nella memoria privata dell'app, senza cifratura applicativa dedicata; il backup Android è disabilitato.
- Salvare la rimozione di un calendario o una modifica del suo URL elimina la vecchia cache. “Svuota cache calendari” in Impostazioni → Calendari elimina le copie e avvia un nuovo aggiornamento; non cancella la configurazione né i calendari presso il provider.
- Cancellare i dati dell'app o disinstallarla elimina lo storage privato. Non elimina il file immagine originale o i log/dati dei fornitori esterni.
- Il contatto privacy è andreapersano@gmail.com. Non promettere cancellazioni sui server di terzi che il titolare non può eseguire direttamente.

## 5. Conferme da ottenere prima dell'invio

- [ ] Pubblicare l'informativa privacy e inserire il suo URL reale in app e console.
- [ ] Confermare con Open-Meteo le categorie Google applicabili a IP e log, le finalità tecniche e la conservazione delle ricerche geocoding.
- [ ] Confermare se esiste una procedura per richieste di cancellazione dei log e chi può attivarla.
- [ ] Decidere e documentare se applicare l'eccezione Google alla condivisione per trasferimenti richiesti dall'utente; in alternativa mantenere la proposta prudenziale sopra.
- [ ] Verificare come trattare identificativi/email eventualmente inclusi negli URL iCal, senza classificare come caricamento degli eventi il semplice download.
- [ ] Riconciliare il modulo finale con informativa, comportamento della release distribuita e tutti i canali attivi.
- [ ] Salvare una copia delle risposte effettivamente inviate e riesaminare dopo cambi a SDK, API o funzioni.

Domande suggerite al provider, da inviare solo su scelta del titolare: “Usiamo forecast e geocoding da un'app senza account né telemetria. Quali dati delle richieste conservate, per quali finalità e per quanto tempo? Come classificare IP/query/coordinate nella Data safety di Google Play? Esiste una procedura di cancellazione dei log? Potete confermare se il geocoding segue la stessa conservazione delle API gratuite?”

## Fonti e riferimenti verificati

Consultati il 20 novembre 2026:

- [Google Play: compilare Sicurezza dei dati](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Google Play: tipi di dati e significato delle dichiarazioni](https://support.google.com/googleplay/answer/11416267)
- [Android: dichiarare l'uso dei dati](https://developer.android.com/privacy-and-security/declare-data-use)
- [Open-Meteo: Terms & Privacy](https://open-meteo.com/en/terms)
- [Open-Meteo: geocoding](https://open-meteo.com/en/docs/geocoding-api)

Riferimenti locali: `timewall-app/docs/privacy-data-review.md`, `privacy-notice.md`, manifest, `CalendarFeed`, `CalendarDiskCache`, `CalendarRepository`, `DashboardWeather`, `OpenMeteoClient`, `BackgroundImageEditor` e `SettingsStore`. L'analisi del client non certifica le pratiche dei server.
