package it.timewall.app.data.weather;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import it.timewall.app.BuildConfig;
import it.timewall.app.domain.model.WeatherConditions;
import it.timewall.app.domain.model.WeatherPlace;
import it.timewall.app.domain.weather.WeatherRefreshPolicy;
import it.timewall.app.platform.logging.WeatherLog;

import org.json.JSONObject;

/** One forecast and refresh schedule shared by the dashboard and every weather widget. */
public final class WeatherForecast {
    public static final String CACHE_KEY = "weather.forecast";
    public static final String WIDGET_UPDATE = "it.timewall.app.action.WEATHER_CHANGED";
    private static final String FREQUENCY = "weather.refreshMinutes";
    private static final long MANUAL_DEBOUNCE_MS = 10_000L;
    private static String attemptedPlace;
    private static long attemptedAt;
    private static boolean failed;

    public final WeatherConditions conditions;
    public final long updatedAt;

    private WeatherForecast(WeatherConditions conditions, long updatedAt) {
        this.conditions = conditions;
        this.updatedAt = updatedAt;
    }

    public static WeatherPlace location(SharedPreferences settings) {
        String name = settings.getString("weather.name", "");
        if (name.isEmpty()) return null;
        return new WeatherPlace(
                name,
                settings.getString("weather.detail", ""),
                Double.longBitsToDouble(settings.getLong("weather.lat", 0)),
                Double.longBitsToDouble(settings.getLong("weather.lon", 0)));
    }

    public static WeatherForecast cached(SharedPreferences settings, WeatherPlace place) {
        if (place == null) return null;
        try {
            JSONObject record = new JSONObject(settings.getString(CACHE_KEY, "{}"));
            if (placeKey(place).equals(record.optString("place"))) {
                return new WeatherForecast(
                        WeatherResponseParser.parse(record.getJSONObject("current")),
                        record.getLong("updatedAt"));
            }
        } catch (Exception invalidCache) {
            // A missing or invalid snapshot is replaced by the next successful request.
        }
        return null;
    }

    public static long delay(SharedPreferences settings, WeatherForecast forecast) {
        if (forecast == null) return 0;
        long interval = WeatherRefreshPolicy.delayMillis(settings.getInt(FREQUENCY, 30), false);
        long age = System.currentTimeMillis() - forecast.updatedAt;
        return age < 0 ? 0 : Math.max(0, interval - age);
    }

    /** Executes off the UI thread. Concurrent callers reuse the completed attempt. */
    public static synchronized boolean refresh(
            Context context, SharedPreferences settings, WeatherPlace place, boolean manual) {
        if (!isCurrent(settings, place)) return false;
        String key = placeKey(place);
        long elapsed = SystemClock.elapsedRealtime() - attemptedAt;
        if (key.equals(attemptedPlace) && elapsed < MANUAL_DEBOUNCE_MS) return !failed;
        if (!manual) {
            if (delay(settings, cached(settings, place)) > 0) return true;
            if (key.equals(attemptedPlace)
                    && failed
                    && elapsed < WeatherRefreshPolicy.delayMillis(30, true)) return false;
        }

        attemptedPlace = key;
        try {
            JSONObject current = OpenMeteoClient.current(context, place);
            WeatherResponseParser.parse(current);
            if (!isCurrent(settings, place)) return false;
            JSONObject record =
                    new JSONObject()
                            .put("place", key)
                            .put("current", current)
                            .put("updatedAt", System.currentTimeMillis());
            settings.edit().putString(CACHE_KEY, record.toString()).apply();
            notifyWidgets(context);
            failed = false;
            return true;
        } catch (Exception | LinkageError error) {
            failed = true;
            WeatherLog.warn(WeatherLog.Operation.FORECAST, error);
            return false;
        } finally {
            attemptedAt = SystemClock.elapsedRealtime();
        }
    }

    public static void notifyWidgets(Context context) {
        if (!BuildConfig.FREE) {
            context.sendBroadcast(
                    new Intent(WIDGET_UPDATE)
                            .setClassName(
                                    context.getPackageName(),
                                    "it.timewall.app.ui.widget.WeatherWidget$InternalReceiver"));
        }
    }

    private static boolean isCurrent(SharedPreferences settings, WeatherPlace place) {
        WeatherPlace configured = location(settings);
        return place != null
                && configured != null
                && settings.getBoolean("weather.enabled", false)
                && placeKey(place).equals(placeKey(configured));
    }

    private static String placeKey(WeatherPlace place) {
        return place.latitude + "," + place.longitude;
    }
}
