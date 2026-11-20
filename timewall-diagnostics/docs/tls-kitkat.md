# Open-Meteo su Android 4.4.2

Il tablet supporta TLS 1.2 ma il provider di sistema non disponeva di alcuna delle
suite negoziabili con Open-Meteo. Abilitare solo `TLSv1.2` non aggiunge algoritmi:
per questo il semplice wrapper non bastava. Separatamente, un trust store vecchio
può produrre `Trust anchor for certification path not found`.

Per API <= 20, `OpenMeteoClient` costruisce **per quella connessione** una factory:

1. `OpenMeteoTrust` carica la root ISRG Root X1 inclusa negli assets in un KeyStore
   dedicato e ne ricava i TrustManager.
2. `PlayServicesTls` verifica che `com.google.android.gms` sia un'app di sistema,
   carica il provider `GmsCore_OpenSSL` dal suo APK e crea un SSLContext con quei manager.
3. `Tls12SocketFactory` abilita TLS 1.2 sui socket della factory risultante.

La verifica X.509 e la verifica del nome host restano attive. Non vengono installati
TrustManager permissivi, hostname verifier che restituiscono sempre true o un trust
store globale alternativo. Le richieste moderne (API > 20) usano lo stack di sistema.
I calendari conservano il proprio percorso TLS di sistema con TLS 1.2 abilitato.

Il caricamento usa per riflessione `ProviderInstallerImpl`, API interna del vecchio
Google Play Services: dipende dalla sua presenza/versione sul tablet e può fallire
su altri dispositivi. Non è un provider TLS incorporato nell'APK. Se il provider non
esisteva già, viene rimosso dal registro globale nel `finally`, dopo averne ricavato
la factory. Questo non altera il trust store del dispositivo.

La root inclusa non è un pin del certificato del server: continua a verificare la
catena fino a quell'autorità. Una futura variazione della catena del provider può
richiedere manutenzione. Non sostituire mai questo percorso con una disattivazione
dei controlli TLS.

I test JVM verificano protocollo e root inclusa, non possono dimostrare un handshake
sullo stack nativo KitKat. Quella prova appartiene all'app diagnostica, indipendente.
