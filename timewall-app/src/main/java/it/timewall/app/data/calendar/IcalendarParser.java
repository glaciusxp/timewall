package it.timewall.app.data.calendar;

import it.timewall.app.domain.model.CalendarEvent;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Offline ICS reader. Recurrence expansion is intentionally outside its current scope. */
public final class IcalendarParser {
    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private IcalendarParser() {}

    /**
     * Decodifica il feed UTF-8, rimuove il BOM e ricompone le righe ripiegate. Legge i campi dei
     * VEVENT senza confonderli con i sotto-componenti VALARM; scarta eventi non validi o
     * cancellati. Non espande RRULE o EXDATE.
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
        String body = new String(data, Charset.forName("UTF-8")).replaceFirst("^\uFEFF", "");
        String[] lines = body.replaceAll("\r?\n[ \t]", "").split("\r?\n");
        if (lines.length < 2
                || !"BEGIN:VCALENDAR".equalsIgnoreCase(lines[0])
                || !"END:VCALENDAR".equalsIgnoreCase(lines[lines.length - 1])) {
            throw new IOException("Risposta iCal non valida");
        }
        List<CalendarEvent> events = new ArrayList<>();
        Map<String, String> values = null;
        Map<String, String> parameters = null;
        int depth = 0;
        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line)) {
                values = new HashMap<>();
                parameters = new HashMap<>();
                depth = 1;
            } else if (depth == 1 && "END:VEVENT".equalsIgnoreCase(line)) {
                CalendarEvent event = event(values, parameters, color, feedAddress);
                if (event != null) {
                    events.add(event);
                }
                depth = 0;
            } else if (depth > 0) {
                if (line.regionMatches(true, 0, "BEGIN:", 0, 6)) {
                    depth++;
                } else if (line.regionMatches(true, 0, "END:", 0, 4)) {
                    depth--;
                } else if (depth == 1) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String[] key = line.substring(0, colon).split(";", 2);
                        String name = key[0].toUpperCase(Locale.ROOT);
                        values.put(name, line.substring(colon + 1));
                        parameters.put(name, key.length == 2 ? key[1] : "");
                    }
                }
            }
        }
        return events;
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
            String feedAddress) {
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
        Calendar finish = Calendar.getInstance();
        finish.setTime(start);
        if (allDay) {
            finish.add(Calendar.DAY_OF_MONTH, 1);
        }
        long endTime = end != null ? end.getTime() : finish.getTimeInMillis();
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
    private static Date parseDate(String value, String parameters) {
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
        if (utc) {
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
        } else if (value.length() != 8 && parameters != null) {
            for (String parameter : parameters.split(";")) {
                if (parameter.regionMatches(true, 0, "TZID=", 0, 5)) {
                    format.setTimeZone(
                            TimeZone.getTimeZone(parameter.substring(5).replace("\"", "")));
                }
            }
        }
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return position.getIndex() == value.length() ? parsed : null;
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
