package it.timewall.app.data.calendar;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;

import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.domain.time.CalendarDays;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Read-only access to calendars already maintained by Android. */
public final class DeviceCalendars {
    public static final int REQUEST_READ = 604;

    private DeviceCalendars() {}

    public static boolean canRead(Context context) {
        return context.checkCallingOrSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static final class Choice {
        public final Feed feed;
        public final String label;

        private Choice(Feed feed, String label) {
            this.feed = feed;
            this.label = label;
        }
    }

    public static List<Choice> list(Context context) {
        List<Choice> result = new ArrayList<>();
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                Calendars.CONTENT_URI,
                                new String[] {
                                    Calendars._ID,
                                    Calendars.CALENDAR_DISPLAY_NAME,
                                    Calendars.CALENDAR_COLOR,
                                    Calendars.ACCOUNT_NAME
                                },
                                null,
                                null,
                                Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE")) {
            if (cursor == null) throw new IllegalStateException("Calendar provider unavailable");
            while (cursor.moveToNext()) {
                String name = text(cursor, 1);
                String account = text(cursor, 3);
                Feed feed = new Feed(name, Feed.deviceAddress(cursor.getLong(0)), cursor.getInt(2));
                String label =
                        account.isEmpty() || "LOCAL".equalsIgnoreCase(account)
                                ? name
                                : name + " (" + account + ")";
                result.add(new Choice(feed, label));
            }
        }
        return result;
    }

    public static List<CalendarEvent> events(Context context, Feed feed, long day) {
        if (!canRead(context)) throw new SecurityException("Calendar permission unavailable");
        Calendar horizon = Calendar.getInstance();
        horizon.setTimeInMillis(day);
        horizon.add(Calendar.DAY_OF_MONTH, 60);
        long until = horizon.getTimeInMillis();
        // Include the UTC boundaries used by the provider for all-day dates.
        Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, day - 86400000L);
        ContentUris.appendId(uri, until + 86400000L);
        String[] columns = {
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.TITLE,
            Instances.EVENT_LOCATION,
            Instances.DESCRIPTION,
            Instances.EVENT_ID
        };
        List<CalendarEvent> result = new ArrayList<>();
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                uri.build(),
                                columns,
                                Instances.CALENDAR_ID
                                        + "=? AND ("
                                        + Instances.STATUS
                                        + " IS NULL OR "
                                        + Instances.STATUS
                                        + "!=?)",
                                new String[] {
                                    Long.toString(Feed.deviceId(feed.address)),
                                    Integer.toString(Events.STATUS_CANCELED)
                                },
                                Instances.BEGIN + " ASC")) {
            if (cursor == null) throw new IllegalStateException("Calendar provider unavailable");
            while (cursor.moveToNext()) {
                boolean allDay = cursor.getInt(2) != 0;
                long start = cursor.getLong(0);
                long end = cursor.getLong(1);
                if (allDay) {
                    start = localDate(start);
                    end = localDate(end);
                }
                if (end <= day || start >= until) continue;
                result.add(
                        new CalendarEvent(
                                start,
                                end,
                                allDay,
                                text(cursor, 3),
                                text(cursor, 4),
                                text(cursor, 5),
                                null,
                                feed.address,
                                feed.color,
                                text(cursor, 6)));
            }
        }
        return result;
    }

    public static List<CalendarEvent> events(Context context, Feed feed) {
        return events(context, feed, CalendarDays.midnight(System.currentTimeMillis()));
    }

    private static String text(Cursor cursor, int index) {
        String value = cursor.getString(index);
        return value == null ? "" : value;
    }

    private static long localDate(long millis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(millis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }
}
