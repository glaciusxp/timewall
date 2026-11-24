package it.timewall.app.localization;

import android.content.Context;

/**
 * In-app privacy notice; identity stays invariant across languages, and no provisional website is
 * linked.
 */
public final class PrivacyNotice {
    public static final String CONTROLLER = "Andrea Persano";
    public static final String CONTACT = "andreapersano@gmail.com";
    public static final String UPDATED = "2026-11-20";
    public static final String WEBSITE = "";

    /** Prevents instantiation of the notice composer. */
    private PrivacyNotice() {}

    /**
     * Composes translated sections from bundled resources, making the notice available without
     * network access.
     */
    public static String text(Context context) {
        StringBuilder text =
                new StringBuilder(I18n.text(context, "privacy_identity", CONTROLLER, CONTACT));
        text.append("\n\n").append(I18n.text(context, "privacy_updated", UPDATED));
        for (String key :
                new String[] {
                    "privacy_service",
                    "privacy_calendars",
                    "privacy_weather",
                    "privacy_local",
                    "privacy_retention",
                    "privacy_security",
                    "privacy_rights"
                }) {
            text.append("\n\n").append(I18n.text(context, key));
        }
        text.append("\n\nOpen-Meteo: https://open-meteo.com/en/terms");
        text.append("\nGarante: https://www.garanteprivacy.it/");
        text.append("\n\n")
                .append(WEBSITE.isEmpty() ? I18n.text(context, "privacy_web_pending") : WEBSITE);
        return text.toString();
    }
}
