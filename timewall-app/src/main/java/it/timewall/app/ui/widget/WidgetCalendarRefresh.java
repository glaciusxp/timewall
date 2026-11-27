package it.timewall.app.ui.widget;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.data.calendar.CalendarRefreshWorker;
import it.timewall.app.data.calendar.CalendarRepository;
import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.model.Feed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Runs an explicit widget refresh even if the dashboard Activity is closed. */
public final class WidgetCalendarRefresh {
    private static final String REQUESTED_AT = "requested_at";

    private WidgetCalendarRefresh() {}

    static boolean schedule(Context context, int widgetId) {
        Intent work =
                new Intent()
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .putExtra(REQUESTED_AT, SystemClock.elapsedRealtime());
        try {
            if (Build.VERSION.SDK_INT >= 26) return BackgroundJob.enqueue(context, work);
            return context.startService(work.setClass(context, LegacyService.class)) != null;
        } catch (IllegalStateException | SecurityException rejected) {
            return false;
        }
    }

    private static void refresh(Context context, Intent work) {
        int id =
                work.getIntExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Set<String> selected =
                WhatsNextWidget.preferences(context).getStringSet(Integer.toString(id), null);
        if (selected == null || selected.isEmpty()) return;
        List<Feed> filtered = new ArrayList<>();
        for (Feed feed : WhatsNextWidget.feeds(SettingsStore.open(context, Context.MODE_PRIVATE))) {
            if (selected.contains(feed.address)) filtered.add(feed);
        }
        if (filtered.isEmpty()) {
            WhatsNextWidget.requestUpdate(context);
            return;
        }

        long requestedAt = work.getLongExtra(REQUESTED_AT, SystemClock.elapsedRealtime());
        CalendarRepository repository =
                CalendarRefreshWorker.open(context.getFilesDir(), requestedAt);
        repository.refresh(
                filtered,
                false,
                new CalendarRepository.Listener() {
                    @Override
                    public void cached(List<CalendarEvent> events) {}

                    @Override
                    public void loaded(List<CalendarEvent> events, int failures) {
                        WhatsNextWidget.requestUpdate(context);
                    }
                });
    }

    public static final class LegacyService extends IntentService {
        public LegacyService() {
            super("TimeWall widget refresh");
        }

        @Override
        protected void onHandleIntent(Intent work) {
            if (work == null) return;
            try {
                CalendarRefreshWorker.EXECUTOR.submit(() -> refresh(this, work)).get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException failure) {
                WhatsNextWidget.requestUpdate(this);
            }
        }
    }

    @TargetApi(26)
    public static final class BackgroundJob extends WidgetRefreshJob {
        private static boolean enqueue(Context context, Intent work) {
            return enqueue(context, work, 101, BackgroundJob.class);
        }

        @Override
        protected void execute(Runnable work) {
            CalendarRefreshWorker.EXECUTOR.execute(work);
        }

        @Override
        protected void refresh(Intent work) {
            WidgetCalendarRefresh.refresh(this, work);
        }
    }
}
