package it.timewall.app.domain.alerts;

import static org.junit.Assert.*;

import it.timewall.app.domain.model.CalendarEvent;

import org.junit.Test;

import java.util.*;

public class EventAlertsTest {
    /**
     * Crea un’occorrenza sintetica con gli istanti e l’identità richiesti, senza rete o clock
     * reale.
     *
     * @param start istante iniziale in millisecondi
     * @param end istante finale esclusivo in millisecondi
     * @param allDay true per un evento che occupa una o più giornate intere
     * @param uid UID iCal; vuoto se non fornito
     * @param title titolo dell’evento
     * @return evento interpretato, oppure null se il VEVENT deve essere ignorato
     */
    private CalendarEvent event(long start, long end, boolean allDay, String uid, String title) {
        return new CalendarEvent(start, end, allDay, title, "", "", null, "feed", 0, uid);
    }

    /**
     * Costruisce la lista di eventi di prova mantenendo l’ordine fornito dalle fixture.
     *
     * @param es occorrenze della fixture nell’ordine desiderato
     * @return lista dei risultati validi nell’ordine di lettura o selezione descritto
     */
    private List<CalendarEvent> list(CalendarEvent... es) {
        return Arrays.asList(es);
    }

    /** Verifica che il raggiungimento dell’inizio produca una sola notifica per occorrenza. */
    @Test
    public void startSoundsOnce() {
        EventAlerts alerts = new EventAlerts();
        CalendarEvent e = event(1000, 2000, false, "a", "A");
        assertTrue(alerts.check(list(e), null, 0, 900).isEmpty());
        assertEquals(1, alerts.tick(list(e), 900, 1000).size());
        assertTrue(alerts.check(list(e), list(), 900, 1100).isEmpty());
    }

    /** Verifica che il primo caricamento non notifichi retroattivamente eventi già in corso. */
    @Test
    public void initialLoadDoesNotSoundOldActiveEvents() {
        assertTrue(
                new EventAlerts()
                        .check(list(event(1000, 4000, false, "a", "A")), null, 2000, 3000)
                        .isEmpty());
    }

    /** Verifica la notifica di un evento attivo apparso dopo il caricamento precedente. */
    @Test
    public void newlyDownloadedOngoingEventSounds() {
        assertEquals(
                1,
                new EventAlerts()
                        .check(list(event(1000, 4000, false, "a", "A")), list(), 2000, 3000)
                        .size());
    }

    /** Verifica che un evento appena scoperto ma già concluso non produca notifiche. */
    @Test
    public void downloadedFinishedEventDoesNotSound() {
        assertTrue(
                new EventAlerts()
                        .check(list(event(1000, 2000, false, "a", "A")), list(), 2000, 3000)
                        .isEmpty());
    }

    /** Verifica che il cambio di titolo con UID invariato non causi una seconda notifica. */
    @Test
    public void uidPreventsRenamedEventFromSoundingAgain() {
        assertTrue(
                new EventAlerts()
                        .check(
                                list(event(1000, 4000, false, "a", "New")),
                                list(event(1000, 4000, false, "a", "Old")),
                                2000,
                                3000)
                        .isEmpty());
    }

    /** Verifica la notifica all’inizio della giornata di un evento tutto il giorno. */
    @Test
    public void allDayMidnightSounds() {
        assertEquals(
                1,
                new EventAlerts()
                        .check(list(event(1000, 86401000, true, "a", "Day")), null, 999, 1000)
                        .size());
    }

    /** Verifica la notifica di un evento istantaneo nell’istante di inizio. */
    @Test
    public void zeroDurationStartSounds() {
        assertEquals(
                1,
                new EventAlerts()
                        .check(list(event(1000, 1000, false, "a", "Instant")), null, 999, 1000)
                        .size());
    }

    /**
     * Verifica l’assenza di notifiche spurie per eventi invariati o spostamenti indietro
     * dell’orologio.
     */
    @Test
    public void unchangedEventAndBackwardsClockDoNotSound() {
        CalendarEvent e = event(1000, 4000, false, "a", "A");
        EventAlerts alerts = new EventAlerts();
        assertTrue(alerts.check(list(e), list(e), 2000, 3000).isEmpty());
        assertTrue(alerts.tick(list(e), 3000, 2000).isEmpty());
    }
}
