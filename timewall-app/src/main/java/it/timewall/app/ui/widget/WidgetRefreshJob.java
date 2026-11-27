package it.timewall.app.ui.widget;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates queued widget refreshes with the API 26 JobScheduler lifecycle. */
@TargetApi(26)
abstract class WidgetRefreshJob extends JobService {
    private volatile JobParameters running;

    protected abstract void execute(Runnable work);

    protected abstract void refresh(Intent work);

    static boolean enqueue(Context context, Intent work, int id, Class<?> service) {
        JobScheduler scheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return false;
        JobInfo job =
                new JobInfo.Builder(id, new ComponentName(context, service))
                        .setOverrideDeadline(0)
                        .build();
        return scheduler.enqueue(job, new JobWorkItem(work)) == JobScheduler.RESULT_SUCCESS;
    }

    @Override
    public final boolean onStartJob(JobParameters parameters) {
        running = parameters;
        execute(
                () ->
                        drain(
                                () -> running == parameters,
                                parameters::dequeueWork,
                                item -> {
                                    Intent intent = item.getIntent();
                                    if (intent != null) refresh(intent);
                                },
                                parameters::completeWork,
                                () -> Log.w("TimeWall", "Widget refresh failed")));
        return true;
    }

    @Override
    public final boolean onStopJob(JobParameters parameters) {
        running = null;
        return true;
    }

    static <T> void drain(
            BooleanSupplier active,
            Supplier<T> next,
            Consumer<T> process,
            Consumer<T> complete,
            Runnable failed) {
        try {
            while (active.getAsBoolean()) {
                T item = next.get();
                if (item == null || !active.getAsBoolean()) return;
                try {
                    process.accept(item);
                } catch (RuntimeException failure) {
                    failed.run();
                }
                if (active.getAsBoolean()) complete.accept(item);
            }
        } catch (IllegalArgumentException stopped) {
            // JobScheduler may stop a job before onStopJob reaches the main thread.
        }
    }
}
