# Cache dei calendari — 0.100

Impostazioni → Calendari raggruppa l'elenco dei feed e l'azione «Svuota cache
calendari». L'azione usa la configurazione già salvata, non la bozza: non salva
altre opzioni, non rimuove calendari e non modifica nomi, colori, filtri o meteo.
Il pulsante resta disabilitato durante la pulizia; esito ed errori sono tradotti
nelle nove lingue. Dopo il successo viene avviato un nuovo download. Offline non
si ripropongono gli eventi della vecchia cache appena eliminata.

Le eliminazioni e modifiche URL della bozza diventano effettive al salvataggio.
Il confronto usa gli insiemi degli URL prima/dopo, quindi rinominare un calendario
o cambiarne il colore conserva il suo file. Annullare la bozza non elimina dati.
All'avvio una pulizia rimuove anche le cache orfane lasciate dalle vecchie versioni.

## Concorrenza e lifecycle

CalendarDiskCache espone una generazione di processo AtomicLong. Il chiamante
cattura il ticket prima di accodare un refresh; invalidare la generazione rende
obsolete anche le richieste già in attesa o appartenenti a una vecchia Activity.
Il repository controlla il ticket prima/dopo rete e prima delle callback; lo store
ricontrolla sotto il lock di scrittura. Gli snapshot interpretati sono invalidati
quando cambia generazione.

Download e operazioni di pulizia sono serializzati sul worker condiviso dei
calendari, indipendente da quello delle immagini dell'Activity. La ricreazione
non perde quindi una cancellazione già accodata. Un repository distrutto non
esegue ulteriori download; il processo può essere normalmente terminato da Android.
Una richiesta di rete già in corso può finire prima della pulizia, ma non riscrive
i byte invalidati. I nuovi download iniziano dopo le operazioni di pulizia accodate.

I file eliminabili sono solo `calendar-<64 cifre esadecimali minuscole>` e le
varianti `.bak`/`.new`. Non si attraversano directory, non si cancellano altri file
dell'app e non si interpreta mai l'URL come percorso. Gli errori di I/O impediscono
di dichiarare un successo completo; configurazione e aggiornamenti restano attivi.

## Verifiche

Test JVM con file temporanei reali: conservazione dei calendari attivi, eliminazione
delle copie di un URL rimosso, sidecar AtomicFile, altri file/directory preservati,
directory non disponibile e scritture con ticket obsoleto.

Test repository: download bloccato e invalidato da un altro thread, richieste
obsolete ancora in coda, invalidazione condivisa tra repository, assenza di dati
precedenti quando offline dopo la pulizia, repository distrutti senza nuovi accessi.
La suite delle risorse verifica chiavi e parametri nelle nove lingue.

Le prove automatiche non aprono l'app sul tablet né modificano calendari utente.
I controlli visivi della sezione e del pulsante restano all'utente dopo l'installazione.


## Compatibilità iCalendar

TimeWall espande le ricorrenze giornaliere, settimanali, mensili e annuali basate
su DTSTART. Supporta INTERVAL, COUNT, UNTIL, BYDAY nelle ricorrenze settimanali
con settimana che inizia il lunedì, EXDATE, RDATE con una data o un istante e le
eccezioni per singola occorrenza tramite RECURRENCE-ID. Interpreta DURATION e
i fusi disponibili in Android, mantenendo l’ora locale durante le ricorrenze.

Per le regole più complesse, come BYSETPOS, i fusi VTIMEZONE definiti dal feed
con un identificativo sconosciuto e gli intervalli RDATE di tipo PERIOD, il
supporto è parziale. Le regole di ricorrenza non supportate conservano DTSTART
e le date esplicite interpretabili. Un fuso sconosciuto o una struttura
incompleta causa un aggiornamento fallito e preserva la cache valida.

Le occorrenze generate sono limitate ai prossimi 60 giorni, con un giorno
precedente per gli eventi ancora in corso, e a 512 per feed. Le serie con
COUNT vengono esplorate al massimo per 8192 periodi. Il repository ricostruisce
le occorrenze dalla cache quando cambia il giorno locale.
