package it.timewall.app.platform.window;

import android.annotation.TargetApi;
import android.app.Activity;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** New platform types are isolated in a class instantiated only on API 33 and newer. */
@TargetApi(33)
final class Api33BackNavigation implements BackNavigation.Registration {
    private final OnBackInvokedDispatcher dispatcher;
    private final OnBackInvokedCallback callback;
    private boolean registered;

    /**
     * Adapts the existing settings-exit action to the Activity's native dispatcher.
     *
     * @param activity owner of the dispatcher
     * @param action action to run when Back is committed
     */
    Api33BackNavigation(Activity activity, Runnable action) {
        dispatcher = activity.getOnBackInvokedDispatcher();
        callback = action::run;
    }

    /**
     * Registers one callback or removes it; repeated requests do not duplicate registration.
     * Removing it restores the system's normal dashboard-to-home navigation.
     *
     * @param enabled true while settings are visible
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (registered == enabled) return;
        if (enabled) {
            dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        } else {
            dispatcher.unregisterOnBackInvokedCallback(callback);
        }
        registered = enabled;
    }
}
