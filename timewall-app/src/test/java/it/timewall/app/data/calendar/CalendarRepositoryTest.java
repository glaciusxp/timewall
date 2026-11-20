package it.timewall.app.data.calendar;

import static org.junit.Assert.*;

import it.timewall.app.domain.model.*;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CalendarRepositoryTest {
    /** Rejects a queued refresh captured before an invalidation, without contacting its old URL. */
    @Test
    public void queuedOldGenerationNeverDownloads() {
        Memory store = new Memory();
        int[] downloads = {0};
        CalendarRepository repo =
                new CalendarRepository(
                        url -> {
                            downloads[0]++;
                            return valid;
                        },
                        store);
        long ticket = repo.generation();
        store.generation++;
        Result result = new Result();
        repo.refresh(Arrays.asList(feed), true, result, ticket);
        assertEquals(0, downloads[0]);
        assertNull(result.loaded);
        assertEquals(0, store.writes);
    }

    /**
     * Reproduces invalidation while a download is blocked; old bytes cannot be written or
     * published.
     */
    @Test
    public void inFlightDownloadCannotUndoClear() throws Exception {
        Memory store = new Memory();
        java.util.concurrent.CountDownLatch downloading =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        CalendarRepository repo =
                new CalendarRepository(
                        url -> {
                            downloading.countDown();
                            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IOException("Test timed out");
                            return valid;
                        },
                        store);
        Result result = new Result();
        long ticket = repo.generation();
        Thread thread = new Thread(() -> repo.refresh(Arrays.asList(feed), true, result, ticket));
        thread.start();
        try {
            assertTrue(downloading.await(5, java.util.concurrent.TimeUnit.SECONDS));
            store.generation++;
            store.saved = null;
        } finally {
            release.countDown();
            thread.join(5000);
        }
        assertFalse(thread.isAlive());
        assertEquals(0, store.writes);
        assertNull(store.saved);
        assertNull(result.loaded);
        Result next = new Result();
        repo.refresh(Arrays.asList(feed), false, next);
        assertEquals(1, store.writes);
        assertEquals(1, next.loaded.size());
    }

    /**
     * Clearing disk data and changing generation also invalidates parsed snapshots during offline
     * refresh.
     */
    @Test
    public void offlineAfterClearDoesNotReuseOldMemory() {
        Memory store = new Memory();
        boolean[] online = {true};
        CalendarRepository repo =
                new CalendarRepository(
                        url -> {
                            if (!online[0]) throw new IOException();
                            return valid;
                        },
                        store);
        repo.refresh(Arrays.asList(feed), false, new Result());
        store.generation++;
        store.saved = null;
        online[0] = false;
        Result result = new Result();
        repo.refresh(Arrays.asList(feed), true, result);
        assertNull(result.cached);
        assertTrue(result.loaded.isEmpty());
        assertEquals(1, result.failures);
    }

    /** A repository owned by a destroyed Activity must not execute queued work. */
    @Test
    public void disposedRepositoryDoesNotDownload() {
        Memory store = new Memory();
        int[] downloads = {0};
        CalendarRepository repo =
                new CalendarRepository(
                        url -> {
                            downloads[0]++;
                            return valid;
                        },
                        store);
        repo.dispose();
        repo.refresh(Arrays.asList(feed), true, new Result());
        assertEquals(0, downloads[0]);
    }

    /** Invalidation from a new repository instance also cancels writes by its predecessor. */
    @Test
    public void repositoriesShareStoreInvalidation() {
        Memory store = new Memory();
        CalendarRepository next = new CalendarRepository(url -> valid, store);
        CalendarRepository old =
                new CalendarRepository(
                        url -> {
                            store.generation++;
                            return valid;
                        },
                        store);
        old.refresh(Arrays.asList(feed), false, new Result());
        assertEquals(0, store.writes);
        Result result = new Result();
        next.refresh(Arrays.asList(feed), false, result);
        assertEquals(1, store.writes);
        assertEquals(1, result.loaded.size());
    }

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
        long generation;

        /** Exposes the fake store's invalidation version for race-condition tests. */
        public long generation() {
            return generation;
        }

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
        public void write(String address, byte[] body, long expectedGeneration) throws Exception {
            if (expectedGeneration != generation) return;
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
