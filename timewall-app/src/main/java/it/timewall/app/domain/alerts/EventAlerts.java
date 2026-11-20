package it.timewall.app.domain.alerts;

import it.timewall.app.domain.model.CalendarEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure notification decisions for sorted snapshots. A first load does not announce old active
 * events; a subsequent download can announce a newly discovered active occurrence once.
 */
public final class EventAlerts {
    private final Set<String> notified = new HashSet<>();
    private long nextStart = Long.MAX_VALUE;

    /**
     * Rivaluta le notifiche soltanto al prossimo inizio previsto o quando l’orologio torna
     * indietro. Negli altri tick restituisce un insieme vuoto senza scandire gli eventi.
     *
     * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
     *     destinazione dei blocchi
     * @param lastTick istante civile dell’ultimo controllo, in millisecondi
     * @param now istante civile corrente, in millisecondi
     * @return chiavi delle nuove notifiche, oppure insieme vuoto se non serve una rivalutazione
     */
    public Set<String> tick(List<CalendarEvent> events, long lastTick, long now) {
        return now >= nextStart || now < lastTick
                ? check(events, null, lastTick, now)
                : Collections.emptySet();
    }

    /**
     * Esamina uno snapshot ordinato per inizio e aggiorna il prossimo istante da controllare.
     * Segnala una sola volta gli eventi appena iniziati e quelli nuovi già attivi comparsi dopo un
     * download; il primo caricamento non annuncia eventi attivi preesistenti.
     *
     * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
     *     destinazione dei blocchi
     * @param previous snapshot precedente; null per una valutazione senza confronto con il download
     *     precedente
     * @param lastTick istante civile dell’ultimo controllo, in millisecondi
     * @param now istante civile corrente, in millisecondi
     * @return chiavi delle occorrenze da notificare per la prima volta in questa valutazione
     */
    public Set<String> check(
            List<CalendarEvent> events, List<CalendarEvent> previous, long lastTick, long now) {
        Set<String> known = null;
        if (previous != null) {
            known = new HashSet<>();
            for (CalendarEvent event : previous) {
                known.add(event.occurrenceKey());
            }
        }
        nextStart = Long.MAX_VALUE;
        Set<String> alerts = Collections.emptySet();
        for (CalendarEvent event : events) {
            if (event.start > now) {
                nextStart = event.start;
                break; // Event snapshots are sorted by start time.
            }
            boolean active = event.end > now;
            boolean justStarted = event.start > lastTick && (active || now - event.start < 1000);
            if (!justStarted && (known == null || !active)) {
                continue;
            }
            String key = event.occurrenceKey();
            if ((justStarted || !known.contains(key)) && notified.add(key)) {
                if (alerts.isEmpty()) {
                    alerts = new HashSet<>();
                }
                alerts.add(key);
            }
        }
        return alerts;
    }
}
