package it.timewall.app.ui.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import it.timewall.app.R;
import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.domain.time.CalendarDays;
import it.timewall.app.domain.time.TodayEvents;
import it.timewall.app.localization.DashboardDates;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Supplies scrollable, independently filtered agenda rows from the shared iCal cache. */
public final class AgendaWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new AgendaRows(
                getApplicationContext(),
                intent.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID));
    }

    private static final class AgendaRows implements RemoteViewsFactory {
        private final Context context;
        private final int id;
        private final List<Row> rows = new ArrayList<>();

        AgendaRows(Context context, int id) {
            this.context = context;
            this.id = id;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            rows.clear();
            SharedPreferences dashboard = SettingsStore.open(context, Context.MODE_PRIVATE);
            I18n.apply(context, dashboard);
            SharedPreferences widgets = WhatsNextWidget.preferences(context);
            Set<String> filter = widgets.getStringSet(Integer.toString(id), null);
            if (filter == null) return;

            WidgetTheme theme = new WidgetTheme(dashboard);
            Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id);
            boolean landscape =
                    context.getResources().getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE;
            int width =
                    options.getInt(
                            landscape
                                    ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                                    : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                            180);
            WidgetText text =
                    new WidgetText(
                            context, WhatsNextWidget.selectedFont(widgets, dashboard, id), width);
            List<CalendarEvent> events = new ArrayList<>();
            boolean selected = false;
            boolean hasCache = false;
            for (Feed feed : WhatsNextWidget.feeds(dashboard)) {
                if (!filter.contains(feed.address)) continue;
                selected = true;
                List<CalendarEvent> cached = WhatsNextWidget.readEvents(context, feed);
                if (cached != null) {
                    hasCache = true;
                    events.addAll(cached);
                }
            }
            if (!hasCache) {
                addLabel(
                        context.getString(
                                selected ? R.string.widget_cache_empty : R.string.no_calendars),
                        theme,
                        text,
                        false);
                return;
            }

            Collections.sort(events, (first, second) -> Long.compare(first.start, second.start));
            long now = System.currentTimeMillis();
            long today = CalendarDays.midnight(now);
            long tomorrow = CalendarDays.nextDay(today);
            List<CalendarEvent> current = new ArrayList<>();
            List<CalendarEvent> upcoming = new ArrayList<>();
            for (CalendarEvent event : events) {
                if (TodayEvents.intersectsDay(event, today, tomorrow)) current.add(event);
                else if (event.start >= tomorrow) upcoming.add(event);
            }

            addLabel(context.getString(R.string.today), theme, text, true);
            if (current.isEmpty()) {
                addLabel(context.getString(R.string.no_today), theme, text, false);
            } else {
                TodayEvents selection = new TodayEvents(current, now);
                boolean linePending = selection.active == null;
                for (CalendarEvent event : current) {
                    if (linePending && event == selection.next) {
                        addNowLine(theme);
                        linePending = false;
                    }
                    boolean active = !event.allDay && event.start <= now && now < event.end;
                    String detail = time(dashboard, event);
                    if (active) {
                        detail +=
                                " · "
                                        + I18n.text(
                                                context,
                                                "ends_in",
                                                TimeDisplay.countdownDuration(
                                                        context, event.end - now));
                    } else if (event == selection.next) {
                        detail += " · " + TimeDisplay.startsIn(context, event.start - now);
                    }
                    addEvent(event, detail, active, active || event.allDay, theme, text);
                }
                if (linePending) {
                    addNowLine(theme);
                    addLabel("✓  " + context.getString(R.string.day_complete), theme, text, false);
                }
            }

            addLabel(context.getString(R.string.upcoming), theme, text, true);
            if (upcoming.isEmpty()) {
                addLabel(context.getString(R.string.no_upcoming), theme, text, false);
                return;
            }
            DashboardDates dates = new DashboardDates(context);
            long currentGroup = Long.MIN_VALUE;
            for (CalendarEvent event : upcoming) {
                long day = CalendarDays.midnight(event.start);
                if (day != currentGroup) {
                    currentGroup = day;
                    addLabel(
                            day == tomorrow
                                    ? dates.localizedDate(day, "tomorrow_date_pattern")
                                    : dates.dateLabel(day),
                            theme,
                            text,
                            false);
                }
                addEvent(event, time(dashboard, event), false, false, theme, text);
            }
        }

        private String time(SharedPreferences dashboard, CalendarEvent event) {
            return event.allDay
                    ? context.getString(R.string.all_day)
                    : TimeDisplay.time(context, dashboard, event.start)
                            + " - "
                            + TimeDisplay.time(context, dashboard, event.end);
        }

        private interface Row {
            RemoteViews render();
        }

        private void addEvent(
                CalendarEvent event,
                String detail,
                boolean highlightedFill,
                boolean highlightedBorder,
                WidgetTheme theme,
                WidgetText text) {
            rows.add(
                    () -> {
                        RemoteViews card =
                                WhatsNextWidget.eventCard(
                                        context,
                                        event,
                                        "",
                                        detail,
                                        highlightedFill,
                                        highlightedBorder,
                                        theme.palette,
                                        theme.legacy,
                                        text,
                                        17,
                                        15);
                        card.setOnClickFillInIntent(R.id.widget_agenda_card_root, new Intent());
                        return card;
                    });
        }

        private void addLabel(String label, WidgetTheme theme, WidgetText text, boolean heading) {
            rows.add(
                    () -> {
                        RemoteViews row =
                                new RemoteViews(
                                        context.getPackageName(), R.layout.widget_agenda_heading);
                        text.show(
                                row,
                                R.id.widget_agenda_heading,
                                R.id.widget_agenda_heading_font,
                                label,
                                heading ? theme.palette.accent : theme.palette.secondaryText,
                                17,
                                heading,
                                16);
                        row.setViewVisibility(
                                R.id.widget_agenda_divider, heading ? View.VISIBLE : View.GONE);
                        row.setInt(
                                R.id.widget_agenda_divider,
                                "setBackgroundColor",
                                theme.palette.border);
                        return row;
                    });
        }

        private void addNowLine(WidgetTheme theme) {
            rows.add(
                    () -> {
                        RemoteViews row =
                                new RemoteViews(
                                        context.getPackageName(), R.layout.widget_agenda_now);
                        row.setInt(
                                R.id.widget_agenda_now_line,
                                "setBackgroundColor",
                                theme.palette.accent);
                        WhatsNextWidget.tint(row, R.id.widget_agenda_now_dot, theme.palette.accent);
                        return row;
                    });
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            return position >= 0 && position < rows.size() ? rows.get(position).render() : null;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public void onDestroy() {
            rows.clear();
        }
    }
}
