package it.timewall.app.ui.weather;

import android.content.Context;

import it.timewall.app.R;
import it.timewall.app.domain.model.WeatherConditions;
import it.timewall.app.localization.I18n;

import java.text.NumberFormat;

public final class WeatherText {
    private final Context context;
    private final NumberFormat number;
    private final boolean fahrenheit;

    public WeatherText(Context context, boolean fahrenheit) {
        this.context = context;
        this.fahrenheit = fahrenheit;
        number = NumberFormat.getNumberInstance(I18n.locale(context));
        number.setMaximumFractionDigits(1);
    }

    public String temperature(double celsius) {
        double value = fahrenheit ? celsius * 1.8 + 32 : celsius;
        return number.format(value) + (fahrenheit ? "°F" : "°C");
    }

    public String measure(Double value, String unit) {
        return value == null ? "" : number.format(value) + unit;
    }

    public String wind(WeatherConditions weather) {
        if (weather.windSpeedAt10mKmh == null) return "";
        String speed = Math.round(weather.windSpeedAt10mKmh) + " km/h";
        if (weather.windDirectionAt10mDegrees == null) return speed;
        double degrees = weather.windDirectionAt10mDegrees;
        double normalized = (degrees % 360 + 360) % 360;
        String[] points = context.getResources().getStringArray(R.array.wind_directions);
        return speed + " " + points[(int) Math.round(normalized / 22.5) % points.length];
    }
}
