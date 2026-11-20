package it.timewall.app.platform.tls;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;

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
            return socket;
        } catch (IllegalArgumentException exception) {
            socket.close();
            throw new IOException("TLS 1.2 non disponibile", exception);
        }
    }
}
