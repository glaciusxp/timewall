# Architettura

| Package | Responsabilità |
| --- | --- |
| `configuration` | Cataloghi font/temi, palette, validazione HEX e compatibilità preferenze |
| `domain.model` | Eventi, sorgenti calendario, località, condizioni e codici meteo |
| `domain.alerts` | Decisione e deduplicazione delle notifiche, con tempo passato come parametro |
| `domain.time` | Selezione evento attivo/prossimo e confini dei giorni locali |
| `domain.weather` | Intervalli di aggiornamento e retry |
| `data.calendar` | Download, parser offline, repository con cache e persistenza atomica |
| `data.weather` | Richieste Open-Meteo e parsing JSON |
| `platform.tls` | Adattatore TLS 1.2, provider alternativo e trust store circoscritto |
| `platform.images` | Storage Access Framework, metadati e decodifica campionata |
| `localization` | Risorse tradotte e formattazione locale di data/ora |
| `ui.common` | Stile risolto e costruzione coerente di piccoli componenti Views |
| `ui.dashboard` | Activity/lifecycle, layout orizzontale, dialogo dettaglio evento |
| `ui.settings` | Schermata impostazioni, bozza non salvata, calendari, sfondo e color picker |
| `ui.weather` | Visualizzazione, scheduling Android e ricerca località |

Le dipendenze del dominio non puntano alle Views o alla diagnostica. Il repository
calendari riceve transport/store e pubblica prima la cache, poi l'esito di rete.
Rimane sincrono per essere testabile; l'Activity lo esegue su un singolo worker.
Il contatore di revisione dell'Activity scarta risultati non più pertinenti.

La notifica riceve istanti espliciti, quindi i test non aspettano timer reali.
Lo scheduling meteo usa `elapsedRealtime`; l'ora mostrata dell'ultimo aggiornamento
usa il tempo civile, salvato nelle preferenze. Un fallimento riprova dopo 5 minuti.

Le impostazioni possiedono una bozza: i controlli di validità e la conferma di uscita
restano locali alla schermata. Il salvataggio continua a usare le chiavi storiche.
Non sono state introdotte librerie UI recenti che alzino la versione Android minima.

## Gradle e Maven

È stato adottato Gradle: il plugin Android gestisce manifest, risorse, desugaring,
APK, firma, JUnit e lint con una pipeline standard, integrata con Android Studio.
Il costo è una configurazione più articolata e il download iniziale degli strumenti.
Maven sarebbe adeguato per moduli Java puri, ma aggiungerebbe una seconda pipeline
per risorse/APK Android e una manutenzione non giustificata da questo progetto.

Le versioni degli strumenti sono fissate; i dependency lock rendono espliciti gli
aggiornamenti delle dipendenze risolte. Nessuna dipendenza runtime nuova è richiesta
per i test: JUnit e l'implementazione JSON JVM esistono solo in `testImplementation`.

## Risorse

- `src/main/assets/`: font TTF/OTF, Font Awesome e relativa licenza, root DER TLS.
- `src/main/res/`: stringhe/plurali tradotti, icone Android convertite e suono attivo.
- `design/icons/`: SVG meteo originali e sorgente dell'icona launcher; non letti a runtime.

I font restano **alla radice degli assets** e non compressi (`ttf`, `otf`), per
conservare la soluzione già necessaria al caricamento su KitKat. I nomi dei file
sono valori persistiti e non vanno cambiati senza migrazione. Gli SVG originali
non sostituiscono i PNG convertiti: Android 4.4 non carica nativamente VectorDrawable.

Il suono effettivo è `res/raw/notifica_evento.wav`, conservato byte per byte.
I font forniti dall'utente conservano i nomi originali; eventuale distribuzione
pubblica richiede di conservare/verificare le licenze dei rispettivi autori.

## Convenzioni

Preferire classi con una responsabilità e dipendenze esplicite. Evitare classi
`Utils` generiche e gerarchie di Activity usate per distribuire codice tra file.
Commentare vincoli e ragioni tecniche, senza descrivere ogni istruzione.
Il formato Java adottato è Google Java Format, stile AOSP.
Non registrare URL segrete dei calendari, contenuto degli eventi o query personali.

Possibili evoluzioni separate: estrarre ulteriormente i binding delle sezioni delle
impostazioni, aggiungere test strumentali in un ambiente API 19 dedicato e coprire
nuove regole di ricorrenza solo dopo una specifica funzionale. Il parser attuale
non espande RRULE/EXDATE: la riorganizzazione non ne modifica questa limitazione.
