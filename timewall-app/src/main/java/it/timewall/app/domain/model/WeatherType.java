package it.timewall.app.domain.model;

/** Open-Meteo WMO mapping. Missing values deliberately map to unknown, never clear sky. */
public final class WeatherType {
    public final String descriptionKey;
    public final String icon;

    /**
     * Associa una chiave testuale della condizione alla risorsa grafica, che può essere null per
     * condizioni sconosciute.
     *
     * @param description descrizione estesa o descrizione accessibile del controllo
     * @param icon nome della risorsa grafica o glifo da visualizzare
     */
    WeatherType(String description, String icon) {
        this.descriptionKey = description;
        this.icon = icon;
    }

    /**
     * Associa un codice WMO numerico intero alla descrizione tradotta e alla risorsa grafica.
     * Valori assenti, non interi o sconosciuti restano sconosciuti senza icona; la pioggia gelata
     * mantiene una descrizione specifica.
     *
     * @param value valore da validare, formattare o mostrare
     * @return descrizione e riferimento dell’icona; per codici sconosciuti l’icona è null
     */
    public static WeatherType describe(Object value) {
        // JSON null and a missing field must never be treated as clear skies (code 0).
        int code = -1;
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number == (int) number) {
                code = (int) number;
            }
        }
        switch (code) {
            case 0:
                return new WeatherType("clear", "weather_sole");
            case 1:
                return new WeatherType("mainly_clear", "weather_sole_nuvola");
            case 2:
                return new WeatherType("partly_cloudy", "weather_sole_nuvola");
            case 3:
                return new WeatherType("overcast", "weather_nuvola");
            case 45:
                return new WeatherType("fog", "weather_nebbia");
            case 48:
                return new WeatherType("rime_fog", "weather_nebbia");
            case 51:
            case 53:
            case 55:
                return new WeatherType("drizzle", "weather_pioggia");
            case 56:
            case 57:
                return new WeatherType("freezing_drizzle", "weather_pioggia");
            case 61:
            case 63:
                return new WeatherType("rain", "weather_pioggia");
            case 65:
                return new WeatherType("heavy_rain", "weather_pioggia_forte");
            case 66:
                return new WeatherType("freezing_rain", "weather_pioggia");
            case 67:
                return new WeatherType("heavy_freezing_rain", "weather_pioggia_forte");
            case 71:
            case 73:
            case 75:
                return new WeatherType("snow", "weather_neve");
            case 77:
                return new WeatherType("snow_grains", "weather_neve");
            case 80:
            case 81:
                return new WeatherType("rain_showers", "weather_pioggia");
            case 82:
                return new WeatherType("heavy_showers", "weather_pioggia_forte");
            case 85:
            case 86:
                return new WeatherType("snow_showers", "weather_neve");
            case 95:
                return new WeatherType("thunderstorm", "weather_temporale");
            case 97:
                return new WeatherType("heavy_thunderstorm", "weather_temporale");
            case 96:
            case 99:
                return new WeatherType("thunderstorm_hail", "weather_temporale");
            default:
                return new WeatherType("unknown_weather", null);
        }
    }
}
