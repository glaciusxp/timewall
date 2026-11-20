package it.timewall.diagnostics.network;

import org.json.JSONObject;

import java.util.Iterator;

public final class OpenMeteoDiagnostic {
    private static final String SURFACE =
            "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,"
                + "wet_bulb_temperature_2m,is_day,weather_code,precipitation,precipitation_probability,rain,"
                + "showers,snowfall,snow_depth,snowfall_height,freezing_level_height,pressure_msl,"
                + "surface_pressure,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high,visibility,"
                + "wind_speed_10m,wind_speed_80m,wind_speed_120m,wind_speed_180m,wind_direction_10m,"
                + "wind_direction_80m,wind_direction_120m,wind_direction_180m,wind_gusts_10m,uv_index,"
                + "uv_index_clear_sky,sunshine_duration,cape,vapour_pressure_deficit,evapotranspiration,"
                + "et0_fao_evapotranspiration,shortwave_radiation,direct_radiation,direct_normal_irradiance,"
                + "diffuse_radiation,global_tilted_irradiance,terrestrial_radiation,"
                + "shortwave_radiation_instant,direct_radiation_instant,direct_normal_irradiance_instant,"
                + "diffuse_radiation_instant,global_tilted_irradiance_instant,terrestrial_radiation_instant,"
                + "soil_temperature_0cm,soil_temperature_6cm,soil_temperature_18cm,soil_temperature_54cm,"
                + "soil_moisture_0_to_1cm,soil_moisture_1_to_3cm,soil_moisture_3_to_9cm,"
                + "soil_moisture_9_to_27cm,soil_moisture_27_to_81cm";
    private static final String DAILY =
            "temperature_2m_max,temperature_2m_mean,temperature_2m_min,apparent_temperature_max,"
                + "apparent_temperature_mean,apparent_temperature_min,precipitation_sum,rain_sum,"
                + "showers_sum,snowfall_sum,precipitation_hours,precipitation_probability_max,"
                + "precipitation_probability_mean,precipitation_probability_min,weather_code,sunrise,sunset,"
                + "sunshine_duration,daylight_duration,wind_speed_10m_max,wind_gusts_10m_max,"
                + "wind_direction_10m_dominant,shortwave_radiation_sum,et0_fao_evapotranspiration,"
                + "uv_index_max,uv_index_clear_sky_max";
    public static final String URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=41.90&longitude=12.50&timezone=Europe%2FRome"
                    + "&forecast_days=1&forecast_hours=2"
                    + "&current="
                    + SURFACE
                    + "&hourly="
                    + SURFACE
                    + "&daily="
                    + DAILY;

    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private OpenMeteoDiagnostic() {}

    /**
     * Compone un report leggibile delle sezioni current, hourly e daily ricevute da Open-Meteo,
     * riportando valori e unità invece di selezionare soltanto i campi della dashboard.
     *
     * @param response risposta JSON del provider
     * @return report testuale con tutte le sezioni meteo disponibili e i metadati
     */
    public static String describe(JSONObject response) {
        StringBuilder report = new StringBuilder("Open-Meteo · Roma\n");
        appendSection(report, response, "current", "Condizioni attuali");
        appendSection(report, response, "hourly", "Dati orari · 2 campioni");
        appendSection(report, response, "daily", "Riepilogo giornaliero · oggi");
        report.append("\nMetadati della risposta\n");
        for (Iterator<String> keys = response.keys(); keys.hasNext(); ) {
            String key = keys.next();
            if (!key.equals("current")
                    && !key.equals("hourly")
                    && !key.equals("daily")
                    && !key.equals("current_units")
                    && !key.equals("hourly_units")
                    && !key.equals("daily_units")) {
                report.append(key).append(": ").append(response.opt(key)).append('\n');
            }
        }
        return report.toString();
    }

    /**
     * Aggiunge al report una sezione JSON e le relative unità. Omette la sezione se mancano
     * entrambi gli oggetti e riporta come non disponibili i campi presenti soltanto tra le unità.
     *
     * @param report buffer nel quale aggiungere il report diagnostico
     * @param response risposta JSON del provider
     * @param key chiave della proprietà o risorsa richiesta
     * @param heading titolo tradotto del campo
     */
    private static void appendSection(
            StringBuilder report, JSONObject response, String key, String heading) {
        JSONObject values = response.optJSONObject(key);
        JSONObject units = response.optJSONObject(key + "_units");
        if (values == null && units == null) {
            return;
        }
        report.append('\n').append(heading).append('\n');
        appendValues(report, values, units, false);
        appendValues(report, units, values, true);
    }

    /**
     * Scorre i campi di una sezione JSON e aggiunge al report ogni valore con la relativa unità,
     * conservando anche dati non usati dalla dashboard.
     *
     * @param report buffer nel quale aggiungere il report diagnostico
     * @param fields oggetto JSON con valori o unità da enumerare
     * @param other oggetto JSON complementare con unità o campi già mostrati
     * @param unitsOnly true per riportare le sole unità dei campi il cui valore non era disponibile
     */
    private static void appendValues(
            StringBuilder report, JSONObject fields, JSONObject other, boolean unitsOnly) {
        if (fields == null) {
            return;
        }
        for (Iterator<String> keys = fields.keys(); keys.hasNext(); ) {
            String field = keys.next();
            if (unitsOnly && other != null && other.has(field)) {
                continue;
            }
            report.append(field).append(": ");
            if (unitsOnly) {
                report.append("non disponibile · ").append(fields.opt(field));
            } else {
                report.append(fields.isNull(field) ? "non disponibile" : fields.opt(field));
                if (other != null && !other.isNull(field)) {
                    report.append(' ').append(other.opt(field));
                }
            }
            report.append('\n');
        }
    }
}
