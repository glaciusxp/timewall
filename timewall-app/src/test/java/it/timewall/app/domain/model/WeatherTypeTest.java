package it.timewall.app.domain.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class WeatherTypeTest {
    /** Verifica che codici mancanti, non numerici, frazionari o non finiti restino sconosciuti. */
    @Test
    public void unknownAndMissingAreNotClearSky() {
        for (Object v :
                new Object[] {null, -1, 1000, "0", 0.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertNull(WeatherType.describe(v).icon);
            assertEquals("unknown_weather", WeatherType.describe(v).descriptionKey);
        }
    }

    /**
     * Verifica tutti i gruppi di codici meteo supportati rispetto alle risorse grafiche previste.
     */
    @Test
    public void mapsAllSupportedCodes() {
        int[][] groups = {
            {0},
            {1, 2},
            {3},
            {45, 48},
            {51, 53, 55, 56, 57, 61, 63, 66, 80, 81},
            {65, 67, 82},
            {71, 73, 75, 77, 85, 86},
            {95, 96, 97, 99}
        };
        String[] icons = {
            "sole",
            "sole_nuvola",
            "nuvola",
            "nebbia",
            "pioggia",
            "pioggia_forte",
            "neve",
            "temporale"
        };
        for (int i = 0; i < groups.length; i++)
            for (int code : groups[i])
                assertEquals(
                        "Code " + code, "weather_" + icons[i], WeatherType.describe(code).icon);
    }

    /**
     * Verifica che la descrizione conservi il significato di precipitazione gelata anche con
     * un’icona pioggia.
     */
    @Test
    public void freezingRainIsStillDescribedAsFreezing() {
        for (int code : new int[] {56, 57, 66, 67})
            assertTrue(WeatherType.describe(code).descriptionKey.contains("freezing"));
    }
}
