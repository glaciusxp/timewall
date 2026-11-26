package it.timewall.app.data.calendar;

import it.timewall.app.domain.model.CalendarEvent;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline ICS reader with bounded recurrence expansion. */
public final class IcalendarParser {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private IcalendarParser() {}

    /**
     * Decodifica il feed UTF-8, rimuove il BOM e ricompone le righe ripiegate. Legge i campi dei
     * VEVENT senza confonderli con i sotto-componenti VALARM; scarta eventi non validi o
     * cancellati. Espande le regole supportate nella finestra dell’agenda.
     *
     * @param data byte UTF-8 del feed da elaborare
     * @param color colore ARGB da associare al contenuto
     * @param feedAddress indirizzo del calendario di origine usato per identità e deduplicazione
     * @return occorrenze valide nell’ordine dei VEVENT ricevuti
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    public static List<CalendarEvent> parse(byte[] data, int color, String feedAddress)
            throws IOException {
        return parse(data, color, feedAddress, System.currentTimeMillis());
    }

    static List<CalendarEvent> parse(byte[] data, int color, String feedAddress, long now)
            throws IOException {
        String body = new String(data, Charset.forName("UTF-8")).replaceFirst("^\uFEFF", "");
        String[] lines = body.replaceAll("\r?\n[ \t]", "").split("\r?\n");
        if (lines.length < 2
                || !"BEGIN:VCALENDAR".equalsIgnoreCase(lines[0])
                || !"END:VCALENDAR".equalsIgnoreCase(lines[lines.length - 1])) {
            throw new IOException("Invalid iCalendar envelope");
        }
        List<CalendarEvent> events = new ArrayList<>();
        List<EventComponent> components = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        EventComponent component = null;
        for (int index = 1; index < lines.length - 1; index++) {
            String line = lines[index];
            if (line.regionMatches(true, 0, "BEGIN:", 0, 6)) {
                String name = line.substring(6).toUpperCase(Locale.ROOT);
                if ("VEVENT".equals(name)) {
                    if (!stack.isEmpty()) throw new IOException("Nested VEVENT");
                    component = new EventComponent();
                }
                stack.push(name);
            } else if (line.regionMatches(true, 0, "END:", 0, 4)) {
                if (!line.substring(4).equalsIgnoreCase(stack.poll())) {
                    throw new IOException("Unbalanced iCalendar component");
                }
                if (stack.isEmpty() && component != null) {
                    components.add(component);
                    component = null;
                }
            } else if (component != null && stack.size() == 1) {
                component.add(line);
            }
        }
        if (!stack.isEmpty()) throw new IOException("Incomplete iCalendar component");

        CalendarRecurrence.Window window = new CalendarRecurrence.Window(now);
        Map<String, Set<Long>> replacements = new HashMap<>();
        for (EventComponent entry : components) {
            String uid = entry.values.get("UID");
            Long replacement = entry.recurrenceId();
            if (uid != null && replacement != null) {
                Set<Long> starts = replacements.get(uid);
                if (starts == null) {
                    starts = new HashSet<>();
                    replacements.put(uid, starts);
                }
                starts.add(replacement);
            }
        }
        for (EventComponent entry : components) {
            CalendarEvent event = event(entry.values, entry.parameters, color, feedAddress);
            if (event == null) continue;
            if (entry.values.containsKey("RECURRENCE-ID")) {
                events.add(event);
                continue;
            }
            Set<Long> excluded = entry.dates(entry.exclusions);
            Set<Long> replaced = replacements.get(entry.values.get("UID"));
            if (replaced != null) excluded.addAll(replaced);
            TimeZone zone = eventZone(entry.values.get("DTSTART"), entry.parameters.get("DTSTART"));
            CalendarRecurrence.append(
                    events,
                    event,
                    zone,
                    entry.values.get("RRULE"),
                    entry.dates(entry.additions),
                    excluded,
                    window,
                    entry.values.containsKey("DURATION") && !entry.values.containsKey("DTEND")
                            ? time ->
                                    defaultEnd(
                                            new Date(time),
                                            zone,
                                            event.allDay,
                                            entry.values.get("DURATION"))
                            : null);
        }
        return events;
    }

    private static final class EventComponent {
        final Map<String, String> values = new HashMap<>();
        final Map<String, String> parameters = new HashMap<>();
        final List<String[]> additions = new ArrayList<>();
        final List<String[]> exclusions = new ArrayList<>();

        void add(String line) {
            int colon = line.indexOf(':');
            if (colon <= 0) return;
            String[] key = line.substring(0, colon).split(";", 2);
            String name = key[0].toUpperCase(Locale.ROOT);
            String params = key.length == 2 ? key[1] : "";
            String value = line.substring(colon + 1);
            if ("EXDATE".equals(name)) exclusions.add(new String[] {value, params});
            else if ("RDATE".equals(name)) additions.add(new String[] {value, params});
            else {
                values.put(name, value);
                parameters.put(name, params);
            }
        }

        Long recurrenceId() throws IOException {
            Date id = parseDate(values.get("RECURRENCE-ID"), parameters.get("RECURRENCE-ID"));
            return id == null ? null : id.getTime();
        }

        Set<Long> dates(List<String[]> properties) throws IOException {
            Set<Long> dates = new HashSet<>();
            for (String[] property : properties) {
                for (String value : property[0].split(",")) {
                    Date date = parseDate(value, property[1]);
                    if (date != null) dates.add(date.getTime());
                }
            }
            return dates;
        }
    }

    /**
     * Costruisce un evento dai campi iCal. Richiede un DTSTART valido, interpreta DTEND come
     * esclusivo, completa la durata degli eventi giornalieri senza fine e accetta CREATED soltanto
     * in UTC; restituisce null per eventi da scartare.
     *
     * @param values campi o valori raccolti per l’elaborazione
     * @param parameters parametri delle proprietà iCal, compreso l’eventuale TZID
     * @param color colore ARGB da associare al contenuto
     * @param feedAddress indirizzo del calendario di origine usato per identità e deduplicazione
     * @return occorrenza costruita, oppure null se cancellata o priva di un inizio valido
     */
    private static CalendarEvent event(
            Map<String, String> values,
            Map<String, String> parameters,
            int color,
            String feedAddress)
            throws IOException {
        String startValue = values.get("DTSTART");
        if (startValue == null || "CANCELLED".equalsIgnoreCase(values.get("STATUS"))) {
            return null;
        }
        boolean allDay = startValue.length() == 8;
        Date start = parseDate(startValue, parameters.get("DTSTART"));
        if (start == null) {
            return null;
        }
        Date end = parseDate(values.get("DTEND"), parameters.get("DTEND"));
        TimeZone zone = eventZone(startValue, parameters.get("DTSTART"));
        long endTime =
                end == null
                        ? defaultEnd(start, zone, allDay, values.get("DURATION"))
                        : end.getTime();
        String createdValue = values.get("CREATED");
        Date created =
                createdValue != null && createdValue.length() == 16 && createdValue.endsWith("Z")
                        ? parseDate(createdValue, null)
                        : null;
        return new CalendarEvent(
                start.getTime(),
                Math.max(start.getTime(), endTime),
                allDay,
                text(values.get("SUMMARY"), ""),
                text(values.get("LOCATION"), ""),
                text(values.get("DESCRIPTION"), ""),
                created,
                feedAddress,
                color,
                text(values.get("UID"), ""));
    }

    /**
     * Interpreta date giornaliere, date e ore UTC o valori locali con TZID. Disabilita la
     * normalizzazione permissiva delle date e restituisce null se il testo non viene consumato
     * completamente.
     *
     * @param value valore da validare, formattare o mostrare
     * @param parameters parametri delle proprietà iCal, compreso l’eventuale TZID
     * @return data nel fuso richiesto, oppure null se il valore non è valido
     */
    static Date parseDate(String value, String parameters) throws IOException {
        if (value == null) {
            return null;
        }
        boolean utc = value.endsWith("Z");
        String pattern =
                value.length() == 8
                        ? "yyyyMMdd"
                        : utc ? "yyyyMMdd'T'HHmmss'Z'" : "yyyyMMdd'T'HHmmss";
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setLenient(false);
        format.setTimeZone(eventZone(value, parameters));
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return position.getIndex() == value.length() ? parsed : null;
    }

    private static final Set<String> ZONE_IDS =
            new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
    private static final Pattern DURATION =
            Pattern.compile("P(?:(\\d+)W|(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?)");

    private static TimeZone eventZone(String value, String parameters) throws IOException {
        if (value.endsWith("Z")) return TimeZone.getTimeZone("UTC");
        if (value.length() != 8 && parameters != null) {
            for (String parameter : parameters.split(";")) {
                if (parameter.regionMatches(true, 0, "TZID=", 0, 5)) {
                    String id = parameter.substring(5).replace("\"", "");
                    if (!ZONE_IDS.contains(id)) throw new IOException("Unsupported iCalendar TZID");
                    return TimeZone.getTimeZone(id);
                }
            }
        }
        return TimeZone.getDefault();
    }

    private static long defaultEnd(Date start, TimeZone zone, boolean allDay, String duration)
            throws IOException {
        Calendar end = Calendar.getInstance(zone);
        end.setTime(start);
        if (duration == null) {
            if (allDay) end.add(Calendar.DAY_OF_MONTH, 1);
            return end.getTimeInMillis();
        }
        Matcher match = DURATION.matcher(duration);
        if (!match.matches()
                || duration.endsWith("T")
                || duration.length() == 1
                || (allDay && duration.contains("T"))) {
            throw new IOException("Invalid iCalendar duration");
        }
        try {
            int days = quantity(match, 1) * 7 + quantity(match, 2);
            end.add(Calendar.DAY_OF_MONTH, days);
            long seconds =
                    3600L * quantity(match, 3) + 60L * quantity(match, 4) + quantity(match, 5);
            return end.getTimeInMillis() + 1000L * seconds;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid iCalendar duration", invalid);
        }
    }

    private static int quantity(Matcher match, int group) {
        String value = match.group(group);
        if (value == null) return 0;
        int result = Integer.parseInt(value);
        if (result > 1_000_000) throw new NumberFormatException("Duration too large");
        return result;
    }

    /**
     * Decodifica gli escape testuali iCal, comprese le nuove righe, virgole e punti e virgola. Usa
     * il fallback quando il campo è assente o vuoto.
     *
     * @param value valore da validare, formattare o mostrare
     * @param fallback valore sostitutivo per testo assente o vuoto
     * @return testo decodificato oppure il fallback quando il campo manca o è vuoto
     */
    private static String text(String value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' && index + 1 < value.length()) {
                character = value.charAt(++index);
                if (character == 'n' || character == 'N') {
                    character = '\n';
                }
            }
            result.append(character);
        }
        return result.toString();
    }
}
