package it.timewall.app.ui.dashboard;

import static it.timewall.app.domain.time.CalendarDays.midnight;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import it.timewall.app.configuration.Palette;
import it.timewall.app.domain.model.CalendarEvent;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.localization.DashboardDates;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.ui.common.ViewFactory;

import java.util.List;

/** Read-only event details with bounded, content-sized dialog dimensions. */
public final class EventDetailsDialog {
    private final Activity activity;
    private final Palette palette;
    private final ViewFactory views;
    private final List<Feed> feeds;
    private final boolean use24Hour;
    private final DashboardDates dates;

    /**
     * Collega Activity, stile, calendari e formato orario necessari a mostrare il dettaglio; la
     * popup viene aperta soltanto da show.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param palette palette dei ruoli cromatici della schermata
     * @param views factory dei componenti coerenti con il tema
     * @param feeds calendari configurati o bozza di calendari da elaborare
     * @param use24Hour true per il formato a 24 ore
     */
    public EventDetailsDialog(
            Activity activity,
            Palette palette,
            ViewFactory views,
            List<Feed> feeds,
            boolean use24Hour) {
        this.activity = activity;
        this.palette = palette;
        this.views = views;
        this.feeds = feeds;
        this.use24Hour = use24Hour;
        dates = new DashboardDates(activity);
    }

    /**
     * Crea il fondo arrotondato della popup usando lo sfondo della palette corrente.
     *
     * @return disegno di fondo o indicatore configurato
     */
    private GradientDrawable roundedBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(palette.background);
        drawable.setCornerRadius(views.dp(5));
        return drawable;
    }

    /**
     * Apre il dettaglio in sola lettura con titolo, orari, descrizione, luogo, calendario e
     * CREATED. Limita la larghezza al 40–70% dello schermo e l’altezza all’80%, applica il backdrop
     * e permette la chiusura con X o tap esterno.
     *
     * @param event occorrenza da visualizzare o frammento iCal da usare nella fixture
     */
    public void show(CalendarEvent event) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0.55f);
        window.setGravity(Gravity.CENTER);

        Rect screen = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(screen);
        final int maxWidth = screen.width() * 7 / 10;
        final int maxHeight = screen.height() * 8 / 10;
        LinearLayout panel =
                new LinearLayout(activity) {
                    /**
                     * Misura il pannello imponendo i limiti massimi di larghezza e altezza della
                     * popup, pur lasciando che si adatti al contenuto.
                     *
                     * @param widthSpec vincolo di larghezza ricevuto dal sistema di misura
                     * @param heightSpec vincolo di altezza ricevuto dal sistema di misura
                     */
                    @Override
                    protected void onMeasure(int widthSpec, int heightSpec) {
                        super.onMeasure(limit(widthSpec, maxWidth), limit(heightSpec, maxHeight));
                    }

                    /**
                     * Converte un vincolo di misura nel massimo spazio consentito dal contenitore e
                     * dalla percentuale dello schermo, usando AT_MOST.
                     *
                     * @param measureSpec vincolo di misura da limitare
                     * @param maximum massimo spazio consentito in pixel
                     * @return specifica AT_MOST limitata al massimo consentito
                     */
                    private int limit(int measureSpec, int maximum) {
                        int available =
                                MeasureSpec.getMode(measureSpec) == MeasureSpec.UNSPECIFIED
                                        ? maximum
                                        : Math.min(maximum, MeasureSpec.getSize(measureSpec));
                        return MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST);
                    }
                };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setMinimumWidth(screen.width() * 4 / 10);
        panel.setBackground(roundedBackground());
        panel.setPadding(views.dp(20), views.dp(16), views.dp(20), views.dp(12));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.TOP);
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView title =
                views.label(
                        event.title.isEmpty() ? I18n.text(activity, "untitled") : event.title,
                        24,
                        true);
        title.setMinHeight(views.dp(48));
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(-2, -2, 1));
        Button close =
                views.addHeaderButton(
                        header, "\uf00d", I18n.text(activity, "close"), view -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = (LinearLayout.LayoutParams) close.getLayoutParams();
        closeParams.leftMargin = 20;
        close.setLayoutParams(closeParams);

        LinearLayout fields = views.column();
        addEventDetail(fields, I18n.text(activity, "schedule"), eventSchedule(event));
        addEventDetail(fields, I18n.text(activity, "description"), event.description);
        addEventDetail(fields, I18n.text(activity, "location"), event.location);
        String calendarName = I18n.text(activity, "unavailable");
        for (Feed feed : feeds) {
            if (feed.address.equals(event.feedAddress)) {
                calendarName = feed.name;
                break;
            }
        }
        addEventDetail(fields, I18n.text(activity, "calendar"), calendarName, event.color);
        addEventDetail(
                fields,
                I18n.text(activity, "created"),
                event.created == null
                        ? I18n.text(activity, "unavailable")
                        : dates.dateLabel(event.created.getTime())
                                + " · "
                                + TimeDisplay.time(
                                        activity, event.created.getTime(), false, use24Hour));

        ScrollView content = new ScrollView(activity);
        content.addView(fields, new ScrollView.LayoutParams(-1, -2));
        panel.addView(content, new LinearLayout.LayoutParams(-1, -2, 1));

        dialog.setContentView(panel);
        window.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }

    /**
     * Aggiunge al dettaglio un campo con label e valore, omettendo valori vuoti. L’overload con
     * colore affianca al valore un quadrato che identifica il calendario.
     *
     * @param fields contenitore dei campi del dettaglio
     * @param heading titolo tradotto del campo
     * @param value valore da validare, formattare o mostrare
     */
    private void addEventDetail(LinearLayout fields, String heading, String value) {
        addEventDetail(fields, heading, value, null);
    }

    /**
     * Aggiunge al dettaglio un campo con label e valore, omettendo valori vuoti. L’overload con
     * colore affianca al valore un quadrato che identifica il calendario.
     *
     * @param fields contenitore dei campi del dettaglio
     * @param heading titolo tradotto del campo
     * @param value valore da validare, formattare o mostrare
     * @param color colore ARGB da associare al contenuto
     */
    private void addEventDetail(LinearLayout fields, String heading, String value, Integer color) {
        if (value.trim().isEmpty()) {
            return;
        }
        TextView title = views.secondaryLabel(heading, 14);
        title.setPadding(0, views.dp(12), 0, views.dp(3));
        fields.addView(title);
        TextView text = views.label(value, 17, false);
        if (color == null) {
            fields.addView(text);
            return;
        }
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View swatch = new View(activity);
        swatch.setBackgroundColor(color);
        LinearLayout.LayoutParams swatchParams =
                new LinearLayout.LayoutParams(views.dp(14), views.dp(14));
        swatchParams.rightMargin = views.dp(8);
        row.addView(swatch, swatchParams);
        row.addView(text, new LinearLayout.LayoutParams(-2, -2));
        fields.addView(row);
    }

    /**
     * Compone gli orari estesi dell’evento rispettando il formato 12/24 ore e la data locale. Per
     * eventi giornalieri interpreta DTEND come esclusivo e indica soltanto l’ultimo giorno
     * effettivamente incluso.
     *
     * @param event occorrenza da visualizzare o frammento iCal da usare nella fixture
     * @return orario o intervallo esteso dell’evento, con le sole giornate effettivamente incluse
     */
    private String eventSchedule(CalendarEvent event) {
        String startDate = dates.dateLabel(event.start);
        if (event.allDay) {
            // DTEND for an all-day event is exclusive.
            long lastDay = Math.max(event.start, event.end - 1);
            return I18n.text(activity, "all_day")
                    + " · "
                    + startDate
                    + (midnight(lastDay) == midnight(event.start)
                            ? ""
                            : " – " + dates.dateLabel(lastDay));
        }
        String start =
                startDate + " · " + TimeDisplay.time(activity, event.start, false, use24Hour);
        if (event.start == event.end) {
            return start;
        }
        String end = TimeDisplay.time(activity, event.end, false, use24Hour);
        return midnight(event.start) == midnight(event.end)
                ? start + " – " + end
                : start + "\n" + dates.dateLabel(event.end) + " · " + end;
    }
}
