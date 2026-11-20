package it.timewall.app.domain.model;

/** Weather measurements in canonical Celsius, km/h and millimetres. */
public final class WeatherConditions {
    public final double celsius;
    public final String descriptionKey;
    public final String icon;
    public final Double apparentCelsius;
    public final Double precipitationProbabilityPercent;
    public final Double windDirectionAt10mDegrees;
    public final Double precipitationMm;
    public final Double windSpeedAt10mKmh;
    public final int intervalSeconds;

    /**
     * Memorizza le misure canoniche e i riferimenti di descrizione e icona; i campi opzionali null
     * rappresentano dati non disponibili.
     *
     * @param celsius temperatura effettiva in gradi Celsius
     * @param descriptionKey chiave della condizione nelle risorse tradotte
     * @param icon nome della risorsa grafica o glifo da visualizzare
     * @param apparentCelsius temperatura percepita in Celsius; null se assente
     * @param precipitationProbabilityPercent probabilità percentuale; null se assente
     * @param windDirectionAt10mDegrees direzione del vento in gradi; null se assente
     * @param precipitationMm precipitazione in millimetri; null se assente
     * @param windSpeedAt10mKmh vento a dieci metri in km/h; null se assente
     * @param intervalSeconds intervallo della rilevazione comunicato dal provider
     */
    public WeatherConditions(
            double celsius,
            String descriptionKey,
            String icon,
            Double apparentCelsius,
            Double precipitationProbabilityPercent,
            Double windDirectionAt10mDegrees,
            Double precipitationMm,
            Double windSpeedAt10mKmh,
            int intervalSeconds) {
        this.celsius = celsius;
        this.descriptionKey = descriptionKey;
        this.icon = icon;
        this.apparentCelsius = apparentCelsius;
        this.precipitationProbabilityPercent = precipitationProbabilityPercent;
        this.windDirectionAt10mDegrees = windDirectionAt10mDegrees;
        this.precipitationMm = precipitationMm;
        this.windSpeedAt10mKmh = windSpeedAt10mKmh;
        this.intervalSeconds = intervalSeconds;
    }
}
