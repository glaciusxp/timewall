# Correzioni TLS e log — dashboard 0.99

Verifica del 20 novembre 2026 su Asus TF303CL, Android 4.4.2/API 19,
Google Play Services 23.30.13. Build 99, target 36, minSdk 19.

## R1: isolamento del provider — risolto nell'ambiente verificato

L'installer globale non viene più invocato. OpenSSLProvider è istanziato per
richiesta tramite il ClassLoader del package di sistema; SSLContext lo riceve
esplicitamente, senza inserirlo nel registro Security. Nessuna nuova dipendenza.
Questo evita ripristini di stato globale soggetti a race con i calendari.

Le prove sul dispositivo confermano identità invariata della factory HTTPS e
di SSLContext predefiniti, hostname verifier e registro provider invariati.
Funzionano contemporaneamente previsioni Open-Meteo, geocoding e download di un
calendario festivo Google pubblico, anche a processo freddo. Certificato non
attendibile e nome host errato vengono rifiutati. Installer assente non è più
una dipendenza: l'assenza del codice provider ora produce un errore chiuso.

## R2: messaggi sensibili nei log — risolto per i percorsi meteo

WeatherLog usa la sola firma Log.w(tag, message). Il messaggio è costruito da
Operation e WeatherFailure enumerate. Il Throwable non viene inoltrato e i suoi
messaggi non vengono letti. Le cause sono attraversate al massimo otto volte.
I test includono URL iCal sintetici, query, coordinate, titoli e JSON malformati.

Il probe conferma sul tablet che JSONException può conservare un marcatore della
risposta: la correzione è quindi al confine di logging, senza assumere che il
parser ripulisca l'eccezione. Il log effettivo contiene le categorie INVALID_RESPONSE
e TLS_PROVIDER_UNAVAILABLE; non contiene il marcatore sintetico segreto o URL.
Nessun log grezzo o dato personale è incluso in questi sorgenti.

## Verifiche

- 55 test JVM, zero fallimenti; lintRelease e formattazione superati.
- 38 controlli strumentati, zero fallimenti: 6 cold, 24 core, 8 UI.
- HTTP 500, redirect, JSON invalido, limite 64 KiB e timeout simulati sul client reale.
- Retry a cinque minuti, meteo disabilitato, pausa/ripresa, conservazione dei dati
  precedenti e fallimento provider senza crash verificati nel controller reale.
- Handshake TLS 1.2 con TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256.
- Preferenze e calendari utente conservati. La UI può aggiornare il normale
  timestamp del meteo. Sonda temporanea rimossa dal dispositivo.

Riproduzione: `verification/tls/README.md` e `verification/tls/run.ps1`.
Il codice di verifica non è una dipendenza runtime dell'APK ufficiale e non contiene
chiavi private. Gli output del probe restano in build, esclusi dagli archivi sorgenti.
I testi UI non sono stati modificati: restano allineate le nove lingue.

## Rischi residui

La classe di Play Services rimane interna e può mancare su altre versioni/ROM:
nessun fallback insicuro è previsto. Il flag SYSTEM non è una verifica esplicita
della firma Google. KitKat e il suo Play Services non ricevono aggiornamenti di
sicurezza moderni: queste prove non certificano lo stack nativo. Restano separati
i test su Android recenti, le future rotazioni delle CA e l'eventuale miglioramento
della cancellazione delle richieste in corso e del timeout complessivo.
