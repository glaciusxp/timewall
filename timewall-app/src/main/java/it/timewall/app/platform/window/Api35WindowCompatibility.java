package it.timewall.app.platform.window;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

/** Edge-to-edge APIs isolated from the KitKat class-loading path. */
@TargetApi(35)
final class Api35WindowCompatibility {
    /** Prevents instantiation of the static helper. */
    private Api35WindowCompatibility() {}

    /**
     * Protects interactive content from visible system bars, cutouts and the keyboard. Base padding
     * is captured once to avoid accumulation after repeated dispatches or window resizes.
     *
     * @param activity owner of the edge-to-edge window
     * @param host container whose safe bounds follow the system insets
     */
    static void apply(Activity activity, View host) {
        activity.getWindow().setDecorFitsSystemWindows(false);
        int left = host.getPaddingLeft();
        int top = host.getPaddingTop();
        int right = host.getPaddingRight();
        int bottom = host.getPaddingBottom();
        host.setOnApplyWindowInsetsListener(
                (view, windowInsets) -> {
                    Insets safe =
                            windowInsets.getInsets(
                                    WindowInsets.Type.systemBars()
                                            | WindowInsets.Type.displayCutout()
                                            | WindowInsets.Type.ime());
                    view.setPadding(
                            left + safe.left,
                            top + safe.top,
                            right + safe.right,
                            bottom + safe.bottom);
                    return windowInsets;
                });
        host.requestApplyInsets();
    }
}
