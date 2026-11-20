package it.timewall.app.platform.window;

import android.app.Activity;
import android.os.Build;

/** Intercepts modern Back for Settings and Info; API 19 retains the Activity callback. */
public final class BackNavigation {
    interface Registration {
        /**
         * Registers or removes settings interception without handling dashboard-to-home Back.
         *
         * @param enabled true while Settings or Info are visible
         */
        void setEnabled(boolean enabled);
    }

    private final Registration registration;

    /**
     * Prepares an initially disabled adapter without resolving Android 13 types on KitKat.
     *
     * @param activity owner of the window dispatcher
     * @param action screen-exit action, including confirmation for unsaved settings
     */
    public BackNavigation(Activity activity, Runnable action) {
        registration =
                Build.VERSION.SDK_INT >= 33 ? new Api33BackNavigation(activity, action) : null;
    }

    /**
     * Enables interception on API 33+; older versions continue using onBackPressed.
     *
     * @param enabled whether the open screen needs to intercept Back
     */
    public void setEnabled(boolean enabled) {
        if (registration != null) registration.setEnabled(enabled);
    }
}
