# Open-Meteo su Android 4.4.2

Il tablet supporta TLS 1.2 ma il provider di sistema non disponeva di alcuna delle
suite negoziabili con Open-Meteo. Abilitare solo `TLSv1.2` non aggiunge algoritmi:
per questo il semplice wrapper non bastava. Separatamente, un trust store vecchio
può produrre `Trust anchor for certification path not found`.

Per API <= 20, `OpenMeteoClient` costruisce **per quella connessione** una factory:

1. `OpenMeteoTrust` carica la root ISRG Root X1 inclusa negli assets in un KeyStore
   dedicato e ne ricava i TrustManager.
2. `PlayServicesTls` verifica che `com.google.android.gms` sia un'app di sistema,
   carica direttamente `OpenSSLProvider` dal suo APK e ne crea un'istanza dedicata
   chiamata `TimeWallLegacyTLS`, senza registrarla in Security. SSLContext usa
   esplicitamente questa istanza con i TrustManager forniti.
3. `Tls12SocketFactory` abilita TLS 1.2 sui socket della factory risultante.

La verifica X.509 e la verifica del nome host restano attive. Non vengono installati
TrustManager permissivi, hostname verifier che restituiscono sempre true o un trust
store globale alternativo. Le richieste moderne (API > 20) usano lo stack di sistema.
I calendari conservano il proprio percorso TLS di sistema con TLS 1.2 abilitato.

La versione 0.99 non usa più `ProviderInstallerImpl`: l'audit sul tablet ha mostrato
che l'installer cambia la factory HTTPS globale anche rimuovendo poi il provider.
L'istanza diretta non modifica factory predefinita, SSLContext globale o registro,
quindi non richiede salvataggi/ripristini globali durante richieste concorrenti.
Il caricamento usa comunque una classe interna del vecchio Google Play Services:
dipende dalla sua presenza/versione e può fallire su altri dispositivi. Package,
API o librerie native indisponibili fanno fallire la richiesta senza fallback
all'installer, HTTP o disattivazione dei controlli. Il provider non è incorporato
nell'APK. La provenienza del package si basa sul flag SYSTEM, non su un controllo
esplicito della firma Google.

La root inclusa non è un pin del certificato del server: continua a verificare la
catena fino a quell'autorità. Una futura variazione della catena del provider può
richiedere manutenzione. Non sostituire mai questo percorso con una disattivazione
dei controlli TLS.

I test JVM verificano protocollo, root e classificazione/redazione degli errori.
`verification/tls/run.ps1` prova la release sul tablet: handshake, hostname,
certificati non attendibili, errori simulati, invarianti globali, richieste
meteo/geocoding/calendario concorrenti a processo freddo e gestione UI degli errori.

DashboardWeather usa WeatherLog, che emette solo operazione e categoria enumerate:
nessun Throwable, messaggio arbitrario, risposta JSON, query, coordinata o URL.
WeatherFailure esamina al massimo otto cause per evitare cicli. Questi log tecnici
non sono testi dell'interfaccia e non dipendono dalla lingua dell'utente.

Il supporto Play Services per KitKat è terminato nel 2023. Il test non certifica
l'assenza di vulnerabilità dello stack nativo. Una cancellazione Future invalida
le callback ma non impone una scadenza globale di rete: restano i timeout del
client e la disconnessione nel finally. Vanno mantenute prove su versioni moderne.

https://developer.android.com/privacy-and-security/security-gms-provider
https://android-developers.googleblog.com/2023/07/google-play-services-discontinuing-updates-for-kitkat.html
