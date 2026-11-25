package it.timewall.app.data.weather;

import android.content.Context;

import it.timewall.app.domain.model.WeatherPlace;
import it.timewall.app.localization.I18n;
import it.timewall.app.platform.tls.OpenMeteoTrust;
import it.timewall.app.platform.tls.Tls12SocketFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public final class OpenMeteoClient {
    /**
     * Interroga il geocoding Open-Meteo nella lingua attiva e converte fino a sei risultati in
     * località con nome, regione, paese e coordinate.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param query testo della località cercata
     * @return lista dei risultati validi nell’ordine di lettura o selezione descritto
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static List<WeatherPlace> search(Context context, String query) throws Exception {
        JSONObject response =
                get(
                        context,
                        new URL(
                                "https://geocoding-api.open-meteo.com/v1/search?count=6"
                                        + "&language="
                                        + I18n.locale(context).getLanguage()
                                        + "&name="
                                        + URLEncoder.encode(query, "UTF-8")));
        JSONArray results = response.optJSONArray("results");
        List<WeatherPlace> places = new ArrayList<>();
        for (int index = 0; results != null && index < results.length(); index++) {
            JSONObject entry = results.getJSONObject(index);
            String region = entry.optString("admin1");
            String country = entry.optString("country");
            String detail =
                    region.isEmpty() || region.equals(country)
                            ? country
                            : country.isEmpty() ? region : region + ", " + country;
            places.add(
                    new WeatherPlace(
                            entry.getString("name"),
                            detail,
                            entry.getDouble("latitude"),
                            entry.getDouble("longitude")));
        }
        return places;
    }

    /**
     * Richiede le condizioni correnti della località nelle unità canoniche Celsius, km/h e
     * millimetri, poi delega la conversione del JSON al parser meteo.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param place località selezionata con coordinate geografiche
     * @return condizioni convertite nelle unità canoniche
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    static JSONObject current(Context context, WeatherPlace place) throws Exception {
        return get(
                        context,
                        new URL(
                                "https://api.open-meteo.com/v1/forecast?latitude="
                                        + place.latitude
                                        + "&longitude="
                                        + place.longitude
                                        + "&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm"
                                        + "&current=temperature_2m,weather_code,apparent_temperature,precipitation_probability"
                                        + ",wind_direction_10m,precipitation,wind_speed_10m"))
                .getJSONObject("current");
    }

    /**
     * Esegue una richiesta JSON HTTPS con timeout di otto secondi e limite di 64 KiB. Su API 19–20
     * applica il provider TLS alternativo e il trust store dedicato; conserva la verifica del nome
     * host, rifiuta redirect e chiude sempre la connessione.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param url endpoint HTTPS da interrogare
     * @return corpo HTTP interpretato come oggetto JSON
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    private static JSONObject get(Context context, URL url) throws Exception {
        // Open-Meteo uses the tested KitKat TLS stack and a scoped trust store.
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try {
            if (android.os.Build.VERSION.SDK_INT <= 20) {
                connection.setSSLSocketFactory(
                        new Tls12SocketFactory(OpenMeteoTrust.createSocketFactory(context)));
            }
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (body.size() + count > 65536) {
                        throw new IOException("Risposta meteo troppo grande");
                    }
                    body.write(buffer, 0, count);
                }
                return new JSONObject(body.toString("UTF-8"));
            }
        } finally {
            connection.disconnect();
        }
    }
}
