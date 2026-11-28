package it.timewall.app.ui.widget;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;

import it.timewall.app.data.calendar.DeviceCalendars;

/** Observes the system calendar for as long as the TimeWall process is alive. */
public final class DeviceCalendarObserver {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DEBOUNCE_MS = 500;
    private static ContentObserver observer;
    private static Runnable dashboard;

    private DeviceCalendarObserver() {}

    public static void attachDashboard(Context context, Runnable listener) {
        dashboard = listener;
        observe(context);
    }

    public static void detachDashboard(Runnable listener) {
        if (dashboard == listener) dashboard = null;
    }

    static void observe(Context context) {
        Context app = context.getApplicationContext();
        if (Looper.myLooper() != MAIN.getLooper()) {
            MAIN.post(() -> observe(app));
            return;
        }
        if (observer != null || !DeviceCalendars.canRead(app)) return;
        Runnable refresh =
                () -> {
                    if (dashboard != null) dashboard.run();
                    WhatsNextWidget.requestUpdate(app);
                };
        ContentObserver candidate =
                new ContentObserver(MAIN) {
                    @Override
                    public void onChange(boolean selfChange) {
                        MAIN.removeCallbacks(refresh);
                        MAIN.postDelayed(refresh, DEBOUNCE_MS);
                    }
                };
        try {
            app.getContentResolver()
                    .registerContentObserver(CalendarContract.CONTENT_URI, true, candidate);
            observer = candidate;
        } catch (SecurityException unavailable) {
            // After a permission change the next dashboard or widget update can try again.
        }
    }
}
