package it.timewall.diagnostics.calendar;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.util.*;

public class IcalEventDumpTest {
    /**
     * Verifica che il dump diagnostico conservi proprietà sconosciute e componenti VALARM del
     * VEVENT.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void retainsUnknownPropertiesAndNestedAlarm() throws Exception {
        List<String> events = new ArrayList<>();
        IcalEventDump.read(
                new BufferedReader(
                        new StringReader(
                                "BEGIN:VCALENDAR\n"
                                        + "BEGIN:VEVENT\n"
                                        + "X-CUSTOM:value\n"
                                        + "BEGIN:VALARM\n"
                                        + "TRIGGER:-PT5M\n"
                                        + "END:VALARM\n"
                                        + "END:VEVENT\n"
                                        + "END:VCALENDAR\n")),
                events);
        assertEquals(1, events.size());
        assertTrue(events.get(0).contains("X-CUSTOM:value"));
        assertTrue(events.get(0).contains("TRIGGER:-PT5M"));
    }

    /**
     * Verifica che un calendario troncato non lasci blocchi parziali nel risultato diagnostico.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void incompleteResponseClearsPartialResults() throws Exception {
        List<String> events = new ArrayList<>();
        IcalEventDump.read(
                new BufferedReader(new StringReader("BEGIN:VCALENDAR\nBEGIN:VEVENT\nEND:VEVENT\n")),
                events);
        assertTrue(events.isEmpty());
    }

    /**
     * Verifica che una risposta non iCal non generi eventi nel dump.
     *
     * @throws Exception se la preparazione o lettura dei dati della prova fallisce
     */
    @Test
    public void nonCalendarDoesNotCreateEvents() throws Exception {
        List<String> events = new ArrayList<>();
        IcalEventDump.read(new BufferedReader(new StringReader("<html/>")), events);
        assertTrue(events.isEmpty());
    }
}
