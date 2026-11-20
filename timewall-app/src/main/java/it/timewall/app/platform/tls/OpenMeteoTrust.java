package it.timewall.app.platform.tls;

import android.content.Context;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Scoped CA trust for the KitKat Open-Meteo connection; hostname verification is untouched. */
public final class OpenMeteoTrust {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private OpenMeteoTrust() {}

    /**
     * Carica ISRG Root X1 dagli assets in un KeyStore dedicato e ricava una factory dal provider
     * Play Services. Il trust è limitato a quella factory: non sostituisce i default HTTPS né la
     * verifica del nome host.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @return factory TLS configurata con il provider e i controlli di fiducia descritti
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static SSLSocketFactory createSocketFactory(Context context) throws Exception {
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        try (InputStream root = context.getAssets().open("isrg-root-x1.der")) {
            trust.setCertificateEntry(
                    "isrg-root-x1",
                    CertificateFactory.getInstance("X.509").generateCertificate(root));
        }

        TrustManagerFactory manager =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        manager.init(trust);
        return PlayServicesTls.createSocketFactory(context, manager.getTrustManagers());
    }
}
