package it.timewall.app.configuration;

import it.timewall.app.data.calendar.CalendarFeed;
import it.timewall.app.domain.model.Feed;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Portable dashboard preferences. Missing keys are deliberately left to the app defaults. */
public final class SettingsBackup {
    public static final String IMAGE_URI = "dashboardBackgroundUri";
    public static final String IMAGE_NAME = "dashboardBackgroundName";
    public static final String IMAGE_BLUR = "dashboardBackgroundBlur";
    public static final String THEME_BACKGROUND_MIGRATED = "themeBackgroundMigrated";
    public static final String CUSTOM_THEME_BORDER = "customTheme.border";
    private static final String FORMAT = "timewall-dashboard-settings";
    private static final int VERSION = 1;
    private static final String FEEDS = "feeds";
    private static final String FILTER = "excluded_calendars";
    private static final String[] STRINGS = {
        "language",
        "dashboardFont",
        IMAGE_NAME,
        "weather.name",
        "weather.detail",
        "customTheme.accent",
        "customTheme.background",
        "customTheme.card",
        "customTheme.highlight",
        "customTheme.text",
        "customTheme.secondaryText",
        "customTheme.clock",
        "customTheme.date",
        CUSTOM_THEME_BORDER
    };
    private static final String[] BOOLEANS = {
        "clock.showSeconds",
        "clock.use24Hour",
        "weather.enabled",
        "weather.fahrenheit",
        THEME_BACKGROUND_MIGRATED
    };
    private static final String[] INTEGERS = {
        "theme", "clock.dateFormat", IMAGE_BLUR, "weather.refreshMinutes"
    };
    private static final String[] COORDINATES = {"weather.lat", "weather.lon"};
    public final Map<String, Object> settings;
    public final String image;

    private SettingsBackup(Map<String, Object> settings, String image) {
        this.settings = settings;
        this.image = image;
    }

    public static final class UnsupportedVersion extends IOException {}

    public static String encode(Map<String, ?> preferences, String image) throws JSONException {
        JSONObject values = new JSONObject();
        for (String[] group : new String[][] {STRINGS, BOOLEANS, INTEGERS, COORDINATES}) {
            for (String key : group) {
                if (preferences.containsKey(key)) values.put(key, preferences.get(key));
            }
        }
        if (preferences.containsKey(FEEDS)) {
            JSONArray feeds = new JSONArray((String) preferences.get(FEEDS));
            JSONArray portable = new JSONArray();
            for (int index = 0; index < feeds.length(); index++) {
                JSONObject feed = feeds.getJSONObject(index);
                if (Feed.deviceId(feed.getString("address")) < 0) portable.put(feed);
            }
            values.put(FEEDS, portable);
        }
        if (preferences.containsKey(FILTER)) {
            JSONArray portable = new JSONArray();
            for (Object address : (Set<?>) preferences.get(FILTER)) {
                if (address instanceof String && Feed.deviceId((String) address) < 0) {
                    portable.put(address);
                }
            }
            values.put(FILTER, portable);
        }
        return new JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("settings", values)
                .put("background", image)
                .toString();
    }

    public static SettingsBackup decode(String input) throws JSONException, UnsupportedVersion {
        JSONTokener tokens = new JSONTokener(input);
        Object parsed = tokens.nextValue();
        if (!(parsed instanceof JSONObject) || tokens.nextClean() != 0) {
            throw new JSONException("Invalid backup document");
        }
        JSONObject document = (JSONObject) parsed;
        if (!FORMAT.equals(document.get("format"))) throw new JSONException("Wrong backup format");
        Object version = document.get("version");
        if (!(version instanceof Integer) || (int) version < 1) {
            throw new JSONException("Invalid backup version");
        }
        if ((int) version > VERSION) throw new UnsupportedVersion();
        JSONObject values = document.getJSONObject("settings");
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : STRINGS) {
            if (!values.has(key)) continue;
            Object value = values.get(key);
            if (!(value instanceof String)) throw new JSONException("Invalid setting type");
            String text = (String) value;
            if (key.startsWith("customTheme.")
                    && !text.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
                throw new JSONException("Invalid theme color");
            }
            if ("language".equals(key)
                    && !Arrays.asList(
                                    "system", "en", "it", "de", "es", "fr", "ja", "pt", "ru", "zh")
                            .contains(text)) {
                continue;
            }
            if ("dashboardFont".equals(key)
                    && !DashboardFont.fromSavedFile(text).file.equals(text)) {
                continue;
            }
            result.put(key, text);
        }
        for (String key : BOOLEANS) {
            if (!values.has(key)) continue;
            Object value = values.get(key);
            if (!(value instanceof Boolean)) throw new JSONException("Invalid setting type");
            result.put(key, value);
        }
        for (String key : INTEGERS) {
            if (!values.has(key)) continue;
            Object value = values.get(key);
            if (!(value instanceof Integer)) throw new JSONException("Invalid setting type");
            int number = (int) value;
            if ("theme".equals(key)) {
                for (DashboardTheme theme : DashboardTheme.values()) {
                    if (theme.id == number) result.put(key, value);
                }
            } else if (("clock.dateFormat".equals(key) && number >= 0 && number <= 3)
                    || (IMAGE_BLUR.equals(key)
                            && (number >= 0 && number <= 3
                                    || number == 25
                                    || number == 50
                                    || number == 75
                                    || number == 100))
                    || ("weather.refreshMinutes".equals(key)
                            && (number == 15 || number == 30 || number == 60 || number == 120))) {
                result.put(key, value);
            }
        }
        for (String key : COORDINATES) {
            if (!values.has(key)) continue;
            Object value = values.get(key);
            if (!(value instanceof Integer || value instanceof Long)) {
                throw new JSONException("Invalid coordinate type");
            }
            long bits = ((Number) value).longValue();
            double coordinate = Double.longBitsToDouble(bits);
            if (Double.isNaN(coordinate)
                    || Math.abs(coordinate) > ("weather.lat".equals(key) ? 90 : 180)) {
                throw new JSONException("Invalid coordinates");
            }
            result.put(key, bits);
        }
        if (!(result.containsKey("weather.lat")
                && result.containsKey("weather.lon")
                && result.containsKey("weather.name"))) {
            result.remove("weather.name");
            result.remove("weather.detail");
            result.remove("weather.lat");
            result.remove("weather.lon");
        }
        Set<String> addresses = new HashSet<>();
        if (values.has(FEEDS)) {
            JSONArray entries = values.getJSONArray(FEEDS);
            JSONArray feeds = new JSONArray();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.getJSONObject(i);
                Object address = item.get("address");
                Object name = item.get("name");
                Object color = item.get("color");
                if (!(address instanceof String)
                        || !CalendarFeed.accepts((String) address)
                        || !(name instanceof String)
                        || ((String) name).trim().isEmpty()
                        || !(color instanceof Integer)
                        || !addresses.add((String) address)) {
                    throw new JSONException("Invalid calendar");
                }
                feeds.put(
                        new JSONObject()
                                .put("address", address)
                                .put("name", name)
                                .put("color", color));
            }
            result.put(FEEDS, feeds.toString());
        }
        if (values.has(FILTER)) {
            JSONArray list = values.getJSONArray(FILTER);
            Set<String> filter = new HashSet<>();
            for (int i = 0; i < list.length(); i++) {
                Object item = list.get(i);
                if (!(item instanceof String)) throw new JSONException("Invalid calendar filter");
                if (addresses.contains(item)) filter.add((String) item);
            }
            if (filter.size() == addresses.size()) filter.clear();
            result.put(FILTER, filter);
        }
        String image = "";
        if (document.has("background")) {
            Object raw = document.get("background");
            if (!(raw instanceof String)) throw new JSONException("Invalid background");
            image = (String) raw;
        }
        if (image.isEmpty()) {
            result.remove(IMAGE_NAME);
            result.remove(IMAGE_BLUR);
        }
        return new SettingsBackup(result, image);
    }

    public static Set<String> managedKeys() {
        Set<String> keys = new HashSet<>();
        for (String[] group : new String[][] {STRINGS, BOOLEANS, INTEGERS, COORDINATES}) {
            keys.addAll(Arrays.asList(group));
        }
        keys.addAll(Arrays.asList(FEEDS, FILTER, IMAGE_URI));
        return keys;
    }
}
