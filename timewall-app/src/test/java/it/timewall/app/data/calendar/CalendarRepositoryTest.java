package it.timewall.app.data.calendar;

import static org.junit.Assert.*;

import it.timewall.app.domain.model.*;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CalendarRepositoryTest {
    private final Feed feed = new Feed("Calendar", "https://example.org/private.ics", 123);
    private final byte[] valid =
            ("BEGIN:VCALENDAR\n"
                            + "BEGIN:VEVENT\n"
                            + "DTSTART:20261119T100000Z\n"
                            + "SUMMARY:Event\n"
                            + "END:VEVENT\n"
                            + "END:VCALENDAR\n")
                    .getBytes(StandardCharsets.UTF_8);

    private static class Memory implements CalendarRepository.Store {
        byte[] saved;
        int writes;
        boolean failWrite;

        /**
         * Restituisce i byte della cache finta o simula un file mancante; non accede al filesystem.
         *
         * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
         * @return byte letti o digest calcolato, secondo l’operazione
         * @throws Exception se la preparazione o lettura dei dati della prova fallisce
         */
        @Override
        public byte[] read(String address) throws Exception {
            if (saved == null) throw new IOException();
            return saved;
        }

        /**
         * Registra i byte e il numero di scritture della cache finta, oppure simula il fallimento
         * richiesto dal test.
         *
         * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
         * @param body corpo completo del feed già ricevuto
         * @throws Exception se la preparazione o lettura dei dati della prova fallisce
         */
        @Override
        public void write(String address, byte[] body) throws Exception {
            if (failWrite) throw new IOException();
            saved = body;
            writes++;
        }
    }

    private static class Result implements CalendarRepository.Listener {
        List<CalendarEvent> cached, loaded;
        int failures;
        List<String> order = new ArrayList<>();

        /**
         * Registra lo snapshot locale e l’ordine della callback per verificare la precedenza della
         * cache.
         *
         * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
         *     destinazione dei blocchi
         */
        @Override
        public void cached(List<CalendarEvent> events) {
            cached = events;
            order.add("cache");
        }

        /**
         * Registra lo snapshot finale, il numero di errori e l’ordine di pubblicazione per le
         * asserzioni del test.
         *
         * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
         *     destinazione dei blocchi
         * @param failed true per il retry meteo oppure numero di errori registrati dalla callback
         *     di prova
         */
        @Override
        public void loaded(List<CalendarEvent> events, int failed) {
            loaded = events;
            failures = failed;
            order.add("network");
        }
    }

    /**
     * Verifica che la callback della cache preceda quella finale e che un download invariato non
     * riscriva il disco.
     */
    @Test
    public void diskCacheIsPublishedBeforeNetwork() {
        Memory store = new Memory();
        store.saved = valid;
        Result result = new Result();
        new CalendarRepository(u -> valid, store).refresh(Arrays.asList(feed), true, result);
        assertEquals(Arrays.asList("cache", "network"), result.order);
        assertEquals(1, result.loaded.size());
        assertEquals(0, store.writes);
    }

    /**
     * Verifica che un hash invariato riusi gli stessi oggetti evento senza ripetere parsing e
     * scrittura.
     */
    @Test
    public void matchingHashReusesParsedEvents() {
        Memory store = new Memory();
        CalendarRepository repo = new CalendarRepository(u -> valid, store);
        Result a = new Result(), b = new Result();
        repo.refresh(Arrays.asList(feed), false, a);
        repo.refresh(Arrays.asList(feed), false, b);
        assertSame(a.loaded.get(0), b.loaded.get(0));
        assertEquals(1, store.writes);
    }

    /** Verifica che un fallimento del download mantenga gli eventi validi letti dalla cache. */
    @Test
    public void networkFailureKeepsLastGoodData() {
        Memory store = new Memory();
        store.saved = valid;
        Result result = new Result();
        new CalendarRepository(
                        u -> {
                            throw new IOException();
                        },
                        store)
                .refresh(Arrays.asList(feed), true, result);
        assertEquals(1, result.failures);
        assertEquals(1, result.loaded.size());
    }

    /**
     * Verifica che una risposta non iCal non sostituisca la cache valida e venga contata come
     * fallimento.
     */
    @Test
    public void malformedDownloadDoesNotOverwriteGoodCache() {
        Memory store = new Memory();
        store.saved = valid;
        Result result = new Result();
        new CalendarRepository(u -> "<html/>".getBytes(StandardCharsets.UTF_8), store)
                .refresh(Arrays.asList(feed), true, result);
        assertEquals(1, result.failures);
        assertSame(valid, store.saved);
        assertEquals(1, result.loaded.size());
    }

    /**
     * Verifica che gli eventi scaricati validi restino disponibili anche quando la persistenza
     * fallisce.
     */
    @Test
    public void diskWriteFailureStillPublishesValidatedEvents() {
        Memory store = new Memory();
        store.failWrite = true;
        Result result = new Result();
        new CalendarRepository(u -> valid, store).refresh(Arrays.asList(feed), false, result);
        assertEquals(0, result.failures);
        assertEquals(1, result.loaded.size());
    }

    /** Verifica che il cambio del colore del calendario ricrei gli eventi dal corpo in cache. */
    @Test
    public void changedCalendarColorReparsesCachedBody() {
        Memory store = new Memory();
        CalendarRepository repo = new CalendarRepository(u -> valid, store);
        Result a = new Result(), b = new Result();
        repo.refresh(Arrays.asList(feed), false, a);
        repo.refresh(Arrays.asList(new Feed(feed.name, feed.address, 456)), true, b);
        assertEquals(456, b.loaded.get(0).color);
        assertNotSame(a.loaded.get(0), b.loaded.get(0));
    }

    /** Verifica che i calendari rimossi non contribuiscano agli snapshot pubblicati. */
    @Test
    public void removedCalendarsAreNotPublished() {
        Memory store = new Memory();
        CalendarRepository repo = new CalendarRepository(u -> valid, store);
        repo.refresh(Arrays.asList(feed), false, new Result());
        Result result = new Result();
        repo.refresh(Collections.emptyList(), true, result);
        assertTrue(result.loaded.isEmpty());
        assertNull(result.cached);
    }
}
