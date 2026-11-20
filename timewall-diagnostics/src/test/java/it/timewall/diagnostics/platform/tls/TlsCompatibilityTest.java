package it.timewall.diagnostics.platform.tls;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.InputStream;
import java.nio.file.*;
import java.security.cert.*;

import javax.net.ssl.*;

public class TlsCompatibilityTest {
    /**
     * Verifica su un socket non connesso l’abilitazione di TLS 1.2 e l’assenza di modifiche ai
     * default globali HTTPS.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void adapterEnablesTls12WithoutChangingGlobalDefaults() throws Exception {
        SSLSocketFactory original = HttpsURLConnection.getDefaultSSLSocketFactory();
        Tls12SocketFactory adapter = new Tls12SocketFactory(original);
        try (SSLSocket socket = (SSLSocket) adapter.createSocket()) {
            assertArrayEquals(new String[] {"TLSv1.2"}, socket.getEnabledProtocols());
            assertSame(original, HttpsURLConnection.getDefaultSSLSocketFactory());
            assertArrayEquals(
                    original.getSupportedCipherSuites(), adapter.getSupportedCipherSuites());
        }
    }

    /**
     * Verifica che la root DER inclusa sia una CA ISRG Root X1 con firma autofirmata verificabile.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void bundledRootIsSelfSignedCa() throws Exception {
        try (InputStream input =
                Files.newInputStream(Paths.get("src/main/assets/isrg-root-x1.der"))) {
            X509Certificate cert =
                    (X509Certificate)
                            CertificateFactory.getInstance("X.509").generateCertificate(input);
            assertTrue(cert.getBasicConstraints() >= 0);
            assertTrue(cert.getSubjectX500Principal().getName().contains("ISRG Root X1"));
            cert.verify(cert.getPublicKey());
        }
    }
}
