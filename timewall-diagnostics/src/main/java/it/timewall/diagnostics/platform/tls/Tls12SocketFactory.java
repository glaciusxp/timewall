package it.timewall.diagnostics.platform.tls;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class Tls12SocketFactory extends SSLSocketFactory {
    private static final List<String> OPEN_METEO_TLS12_SUITES =
            Arrays.asList(
                    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256");
    private final SSLSocketFactory delegate;
    private String[] supportedSuites;
    private String[] enabledSuites;

    /**
     * Avvolge la factory ricevuta senza cambiarne il provider o i controlli di fiducia; TLS 1.2
     * viene abilitato sui singoli socket creati.
     *
     * @param delegate factory SSL del provider effettivo
     */
    public Tls12SocketFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    /**
     * Restituisce le suite predefinite della factory delegata, senza aggiungerne o abilitarne
     * altre.
     *
     * @return suite predefinite esposte dalla factory delegata
     */
    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    /**
     * Restituisce le suite supportate dal provider delegato. Il wrapper non può aggiungere
     * algoritmi mancanti al provider.
     *
     * @return suite supportate esposte dalla factory delegata
     */
    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    /**
     * Delega la creazione del socket all’overload corrispondente e abilita TLS 1.2 sul socket
     * ottenuto. Eventuali destinazione, binding locale e regola di chiusura del socket sottostante
     * restano quelli richiesti.
     *
     * @return socket creato dal delegato con TLS 1.2 abilitato
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    @Override
    public Socket createSocket() throws IOException {
        return configure(delegate.createSocket());
    }

    /**
     * Delega la creazione del socket all’overload corrispondente e abilita TLS 1.2 sul socket
     * ottenuto. Eventuali destinazione, binding locale e regola di chiusura del socket sottostante
     * restano quelli richiesti.
     *
     * @param socket socket TLS o connessione sottostante a cui applicare il comportamento
     * @param host destinatario delle callback della schermata; nelle factory TLS indica l’host
     *     remoto
     * @param port porta remota
     * @param autoClose true per chiudere anche il socket sottostante quando viene chiuso quello TLS
     * @return socket creato dal delegato con TLS 1.2 abilitato
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
            throws IOException {
        return configure(delegate.createSocket(socket, host, port, autoClose));
    }

    /**
     * Delega la creazione del socket all’overload corrispondente e abilita TLS 1.2 sul socket
     * ottenuto. Eventuali destinazione, binding locale e regola di chiusura del socket sottostante
     * restano quelli richiesti.
     *
     * @param host destinatario delle callback della schermata; nelle factory TLS indica l’host
     *     remoto
     * @param port porta remota
     * @return socket creato dal delegato con TLS 1.2 abilitato
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return configure(delegate.createSocket(host, port));
    }

    /**
     * Delega la creazione del socket all’overload corrispondente e abilita TLS 1.2 sul socket
     * ottenuto. Eventuali destinazione, binding locale e regola di chiusura del socket sottostante
     * restano quelli richiesti.
     *
     * @param host destinatario delle callback della schermata; nelle factory TLS indica l’host
     *     remoto
     * @param port porta remota
     * @param local indirizzo locale a cui associare il socket
     * @param localPort porta locale richiesta
     * @return socket creato dal delegato con TLS 1.2 abilitato
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress local, int localPort)
            throws IOException {
        return configure(delegate.createSocket(host, port, local, localPort));
    }

    /**
     * Delega la creazione del socket all’overload corrispondente e abilita TLS 1.2 sul socket
     * ottenuto. Eventuali destinazione, binding locale e regola di chiusura del socket sottostante
     * restano quelli richiesti.
     *
     * @param host destinatario delle callback della schermata; nelle factory TLS indica l’host
     *     remoto
     * @param port porta remota
     * @return socket creato dal delegato con TLS 1.2 abilitato
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return configure(delegate.createSocket(host, port));
    }

    /**
     * Delega la creazione del socket all’overload corrispondente e abilita TLS 1.2 sul socket
     * ottenuto. Eventuali destinazione, binding locale e regola di chiusura del socket sottostante
     * restano quelli richiesti.
     *
     * @param host destinatario delle callback della schermata; nelle factory TLS indica l’host
     *     remoto
     * @param port porta remota
     * @param local indirizzo locale a cui associare il socket
     * @param localPort porta locale richiesta
     * @return socket creato dal delegato con TLS 1.2 abilitato
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort)
            throws IOException {
        return configure(delegate.createSocket(host, port, local, localPort));
    }

    /**
     * Abilita esclusivamente TLS 1.2 sul socket delegato; nella diagnostica registra anche le suite
     * supportate e abilitate. Se il protocollo non è disponibile chiude il socket e propaga un
     * IOException.
     *
     * @param socket socket TLS o connessione sottostante a cui applicare il comportamento
     * @return stesso socket ricevuto, configurato per TLS 1.2
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    private Socket configure(Socket socket) throws IOException {
        SSLSocket tlsSocket = (SSLSocket) socket;
        try {
            tlsSocket.setEnabledProtocols(new String[] {"TLSv1.2"});
            supportedSuites = tlsSocket.getSupportedCipherSuites();
            enabledSuites = tlsSocket.getEnabledCipherSuites();
            return socket;
        } catch (IllegalArgumentException exception) {
            socket.close();
            throw new IOException("TLS 1.2 non disponibile", exception);
        }
    }

    /**
     * Compone il report delle suite rilevate sull’ultimo socket, evidenziando quante delle tre
     * suite Open-Meteo risultano supportate e abilitate.
     *
     * @return report delle suite rilevate o messaggio di socket non ancora configurato
     */
    public String describeCipherSuites() {
        if (supportedSuites == null || enabledSuites == null) {
            return "\n\nSuite TLS non rilevate: socket TLS 1.2 non configurato.";
        }
        return "\n\nSuite del report Open-Meteo: "
                + countOpenMeteoSuites(supportedSuites)
                + "/3 supportate, "
                + countOpenMeteoSuites(enabledSuites)
                + "/3 abilitate."
                + "\n\nSuite supportate:\n"
                + joinLines(supportedSuites)
                + "\n\nSuite abilitate:\n"
                + joinLines(enabledSuites);
    }

    /**
     * Conta le suite dell’elenco che appartengono al gruppo richiesto dal report Open-Meteo, senza
     * modificare la configurazione TLS.
     *
     * @param suites nomi delle suite TLS da contare o concatenare
     * @return numero di corrispondenze con le tre suite del report Open-Meteo
     */
    private static int countOpenMeteoSuites(String[] suites) {
        int count = 0;
        for (String suite : suites) {
            if (OPEN_METEO_TLS12_SUITES.contains(suite)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Concatena i nomi delle suite su righe distinte, senza aggiungere una nuova riga iniziale.
     *
     * @param suites nomi delle suite TLS da contare o concatenare
     * @return nomi concatenati con un separatore di nuova riga
     */
    private static String joinLines(String[] suites) {
        StringBuilder lines = new StringBuilder();
        for (String suite : suites) {
            if (lines.length() > 0) {
                lines.append('\n');
            }
            lines.append(suite);
        }
        return lines.toString();
    }
}
