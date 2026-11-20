package it.timewall.app.data.calendar;

import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.platform.tls.Tls12SocketFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public final class CalendarFeed {

    /**
     * Verifica che l’indirizzo sia HTTPS, abbia un host e una porta valida e non contenga
     * credenziali, frammenti o spazi. Non effettua richieste di rete.
     *
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @return true soltanto per un indirizzo HTTPS che rispetta tutti i vincoli locali
     */
    public static boolean accepts(String address) {
        try {
            URL url = new URL(address);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && !url.getHost().isEmpty()
                    && (url.getPort() == -1 || (url.getPort() > 0 && url.getPort() <= 65535))
                    && url.getUserInfo() == null
                    && url.getRef() == null
                    && !address.matches(".*\\s.*");
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Scarica il feed HTTPS con TLS 1.2, timeout di dieci secondi e limite di 8 MiB. Rifiuta
     * risposte diverse da HTTP 200 e redirect, chiudendo sempre la connessione.
     *
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @return corpo del feed ricevuto con HTTP 200, entro il limite di 8 MiB
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    public static byte[] download(String address) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(address).openConnection();
        connection.setSSLSocketFactory(new Tls12SocketFactory(connection.getSSLSocketFactory()));
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Risposta HTTP inattesa");
            }
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > 8 * 1024 * 1024) {
                        throw new IOException("Calendario troppo grande");
                    }
                    body.write(buffer, 0, count);
                }
                return body.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Calcola il digest SHA-256 dei byte ricevuti per riconoscere un feed invariato senza ripeterne
     * il parsing.
     *
     * @param data byte UTF-8 del feed da elaborare
     * @return digest SHA-256 di 32 byte del corpo ricevuto
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static byte[] hash(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    /**
     * Delega al parser iCal offline mantenendo il punto di ingresso usato dai chiamanti esistenti;
     * assegna a ogni evento il colore e l’origine del calendario.
     *
     * <p>Compatibility entry point; parsing has no dependency on Android or the network.
     *
     * @param data byte UTF-8 del feed da elaborare
     * @param color colore ARGB da associare al contenuto
     * @param feedAddress indirizzo del calendario di origine usato per identità e deduplicazione
     * @return occorrenze valide lette dal feed, con colore e origine assegnati
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    public static List<CalendarEvent> parse(byte[] data, int color, String feedAddress)
            throws IOException {
        return IcalendarParser.parse(data, color, feedAddress);
    }
}
