package it.timewall.app.ui.widget;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
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

    static void schedule(Context context, int widgetId) {
        Intent work =
                new Intent()
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        .putExtra(REQUESTED_AT, SystemClock.elapsedRealtime());
        if (Build.VERSION.SDK_INT >= 26) {
            BackgroundJob.enqueue(context, work);
        } else {
            context.startService(work.setClass(context, LegacyService.class));
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
    public static final class BackgroundJob extends JobService {
        private static final int JOB_ID = 101;
        private volatile JobParameters running;

        private static void enqueue(Context context, Intent work) {
            JobScheduler scheduler =
                    (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            JobInfo job =
                    new JobInfo.Builder(JOB_ID, new ComponentName(context, BackgroundJob.class))
                            .setOverrideDeadline(0)
                            .build();
            scheduler.enqueue(job, new JobWorkItem(work));
        }

        @Override
        public boolean onStartJob(JobParameters parameters) {
            running = parameters;
            CalendarRefreshWorker.EXECUTOR.execute(
                    () -> {
                        try {
                            while (running == parameters) {
                                JobWorkItem item = parameters.dequeueWork();
                                if (item == null) break;
                                try {
                                    refresh(this, item.getIntent());
                                } finally {
                                    if (running == parameters) parameters.completeWork(item);
                                }
                            }
                        } catch (IllegalArgumentException stopped) {
                            // JobScheduler can stop the job before onStopJob reaches the main
                            // thread.
                        }
                    });
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters parameters) {
            running = null;
            return true;
        }
    }
}
