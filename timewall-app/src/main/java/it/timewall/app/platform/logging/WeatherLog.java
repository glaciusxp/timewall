package it.timewall.app.platform.logging;

import android.util.Log;

import it.timewall.app.domain.weather.WeatherFailure;

/**
 * Production weather logging accepts only fixed operation/category names, never Throwable
 * overloads.
 */
public final class WeatherLog {
    public enum Operation {
        FORECAST,
        LOCATION_SEARCH
    }

    /** Prevents instantiation of the static logging boundary. */
    private WeatherLog() {}

    /**
     * Formats an allowlisted diagnostic without copying messages, causes, stack traces or response
     * bodies.
     */
    public static String message(Operation operation, Throwable failure) {
        return "operation="
                + operation.name()
                + " category="
                + WeatherFailure.classify(failure).name();
    }

    /**
     * Emits a fixed-category warning; deliberately does not forward the Throwable to Android's
     * logger.
     */
    public static void warn(Operation operation, Throwable failure) {
        Log.w("TimeWallWeather", message(operation, failure));
    }
}
