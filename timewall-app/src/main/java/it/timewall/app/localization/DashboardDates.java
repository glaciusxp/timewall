package it.timewall.app.localization;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Existing locale-specific display conventions, separate from event time calculations. */
public final class DashboardDates {
    private final Context context;

    /**
     * Conserva il contesto da cui leggere la lingua e i pattern della data applicati
     * all’applicazione.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     */
    public DashboardDates(Context context) {
        this.context = context;
    }

    /**
     * Formatta un istante con il pattern richiesto e la lingua attualmente applicata
     * all’applicazione.
     *
     * @param time istante civile da formattare o delimitare, in millisecondi
     * @param pattern pattern SimpleDateFormat da applicare
     * @return istante formattato con pattern e lingua richiesti
     */
    public String format(long time, String pattern) {
        return new SimpleDateFormat(pattern, I18n.locale(context)).format(new Date(time));
    }

    /**
     * Formatta la data degli eventi usando il pattern tradotto, applicando le convenzioni di
     * maiuscole mantenute dalla dashboard.
     *
     * @param time istante civile da formattare o delimitare, in millisecondi
     * @return data degli eventi secondo il pattern tradotto e le convenzioni di maiuscole
     */
    public String dateLabel(long time) {
        return localizedDate(time, "date_pattern");
    }

    /**
     * Applica il pattern individuato dalla chiave di traduzione, rende maiuscola l’iniziale del
     * giorno e, in italiano e inglese, quella del mese.
     *
     * @param time istante civile da formattare o delimitare, in millisecondi
     * @param patternKey chiave della risorsa contenente il pattern della data
     * @return data localizzata con le maiuscole previste dalla dashboard
     */
    public String localizedDate(long time, String patternKey) {
        String day = format(time, I18n.text(context, patternKey));
        String language = I18n.locale(context).getLanguage();
        if ("it".equals(language) || "en".equals(language)) {
            String month = format(time, "MMMM");
            day = day.replace(month, capitalize(month));
        }
        return capitalize(day);
    }

    /**
     * Rende maiuscola la prima lettera secondo la lingua attiva e conserva il resto del testo.
     * Richiede una stringa non vuota.
     *
     * @param word testo non vuoto di cui capitalizzare l’iniziale
     * @return testo con iniziale maiuscola nella locale attiva
     */
    public String capitalize(String word) {
        return word.substring(0, 1).toUpperCase(I18n.locale(context)) + word.substring(1);
    }
}
