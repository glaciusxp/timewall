package it.timewall.app.ui.widget;

import android.annotation.TargetApi;
import android.app.IntentService;
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
    public static final class BackgroundJob extends WidgetRefreshJob {
        private static boolean enqueue(Context context, Intent work) {
            return enqueue(context, work, 102, BackgroundJob.class);
        }

        @Override
        protected void execute(Runnable work) {
            WORKER.execute(work);
        }

        @Override
        protected void refresh(Intent work) {
            WeatherWidget.refresh(this, work.getBooleanExtra(MANUAL, false));
        }
    }
}
