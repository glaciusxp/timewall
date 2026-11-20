# Test e verifiche

Comando ordinario: `gradlew.bat testDebugUnitTest lintRelease assembleRelease`.
La suite JVM non usa Android stub permissivi (`returnDefaultValues=false`), rete,
timer reali, segreti o calendari personali. Il JSON JVM è una dipendenza di solo test.

Copertura di regressione:

- iCal: UTC/TZID, CREATED, eventi cancellati, date errate, folding, escape, VALARM,
  eventi giornalieri con DTEND esclusivo e passaggi di ora legale.
- Cache: precedenza del dato locale, hash invariato, colore modificato, fallimenti
  di rete/scrittura, download non valido e rimozione dei calendari.
- Notifiche: inizio, evento appena scaricato già attivo, eventi terminati, deduplica
  per UID, mezzanotte, durata nulla e orologio spostato indietro.
- Timeline: evento attivo/prossimo, eventi giornalieri, confini di fine evento/giorno.
- Meteo: codici WMO, pioggia gelata, campi assenti/null, unità canoniche e retry.
- Impostazioni: identificativi storici dei temi, archivio delle preferenze, nomi font,
  colori RGB/ARGB e input non validi.
- Localizzazione: chiavi e parametri di formattazione allineati nelle nove lingue.
- TLS: TLS 1.2 senza modifica dei default globali, root X.509 CA autofirmata valida.

Lint è bloccante sugli errori e include il controllo delle API disponibili su API 19.
Le segnalazioni sul target volutamente storico sono escluse in modo esplicito;
non viene impiegata una baseline generale per nascondere errori nuovi.
Gli avvisi residui includono UI programmatica, proprietà dipendenti dalla versione
Android e risorse richiamate dinamicamente: il dettaglio è nei report lint.

I test non coprono visualmente tutte le schermate e non sostituiscono l'esecuzione
reale su KitKat, la riproduzione audio o le dipendenze native dei font/provider TLS.
Non vengono avviati test strumentali sul tablet durante questa consegna: l'utente
ha chiesto di fermarsi all'installazione, lasciando a lui apertura e controlli.

Per nuove modifiche, aggiungere test di comportamento su regole/dati; evitare test
che ripetano soltanto la sequenza di costruzione dei widget.
