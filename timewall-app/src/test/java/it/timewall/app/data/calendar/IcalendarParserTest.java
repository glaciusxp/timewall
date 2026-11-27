package it.timewall.app.data.calendar;

import static org.junit.Assert.*;

import it.timewall.app.domain.model.CalendarEvent;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class IcalendarParserTest {
    /**
     * Avvolge il frammento nella struttura minima VCALENDAR/VEVENT e lo interpreta con colore e URL
     * di prova fissi.
     *
     * @param event occorrenza da visualizzare o frammento iCal da usare nella fixture
     * @return lista dei risultati validi nell’ordine di lettura o selezione descritto
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    private List<CalendarEvent> parse(String event) throws Exception {
        return IcalendarParser.parse(
                ("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\n"
                                + event
                                + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
                        .getBytes(StandardCharsets.UTF_8),
                0xff123456,
                "https://example.org/private.ics");
    }

    /**
     * Verifica la lettura dei dettagli iCal e la decodifica degli escape di titolo, descrizione e
     * luogo.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void preservesDetailsAndEscapes() throws Exception {
        CalendarEvent e =
                parse(
                                "DTSTART:20261119T100000Z\r\n"
                                        + "DTEND:20261119T110000Z\r\n"
                                        + "SUMMARY:A\\, B\r\n"
                                        + "DESCRIPTION:First\\n"
                                        + "Second\r\n"
                                        + "LOCATION:Room\\; 1\r\n"
                                        + "UID:abc\r\n"
                                        + "CREATED:20261101T080000Z")
                        .get(0);
        assertEquals("A, B", e.title);
        assertEquals("First\nSecond", e.description);
        assertEquals("Room; 1", e.location);
        assertNotNull(e.created);
        assertEquals("abc", e.uid);
        assertEquals(3600000L, e.end - e.start);
        assertEquals(0xff123456, e.color);
    }

    /**
     * Verifica il ricongiungimento delle righe ripiegate e l’isolamento dei campi VALARM.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void unfoldsLinesAndIgnoresAlarmFields() throws Exception {
        CalendarEvent e =
                parse(
                                "DTSTART:20261119T100000Z\r\n"
                                        + "SUMMARY:Long\r\n"
                                        + " title\r\n"
                                        + "BEGIN:VALARM\r\n"
                                        + "DESCRIPTION:Alarm\r\n"
                                        + "SUMMARY:Must not replace title\r\n"
                                        + "END:VALARM")
                        .get(0);
        assertEquals("Longtitle", e.title);
        assertEquals("", e.description);
    }

    /**
     * Verifica che gli eventi giornalieri mantengano la data di fine esclusiva.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void allDayHasExclusiveEnd() throws Exception {
        CalendarEvent e = parse("DTSTART;VALUE=DATE:20261119\r\nDTEND;VALUE=DATE:20261121").get(0);
        assertTrue(e.allDay);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(e.end);
        assertEquals(21, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Verifica la fine implicita di un evento giornaliero nel passaggio all’ora legale e ripristina
     * il fuso globale a fine prova.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void missingAllDayEndAdvancesLocalCalendarDayAcrossDst() throws Exception {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"));
            CalendarEvent e = parse("DTSTART;VALUE=DATE:20260329").get(0);
            assertEquals(23 * 3600000L, e.end - e.start);
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    /**
     * Verifica che DTSTART con TZID e il corrispondente istante UTC coincidano.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void honorsTimezone() throws Exception {
        CalendarEvent local = parse("DTSTART;TZID=Europe/Rome:20261119T110000").get(0);
        CalendarEvent utc = parse("DTSTART:20261119T100000Z").get(0);
        assertEquals(utc.start, local.start);
    }

    /**
     * Verifica lo scarto degli eventi cancellati, senza DTSTART o con data impossibile.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void skipsCancelledAndMissingOrInvalidStart() throws Exception {
        assertTrue(parse("DTSTART:20261119T100000Z\r\nSTATUS:CANCELLED").isEmpty());
        assertTrue(parse("SUMMARY:No date").isEmpty());
        assertTrue(parse("DTSTART:20260230T100000Z").isEmpty());
    }

    /**
     * Verifica eventi istantanei senza DTEND e normalizzazione di una fine precedente all’inizio.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void missingTimedEndIsInstantAndReverseEndIsClamped() throws Exception {
        CalendarEvent a = parse("DTSTART:20261119T100000Z").get(0);
        assertEquals(a.start, a.end);
        CalendarEvent b = parse("DTSTART:20261119T100000Z\r\nDTEND:20261119T090000Z").get(0);
        assertEquals(b.start, b.end);
    }

    /**
     * Verifica l’accettazione di un feed vuoto con BOM UTF-8.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void acceptsBomAndEmptyCalendar() throws Exception {
        assertTrue(
                IcalendarParser.parse(
                                "\ufeffBEGIN:VCALENDAR\nEND:VCALENDAR\n"
                                        .getBytes(StandardCharsets.UTF_8),
                                0,
                                "")
                        .isEmpty());
    }

    /**
     * Verifica che il parser rifiuti una risposta HTML sollevando IOException.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test(expected = IOException.class)
    public void rejectsHtml() throws Exception {
        IcalendarParser.parse("<html>not a feed</html>".getBytes(StandardCharsets.UTF_8), 0, "");
    }

    /**
     * Verifica che un CREATED privo del suffisso UTC non venga interpretato come data di
     * inserimento valida.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void createdMustBeUtc() throws Exception {
        assertNull(parse("DTSTART:20261119T100000Z\r\nCREATED:20261119T090000").get(0).created);
    }

    /**
     * Verifica l’accettazione di URL iCal HTTPS e il rifiuto di schemi, credenziali, porte e
     * frammenti non ammessi.
     */
    @Test
    public void publicUrlValidation() {
        assertTrue(CalendarFeed.accepts("https://calendar.example.org/events.ics"));
        for (String bad :
                Arrays.asList(
                        "http://example.org/a.ics",
                        "file:///a.ics",
                        "https://user:pass@example.org/a",
                        "https://example.org/#fragment",
                        "https://example.org/a b",
                        "https://example.org:99999/a")) assertFalse(bad, CalendarFeed.accepts(bad));
    }

    @Test
    public void recurringEventsHonorCountAndExceptionDatesAcrossDst() throws Exception {
        String events =
                "DTSTART;TZID=Europe/Rome:20260323T090000\n"
                        + "DURATION:PT90M\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5\n"
                        + "EXDATE;TZID=Europe/Rome:20260325T090000\n"
                        + "RDATE;TZID=Europe/Rome:20260402T090000\n";
        List<CalendarEvent> result = dated(events, "20260323T080000Z");
        assertEquals(5, result.size());
        assertEquals(
                IcalendarParser.parseDate("20260330T070000Z", null).getTime(), result.get(1).start);
        assertEquals(90 * 60_000L, result.get(1).end - result.get(1).start);
        assertEquals(
                IcalendarParser.parseDate("20260402T070000Z", null).getTime(), result.get(3).start);
    }

    @Test
    public void monthlySeriesSkipsMonthsWithoutTheStartDayAndHonorsUntil() throws Exception {
        List<CalendarEvent> result =
                dated(
                        "DTSTART:20260131T100000Z\n"
                                + "RRULE:FREQ=MONTHLY;UNTIL=20260331T100000Z\n",
                        "20260215T000000Z");
        assertEquals(2, result.size());
        assertEquals(
                IcalendarParser.parseDate("20260331T100000Z", null).getTime(), result.get(1).start);
    }

    @Test
    public void unsupportedTimeZoneFailsInsteadOfUsingGmt() {
        assertThrows(
                java.io.IOException.class,
                () -> dated("DTSTART;TZID=Unavailable/Zone:20260323T090000\n", "20260323T000000Z"));
    }

    @Test
    public void rejectsMissingBodyAndNestedCalendar() {
        assertThrows(IOException.class, () -> IcalendarParser.parse(null, 0, ""));
        byte[] nested =
                ("BEGIN:VCALENDAR\nBEGIN:VCALENDAR\nEND:VCALENDAR\nEND:VCALENDAR\n")
                        .getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> IcalendarParser.parse(nested, 0, ""));
    }

    @Test
    public void cancelledOccurrenceRemovesOnlyItsOwnStart() throws Exception {
        List<CalendarEvent> result =
                dated(
                        "UID:series\n"
                                + "DTSTART:20260323T090000Z\n"
                                + "RRULE:FREQ=DAILY;COUNT=3\n"
                                + "END:VEVENT\n"
                                + "BEGIN:VEVENT\n"
                                + "UID:series\n"
                                + "RECURRENCE-ID:20260324T090000Z\n"
                                + "STATUS:CANCELLED\n",
                        "20260323T000000Z");
        assertEquals(2, result.size());
        assertEquals(
                IcalendarParser.parseDate("20260325T090000Z", null).getTime(), result.get(1).start);
    }

    private List<CalendarEvent> dated(String event, String now) throws Exception {
        return IcalendarParser.parse(
                ("BEGIN:VCALENDAR\nBEGIN:VEVENT\n" + event + "END:VEVENT\nEND:VCALENDAR\n")
                        .getBytes(StandardCharsets.UTF_8),
                0,
                "https://example.org/calendar.ics",
                IcalendarParser.parseDate(now, null).getTime());
    }
}
