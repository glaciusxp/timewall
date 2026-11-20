package it.timewall.app.data.weather;

import it.timewall.app.domain.model.WeatherConditions;
import it.timewall.app.domain.model.WeatherType;

import org.json.JSONException;
import org.json.JSONObject;

/** Parses optional measurements without turning absent values into zero. */
public final class WeatherResponseParser {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private WeatherResponseParser() {}

    /**
     * Converte la sezione current di Open-Meteo nel modello meteo. La temperatura effettiva è
     * obbligatoria; misure opzionali assenti o non numeriche restano null e il codice meteo viene
     * risolto insieme all’icona.
     *
     * @param current oggetto JSON con le condizioni meteo correnti
     * @return condizioni convertite nelle unità canoniche
     * @throws JSONException se i campi JSON obbligatori sono assenti o non convertibili
     */
    public static WeatherConditions parse(JSONObject current) throws JSONException {
        double celsius = current.getDouble("temperature_2m");
        WeatherType type = WeatherType.describe(current.opt("weather_code"));
        String descriptionKey = type.descriptionKey;
        String icon = type.icon;
        Double apparentCelsius = optionalNumber(current, "apparent_temperature");
        Double precipitationProbabilityPercent =
                optionalNumber(current, "precipitation_probability");
        Double windDirectionAt10mDegrees = optionalNumber(current, "wind_direction_10m");
        Double precipitationMm = optionalNumber(current, "precipitation");
        Double windSpeedAt10mKmh = optionalNumber(current, "wind_speed_10m");
        int intervalSeconds = current.optInt("interval", 0);
        return new WeatherConditions(
                celsius,
                descriptionKey,
                icon,
                apparentCelsius,
                precipitationProbabilityPercent,
                windDirectionAt10mDegrees,
                precipitationMm,
                windSpeedAt10mKmh,
                intervalSeconds);
    }

    /**
     * Estrae un numero finito da un campo JSON. Restituisce null per campi assenti, null JSON,
     * valori non numerici, NaN o infinito.
     *
     * @param current oggetto JSON con le condizioni meteo correnti
     * @param key chiave della proprietà o risorsa richiesta
     * @return numero finito del campo, oppure null se assente o non numerico
     */
    private static Double optionalNumber(JSONObject current, String key) {
        Object value = current.opt(key);
        if (!(value instanceof Number)) {
            return null;
        }
        double number = ((Number) value).doubleValue();
        return Double.isNaN(number) || Double.isInfinite(number) ? null : number;
    }
}
