package it.timewall.app.ui.widget;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps refresh work alive when the widget is used without an open dashboard. */
public final class WidgetWeatherRefresh {
    private static final String MANUAL = "manual";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private WidgetWeatherRefresh() {}

    static boolean enqueue(Context context, boolean manual) {
        Intent work = new Intent().putExtra(MANUAL, manual);
        try {
            if (Build.VERSION.SDK_INT >= 26) return BackgroundJob.enqueue(context, work);
            return context.startService(work.setClass(context, LegacyService.class)) != null;
        } catch (IllegalStateException | SecurityException rejected) {
            return false;
        }
    }

    public static final class LegacyService extends IntentService {
        public LegacyService() {
            super("TimeWall weather refresh");
        }

        @Override
        protected void onHandleIntent(Intent work) {
            if (work != null) WeatherWidget.refresh(this, work.getBooleanExtra(MANUAL, false));
        }
    }

    @TargetApi(26)
    public static final class BackgroundJob extends JobService {
        private static final int JOB_ID = 102;
        private volatile JobParameters running;

        private static boolean enqueue(Context context, Intent work) {
            JobScheduler scheduler =
                    (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            JobInfo job =
                    new JobInfo.Builder(JOB_ID, new ComponentName(context, BackgroundJob.class))
                            .setOverrideDeadline(0)
                            .build();
            return scheduler != null
                    && scheduler.enqueue(job, new JobWorkItem(work)) == JobScheduler.RESULT_SUCCESS;
        }

        @Override
        public boolean onStartJob(JobParameters parameters) {
            running = parameters;
            WORKER.execute(
                    () -> {
                        try {
                            while (running == parameters) {
                                JobWorkItem item = parameters.dequeueWork();
                                if (item == null) break;
                                try {
                                    WeatherWidget.refresh(
                                            this, item.getIntent().getBooleanExtra(MANUAL, false));
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
