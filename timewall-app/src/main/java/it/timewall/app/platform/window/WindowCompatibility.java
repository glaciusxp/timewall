package it.timewall.app.platform.window;

import android.app.Activity;
import android.os.Build;
import android.view.View;

/** Preserves KitKat decor fitting and handles enforced edge-to-edge on modern devices. */
public final class WindowCompatibility {
    /** Prevents instantiation of the static platform entry point. */
    private WindowCompatibility() {}

    /**
     * Installs insets handling on Android 15+ only, without changing each screen's own spacing.
     * Older versions retain the existing fullscreen layout and system-managed window fitting.
     *
     * @param activity owner of the window
     * @param host shared container of the dashboard and settings overlay
     */
    public static void apply(Activity activity, View host) {
        if (Build.VERSION.SDK_INT >= 35) Api35WindowCompatibility.apply(activity, host);
    }
}
