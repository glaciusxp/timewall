package it.timewall.app.ui.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import it.timewall.app.R;
import it.timewall.app.configuration.Palette;
import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.data.weather.WeatherForecast;
import it.timewall.app.domain.model.WeatherConditions;
import it.timewall.app.domain.model.WeatherPlace;
import it.timewall.app.domain.weather.WeatherRefreshPolicy;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.ui.weather.WeatherText;

public final class WeatherWidget extends AppWidgetProvider {
    private static final String MANUAL = "it.timewall.app.action.REFRESH_WEATHER_WIDGET";
    private static final String DUE = "it.timewall.app.action.WEATHER_WIDGET_DUE";
    private static final String RESCHEDULE = "it.timewall.app.action.RESCHEDULE_WEATHER_WIDGET";
    private static final String RETRY = "retry";
    private static final int FULL_WIDTH_DP = 400;
    private static final int FULL_HEIGHT_DP = 152;
    private static final int COMPACT_HEIGHT_DP = 118;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(action)) {
            int id =
                    intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID);
            if (contains(ids(context), id)) {
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                updateWidget(context, manager, id, manager.getAppWidgetOptions(id));
            }
        } else if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            update(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    public static final class InternalReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MANUAL.equals(action) || DUE.equals(action)) {
                if (ids(context).length > 0) {
                    WidgetWeatherRefresh.enqueue(context, MANUAL.equals(action));
                }
            } else if (RESCHEDULE.equals(action)) {
                update(context, intent.getBooleanExtra(RETRY, false) ? retryDelay() : 1000L);
            } else if (WeatherForecast.WIDGET_UPDATE.equals(action)) {
                update(context);
            }
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        SharedPreferences.Editor edit = WhatsNextWidget.preferences(context).edit();
        int[] active = ids(context);
        for (int id : ids) {
            if (contains(active, id)) continue;
            edit.remove(WhatsNextWidget.fontKey(id)).remove(WhatsNextWidget.transparencyKey(id));
        }
        edit.apply();
    }

    @Override
    public void onDisabled(Context context) {
        if (ids(context).length == 0) alarms(context).cancel(delivery(context, DUE));
    }

    static void refresh(Context context, boolean manual) {
        if (ids(context).length == 0) return;
        SharedPreferences settings = SettingsStore.open(context, Context.MODE_PRIVATE);
        WeatherPlace place = enabledLocation(settings);
        if (place == null) return;
        boolean success = WeatherForecast.refresh(context, settings, place, manual);
        context.sendBroadcast(
                new Intent(context, InternalReceiver.class)
                        .setAction(RESCHEDULE)
                        .putExtra(RETRY, !success));
    }

    private static long retryDelay() {
        return WeatherRefreshPolicy.delayMillis(30, true);
    }

    private static void update(Context context) {
        update(context, 1000L);
    }

    private static void update(Context context, long minimumDelay) {
        int[] ids = ids(context);
        if (ids.length == 0) return;
        SharedPreferences settings = SettingsStore.open(context, Context.MODE_PRIVATE);
        WeatherPlace place = enabledLocation(settings);
        WeatherForecast forecast = WeatherForecast.cached(settings, place);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : ids) {
            updateWidget(context, manager, id, manager.getAppWidgetOptions(id));
        }
        PendingIntent due = delivery(context, DUE);
        if (place == null) {
            alarms(context).cancel(due);
        } else {
            long delay = Math.max(minimumDelay, WeatherForecast.delay(settings, forecast));
            alarms(context)
                    .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, due);
        }
    }

    private static void updateWidget(
            Context context, AppWidgetManager manager, int id, Bundle options) {
        SharedPreferences settings = SettingsStore.open(context, Context.MODE_PRIVATE);
        I18n.apply(context, settings);
        WeatherPlace place = enabledLocation(settings);
        RemoteViews layout =
                face(
                        context,
                        options,
                        settings,
                        WhatsNextWidget.preferences(context),
                        new WidgetTheme(settings).palette,
                        place,
                        WeatherForecast.cached(settings, place),
                        id);

        // Keep the host layout stable and explicitly replace its contents on resize.
        RemoteViews host = new RemoteViews(context.getPackageName(), R.layout.widget_weather_host);
        host.removeAllViews(R.id.weather_host);
        host.addView(R.id.weather_host, layout);
        manager.updateAppWidget(id, host);
    }

    private static RemoteViews face(
            Context context,
            Bundle options,
            SharedPreferences settings,
            SharedPreferences widgets,
            Palette palette,
            WeatherPlace place,
            WeatherForecast forecast,
            int id) {
        boolean landscape =
                context.getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
        int width =
                options.getInt(
                        landscape
                                ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                                : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                        190);
        int height =
                options.getInt(
                        landscape
                                ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                                : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                        118);
        boolean ultraCompact =
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                        < COMPACT_HEIGHT_DP;
        boolean full = !ultraCompact && width >= FULL_WIDTH_DP && height >= FULL_HEIGHT_DP;
        int layout = R.layout.widget_weather_compact;
        if (ultraCompact) layout = R.layout.widget_weather_ultra_compact;
        else if (full) layout = R.layout.widget_weather_full;
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        WidgetText text =
                new WidgetText(context, WhatsNextWidget.selectedFont(widgets, settings, id), width);
        int opacity = Math.round(255 * (4 - WhatsNextWidget.transparency(widgets, id)) / 4f);
        WhatsNextWidget.tint(
                views, R.id.widget_background, (palette.background & 0x00ffffff) | (opacity << 24));

        Intent config =
                new Intent(context, WhatsNextConfig.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        views.setOnClickPendingIntent(
                R.id.weather_root, PendingIntent.getActivity(context, id, config, flags()));
        views.setImageViewBitmap(R.id.weather_refresh, WhatsNextWidget.refreshIcon(context));
        WhatsNextWidget.tint(views, R.id.weather_refresh, palette.text);
        views.setContentDescription(R.id.weather_refresh, I18n.text(context, "refresh_weather"));
        views.setViewVisibility(R.id.weather_refresh, place == null ? View.GONE : View.VISIBLE);
        views.setOnClickPendingIntent(R.id.weather_refresh, delivery(context, MANUAL));

        if (place == null) {
            views.setViewVisibility(R.id.weather_temperature_row, View.GONE);
            if (ultraCompact) return views;
            text.show(
                    views,
                    R.id.weather_status,
                    R.id.weather_status_font,
                    I18n.text(
                            context,
                            settings.getBoolean("weather.enabled", false)
                                    ? "no_place"
                                    : "weather_unavailable"),
                    palette.secondaryText,
                    16,
                    false,
                    12);
            return views;
        }

        if (!ultraCompact) {
            int updateWidth = full ? Math.min(width / 2, 145) : width - 50;
            String time =
                    forecast == null ? "" : TimeDisplay.time(context, settings, forecast.updatedAt);
            String updated =
                    forecast == null
                            ? ""
                            : context.getString(R.string.widget_weather_last_update, time);
            text.show(
                    views,
                    R.id.weather_updated,
                    R.id.weather_updated_font,
                    updated,
                    palette.secondaryText,
                    13,
                    false,
                    width - updateWidth);

            text.show(
                    views,
                    R.id.weather_place,
                    R.id.weather_place_font,
                    place.name,
                    palette.secondaryText,
                    16,
                    false,
                    full ? updateWidth + 68 : 12);
            if (full) glyph(context, views, R.id.weather_place_icon, "", palette.secondaryText);
        }
        if (forecast == null) {
            views.setViewVisibility(R.id.weather_temperature_row, View.GONE);
            if (ultraCompact) return views;
            text.show(
                    views,
                    R.id.weather_status,
                    R.id.weather_status_font,
                    I18n.text(context, "weather_unavailable"),
                    palette.secondaryText,
                    16,
                    false,
                    12);
            return views;
        }

        WeatherConditions current = forecast.conditions;
        WeatherText format =
                new WeatherText(context, settings.getBoolean("weather.fahrenheit", false));
        int icon =
                current.icon == null
                        ? 0
                        : context.getResources()
                                .getIdentifier(current.icon, "drawable", context.getPackageName());
        if (icon != 0) {
            views.setImageViewResource(R.id.weather_icon, icon);
            views.setViewVisibility(R.id.weather_icon, View.VISIBLE);
            WhatsNextWidget.tint(views, R.id.weather_icon, palette.text);
            views.setContentDescription(
                    R.id.weather_icon, I18n.text(context, current.descriptionKey));
        }
        String temperature = format.temperature(current.celsius);
        int centerWidth = text.lineWidthDp(temperature, 44) + (icon == 0 ? 0 : 64);
        text.show(
                views,
                R.id.weather_temperature,
                R.id.weather_temperature_font,
                temperature,
                palette.text,
                44,
                false,
                icon == 0 ? 12 : full ? 76 : 64);
        if (full) {
            text.show(
                    views,
                    R.id.weather_summary,
                    R.id.weather_summary_font,
                    I18n.text(context, current.descriptionKey),
                    palette.text,
                    20,
                    false,
                    12);
            int sideWidth = (width - 12 - centerWidth) / 2;
            int inset = width - sideWidth + 22;
            metric(
                    context,
                    views,
                    text,
                    palette,
                    R.id.weather_wind,
                    R.id.weather_wind_font,
                    R.id.weather_wind_icon,
                    "",
                    format.wind(current),
                    inset);
            if (current.apparentCelsius != null) {
                glyph(
                        context,
                        views,
                        R.id.weather_apparent_person_icon,
                        "\uf183",
                        palette.secondaryText);
                metric(
                        context,
                        views,
                        text,
                        palette,
                        R.id.weather_apparent,
                        R.id.weather_apparent_font,
                        R.id.weather_apparent_icon,
                        "\uf2c9",
                        format.temperature(current.apparentCelsius),
                        width - sideWidth + 32);
            }
            metric(
                    context,
                    views,
                    text,
                    palette,
                    R.id.weather_probability,
                    R.id.weather_probability_font,
                    R.id.weather_probability_icon,
                    "",
                    format.measure(current.precipitationProbabilityPercent, "%"),
                    inset);
            metric(
                    context,
                    views,
                    text,
                    palette,
                    R.id.weather_precipitation,
                    R.id.weather_precipitation_font,
                    R.id.weather_precipitation_icon,
                    "",
                    format.measure(current.precipitationMm, " mm"),
                    inset);
        }
        return views;
    }

    private static void metric(
            Context context,
            RemoteViews views,
            WidgetText text,
            Palette palette,
            int label,
            int image,
            int icon,
            String symbol,
            String value,
            int inset) {
        if (!value.isEmpty()) glyph(context, views, icon, symbol, palette.secondaryText);
        text.show(views, label, image, value, palette.secondaryText, 16, false, inset);
    }

    private static void glyph(
            Context context, RemoteViews views, int id, String symbol, int color) {
        views.setImageViewBitmap(id, WhatsNextWidget.buttonIcon(context, symbol));
        views.setViewVisibility(id, View.VISIBLE);
        WhatsNextWidget.tint(views, id, color);
    }

    private static WeatherPlace enabledLocation(SharedPreferences settings) {
        return settings.getBoolean("weather.enabled", false)
                ? WeatherForecast.location(settings)
                : null;
    }

    private static boolean contains(int[] ids, int id) {
        for (int candidate : ids) {
            if (candidate == id) return true;
        }
        return false;
    }

    private static int[] ids(Context context) {
        return AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, WeatherWidget.class));
    }

    private static AlarmManager alarms(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent delivery(Context context, String action) {
        return PendingIntent.getBroadcast(
                context, 0, new Intent(context, InternalReceiver.class).setAction(action), flags());
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
