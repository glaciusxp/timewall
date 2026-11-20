package it.timewall.app.platform.tls;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Acquires the legacy system Play Services provider without replacing global HTTPS defaults. The
 * reflected installer is version-dependent; see docs/tls-kitkat.md before changing this path.
 */
public final class PlayServicesTls {
    private static final String PACKAGE = "com.google.android.gms";
    private static final String PROVIDER = "GmsCore_OpenSSL";

    /**
     * Ricava una factory TLS dal provider della copia di sistema di Google Play Services, con i
     * TrustManager forniti oppure quelli di sistema nell’overload breve. Usa l’installer legacy per
     * riflessione e rimuove dal registro globale il provider se non era già presente.
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
     * Ricava una factory TLS dal provider della copia di sistema di Google Play Services, con i
     * TrustManager forniti oppure quelli di sistema nell’overload breve. Usa l’installer legacy per
     * riflessione e rimuove dal registro globale il provider se non era già presente.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param managers TrustManager da usare nel contesto TLS dedicato
     * @return factory TLS configurata con il provider e i controlli di fiducia descritti
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static SSLSocketFactory createSocketFactory(Context context, TrustManager[] managers)
            throws Exception {
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(PACKAGE, 0);
        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            throw new GeneralSecurityException("Google Play Services non è un'app di sistema.");
        }

        Provider previous = Security.getProvider(PROVIDER);
        try {
            Context services =
                    context.createPackageContext(
                            PACKAGE,
                            Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            // The legacy Play Services APK exposes the installer used by ProviderInstaller.
            Class<?> installer =
                    services.getClassLoader()
                            .loadClass(
                                    "com.google.android.gms.common.security.ProviderInstallerImpl");
            installer.getMethod("insertProvider", Context.class).invoke(null, services);
            Provider provider = Security.getProvider(PROVIDER);
            if (provider == null) {
                throw new GeneralSecurityException("Provider " + PROVIDER + " assente.");
            }
            SSLContext tls = SSLContext.getInstance("TLS", provider);
            tls.init(null, managers, null);
            return tls.getSocketFactory();
        } finally {
            // SSLContext retains its provider; callers need not leave it globally registered.
            if (previous == null) {
                Security.removeProvider(PROVIDER);
            }
        }
    }
}
