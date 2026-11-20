package it.timewall.app.domain.model;

import java.util.Date;

/** One parsed occurrence; end is exclusive, as in iCalendar. */
public final class CalendarEvent {
    public final long start, end;
    public final boolean allDay;
    public final String title, location, description, feedAddress, uid;
    public final Date created;
    public final int color;

    /**
     * Memorizza i dati interpretati di una singola occorrenza, inclusi origine, colore, UID e
     * CREATED; gli istanti di fine seguono la semantica esclusiva iCal.
     *
     * @param start istante iniziale in millisecondi
     * @param end istante finale esclusivo in millisecondi
     * @param allDay true per un evento che occupa una o più giornate intere
     * @param title titolo dell’evento
     * @param location luogo dell’evento
     * @param description descrizione estesa o descrizione accessibile del controllo
     * @param created data CREATED ricevuta dal feed; null se non disponibile
     * @param feedAddress indirizzo del calendario di origine usato per identità e deduplicazione
     * @param color colore ARGB da associare al contenuto
     * @param uid UID iCal; vuoto se non fornito
     */
    public CalendarEvent(
            long start,
            long end,
            boolean allDay,
            String title,
            String location,
            String description,
            Date created,
            String feedAddress,
            int color,
            String uid) {
        this.start = start;
        this.end = end;
        this.allDay = allDay;
        this.title = title;
        this.location = location;
        this.description = description;
        this.created = created;
        this.feedAddress = feedAddress;
        this.color = color;
        this.uid = uid;
    }

    /**
     * Genera la chiave di deduplicazione da origine, istante iniziale e UID. Se manca l’UID usa il
     * titolo; un cambio di titolo con UID stabile non crea una nuova occorrenza.
     *
     * @return chiave composta da origine, inizio e UID, o titolo in assenza di UID
     */
    public String occurrenceKey() {
        return feedAddress
                + '\u0000'
                + start
                + '\u0000'
                + (uid.isEmpty() ? "title:" + title : "uid:" + uid);
    }
}
