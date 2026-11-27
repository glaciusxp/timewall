package it.timewall.app.ui.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

import it.timewall.app.R;
import it.timewall.app.configuration.DashboardFont;
import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.domain.time.CalendarDays;
import it.timewall.app.localization.DashboardDates;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.ui.settings.CustomThemeEditor;

import java.text.DateFormatSymbols;
import java.util.Locale;

/** A launcher-owned live clock with independently configured appearance and formats. */
public final class ClockWidget extends AppWidgetProvider {
    private static final String UPDATE = "it.timewall.app.action.UPDATE_CLOCK_WIDGET";
    private static final String DASHBOARD_SECONDS = "clock.showSeconds";

    static String secondsKey(int id) {
        return "seconds." + id;
    }

    static String hourKey(int id) {
        return "hour24." + id;
    }

    static String dateKey(int id) {
        return "dateFormat." + id;
    }

    static boolean showSeconds(SharedPreferences widgets, SharedPreferences dashboard, int id) {
        return widgets.getBoolean(secondsKey(id), dashboard.getBoolean(DASHBOARD_SECONDS, true));
    }

    static boolean use24Hour(SharedPreferences widgets, SharedPreferences dashboard, int id) {
        return widgets.getBoolean(hourKey(id), dashboard.getBoolean(TimeDisplay.HOUR_24, true));
    }

    static int dateFormat(SharedPreferences widgets, SharedPreferences dashboard, int id) {
        int format = widgets.getInt(dateKey(id), dashboard.getInt(TimeDisplay.DATE_FORMAT, 0));
        return format >= 0 && format < 4 ? format : 0;
    }

    static void requestUpdate(Context context) {
        context.sendBroadcast(updateIntent(context));
    }

    private static Intent updateIntent(Context context) {
        return new Intent(context, ClockWidget.class).setAction(UPDATE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED.equals(action)) {
            int id =
                    intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                update(context, AppWidgetManager.getInstance(context), id);
            }
        } else if (UPDATE.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            update(context, AppWidgetManager.getInstance(context));
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] active = manager.getAppWidgetIds(new ComponentName(context, ClockWidget.class));
        SharedPreferences.Editor edit = WhatsNextWidget.preferences(context).edit();
        for (int id : ids) {
            if (isActive(active, id)) continue;
            edit.remove(secondsKey(id))
                    .remove(hourKey(id))
                    .remove(dateKey(id))
                    .remove(WhatsNextWidget.fontKey(id))
                    .remove(WhatsNextWidget.transparencyKey(id));
        }
        edit.apply();
        update(context, manager);
    }

    private static boolean isActive(int[] ids, int id) {
        for (int active : ids) {
            if (active == id) return true;
        }
        return false;
    }

    private static void update(Context context, AppWidgetManager manager) {
        update(context, manager, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    private static void update(Context context, AppWidgetManager manager, int resizedId) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ClockWidget.class));
        boolean resizing = resizedId != AppWidgetManager.INVALID_APPWIDGET_ID;
        if (resizing && !isActive(ids, resizedId)) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent alarm =
                PendingIntent.getBroadcast(context, 0, updateIntent(context), flags());
        if (ids.length == 0) {
            alarms.cancel(alarm);
            return;
        }

        SharedPreferences dashboard = SettingsStore.open(context, Context.MODE_PRIVATE);
        I18n.apply(context, dashboard);
        SharedPreferences widgets = WhatsNextWidget.preferences(context);
        WidgetTheme theme = new WidgetTheme(dashboard);
        int[] custom = CustomThemeEditor.loadColors(dashboard);
        boolean customClock =
                theme.custom
                        && custom != null
                        && custom.length > CustomThemeEditor.DATE_COLOR_INDEX;
        int timeColor =
                customClock ? custom[CustomThemeEditor.CLOCK_COLOR_INDEX] : theme.palette.text;
        int dateColor =
                customClock ? custom[CustomThemeEditor.DATE_COLOR_INDEX] : theme.palette.accent;
        long now = System.currentTimeMillis();
        for (int id : ids) {
            if (resizing && id != resizedId) continue;
            Bundle options = manager.getAppWidgetOptions(id);
            boolean landscape =
                    context.getResources().getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE;
            int width =
                    options.getInt(
                            landscape
                                    ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                                    : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                            180);
            int height =
                    options.getInt(
                            landscape
                                    ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                                    : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                            100);
            DashboardFont font = WhatsNextWidget.selectedFont(widgets, dashboard, id);
            DashboardDates dates = new DashboardDates(context);
            int dateFormat = dateFormat(widgets, dashboard, id);
            String date =
                    dateFormat == 0
                            ? dates.localizedDate(now, "clock_date_pattern")
                            : dates.format(now, TimeDisplay.datePattern(context, dateFormat));
            boolean seconds = showSeconds(widgets, dashboard, id);
            boolean hour24 = use24Hour(widgets, dashboard, id);
            String pattern = TimeDisplay.timePattern(seconds, hour24);

            Typeface dateFace = WidgetText.loadTypeface(context, font);
            int face = faceLayout(context, font);
            Typeface timeFace = face == R.layout.widget_clock_digits ? Typeface.DEFAULT : dateFace;
            float scale = fit(context, width, height, timeFace, dateFace, date, seconds, hour24);
            int clockSize = Math.max(1, (int) (42 * scale));
            int dateSize = Math.max(1, (int) (18 * scale));

            RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
            int opacity = Math.round(255 * (4 - WhatsNextWidget.transparency(widgets, id)) / 4f);
            WhatsNextWidget.tint(
                    view,
                    R.id.widget_background,
                    (theme.palette.background & 0x00ffffff) | (opacity << 24));
            RemoteViews digits = new RemoteViews(context.getPackageName(), face);
            digits.setCharSequence(R.id.widget_clock_digits, "setFormat12Hour", pattern);
            digits.setCharSequence(R.id.widget_clock_digits, "setFormat24Hour", pattern);
            digits.setTextViewTextSize(
                    R.id.widget_clock_digits, TypedValue.COMPLEX_UNIT_SP, clockSize);
            digits.setTextColor(R.id.widget_clock_digits, timeColor);
            view.removeAllViews(R.id.widget_clock_face);
            view.addView(R.id.widget_clock_face, digits);
            new WidgetText(context, font, width)
                    .show(
                            view,
                            R.id.widget_clock_date,
                            R.id.widget_clock_date_font,
                            date,
                            dateColor,
                            dateSize,
                            false,
                            16);
            Intent configure =
                    new Intent(context, WhatsNextConfig.class)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            view.setOnClickPendingIntent(
                    R.id.widget_clock_root,
                    PendingIntent.getActivity(context, id, configure, flags()));
            manager.updateAppWidget(id, view);
        }
        if (!resizing) {
            alarms.set(AlarmManager.RTC, CalendarDays.nextDay(CalendarDays.midnight(now)), alarm);
        }
    }

    private static int faceLayout(Context context, DashboardFont font) {
        if (Build.VERSION.SDK_INT >= 26 && !font.file.isEmpty()) {
            String stem =
                    font.file
                            .substring(0, font.file.lastIndexOf('.'))
                            .toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9]", "_");
            int resource =
                    context.getResources()
                            .getIdentifier(
                                    "widget_clock_" + stem, "layout", context.getPackageName());
            if (resource != 0) return resource;
        }
        return R.layout.widget_clock_digits;
    }

    private static float fit(
            Context context,
            int width,
            int height,
            Typeface timeFace,
            Typeface dateFace,
            String date,
            boolean seconds,
            boolean hour24) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float density = context.getResources().getDisplayMetrics().density;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        paint.setTypeface(Typeface.create(timeFace, Typeface.BOLD));
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 42, metrics));
        float widestDigit = 0;
        for (char digit = '0'; digit <= '9'; digit++) {
            widestDigit = Math.max(widestDigit, paint.measureText(Character.toString(digit)));
        }
        float timeWidth = widestDigit * (seconds ? 6 : 4) + paint.measureText(seconds ? "::" : ":");
        if (!hour24) {
            String[] periods = new DateFormatSymbols(I18n.locale(context)).getAmPmStrings();
            timeWidth +=
                    Math.max(
                            paint.measureText(" " + periods[0]),
                            paint.measureText(" " + periods[1]));
        }
        float timeHeight = paint.descent() - paint.ascent();
        paint.setTypeface(dateFace);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18, metrics));
        float dateWidth = paint.measureText(date);
        float blockHeight = timeHeight + paint.descent() - paint.ascent();
        float widthBudget = Math.max(1, Math.min(width * 0.86f, width - 16)) * density;
        float heightBudget = Math.max(1, Math.min(height * 0.9f, height - 16)) * density;
        return Math.min(widthBudget / Math.max(timeWidth, dateWidth), heightBudget / blockHeight);
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
