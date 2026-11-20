package it.timewall.app.domain.time;

import static org.junit.Assert.*;

import it.timewall.app.domain.model.CalendarEvent;

import org.junit.Test;

import java.util.*;

public class TodayEventsTest {
    /**
     * Crea un evento sintetico con durata e tipo richiesti per verificare selezione e confini
     * temporali.
     *
     * @param start istante iniziale in millisecondi
     * @param end istante finale esclusivo in millisecondi
     * @param day true per una fixture di evento tutto il giorno
     * @return evento interpretato, oppure null se il VEVENT deve essere ignorato
     */
    private CalendarEvent e(long start, long end, boolean day) {
        return new CalendarEvent(start, end, day, "", "", "", null, "feed", 0, "");
    }

    /**
     * Verifica la selezione dell’evento attivo, del successivo e degli eventi giornalieri
     * simultaneamente visibili.
     */
    @Test
    public void activeHasPriorityOverNextAndAllDayRemainsVisible() {
        CalendarEvent day = e(0, 10000, true),
                active = e(100, 300, false),
                next = e(400, 500, false);
        TodayEvents t = new TodayEvents(Arrays.asList(day, active, next), 200);
        assertSame(active, t.active);
        assertSame(next, t.next);
        assertEquals(300, t.nextChange);
        assertEquals(Arrays.asList(day), t.allDay);
    }

    /** Verifica che l’istante di fine sia escluso dalla condizione di evento attivo. */
    @Test
    public void exactEndIsNoLongerActive() {
        TodayEvents t = new TodayEvents(Arrays.asList(e(100, 200, false)), 200);
        assertNull(t.active);
        assertNull(t.next);
    }

    /** Verifica i confini del giorno per eventi a cavallo della mezzanotte e istantanei. */
    @Test
    public void midnightOverlapAndZeroLengthEvents() {
        assertFalse(TodayEvents.intersectsDay(e(0, 100, false), 100, 200));
        assertTrue(TodayEvents.intersectsDay(e(100, 100, false), 100, 200));
        assertFalse(TodayEvents.intersectsDay(e(200, 300, false), 100, 200));
    }

    /**
     * Verifica il giorno di 25 ore nel cambio all’ora solare e ripristina il fuso globale dopo il
     * test.
     */
    @Test
    public void nextDayUsesCalendarRatherThan24Hours() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"));
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(2026, Calendar.OCTOBER, 25, 12, 0);
            long day = CalendarDays.midnight(c.getTimeInMillis());
            assertEquals(25 * 3600000L, CalendarDays.nextDay(day) - day);
        } finally {
            TimeZone.setDefault(saved);
        }
    }
}
