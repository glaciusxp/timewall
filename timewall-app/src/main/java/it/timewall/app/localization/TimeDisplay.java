package it.timewall.app.localization;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class TimeDisplay {
    public static final String HOUR_24 = "clock.use24Hour";
    public static final String DATE_FORMAT = "clock.dateFormat";

    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private TimeDisplay() {}

    /**
     * Formatta l’ora nella lingua attiva, scegliendo 12/24 ore e la presenza dei secondi.
     * L’overload con preferenze legge il formato salvato e omette i secondi per gli orari degli
     * eventi.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param millis istante civile da formattare, in millisecondi
     * @param seconds true per includere i secondi
     * @param use24Hour true per il formato a 24 ore
     * @return ora localizzata nel formato selezionato; l’overload con preferenze omette i secondi
     */
    public static String time(Context context, long millis, boolean seconds, boolean use24Hour) {
        String pattern =
                use24Hour ? (seconds ? "HH:mm:ss" : "HH:mm") : (seconds ? "hh:mm:ss a" : "hh:mm a");
        return new SimpleDateFormat(pattern, I18n.locale(context)).format(new Date(millis));
    }

    /**
     * Formatta l’ora nella lingua attiva, scegliendo 12/24 ore e la presenza dei secondi.
     * L’overload con preferenze legge il formato salvato e omette i secondi per gli orari degli
     * eventi.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param preferences archivio delle preferenze da leggere o aggiornare
     * @param millis istante civile da formattare, in millisecondi
     * @return ora localizzata nel formato selezionato; l’overload con preferenze omette i secondi
     */
    public static String time(Context context, SharedPreferences preferences, long millis) {
        return time(context, millis, false, preferences.getBoolean(HOUR_24, true));
    }

    /**
     * Seleziona il pattern della data corrente: esteso o breve tradotto, giorno/mese/anno numerico
     * oppure anno-mese-giorno; valori sconosciuti usano il formato esteso.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param selection indice del formato data selezionato
     * @return pattern SimpleDateFormat corrispondente alla selezione
     */
    public static String datePattern(Context context, int selection) {
        switch (selection) {
            case 1:
                return I18n.text(context, "short_date_pattern");
            case 2:
                return "dd/MM/yyyy";
            case 3:
                return "yyyy-MM-dd";
            default:
                return I18n.text(context, "clock_date_pattern");
        }
    }
}
