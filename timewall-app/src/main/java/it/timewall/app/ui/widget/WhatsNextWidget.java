package it.timewall.app.ui.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import it.timewall.app.BuildConfig;
import it.timewall.app.R;
import it.timewall.app.configuration.DashboardFont;
import it.timewall.app.configuration.Palette;
import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.data.calendar.CalendarDiskCache;
import it.timewall.app.data.calendar.CalendarFeed;
import it.timewall.app.data.calendar.DeviceCalendars;
import it.timewall.app.data.calendar.IcalendarParser;
import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.domain.time.CalendarDays;
import it.timewall.app.domain.time.TodayEvents;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.ui.common.DashboardStyle;
import it.timewall.app.ui.common.ViewFactory;
import it.timewall.app.ui.dashboard.DashboardActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Renders independently filtered views from the dashboard calendar cache. */
public class WhatsNextWidget extends AppWidgetProvider {
    private static final String UPDATE = "it.timewall.app.action.UPDATE_WHATS_NEXT";
    private static final String REFRESH = "it.timewall.app.action.REFRESH_WHATS_NEXT";
    private static Bitmap refreshIcon;
    private static Bitmap settingsIcon;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("whats_next_widgets", Context.MODE_PRIVATE);
    }

    static String transparencyKey(int id) {
        return "transparency." + id;
    }

    static int transparency(SharedPreferences widgets, int id) {
        return Math.max(0, Math.min(4, widgets.getInt(transparencyKey(id), 0)));
    }

    static String enlargedTextKey(int id) {
        return "enlargeText." + id;
    }

    static boolean enlargedText(SharedPreferences widgets, int id) {
        return widgets.getBoolean(enlargedTextKey(id), false);
    }

    static String fontKey(int id) {
        return "font." + id;
    }

    static DashboardFont selectedFont(
            SharedPreferences widgets, SharedPreferences dashboard, int id) {
        return DashboardFont.fromSavedFile(
                widgets.getString(fontKey(id), dashboard.getString("dashboardFont", "")));
    }

    static List<Feed> feeds(SharedPreferences dashboard) {
        List<Feed> feeds = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(dashboard.getString("feeds", "[]"));
            for (int i = 0; i < array.length() && feeds.size() < BuildConfig.CALENDAR_LIMIT; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String address = item.optString("address");
                if (CalendarFeed.accepts(address) || Feed.deviceId(address) > 0) {
                    feeds.add(new Feed(item.optString("name"), address, item.optInt("color")));
                }
            }
        } catch (JSONException ignored) {
            // An invalid settings record leaves the widget empty.
        }
        return feeds;
    }

    static void requestUpdate(Context context) {
        context.sendBroadcast(updateIntent(context, WhatsNextWidget.class));
        context.sendBroadcast(updateIntent(context, AgendaWidget.class));
    }

    private static Intent updateIntent(Context context, Class<?> provider) {
        return new Intent(context, provider).setAction(UPDATE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (REFRESH.equals(action)) {
            int id =
                    intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID);
            if (!WidgetCalendarRefresh.schedule(context, id)) {
                I18n.apply(context, SettingsStore.open(context, Context.MODE_PRIVATE));
                Toast.makeText(context, R.string.refresh_failed, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (UPDATE.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            Context app = context.getApplicationContext();
            PendingResult pending = goAsync();
            WORKER.execute(
                    () -> {
                        try {
                            renderAll(app, getClass());
                        } finally {
                            pending.finish();
                        }
                    });
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        SharedPreferences.Editor editor = preferences(context).edit();
        for (int id : ids)
            editor.remove(Integer.toString(id))
                    .remove(transparencyKey(id))
                    .remove(fontKey(id))
                    .remove(enlargedTextKey(id));
        editor.apply();
        requestUpdate(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context, AppWidgetManager manager, int id, android.os.Bundle options) {
        requestUpdate(context);
    }

    private static void renderAll(Context context, Class<?> provider) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        boolean agenda = provider == AgendaWidget.class;
        boolean inlineAgenda = agenda && Build.VERSION.SDK_INT >= 35;
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent alarm =
                PendingIntent.getBroadcast(context, 0, updateIntent(context, provider), flags());
        if (ids.length == 0) {
            alarms.cancel(alarm);
            return;
        }

        SharedPreferences dashboard = SettingsStore.open(context, Context.MODE_PRIVATE);
        I18n.apply(context, dashboard);
        SharedPreferences widgets = preferences(context);
        List<Feed> feeds = feeds(dashboard);
        long now = System.currentTimeMillis();
        long today = CalendarDays.midnight(now);
        long tomorrow = CalendarDays.nextDay(today);
        long nextChange = tomorrow;
        Map<String, List<CalendarEvent>> snapshots = new HashMap<>();
        for (int id : ids) {
            Set<String> filter = widgets.getStringSet(Integer.toString(id), null);
            if (filter == null) continue;
            List<CalendarEvent> events = new ArrayList<>();
            List<CalendarEvent> agendaEvents = inlineAgenda ? new ArrayList<>() : null;
            boolean hasCalendars = false;
            boolean hasIcal = false;
            boolean hasCache = false;
            for (Feed feed : feeds) {
                if (!filter.contains(feed.address)) continue;
                hasCalendars = true;
                if (feed.isDeviceCalendar()) DeviceCalendarObserver.observe(context);
                else hasIcal = true;
                if (!snapshots.containsKey(feed.address)) {
                    snapshots.put(feed.address, readEvents(context, feed));
                }
                List<CalendarEvent> snapshot = snapshots.get(feed.address);
                if (snapshot != null) {
                    hasCache = true;
                    if (inlineAgenda) agendaEvents.addAll(snapshot);
                    for (CalendarEvent event : snapshot) {
                        if (TodayEvents.intersectsDay(event, today, tomorrow)) events.add(event);
                    }
                }
            }
            Collections.sort(events, (first, second) -> Long.compare(first.start, second.start));
            TodayEvents selection = new TodayEvents(events, now);
            nextChange = Math.min(nextChange, selection.nextChange);
            if (agenda && (selection.next != null || selection.active != null)) {
                nextChange = Math.min(nextChange, now + 60_000L - now % 60_000L);
            }
            if (!agenda
                    && selection.featured != null
                    && (selection.active != null
                            || TodayEvents.isImminent(selection.featured.start, now))) {
                long remaining =
                        (selection.active == null
                                        ? selection.featured.start
                                        : selection.featured.end)
                                - now;
                if (remaining >= 60_000L) {
                    nextChange = Math.min(nextChange, now + remaining % 60_000L + 1);
                }
            }
            android.os.Bundle options = manager.getAppWidgetOptions(id);
            boolean landscape =
                    context.getResources().getConfiguration().orientation
                            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
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
                            110);
            DashboardFont font = selectedFont(widgets, dashboard, id);
            WidgetText text = new WidgetText(context, font, width);
            RemoteViews view =
                    widget(
                            context,
                            dashboard,
                            provider,
                            id,
                            selection,
                            now,
                            focusMessage(context, selection, hasCalendars, hasCache),
                            transparency(widgets, id),
                            !agenda && enlargedText(widgets, id),
                            height,
                            text,
                            hasIcal);
            if (inlineAgenda) {
                AgendaWidgetService.Api35.setCollection(
                        context,
                        view,
                        agendaEvents,
                        hasCalendars,
                        hasCache,
                        dashboard,
                        text,
                        font != DashboardFont.SYSTEM_ROBOTO,
                        width);
            }
            manager.updateAppWidget(id, view);
            if (agenda && !inlineAgenda)
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_agenda);
        }
        alarms.set(AlarmManager.RTC, nextChange, alarm);
    }

    private static String focusMessage(
            Context context, TodayEvents selection, boolean hasCalendars, boolean hasCache) {
        if (!hasCalendars) return context.getString(R.string.no_calendars);
        if (!hasCache) return context.getString(R.string.widget_cache_empty);
        return I18n.text(context, TimeDisplay.focusEmptyKey(selection));
    }

    static List<CalendarEvent> readEvents(Context context, Feed feed) {
        try {
            if (feed.isDeviceCalendar()) return DeviceCalendars.events(context, feed);
            byte[] bytes = CalendarDiskCache.read(context.getFilesDir(), feed.address);
            return bytes == null ? null : IcalendarParser.parse(bytes, feed.color, feed.address);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static RemoteViews widget(
            Context context,
            SharedPreferences dashboard,
            Class<?> provider,
            int id,
            TodayEvents selection,
            long now,
            String emptyMessage,
            int transparency,
            boolean enlarged,
            int heightDp,
            WidgetText text,
            boolean hasIcal) {
        RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.whats_next_widget);

        boolean agenda = provider == AgendaWidget.class;
        WidgetTheme theme = new WidgetTheme(dashboard);
        Palette palette = theme.palette;
        int outerAlpha = Math.round(255 * (4 - transparency) / 4f);
        tint(view, R.id.widget_background, (palette.background & 0x00ffffff) | (outerAlpha << 24));
        text.show(
                view,
                R.id.widget_heading,
                R.id.widget_heading_font,
                context.getString(agenda ? R.string.agenda : R.string.whats_next_widget),
                palette.accent,
                16,
                true,
                126);

        if (agenda) {
            view.setViewVisibility(R.id.widget_agenda, View.VISIBLE);
            view.setViewVisibility(R.id.widget_events, View.GONE);
            view.setViewVisibility(R.id.widget_empty, View.GONE);
            if (Build.VERSION.SDK_INT < 35) {
                Intent adapter =
                        new Intent(context, AgendaWidgetService.class)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .setData(Uri.parse("timewall-widget://agenda/" + id));
                view.setRemoteAdapter(R.id.widget_agenda, adapter);
            }
            view.setPendingIntentTemplate(
                    R.id.widget_agenda,
                    PendingIntent.getActivity(
                            context,
                            id,
                            new Intent(context, DashboardActivity.class),
                            collectionFlags()));
        } else {
            view.removeAllViews(R.id.widget_events);
            CalendarEvent featured = selection.featured;
            int titleSp = enlarged ? DashboardStyle.FEATURED_TITLE_SP : 16;
            int detailSp = enlarged ? DashboardStyle.FEATURED_DETAIL_SP : 15;
            if (featured != null) {
                if (enlarged) {
                    int availableHeight = Math.max(1, heightDp - 56);
                    int linesHeight = text.heightDp(titleSp, detailSp, true);
                    if (linesHeight + 12 > availableHeight) {
                        float scale = Math.max(0.1f, (availableHeight - 12f) / linesHeight);
                        titleSp = Math.max(16, (int) (titleSp * scale));
                        detailSp = Math.max(15, (int) (detailSp * scale));
                    }
                }
                CalendarEvent shown = featured;
                boolean active = selection.active != null;
                String time = TimeDisplay.time(context, dashboard, shown.start);
                if (shown.end > shown.start) {
                    time += " – " + TimeDisplay.time(context, dashboard, shown.end);
                }
                String status =
                        active
                                ? I18n.text(
                                        context,
                                        "ends_in",
                                        TimeDisplay.countdownDuration(context, shown.end - now))
                                : TimeDisplay.focusStart(
                                        context,
                                        shown.start,
                                        now,
                                        dashboard.getBoolean(TimeDisplay.HOUR_24, true));
                view.addView(
                        R.id.widget_events,
                        eventCard(
                                context,
                                shown,
                                status,
                                time,
                                active,
                                active,
                                palette,
                                theme.legacy,
                                text,
                                titleSp,
                                detailSp));
            }
            String footer = featured == null ? emptyMessage : "";
            text.show(
                    view,
                    R.id.widget_empty,
                    R.id.widget_empty_font,
                    footer,
                    palette.secondaryText,
                    14,
                    false,
                    16);
        }
        view.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                        context, 0, new Intent(context, DashboardActivity.class), flags()));
        Intent configure =
                new Intent(context, WhatsNextConfig.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        Intent refresh =
                new Intent(context, provider)
                        .setAction(REFRESH)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        view.setViewVisibility(R.id.widget_refresh, hasIcal ? View.VISIBLE : View.GONE);
        if (hasIcal) view.setImageViewBitmap(R.id.widget_refresh, refreshIcon(context));
        if (settingsIcon == null) settingsIcon = buttonIcon(context, "\uf013");
        view.setImageViewBitmap(R.id.widget_settings, settingsIcon);
        tint(view, R.id.widget_refresh, palette.text);
        tint(view, R.id.widget_settings, palette.text);
        view.setContentDescription(
                R.id.widget_refresh, context.getString(R.string.refresh_calendars));
        view.setOnClickPendingIntent(
                R.id.widget_refresh, PendingIntent.getBroadcast(context, id, refresh, flags()));
        view.setContentDescription(R.id.widget_settings, context.getString(R.string.settings));
        view.setOnClickPendingIntent(
                R.id.widget_settings, PendingIntent.getActivity(context, id, configure, flags()));
        return view;
    }

    static RemoteViews eventCard(
            Context context,
            CalendarEvent event,
            String status,
            String details,
            boolean highlightedFill,
            boolean highlightedBorder,
            Palette palette,
            boolean legacy,
            WidgetText text,
            int titleSp,
            int detailSp) {
        RemoteViews card = new RemoteViews(context.getPackageName(), R.layout.whats_next_event);
        int fill = highlightedFill ? palette.highlight : palette.card;
        tint(card, R.id.widget_card_background, fill);
        tint(card, R.id.widget_card_outline, highlightedBorder ? palette.accent : palette.border);
        card.setInt(R.id.widget_calendar_color, "setBackgroundColor", event.color);
        int primary = legacy ? ViewFactory.contrastingText(fill) : palette.text;
        int secondary = legacy ? primary : palette.secondaryText;
        text.show(
                card,
                R.id.widget_event_status,
                R.id.widget_event_status_font,
                status,
                secondary,
                detailSp,
                false,
                34);
        text.show(
                card,
                R.id.widget_event_title,
                R.id.widget_event_title_font,
                title(context, event),
                primary,
                titleSp,
                true,
                34);
        text.show(
                card,
                R.id.widget_event_details,
                R.id.widget_event_details_font,
                details,
                secondary,
                detailSp,
                false,
                34);
        return card;
    }

    static void tint(RemoteViews view, int id, int color) {
        view.setInt(id, "setColorFilter", color | Color.BLACK);
        view.setInt(id, "setImageAlpha", Color.alpha(color));
    }

    static Bitmap refreshIcon(Context context) {
        if (refreshIcon == null) refreshIcon = buttonIcon(context, ViewFactory.REFRESH_GLYPH);
        return refreshIcon;
    }

    static Bitmap buttonIcon(Context context, String glyph) {
        int size = Math.round(24 * context.getResources().getDisplayMetrics().density);
        Bitmap icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.createFromAsset(context.getAssets(), "font-awesome-solid.ttf"));
        paint.setColor(Color.WHITE);
        paint.setTextSize(size * 0.82f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        new Canvas(icon)
                .drawText(glyph, size / 2f, (size - metrics.ascent - metrics.descent) / 2f, paint);
        return icon;
    }

    private static String title(Context context, CalendarEvent event) {
        return event.title == null || event.title.trim().isEmpty()
                ? context.getString(R.string.untitled)
                : event.title;
    }

    private static int collectionFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
}
