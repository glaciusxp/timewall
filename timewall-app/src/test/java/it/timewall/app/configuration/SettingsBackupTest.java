package it.timewall.app.configuration;

import static org.junit.Assert.*;

import org.json.JSONException;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SettingsBackupTest {
    @Test
    public void keepsSavedDashboardValuesAndSkipsRuntimeState() throws Exception {
        Map<String, Object> saved = new HashMap<>();
        saved.put("theme", 4);
        saved.put("upcomingEventsWindow", 3);
        saved.put("clock.showSeconds", false);
        saved.put("weather.lat", Double.doubleToLongBits(44.5));
        saved.put("weather.lon", Double.doubleToLongBits(11.3));
        saved.put("weather.name", "Bologna");
        saved.put(
                "feeds",
                "[{\"address\":\"https://example.org/cal.ics\",\"name\":\"Demo\",\"color\":-1}]");
        saved.put("excluded_calendars", Collections.singleton("https://example.org/cal.ics"));
        saved.put(SettingsBackup.IMAGE_URI, "content://private/image");
        saved.put(SettingsBackup.IMAGE_BLUR, 75);
        saved.put("weather.forecast", "cache");
        saved.put("enlargeText.5", true);

        SettingsBackup backup = SettingsBackup.decode(SettingsBackup.encode(saved, "image"));

        assertEquals("image", backup.image);
        assertFalse(backup.settings.containsKey("upcomingEventsWindow"));
        assertEquals(75, backup.settings.get(SettingsBackup.IMAGE_BLUR));
        assertEquals(false, backup.settings.get("clock.showSeconds"));
        assertEquals(saved.get("weather.lat"), backup.settings.get("weather.lat"));
        assertTrue(backup.settings.containsKey("feeds"));
        assertFalse(backup.settings.containsKey(SettingsBackup.IMAGE_URI));
        assertFalse(backup.settings.containsKey("weather.forecast"));
        assertFalse(backup.settings.containsKey("enlargeText.5"));
    }

    @Test
    public void keepsRemoteFeedsAndOmitsDeviceLocalCalendarReferences() throws Exception {
        Map<String, Object> saved = new HashMap<>();
        saved.put(
                "feeds",
                "[{\"address\":\"device:42\",\"name\":\"Local\",\"color\":-1},"
                    + "{\"address\":\"https://example.org/cal.ics\",\"name\":\"Remote\",\"color\":-1}]");
        java.util.Set<String> excluded = new java.util.HashSet<>();
        excluded.add("device:42");
        excluded.add("https://example.org/cal.ics");
        saved.put("excluded_calendars", excluded);
        String json = SettingsBackup.encode(saved, "");
        assertFalse(json.contains("device:42"));
        assertTrue(json.contains("https://example.org/cal.ics"));
        assertTrue(SettingsBackup.decode(json).settings.containsKey("feeds"));
    }

    @Test
    public void missingAndRetiredFieldsLeaveAppDefaultsInEffect() throws Exception {
        SettingsBackup backup = SettingsBackup.decode(document(1, "{\"obsolete\":42}"));
        assertTrue(backup.settings.isEmpty());
        assertTrue(SettingsBackup.managedKeys().contains("clock.showSeconds"));
    }

    @Test
    public void validatesFormatVersionTypesAndCalendarUrls() {
        assertThrows(
                SettingsBackup.UnsupportedVersion.class,
                () -> SettingsBackup.decode(document(2, "{}")));
        assertThrows(JSONException.class, () -> SettingsBackup.decode("{}"));
        assertThrows(
                JSONException.class,
                () -> SettingsBackup.decode(document(1, "{\"clock.showSeconds\":\"false\"}")));
        assertThrows(JSONException.class, () -> SettingsBackup.decode(document(1, "{}") + "oops"));
        assertThrows(
                JSONException.class,
                () ->
                        SettingsBackup.decode(
                                document(
                                        1,
                                        "{\"feeds\":[{\"address\":\"file:///secret\",\"name\":\"Test\",\"color\":4}]}")));
    }

    private static String document(int version, String settings) {
        return "{\"format\":\"timewall-dashboard-settings\",\"version\":"
                + version
                + ",\"settings\":"
                + settings
                + "}";
    }
}
