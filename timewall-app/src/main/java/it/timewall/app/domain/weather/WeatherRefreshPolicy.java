package it.timewall.app.domain.weather;

/** Retry timing is independent of the successful-fetch interval selected by the user. */
public final class WeatherRefreshPolicy {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private WeatherRefreshPolicy() {}

    /**
     * Restituisce cinque minuti dopo un fallimento; altrimenti converte in millisecondi uno degli
     * intervalli ammessi. Una frequenza sconosciuta usa il default di trenta minuti.
     *
     * @param minutes intervallo o conteggio espresso in minuti
     * @param failed true per il retry meteo oppure numero di errori registrati dalla callback di
     *     prova
     * @return ritardo in millisecondi da usare per il prossimo aggiornamento
     */
    public static long delayMillis(int minutes, boolean failed) {
        if (failed) return 5 * 60_000L;
        switch (minutes) {
            case 15:
            case 30:
            case 60:
            case 120:
                return minutes * 60_000L;
            default:
                return 30 * 60_000L;
        }
    }
}
