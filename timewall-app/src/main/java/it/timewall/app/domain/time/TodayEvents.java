package it.timewall.app.domain.time;

import it.timewall.app.domain.model.CalendarEvent;

import java.util.ArrayList;
import java.util.List;

/** Selection for today's agenda. Input is sorted by start and already filtered to today. */
public final class TodayEvents {
    private static final long FEATURED_LEAD_MILLIS = 30 * 60_000L;

    public final List<CalendarEvent> allDay = new ArrayList<>();
    public final CalendarEvent active, next, featured;
    public final long nextChange;

    /**
     * Analizza gli eventi odierni già ordinati, raccoglie quelli giornalieri e seleziona il primo
     * attivo e il prossimo. Calcola anche il confine temporale successivo che richiede un
     * ridisegno.
     *
     * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
     *     destinazione dei blocchi
     * @param now istante civile corrente, in millisecondi
     */
    public TodayEvents(List<CalendarEvent> events, long now) {
        CalendarEvent firstActive = null, firstNext = null;
        long change = Long.MAX_VALUE;
        for (CalendarEvent event : events) {
            if (event.allDay) {
                allDay.add(event);
            } else {
                if (firstActive == null && event.start <= now && now < event.end)
                    firstActive = event;
                if (event.start > now) {
                    if (firstNext == null) firstNext = event;
                    change = Math.min(change, event.start);
                }
                if (event.end > now) change = Math.min(change, event.end);
            }
        }
        active = firstActive;
        next = firstNext;
        long featuredFrom = next == null ? Long.MAX_VALUE : next.start - FEATURED_LEAD_MILLIS;
        featured = active != null ? active : (now >= featuredFrom ? next : null);
        nextChange = next != null && featuredFrom > now ? Math.min(change, featuredFrom) : change;
    }

    /**
     * Verifica se un evento appartiene al giorno delimitato dagli istanti indicati. Esclude eventi
     * terminati alla mezzanotte iniziale e include eventi istantanei che iniziano nel giorno.
     *
     * @param event occorrenza da visualizzare o frammento iCal da usare nella fixture
     * @param day mezzanotte iniziale del giorno; nelle fixture indica se l’evento è giornaliero
     * @param tomorrow mezzanotte esclusiva del giorno successivo
     * @return true se l’occorrenza ricade nel giorno delimitato dagli argomenti
     */
    public static boolean intersectsDay(CalendarEvent event, long day, long tomorrow) {
        return event.start < tomorrow && (event.end > day || event.start >= day);
    }
}
