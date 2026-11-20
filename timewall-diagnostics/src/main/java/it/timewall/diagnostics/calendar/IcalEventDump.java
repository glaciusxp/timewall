package it.timewall.diagnostics.calendar;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public final class IcalEventDump {
    private static final int MAX_CHARACTERS = 8 * 1024 * 1024;

    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private IcalEventDump() {}

    /**
     * Legge il calendario conservando il testo completo di ogni blocco VEVENT, comprese proprietà
     * sconosciute e sotto-componenti. Limita la dimensione e svuota risultati parziali se il
     * documento termina senza END:VCALENDAR.
     *
     * @param input campo HEX da aggiornare o reader del calendario nella diagnostica
     * @param events lista in cui aggiungere il testo completo dei blocchi VEVENT
     * @return riepilogo della lettura o messaggio di feed non valido; i VEVENT sono inseriti nella
     *     lista events
     * @throws IOException se lettura, download o configurazione del socket falliscono, oppure il
     *     contenuto non rispetta i vincoli descritti
     */
    public static String read(BufferedReader input, List<String> events) throws IOException {
        String header = input.readLine();
        if (!"BEGIN:VCALENDAR".equalsIgnoreCase(header)
                && !"\uFEFFBEGIN:VCALENDAR".equalsIgnoreCase(header)) {
            return "Intestazione iCal assente.";
        }
        int characters = header.length();
        StringBuilder event = null;
        String line;
        while ((line = input.readLine()) != null) {
            characters += line.length() + 1;
            if (characters > MAX_CHARACTERS) {
                events.clear();
                return "Calendario troppo grande per la diagnostica.";
            }
            if (event == null && "BEGIN:VEVENT".equalsIgnoreCase(line)) {
                event = new StringBuilder(line);
            } else if (event != null) {
                event.append('\n').append(line);
                if ("END:VEVENT".equalsIgnoreCase(line)) {
                    events.add(event.toString());
                    event = null;
                }
            } else if ("END:VCALENDAR".equalsIgnoreCase(line)) {
                return events.isEmpty()
                        ? "Nessun blocco VEVENT nel feed."
                        : "Blocchi VEVENT ricevuti: " + events.size();
            }
        }
        events.clear();
        return "Risposta iCal incompleta.";
    }
}
