package it.timewall.app.data.weather;

import static org.junit.Assert.*;

import it.timewall.app.domain.model.WeatherConditions;
import it.timewall.app.domain.weather.WeatherRefreshPolicy;

import org.json.*;
import org.junit.Test;

public class WeatherResponseParserTest {
    /**
     * Verifica che misure e codice meteo mancanti restino assenti anziché diventare zero o sereno.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void absentMeasurementsStayAbsent() throws Exception {
        WeatherConditions c =
                WeatherResponseParser.parse(new JSONObject("{\"temperature_2m\":12}"));
        assertNull(c.icon);
        assertNull(c.windDirectionAt10mDegrees);
        assertNull(c.precipitationMm);
        assertNull(c.apparentCelsius);
    }

    /**
     * Verifica che null JSON non diventi un’icona di sereno o una precipitazione numerica nulla.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void jsonNullDoesNotBecomeClearSkyOrZero() throws Exception {
        WeatherConditions c =
                WeatherResponseParser.parse(
                        new JSONObject(
                                "{\"temperature_2m\":12,\"weather_code\":null,\"precipitation\":null}"));
        assertNull(c.icon);
        assertNull(c.precipitationMm);
    }

    /**
     * Verifica l’estrazione delle misure nelle unità canoniche e della descrizione della pioggia
     * gelata.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void parsesCanonicalMeasurements() throws Exception {
        WeatherConditions c =
                WeatherResponseParser.parse(
                        new JSONObject(
                                "{\"temperature_2m\":12,\"weather_code\":66,\"apparent_temperature\":10,\"wind_direction_10m\":45,\"wind_speed_10m\":10.5,\"precipitation_probability\":70,\"precipitation\":2,\"interval\":900}"));
        assertEquals("freezing_rain", c.descriptionKey);
        assertEquals(45, c.windDirectionAt10mDegrees, 0);
        assertEquals(10, c.apparentCelsius, 0);
        assertEquals(2, c.precipitationMm, 0);
        assertEquals(900, c.intervalSeconds);
    }

    /**
     * Verifica che una previsione priva della temperatura obbligatoria sollevi JSONException.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test(expected = JSONException.class)
    public void missingTemperatureIsNotAValidForecast() throws Exception {
        WeatherResponseParser.parse(new JSONObject());
    }

    /**
     * Verifica gli intervalli configurabili, il default e il retry fisso separato dall’intervallo
     * di successo.
     */
    @Test
    public void retryDoesNotUseSuccessfulInterval() {
        for (int minutes : new int[] {15, 30, 60, 120}) {
            assertEquals(minutes * 60000L, WeatherRefreshPolicy.delayMillis(minutes, false));
            assertEquals(300000L, WeatherRefreshPolicy.delayMillis(minutes, true));
        }
        assertEquals(1800000L, WeatherRefreshPolicy.delayMillis(-1, false));
    }
}
