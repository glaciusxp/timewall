package it.timewall.app.data.calendar;

import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.time.CalendarDays;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/** Expands common recurrence rules over a finite window on old Android versions as well. */
final class CalendarRecurrence {
    private static final long DAY_MILLIS = 86_400_000L;
    private static final int MAX_PERIODS = 8192;
    private static final String[] WEEKDAYS = {"SU", "MO", "TU", "WE", "TH", "FR", "SA"};

    private CalendarRecurrence() {}

    interface OccurrenceEnd {
        long at(long start) throws IOException;
    }

    static final class Window {
        final long from;
        final long to;
        int remaining = 512;

        Window(long now) {
            Calendar boundary = Calendar.getInstance();
            boundary.setTimeInMillis(CalendarDays.midnight(now));
            boundary.add(Calendar.DAY_OF_MONTH, -1);
            from = boundary.getTimeInMillis();
            boundary.add(Calendar.DAY_OF_MONTH, 61);
            to = boundary.getTimeInMillis();
        }
    }

    static void append(
            java.util.List<CalendarEvent> output,
            CalendarEvent event,
            TimeZone zone,
            String rawRule,
            Set<Long> additions,
            Set<Long> exclusions,
            Window window,
            OccurrenceEnd duration)
            throws IOException {
        if (!exclusions.contains(event.start)) output.add(event);
        TreeSet<Long> starts = new TreeSet<>(additions);
        Rule rule = Rule.read(rawRule, zone);
        if (rule != null && window.remaining > 0 && event.start < window.to) {
            rule.expand(event, zone, window, starts);
        }
        long allDayLength = Math.round((event.end - event.start) / (double) DAY_MILLIS);
        for (long start : starts) {
            if (window.remaining == 0) break;
            if (start == event.start || start >= window.to || exclusions.contains(start)) continue;
            long end;
            if (duration != null) {
                end = duration.at(start);
            } else if (event.allDay) {
                Calendar finish = Calendar.getInstance(zone);
                finish.setTimeInMillis(start);
                finish.add(Calendar.DAY_OF_MONTH, (int) allDayLength);
                end = finish.getTimeInMillis();
            } else {
                end = start + (event.end - event.start);
            }
            if (start < window.from && end <= window.from) continue;
            output.add(
                    new CalendarEvent(
                            start,
                            end,
                            event.allDay,
                            event.title,
                            event.location,
                            event.description,
                            event.created,
                            event.feedAddress,
                            event.color,
                            event.uid));
            window.remaining--;
        }
    }

    private static final class Rule {
        final int field;
        final int interval;
        final int count;
        final long until;
        final boolean[] weekdays;

        Rule(int field, int interval, int count, long until, boolean[] weekdays) {
            this.field = field;
            this.interval = interval;
            this.count = count;
            this.until = until;
            this.weekdays = weekdays;
        }

        static Rule read(String raw, TimeZone zone) throws IOException {
            if (raw == null) return null;
            Map<String, String> parts = new HashMap<>();
            for (String part : raw.toUpperCase(Locale.ROOT).split(";")) {
                String[] pair = part.split("=", 2);
                if (pair.length != 2 || parts.put(pair[0], pair[1]) != null) {
                    throw new IOException("Invalid iCalendar recurrence");
                }
            }
            String frequency = parts.remove("FREQ");
            if (frequency == null) throw new IOException("Missing iCalendar recurrence frequency");
            int field;
            switch (frequency) {
                case "DAILY":
                    field = Calendar.DAY_OF_MONTH;
                    break;
                case "WEEKLY":
                    field = Calendar.WEEK_OF_YEAR;
                    break;
                case "MONTHLY":
                    field = Calendar.MONTH;
                    break;
                case "YEARLY":
                    field = Calendar.YEAR;
                    break;
                default:
                    return null;
            }
            String specifiedDays = parts.remove("BYDAY");
            String weekStart = parts.remove("WKST");
            // Rules outside this subset keep their original DTSTART and explicit dates.
            if ((specifiedDays != null && field != Calendar.WEEK_OF_YEAR)
                    || (weekStart != null && !"MO".equals(weekStart))) return null;
            boolean[] days = null;
            if (specifiedDays != null) {
                days = new boolean[8];
                for (String value : specifiedDays.split(",")) {
                    boolean found = false;
                    for (int index = 0; index < WEEKDAYS.length; index++) {
                        if (WEEKDAYS[index].equals(value)) {
                            days[index + 1] = true;
                            found = true;
                            break;
                        }
                    }
                    if (!found) return null;
                }
            }
            int interval = positive(parts.remove("INTERVAL"), 1);
            int count = positive(parts.remove("COUNT"), Integer.MAX_VALUE);
            String limit = parts.remove("UNTIL");
            if (!parts.isEmpty()) return null;
            long until = Long.MAX_VALUE;
            if (limit != null) {
                Date parsed = IcalendarParser.parseDate(limit, "TZID=" + zone.getID());
                if (parsed == null) throw new IOException("Invalid iCalendar recurrence end");
                until = parsed.getTime();
            }
            return new Rule(field, interval, count, until, days);
        }

        void expand(CalendarEvent event, TimeZone zone, Window window, Set<Long> starts) {
            Calendar origin = Calendar.getInstance(zone);
            origin.setTimeInMillis(event.start);
            int dayOfMonth = origin.get(Calendar.DAY_OF_MONTH);
            int hour = origin.get(Calendar.HOUR_OF_DAY);
            if (weekdays != null) {
                int daysSinceMonday = (origin.get(Calendar.DAY_OF_WEEK) + 5) % 7;
                origin.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);
            }
            long lookback = Math.max(DAY_MILLIS, event.end - event.start);
            long earliest = window.from - lookback;
            long period = firstPeriod(origin.getTimeInMillis(), earliest);
            int emitted = 1;
            for (int visited = 0; visited < MAX_PERIODS && emitted < count; visited++, period++) {
                long delta = period * interval;
                if (delta > Integer.MAX_VALUE) break;
                Calendar candidate = (Calendar) origin.clone();
                candidate.add(field, (int) delta);
                if (candidate.getTimeInMillis() >= window.to || candidate.getTimeInMillis() > until)
                    break;
                int days = weekdays == null ? 1 : 7;
                for (int offset = 0; offset < days && emitted < count; offset++) {
                    Calendar occurrence = (Calendar) candidate.clone();
                    occurrence.add(Calendar.DAY_OF_MONTH, offset);
                    long start = occurrence.getTimeInMillis();
                    if (start <= event.start || start >= window.to || start > until) continue;
                    if (weekdays != null && !weekdays[occurrence.get(Calendar.DAY_OF_WEEK)])
                        continue;
                    if ((field == Calendar.MONTH || field == Calendar.YEAR)
                            && occurrence.get(Calendar.DAY_OF_MONTH) != dayOfMonth) continue;
                    if (occurrence.get(Calendar.HOUR_OF_DAY) != hour) continue;
                    emitted++;
                    if (start >= earliest) starts.add(start);
                }
            }
        }

        private long firstPeriod(long origin, long earliest) {
            if (count != Integer.MAX_VALUE || earliest <= origin) return 0;
            // Conservative jump for old unbounded rules; remainders are searched in local time.
            int longestPeriod =
                    field == Calendar.DAY_OF_MONTH
                            ? 1
                            : field == Calendar.WEEK_OF_YEAR
                                    ? 7
                                    : field == Calendar.MONTH ? 31 : 366;
            return Math.max(0, (earliest - origin) / (DAY_MILLIS * longestPeriod * interval) - 2);
        }

        private static int positive(String value, int fallback) throws IOException {
            if (value == null) return fallback;
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > 0) return parsed;
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid iCalendar recurrence integer", invalid);
            }
            throw new IOException("Invalid iCalendar recurrence integer");
        }
    }
}
