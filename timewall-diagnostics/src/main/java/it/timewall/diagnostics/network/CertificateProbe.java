package it.timewall.diagnostics.network;

import android.content.Context;

import it.timewall.diagnostics.platform.tls.PlayServicesTls;
import it.timewall.diagnostics.platform.tls.Tls12SocketFactory;

import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class CertificateProbe {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private CertificateProbe() {}

    /**
     * Ispeziona la catena X.509 usando il provider di sistema o, nell’overload dedicato, Play
     * Services. Registra emittenti ed esito della verifica, poi interrompe intenzionalmente TLS
     * prima di inviare una richiesta HTTP.
     *
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @return report della catena e della validazione, oppure motivo per cui la catena non è stata
     *     ricevuta
     */
    public static String inspect(String address) {
        return inspect(address, null);
    }

    /**
     * Ispeziona la catena X.509 usando il provider di sistema o, nell’overload dedicato, Play
     * Services. Registra emittenti ed esito della verifica, poi interrompe intenzionalmente TLS
     * prima di inviare una richiesta HTTP.
     *
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @param playServicesContext contesto per caricare il provider alternativo; null per il
     *     provider di sistema
     * @return report della catena e della validazione, oppure motivo per cui la catena non è stata
     *     ricevuta
     */
    public static String inspect(String address, Context playServicesContext) {
        HttpsURLConnection connection = null;
        final StringBuilder report = new StringBuilder();
        try {
            TrustManagerFactory factory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            X509TrustManager system = (X509TrustManager) factory.getTrustManagers()[0];
            X509TrustManager inspector =
                    new X509TrustManager() {
                        /**
                         * Registra i certificati ricevuti e delega la validazione al trust manager
                         * di sistema. Anche dopo una validazione riuscita solleva intenzionalmente
                         * CertificateException per terminare la sola ispezione prima dell’HTTP.
                         *
                         * @param chain catena di certificati X.509 ricevuta dal peer
                         * @param authType tipo di autenticazione TLS comunicato al trust manager
                         * @throws CertificateException se la verifica del certificato fallisce o la
                         *     sonda interrompe intenzionalmente l’ispezione TLS
                         */
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                            for (X509Certificate certificate : chain) {
                                report.append("Certificato: ")
                                        .append(certificate.getSubjectX500Principal().getName())
                                        .append("\nEmesso da: ")
                                        .append(certificate.getIssuerX500Principal().getName())
                                        .append("\n\n");
                            }
                            try {
                                system.checkServerTrusted(chain, authType);
                                report.append("Catena accettata dall’archivio di sistema.");
                            } catch (CertificateException exception) {
                                report.append("Catena rifiutata: ").append(exception.getMessage());
                                throw exception;
                            }
                            // Inspect only: abort TLS before sending an HTTP request.
                            throw new CertificateException("Fine ispezione");
                        }

                        /**
                         * Delega integralmente la validazione dei certificati client al trust
                         * manager di sistema senza introdurre eccezioni di fiducia.
                         *
                         * @param chain catena di certificati X.509 ricevuta dal peer
                         * @param authType tipo di autenticazione TLS comunicato al trust manager
                         * @throws CertificateException se la verifica del certificato fallisce o la
                         *     sonda interrompe intenzionalmente l’ispezione TLS
                         */
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType)
                                throws CertificateException {
                            system.checkClientTrusted(chain, authType);
                        }

                        /**
                         * Restituisce le autorità accettate dal trust manager di sistema usato
                         * dalla sonda.
                         *
                         * @return autorità accettate dal trust manager di sistema
                         */
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return system.getAcceptedIssuers();
                        }
                    };
            TrustManager[] managers = {inspector};
            SSLSocketFactory sockets;
            if (playServicesContext == null) {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, managers, null);
                sockets = context.getSocketFactory();
            } else {
                sockets = PlayServicesTls.createSocketFactory(playServicesContext, managers);
            }
            connection = (HttpsURLConnection) new URL(address).openConnection();
            connection.setSSLSocketFactory(new Tls12SocketFactory(sockets));
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
        } catch (Exception | LinkageError exception) {
            if (report.length() == 0) {
                return "Catena non ricevuta: " + exception.getClass().getSimpleName();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return report.toString();
    }
}
