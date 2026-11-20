package it.timewall.app.platform.tls;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.security.GeneralSecurityException;
import java.security.Provider;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Creates an unregistered legacy Play Services provider for a dedicated SSLContext. Never invokes
 * ProviderInstallerImpl: that installer also replaces global HTTPS defaults on KitKat.
 */
public final class PlayServicesTls {
    private static final String PACKAGE = "com.google.android.gms";
    private static final String PROVIDER_CLASS =
            "com.google.android.gms.org.conscrypt.OpenSSLProvider";

    /**
     * Crea una factory TLS dedicata usando il provider della copia di sistema di Google Play
     * Services e i TrustManager di sistema, senza modificare le impostazioni HTTPS globali.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @return factory TLS configurata con il provider e i controlli di fiducia descritti
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static SSLSocketFactory createSocketFactory(Context context) throws Exception {
        TrustManagerFactory trust =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init((java.security.KeyStore) null);
        return createSocketFactory(context, trust.getTrustManagers());
    }

    /**
     * Carica direttamente la classe del provider dall'app di sistema e ne crea un'istanza privata
     * per questa connessione. Il provider non viene registrato: richieste concorrenti non devono
     * salvare e ripristinare stato globale. Assenza del package, API incompatibili o librerie
     * native mancanti fanno fallire la richiesta, senza ricorrere all'installer o aggirare TLS.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param managers TrustManager da usare nel contesto TLS dedicato
     * @return factory TLS configurata con il provider e i controlli di fiducia descritti
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static SSLSocketFactory createSocketFactory(Context context, TrustManager[] managers)
            throws Exception {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(PACKAGE, 0);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                throw new GeneralSecurityException("Legacy TLS package is not a system app");
            }
            Context services =
                    context.createPackageContext(
                            PACKAGE,
                            Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Class<?> type = services.getClassLoader().loadClass(PROVIDER_CLASS);
            if (!Provider.class.isAssignableFrom(type)) {
                throw new GeneralSecurityException("Legacy TLS provider has an incompatible type");
            }
            // NativeCrypto initializes through the package ClassLoader; no global installer is
            // needed.
            Provider provider =
                    (Provider) type.getConstructor(String.class).newInstance("TimeWallLegacyTLS");
            SSLContext tls = SSLContext.getInstance("TLS", provider);
            tls.init(null, managers, null);
            return tls.getSocketFactory();
        } catch (PackageManager.NameNotFoundException
                | ReflectiveOperationException
                | LinkageError failure) {
            throw new GeneralSecurityException("Legacy TLS provider unavailable", failure);
        }
    }
}
