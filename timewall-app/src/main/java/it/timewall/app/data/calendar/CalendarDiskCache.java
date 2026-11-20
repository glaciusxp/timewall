package it.timewall.app.data.calendar;

import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class CalendarDiskCache {
    private static final AtomicLong GENERATION = new AtomicLong();

    /** Returns the process-wide cache version without performing disk I/O on the caller thread. */
    public static long generation() {
        return GENERATION.get();
    }

    /**
     * Immediately makes already queued or running downloads obsolete, including those from an old
     * Activity.
     */
    public static long invalidate() {
        return GENERATION.incrementAndGet();
    }

    /**
     * Removes only calendar cache files and their AtomicFile sidecars, preserving all other app
     * files.
     */
    public static synchronized void clear(File directory) throws Exception {
        retainOnly(directory, java.util.Collections.emptySet());
    }

    /**
     * Deletes caches for URLs no longer configured; names are hashes, never caller-supplied file
     * paths.
     */
    public static synchronized void retainOnly(File directory, Set<String> addresses)
            throws Exception {
        Set<String> keep = new HashSet<>();
        for (String address : addresses) keep.add(name(address));
        File[] files = directory.listFiles();
        if (files == null) {
            if (directory.exists()) throw new IOException("Calendar cache directory unavailable");
            return;
        }
        for (File entry : files) {
            String name = entry.getName();
            if (!entry.isFile() || !name.matches("calendar-[0-9a-f]{64}(\\.bak|\\.new)?")) continue;
            String base = name.replaceFirst("\\.(bak|new)$", "");
            if (!keep.contains(base) && !entry.delete())
                throw new IOException("Calendar cache cleanup failed");
        }
    }

    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private CalendarDiskCache() {}

    /**
     * Calcola il nome della cache come SHA-256 dell’URL e crea il relativo AtomicFile. L’indirizzo
     * segreto non compare nel nome del file.
     *
     * @param directory directory privata che contiene i file di cache
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @return file atomico identificato dall’hash dell’URL
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    private static AtomicFile file(File directory, String address) throws Exception {
        return new AtomicFile(new File(directory, name(address)));
    }

    /**
     * Computes the historical SHA-256 filename independently of Android's AtomicFile
     * implementation.
     */
    private static String name(String address) throws Exception {
        byte[] hash = CalendarFeed.hash(address.getBytes(Charset.forName("UTF-8")));
        StringBuilder name = new StringBuilder("calendar-");
        for (byte value : hash) {
            name.append(Character.forDigit((value & 0xff) >>> 4, 16));
            name.append(Character.forDigit(value & 0xf, 16));
        }
        return name.toString();
    }

    /**
     * Legge interamente l’ultima copia atomica del feed dal disco. Un file assente o illeggibile
     * viene segnalato al chiamante, che può proseguire con la rete.
     *
     * @param directory directory privata che contiene i file di cache
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @return corpo del feed salvato nell’ultima copia leggibile
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static synchronized byte[] read(File directory, String address) throws Exception {
        return file(directory, address).readFully();
    }

    /**
     * Salva il feed con una scrittura atomica: completa il file soltanto dopo la scrittura dei byte
     * e ripristina la copia precedente in caso di IOException.
     *
     * @param directory directory privata che contiene i file di cache
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @param body corpo completo del feed già ricevuto
     * @throws Exception se una dipendenza non completa l’operazione; il fallimento viene propagato
     *     al chiamante
     */
    public static synchronized void write(
            File directory, String address, byte[] body, long expectedGeneration) throws Exception {
        if (expectedGeneration != generation()) return;
        AtomicFile file = file(directory, address);
        FileOutputStream output = file.startWrite();
        try {
            output.write(body);
            file.finishWrite(output);
        } catch (IOException exception) {
            file.failWrite(output);
            throw exception;
        }
    }
}
