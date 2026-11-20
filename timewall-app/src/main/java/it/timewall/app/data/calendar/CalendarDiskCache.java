package it.timewall.app.data.calendar;

import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

public final class CalendarDiskCache {
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
        byte[] hash = CalendarFeed.hash(address.getBytes(Charset.forName("UTF-8")));
        StringBuilder name = new StringBuilder("calendar-");
        for (byte value : hash) {
            name.append(Character.forDigit((value & 0xff) >>> 4, 16));
            name.append(Character.forDigit(value & 0xf, 16));
        }
        return new AtomicFile(new File(directory, name.toString()));
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
    public static synchronized void write(File directory, String address, byte[] body)
            throws Exception {
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
