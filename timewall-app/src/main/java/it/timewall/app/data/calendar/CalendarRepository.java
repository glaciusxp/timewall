package it.timewall.app.data.calendar;

import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.domain.time.CalendarDays;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Blocking cache-first repository. Its caller supplies a worker and rejects stale UI results. */
public final class CalendarRepository {
    public interface Transport {
        /**
         * Contratto di download bloccante del feed. Deve restituire il corpo ricevuto oppure
         * propagare il fallimento; viene invocato dal worker del repository.
         *
         * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
         * @return corpo del feed scaricato; un fallimento deve essere segnalato tramite eccezione
         * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene
         *     propagato al chiamante
         */
        byte[] download(String address) throws Exception;
    }

    public interface Store {
        /** Returns the shared invalidation version used to reject obsolete reads and writes. */
        long generation();

        /**
         * Contratto di lettura della copia persistita di un feed. Un errore indica una cache
         * indisponibile e non impedisce il successivo download.
         *
         * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
         * @return corpo del feed conservato dalla persistenza configurata
         * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene
         *     propagato al chiamante
         */
        byte[] read(String address) throws Exception;

        /**
         * Contratto di persistenza dei byte già validati. Un errore di scrittura non invalida gli
         * eventi conservati in memoria dal repository.
         *
         * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
         * @param body corpo completo del feed già ricevuto
         * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene
         *     propagato al chiamante
         */
        void write(String address, byte[] body, long expectedGeneration) throws Exception;
    }

    public interface Listener {
        /**
         * Riceve il risultato aggregato della cache prima della rete, quando almeno una cache è
         * disponibile. La callback usa il thread di refresh, non necessariamente quello UI.
         *
         * @param events snapshot aggregato dei calendari, ancora da ordinare per la presentazione
         */
        void cached(List<CalendarEvent> events);

        /**
         * Riceve il risultato finale, con eventuali dati precedenti riutilizzati e numero di
         * download falliti. La callback usa il thread che esegue refresh.
         *
         * @param events snapshot aggregato dei calendari, ancora da ordinare per la presentazione
         * @param failures numero di calendari il cui download è fallito
         */
        void loaded(List<CalendarEvent> events, int failures);
    }

    public interface LocalSource {
        List<CalendarEvent> read(Feed feed, long day);
    }

    private final LocalSource localSource;
    private final Transport transport;
    private final Store store;
    private final Map<String, Snapshot> cache = new HashMap<>();
    private long cacheGeneration = Long.MIN_VALUE;
    private volatile boolean disposed;

    /**
     * Captures a ticket before enqueueing a refresh, so delayed requests cannot adopt a newer
     * version.
     */
    public long generation() {
        return store.generation();
    }

    /** Stops this Activity's pending refreshes without interrupting the shared calendar worker. */
    public void dispose() {
        disposed = true;
    }

    /**
     * Clears parsed snapshots on the same serial worker used by refresh; it performs no network
     * I/O.
     */
    public void clearMemory() {
        cache.clear();
        cacheGeneration = Long.MIN_VALUE;
    }

    /**
     * Checks both cache invalidation and Activity lifecycle before using a download or publishing
     * results.
     */
    private boolean isCurrent(long generation) {
        return !disposed && generation == store.generation();
    }

    /**
     * Collega trasporto e persistenza iniettati alla cache in memoria; la creazione non avvia
     * richieste né accede al disco.
     *
     * @param transport dipendenza che scarica i feed sul thread chiamante
     * @param store dipendenza che legge e scrive la cache persistita
     */
    public CalendarRepository(Transport transport, Store store) {
        this(
                transport,
                store,
                (feed, day) -> {
                    throw new IllegalStateException("No local source");
                });
    }

    public CalendarRepository(Transport transport, Store store, LocalSource localSource) {
        this.localSource = localSource;
        this.transport = transport;
        this.store = store;
    }

    /**
     * Carica prima la cache dei calendari configurati, poi aggiorna ciascun feed. Riusa gli eventi
     * se hash e colore non cambiano, conserva l’ultimo dato valido in caso di errore e rimuove
     * dalla cache in memoria le sorgenti non più configurate. Le chiamate devono essere seriali.
     *
     * <p>Invoke serially; cache entries are scoped by secret URL and the configured calendar
     * colour.
     *
     * @param feeds calendari configurati o bozza di calendari da elaborare
     * @param publishCache true per pubblicare uno snapshot locale prima della rete, se disponibile
     * @param listener destinatario delle callback eseguite sul thread di refresh
     */
    public void refresh(List<Feed> feeds, boolean publishCache, Listener listener) {
        refresh(feeds, publishCache, listener, generation());
    }

    /**
     * Refreshes only for the ticket captured by the caller, discarding obsolete downloads and
     * callbacks.
     */
    public void refresh(
            List<Feed> feeds, boolean publishCache, Listener listener, long generation) {
        if (!isCurrent(generation)) return;
        if (cacheGeneration != generation) {
            clearMemory();
            cacheGeneration = generation;
        }
        long day = CalendarDays.midnight(System.currentTimeMillis());
        List<CalendarEvent> cached = new ArrayList<>();
        boolean foundCache = false;
        for (Feed feed : feeds) {
            if (!isCurrent(generation)) return;
            if (feed.isDeviceCalendar()) continue;
            Snapshot snapshot = cache.get(feed.address);
            if (snapshot == null || snapshot.color != feed.color || snapshot.day != day) {
                try {
                    snapshot = parse(feed, store.read(feed.address), day);
                    if (!isCurrent(generation)) return;
                    cache.put(feed.address, snapshot);
                } catch (Exception unavailable) {
                    continue;
                }
            }
            foundCache = true;
            cached.addAll(snapshot.events);
        }
        if (!isCurrent(generation)) return;
        if (publishCache && foundCache) listener.cached(cached);
        List<CalendarEvent> loaded = new ArrayList<>();
        Map<String, Snapshot> updated = new HashMap<>();
        int failures = 0;
        for (Feed feed : feeds) {
            if (!isCurrent(generation)) return;
            if (feed.isDeviceCalendar()) {
                try {
                    loaded.addAll(localSource.read(feed, day));
                } catch (RuntimeException unavailable) {
                    failures++;
                }
                continue;
            }
            Snapshot previous = cache.get(feed.address);
            Snapshot current = previous;
            try {
                byte[] body = transport.download(feed.address);
                if (!isCurrent(generation)) return;
                byte[] hash = CalendarFeed.hash(body);
                if (previous == null
                        || previous.color != feed.color
                        || previous.day != day
                        || !Arrays.equals(previous.hash, hash)) {
                    current =
                            new Snapshot(
                                    hash,
                                    feed.color,
                                    IcalendarParser.parse(body, feed.color, feed.address, day),
                                    day);
                    try {
                        store.write(feed.address, body, generation);
                    } catch (Exception unavailable) {
                        /* Validated data is still usable in memory. */
                    }
                }
            } catch (Exception unavailable) {
                failures++;
            }
            if (current != null) {
                updated.put(feed.address, current);
                loaded.addAll(current.events);
            }
        }
        if (!isCurrent(generation)) return;
        cache.clear();
        cache.putAll(updated);
        if (isCurrent(generation)) listener.loaded(loaded, failures);
    }

    /**
     * Crea uno snapshot dal corpo del feed, associando digest, colore e lista degli eventi. Non
     * effettua download né scritture sul disco.
     *
     * @param feed configurazione del calendario a cui appartengono i byte
     * @param body corpo completo del feed già ricevuto
     * @return snapshot del feed con hash, colore ed eventi interpretati
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    private static Snapshot parse(Feed feed, byte[] body, long day) throws Exception {
        return new Snapshot(
                CalendarFeed.hash(body),
                feed.color,
                IcalendarParser.parse(body, feed.color, feed.address, day),
                day);
    }

    private static final class Snapshot {
        final byte[] hash;
        final int color;
        final List<CalendarEvent> events;
        final long day;

        /**
         * Raggruppa hash, colore e lista già interpretata di un feed per poter riusare il parsing
         * nelle letture successive.
         *
         * @param hash digest dei byte del feed
         * @param color colore ARGB da associare al contenuto
         * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
         *     destinazione dei blocchi
         */
        Snapshot(byte[] hash, int color, List<CalendarEvent> events, long day) {
            this.hash = hash;
            this.color = color;
            this.events = events;
            this.day = day;
        }
    }
}
