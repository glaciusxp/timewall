package it.timewall.app.domain.time;

import java.util.Calendar;

/** Local calendar boundaries; adding 24 hours is incorrect across DST changes. */
public final class CalendarDays {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private CalendarDays() {}

    /**
     * Calcola la mezzanotte locale del giorno contenente l’istante indicato, azzerando ore, minuti,
     * secondi e millisecondi.
     *
     * @param time istante civile da formattare o delimitare, in millisecondi
     * @return istante o intervallo calcolato in millisecondi
     */
    public static long midnight(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * Avanza di un giorno nel calendario locale. Non somma 24 ore fisse, perché i giorni del cambio
     * dell’ora legale possono durare 23 o 25 ore.
     *
     * @param day mezzanotte iniziale del giorno; nelle fixture indica se l’evento è giornaliero
     * @return istante o intervallo calcolato in millisecondi
     */
    public static long nextDay(long day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(day);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }
}
