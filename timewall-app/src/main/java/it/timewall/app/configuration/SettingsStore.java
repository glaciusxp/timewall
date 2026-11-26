package it.timewall.app.configuration;

import android.content.Context;
import android.content.SharedPreferences;

import it.timewall.app.BuildConfig;

/** Opens the existing settings file for each distribution channel. */
public final class SettingsStore {
    public static final String FILE_NAME = "it.timewall.diagnostics.DashboardActivity";
    private static final String LEGACY_FREE_FILE_NAME = FILE_NAME + ".free";

    private SettingsStore() {}

    public static SharedPreferences open(Context context, int mode) {
        // Android isolates the three application IDs. Retain the existing file for in-place
        // updates.
        String file = "complete".equals(BuildConfig.FLAVOR) ? FILE_NAME : LEGACY_FREE_FILE_NAME;
        return context.getSharedPreferences(file, mode);
    }
}
