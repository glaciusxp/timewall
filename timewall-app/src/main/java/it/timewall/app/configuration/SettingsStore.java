package it.timewall.app.configuration;

import android.content.Context;
import android.content.SharedPreferences;

import it.timewall.app.BuildConfig;
import it.timewall.app.data.calendar.CalendarFeed;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Keeps the complete and free dashboard preferences independent within an installation. */
public final class SettingsStore {
    public static final String FILE_NAME = "it.timewall.diagnostics.DashboardActivity";
    private static final String FREE_FILE_NAME = FILE_NAME + ".free";
    private static final String INITIALIZED = "profile.initialized";

    private SettingsStore() {}

    public static synchronized SharedPreferences open(Context context, int mode) {
        SharedPreferences complete = context.getSharedPreferences(FILE_NAME, mode);
        SharedPreferences free = context.getSharedPreferences(FREE_FILE_NAME, mode);
        SharedPreferences active = BuildConfig.FREE ? free : complete;
        if (active.getAll().isEmpty()) {
            SharedPreferences source = BuildConfig.FREE ? complete : free;
            SharedPreferences.Editor editor =
                    active.edit()
                            .putBoolean(INITIALIZED, true)
                            .putString("feeds", firstCalendar(source.getString("feeds", "[]")))
                            .putString(
                                    I18n.LANGUAGE_PREFERENCE,
                                    source.getString(I18n.LANGUAGE_PREFERENCE, I18n.SYSTEM))
                            .putBoolean(
                                    "clock.showSeconds",
                                    source.getBoolean("clock.showSeconds", true))
                            .putBoolean(
                                    TimeDisplay.HOUR_24,
                                    source.getBoolean(TimeDisplay.HOUR_24, true))
                            .putInt(
                                    TimeDisplay.DATE_FORMAT,
                                    source.getInt(TimeDisplay.DATE_FORMAT, 0));
            DashboardTheme theme =
                    DashboardTheme.fromSavedId(
                            source.getInt("theme", DashboardTheme.SLATE_AZURE_DARK.id));
            DashboardFont font = DashboardFont.fromSavedFile(source.getString("dashboardFont", ""));
            editor.putInt(
                    "theme",
                    theme.availableInFreeEdition() ? theme.id : DashboardTheme.SLATE_AZURE_DARK.id);
            editor.putString("dashboardFont", font.availableInFreeEdition() ? font.file : "");
            editor.apply();
        }
        return active;
    }

    private static String firstCalendar(String saved) {
        try {
            JSONArray calendars = new JSONArray(saved);
            for (int index = 0; index < calendars.length(); index++) {
                JSONObject calendar = calendars.optJSONObject(index);
                if (calendar != null
                        && CalendarFeed.accepts(calendar.optString("address"))
                        && calendar.has("name")
                        && calendar.has("color")) {
                    return new JSONArray().put(calendar).toString();
                }
            }
        } catch (JSONException ignored) {
            // An invalid source does not prevent opening a new profile.
        }
        return "[]";
    }
}
