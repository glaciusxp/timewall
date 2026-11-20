package it.timewall.app.localization;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Arrays;
import java.util.Locale;

public final class I18n {
    public static final String LANGUAGE_PREFERENCE = "language";
    public static final String SYSTEM = "system";

    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private I18n() {}

    /**
     * Legge l’elenco centralizzato dei codici lingua supportati dalle risorse Android.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @return codici lingua dichiarati nell’array centralizzato delle risorse
     */
    public static String[] supportedLanguages(Context context) {
        return context.getResources().getStringArray(id(context, "array", "language_codes"));
    }

    /**
     * Risolve la scelta esplicita o la lingua di sistema. Se la lingua richiesta non è nell’elenco
     * supportato restituisce sempre inglese.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param selected scelta della lingua o indice selezionato
     * @param systemLanguage codice della lingua configurata sul dispositivo
     * @return codice supportato della lingua scelta oppure en come fallback
     */
    public static String resolveLanguage(Context context, String selected, String systemLanguage) {
        String requested = SYSTEM.equals(selected) ? systemLanguage : selected;
        return Arrays.asList(supportedLanguages(context)).contains(requested) ? requested : "en";
    }

    /**
     * Applica alle risorse dell’app la lingua salvata o quella di sistema con fallback inglese. Usa
     * la configurazione compatibile con KitKat e non cambia la lingua del dispositivo.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param preferences archivio delle preferenze da leggere o aggiornare
     */
    @SuppressWarnings("deprecation")
    public static void apply(Context context, SharedPreferences preferences) {
        String systemLanguage = Resources.getSystem().getConfiguration().locale.getLanguage();
        String language =
                resolveLanguage(
                        context,
                        preferences.getString(LANGUAGE_PREFERENCE, SYSTEM),
                        systemLanguage);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(new Locale(language));
        context.getResources()
                .updateConfiguration(configuration, context.getResources().getDisplayMetrics());
    }

    /**
     * Restituisce la locale della configurazione attualmente applicata alle risorse del contesto.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @return locale attualmente applicata al contesto Android
     */
    @SuppressWarnings("deprecation")
    public static Locale locale(Context context) {
        return context.getResources().getConfiguration().locale;
    }

    /**
     * Risolve una stringa mediante la sua chiave e formatta gli eventuali argomenti nella lingua
     * attiva.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param key chiave della proprietà o risorsa richiesta
     * @param arguments argomenti da sostituire nei segnaposto della stringa
     * @return stringa localizzata con gli argomenti eventualmente sostituiti
     */
    public static String text(Context context, String key, Object... arguments) {
        int resource = id(context, "string", key);
        return arguments.length == 0
                ? context.getString(resource)
                : context.getString(resource, arguments);
    }

    /**
     * Risolve in ordine un insieme di chiavi di stringhe, per esempio per popolare un selettore
     * localizzato.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param keys chiavi delle stringhe, nell’ordine di visualizzazione
     * @return stringhe tradotte nello stesso ordine delle chiavi
     */
    public static String[] texts(Context context, String[] keys) {
        String[] values = new String[keys.length];
        for (int index = 0; index < keys.length; index++) {
            values[index] = text(context, keys[index]);
        }
        return values;
    }

    /**
     * Sceglie la forma plurale per il conteggio e formatta il valore completo senza perdere il
     * conteggio originale.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param key chiave della proprietà o risorsa richiesta
     * @param count conteggio per i plurali o numero di caratteri coinvolti nella callback
     * @return conteggio formattato con la forma plurale appropriata
     */
    public static String quantity(Context context, String key, long count) {
        int quantity = (int) Math.min(Integer.MAX_VALUE, Math.max(0, count));
        return context.getResources()
                .getQuantityString(id(context, "plurals", key), quantity, count);
    }

    /**
     * Risolve dinamicamente una risorsa per tipo e nome nel package dell’app; segnala una chiave
     * mancante anziché usare silenziosamente una label errata.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param type tipo della risorsa Android cercata
     * @param name nome della voce, del campo o della località
     * @return identificativo Android della risorsa trovata
     */
    private static int id(Context context, String type, String name) {
        int id = context.getResources().getIdentifier(name, type, context.getPackageName());
        if (id == 0) {
            throw new IllegalArgumentException("Missing resource: " + type + "/" + name);
        }
        return id;
    }
}
