package it.timewall.app.domain.time;

import it.timewall.app.domain.model.CalendarEvent;

import java.util.List;

/** Selection for today's agenda. Input is sorted by start and already filtered to today. */
public final class TodayEvents {
    private static final long IMMINENT_LEAD = 30 * 60_000L;

    public final CalendarEvent active, next, featured;
    public final boolean hasEvents, hasOngoingAllDay;
    public final long nextChange;

    public TodayEvents(List<CalendarEvent> events, long now) {
        CalendarEvent firstActive = null, firstNext = null;
        boolean ongoingAllDay = false;
        long change = Long.MAX_VALUE;
        for (CalendarEvent event : events) {
            if (event.end > now) change = Math.min(change, event.end);
            if (event.allDay) {
                if (event.end > now) ongoingAllDay = true;
                continue;
            }
            if (firstActive == null && event.start <= now && now < event.end) {
                firstActive = event;
            }
            if (event.start > now) {
                if (firstNext == null) firstNext = event;
                change = Math.min(change, event.start);
            }
        }
        active = firstActive;
        next = firstNext;
        featured = active != null ? active : next;
        hasEvents = !events.isEmpty();
        hasOngoingAllDay = ongoingAllDay;
        if (active == null && next != null && !isImminent(next.start, now)) {
            change = Math.min(change, next.start - IMMINENT_LEAD);
        }
        nextChange = change;
    }

    public static boolean isImminent(long start, long now) {
        return start - now <= IMMINENT_LEAD;
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
