# Analisi dei dati — TimeWall 0.97

Analisi statica del 19 novembre 2026. Non è una privacy policy pubblicata e non
certifica le pratiche dei server esterni. Nessun dato del tablet è stato letto.

## Flussi verificati nel codice

| Dati | Uso e destinatario | Conservazione nell'app |
|---|---|---|
| URL HTTPS iCal, nome e colore calendario | Download diretto dal server configurato dall'utente. Il server riceve la richiesta al percorso dell'URL, IP e normali metadati di connessione. Nessun invio a un backend TimeWall. | URL completo e configurazione in SharedPreferences private. |
| File iCal completo | Il feed viene letto e interpretato sul dispositivo. Titolo, orari, descrizione, luogo, UID e CREATED sono usati per eventi e dettaglio. Il corpo originale può contenere ulteriori campi personali non mostrati. | Copia integrale in filesDir con scrittura atomica; nome derivato dall'hash SHA-256 dell'URL. Il contenuto non viene cifrato dall'app. |
| Testo ricerca località e lingua | Geocoding Open-Meteo: query dopo almeno tre caratteri e 450 ms di pausa. La ricerca nei settings può essere usata anche con dashboard meteo disabilitata. | Risultati e query nella sessione; località scelta, dettaglio e coordinate in preferenze al salvataggio. |
| Coordinate della località scelta | Richiesta previsioni a api.open-meteo.com; il servizio vede anche IP e metadati di rete. Non si usa GPS né il permesso posizione. La località può essere diversa da quella reale dell'utente. | Coordinate e preferenze meteo persistenti; condizioni correnti in memoria, ultimo successo salvato come timestamp. |
| Immagine sfondo | File picker Android limitato ai file locali; lettura tramite content URI e concessione persistente. Nessun upload eseguito da TimeWall. | URI e nome file in preferenze; immagine originale resta gestita dal provider documenti. Il permesso precedente viene rilasciato quando si salva sostituzione/rimozione. |
| Lingua, tema, colori, font, formato orario, filtri | Personalizzazione locale; nessun destinatario TimeWall esterno. | Preferenze private persistenti. |
| Eccezioni tecniche | Log Android locali per font e audio. Dalla 0.99 il meteo registra soltanto operazione e categoria enumerate, senza Throwable, messaggi o contenuto delle risposte. Nessun invio a crash-reporting o analytics nell'app. | Conservazione controllata dal sistema. La redazione dei log meteo è verificata con marcatori sintetici e prove sul dispositivo. |
| Collegamenti Info | Licenze font lette dagli assets senza rete. I link esterni si aprono solo su scelta dell'utente tramite il gestore Android; il sito aperto riceve i normali metadati di navigazione. | Nessuna telemetria aggiunta alla schermata Info. |

## Rete, sicurezza e lifecycle

- Manifest: solo INTERNET; niente autorizzazioni calendario di sistema, posizione,
  contatti o accesso generale alla memoria. Backup Android disabilitato.
- Calendari e meteo usano HTTPS, senza accettazione indiscriminata di certificati
  o hostname. Redirect rifiutati. Il supporto KitKat del meteo usa un provider TLS
  della copia di sistema di Google Play Services e la radice ISRG inclusa.
- Refresh calendari ogni cinque minuti con Activity attiva. Meteo alla frequenza
  configurata con retry; disabilitare il meteo impedisce i refresh previsioni.
  Le ricerche esplicite nella configurazione restano chiamate al geocoding.
- onPause rimuove timer e sospende il meteo; una richiesta calendario già in volo
  può terminare. Non sono presenti servizi di aggiornamento in background.
- Non risultano account TimeWall, autenticazione, advertising SDK, analytics,
  backend dello sviluppatore o caricamento degli eventi su altri servizi.
  Questa osservazione non equivale automaticamente a "nessun dato raccolto"
  nella scheda Sicurezza dei dati: vanno valutati anche i servizi terzi.

## Conservazione e cancellazione: limiti attuali

- La cache iCal viene sostituita al refresh riuscito, senza scadenza automatica.
- Rimuovere un calendario aggiorna la lista e toglie i suoi eventi visibili, ma
  non elimina il precedente file cache dal disco. Anche il repository in memoria
  può trattenerne lo snapshot fino alla distruzione dell'Activity.
- Disabilitare il meteo non cancella automaticamente località e preferenze.
- Non c'è ancora un comando dedicato per cancellare tutte le cache e i dati.
- Cancellazione dati dell'app o disinstallazione eliminano lo storage privato;
  i file immagine originali e i dati sui server esterni non vengono cancellati.
- URL segreti e iCal sono protetti dall'isolamento Android, non da cifratura
  applicativa dedicata. Un URL iCal segreto è una credenziale di accesso di fatto:
  non è opportuno descriverlo semplicemente come informazione pubblica.

## Decisioni prima della stesura

Verifica esterna: la pagina Terms & Privacy di Open-Meteo, consultata il
19 novembre 2026, dichiara per il servizio API gratuito possibili log con IP e
coordinate geografiche, eliminati dopo 90 giorni. OpenMeteo GmbH si identifica
come titolare indipendente. Sono dichiarazioni del provider, non misure
verificate sul server da TimeWall; non va quindi descritta la trasmissione come
necessariamente effimera o limitata alla durata della richiesta.

1. Confermare titolare del trattamento, recapito privacy e URL pubblico della
   policy. L'autore indicato nell'app non definisce da solo tutti questi dati.
2. Decidere durata e cancellazione cache: proporre eliminazione quando si
   rimuove/modifica l'URL di un calendario, più comando esplicito di pulizia.
3. Integrare le dichiarazioni Open-Meteo sopra riportate e verificare i
   trattamenti specifici dei provider iCal. Le pratiche di tutti i server
   configurabili non sono deducibili dal solo client; ricontrollare la policy
   Open-Meteo prima della pubblicazione.
4. Redigere la policy descrivendo uso locale e richieste esterne; distinguere
   posizione scelta manualmente da posizione effettivamente rilevata.
5. Compilare separatamente Data safety secondo definizioni Google, valutando
   coordinate, query e traffico verso terzi, senza inferire automaticamente
   raccolta di posizione GPS o totale assenza di raccolta.
6. Pubblicare la policy sul sito e aggiungere il link nella sezione Privacy;
   tradurre tutto il testo UI nelle nove lingue. I testi legali originali delle
   licenze rimangono nella lingua e nella forma distribuite dagli autori.

Riferimenti di codice: CalendarFeed, CalendarDiskCache, CalendarRepository,
DashboardWeather, OpenMeteoClient, BackgroundImageEditor, SettingsStore,
DashboardActivity, AndroidManifest.xml.

Riferimenti per la futura dichiarazione (da ricontrollare alla pubblicazione):
https://support.google.com/googleplay/android-developer/answer/10787469
https://support.google.com/googleplay/android-developer/answer/10144311
https://open-meteo.com/en/terms
https://open-meteo.com/en/docs/geocoding-api

## Ambito dei crediti di questa build

Info ora rende consultabili i crediti Open-Meteo e GeoNames, il font icone
FontAwesome e tutte le licenze/metadata dei font presenti negli assets. Non
attribuisce licenze inventate alle risorse grafiche e al suono preparati per il
progetto. Non aggiunge ancora il credito vicino ai dati meteo della dashboard:
quell'intervento resta nella checklist prima della pubblicazione.
