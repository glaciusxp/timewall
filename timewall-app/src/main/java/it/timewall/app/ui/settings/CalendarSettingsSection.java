package it.timewall.app.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import it.timewall.app.data.calendar.CalendarFeed;
import it.timewall.app.domain.model.Feed;
import it.timewall.app.localization.I18n;
import it.timewall.app.ui.common.ViewFactory;

import java.util.List;

/** Calendar configuration editor: never persists changes until the parent screen saves. */
public final class CalendarSettingsSection {
    private final Activity activity;
    private final ViewFactory views;

    /**
     * Collega il contesto e il generatore di viste usati per modificare i calendari della bozza,
     * senza accedere alle preferenze.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param views factory dei componenti coerenti con il tema
     */
    public CalendarSettingsSection(Activity activity, ViewFactory views) {
        this.activity = activity;
        this.views = views;
    }

    private static final int[] COLORS = {
        0xff1565c0,
        0xff2e7d32,
        0xffc62828,
        0xff6a1b9a,
        0xffe65100,
        0xfff9c922,
        0xff40bfe8,
        0xffffffff,
        0xff808080,
        0xffd81b88,
        0xff9ccc24,
        0xff795548
    };
    private static final String[] COLOR_NAMES = {
        "blue",
        "green",
        "red",
        "purple",
        "orange",
        "yellow",
        "sky_blue",
        "white",
        "gray",
        "fuchsia",
        "lime",
        "brown"
    };

    /**
     * Ricostruisce l’elenco dei calendari della bozza con pallino, nome e azioni di aggiunta,
     * modifica o rimozione. Le modifiche non vengono ancora persistite.
     *
     * @param panel contenitore della sezione calendari
     * @param pendingFeeds lista dei calendari della bozza non ancora salvata
     */
    public void render(LinearLayout panel, List<Feed> pendingFeeds) {
        panel.removeAllViews();
        Button add = new Button(activity);
        add.setText(I18n.text(activity, "add_calendar"));
        panel.addView(add);
        Runnable updated = () -> render(panel, pendingFeeds);
        add.setOnClickListener(view -> editCalendar(-1, pendingFeeds, updated));
        if (pendingFeeds.isEmpty()) {
            panel.addView(views.label(I18n.text(activity, "no_calendars"), 18, false));
        }
        for (int index = 0; index < pendingFeeds.size(); index++) {
            Feed feed = pendingFeeds.get(index);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(views.dp(12), views.dp(8), 0, views.dp(8));
            TextView name = views.label(feed.name, 18, false);
            views.setCalendarDot(name, feed.color, 16);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = new Button(activity);
            edit.setText(I18n.text(activity, "edit"));
            row.addView(edit);
            Button remove = new Button(activity);
            remove.setText(I18n.text(activity, "remove"));
            row.addView(remove);
            final int position = index;
            edit.setOnClickListener(view -> editCalendar(position, pendingFeeds, updated));
            remove.setOnClickListener(
                    view -> {
                        pendingFeeds.remove(position);
                        updated.run();
                    });
            panel.addView(row);
        }
    }

    /**
     * Apre il form di aggiunta o modifica con nome, URL visibile e colore. Valida HTTPS e duplicati
     * prima di modificare la bozza e richiama il ridisegno dell’elenco dopo una modifica accettata.
     *
     * @param index indice del calendario o del blocco da modificare; negativo quando previsto per
     *     un’aggiunta
     * @param pendingFeeds lista dei calendari della bozza non ancora salvata
     * @param updated callback che ricostruisce l’elenco dopo una modifica della bozza
     */
    private void editCalendar(int index, List<Feed> pendingFeeds, Runnable updated) {
        Feed existing = index < 0 ? null : pendingFeeds.get(index);
        LinearLayout fields = views.column();
        fields.setPadding(views.dp(16), views.dp(8), views.dp(16), 0);
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        fields.addView(views.secondaryLabel(I18n.text(activity, "name"), 14));
        fields.addView(name);
        TextView addressHeading = views.secondaryLabel(I18n.text(activity, "ical_address"), 14);
        addressHeading.setPadding(0, views.dp(12), 0, 0);
        fields.addView(addressHeading);
        EditText address = new EditText(activity);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setSaveEnabled(false);
        fields.addView(address);
        TextView colorHeading = views.secondaryLabel(I18n.text(activity, "calendar_color"), 14);
        colorHeading.setPadding(0, views.dp(12), 0, 0);
        fields.addView(colorHeading);
        Spinner colors = new Spinner(activity, Spinner.MODE_DROPDOWN);
        ColorLabelAdapter adapter =
                new ColorLabelAdapter(
                        android.R.layout.simple_spinner_item,
                        I18n.texts(activity, COLOR_NAMES),
                        COLORS,
                        0);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colors.setAdapter(adapter);
        fields.addView(colors);
        if (existing != null) {
            name.setText(existing.name);
            address.setText(existing.address);
            for (int color = 0; color < COLORS.length; color++) {
                if (COLORS[color] == existing.color) {
                    colors.setSelection(color);
                }
            }
        }
        AlertDialog.Builder builder =
                new AlertDialog.Builder(activity)
                        .setTitle(
                                I18n.text(
                                        activity,
                                        existing == null ? "add_calendar" : "edit_calendar"))
                        .setView(fields)
                        .setNegativeButton(I18n.text(activity, "cancel"), null)
                        .setPositiveButton(I18n.text(activity, "save"), null);
        if (existing != null) {
            builder.setNeutralButton(
                    I18n.text(activity, "remove"),
                    (dialog, which) -> {
                        pendingFeeds.remove(index);
                        updated.run();
                    });
        }
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        view -> {
                            String url = address.getText().toString().trim();
                            String title = name.getText().toString().trim();
                            if (!CalendarFeed.accepts(url)) {
                                address.setError(I18n.text(activity, "invalid_calendar_url"));
                                return;
                            }
                            for (Feed feed : pendingFeeds) {
                                if (feed != existing && feed.address.equals(url)) {
                                    address.setError(I18n.text(activity, "duplicate_calendar"));
                                    return;
                                }
                            }
                            Feed feed =
                                    new Feed(
                                            title.isEmpty()
                                                    ? I18n.text(activity, "calendar")
                                                    : title,
                                            url,
                                            COLORS[colors.getSelectedItemPosition()]);
                            if (index < 0) {
                                pendingFeeds.add(feed);
                            } else {
                                pendingFeeds.set(index, feed);
                            }
                            dialog.dismiss();
                            updated.run();
                        });
    }

    private final class ColorLabelAdapter extends ArrayAdapter<String> {
        private final int[] colors;
        private final int minimumLeftPadding;

        /**
         * Configura il selettore dei colori con nomi, pallini e padding minimo senza cambiare la
         * palette della dashboard.
         *
         * @param layout risorsa Android della riga del selettore
         * @param names label delle voci del selettore
         * @param colors colori ARGB delle voci o della palette nell’ordine previsto
         * @param minimumLeftPaddingDp padding minimo sinistro espresso in dp
         */
        ColorLabelAdapter(int layout, String[] names, int[] colors, int minimumLeftPaddingDp) {
            super(activity, layout, names);
            this.colors = colors;
            this.minimumLeftPadding = views.dp(minimumLeftPaddingDp);
        }

        /**
         * Mostra nel selettore chiuso il nome del colore accompagnato dal pallino corrispondente.
         *
         * @param position indice della voce selezionata o della riga da visualizzare
         * @param convertView vista riciclabile; null se occorre crearne una nuova
         * @param parent contenitore o selettore che richiede la vista
         * @return vista costruita o aggiornata, pronta per il contenitore chiamante
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return withDot((TextView) super.getView(position, convertView, parent), position);
        }

        /**
         * Mostra nome e pallino del colore anche nelle righe del menu aperto.
         *
         * @param position indice della voce selezionata o della riga da visualizzare
         * @param convertView vista riciclabile; null se occorre crearne una nuova
         * @param parent contenitore o selettore che richiede la vista
         * @return vista costruita o aggiornata, pronta per il contenitore chiamante
         */
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return withDot(
                    (TextView) super.getDropDownView(position, convertView, parent), position);
        }

        /**
         * Associa il colore della posizione al testo e conserva un padding sinistro non inferiore
         * al minimo configurato.
         *
         * @param text testo da visualizzare o analizzare
         * @param position indice della voce selezionata o della riga da visualizzare
         * @return label configurata secondo lo stile descritto
         */
        private TextView withDot(TextView text, int position) {
            views.setCalendarDot(text, colors[position], 13);
            if (text.getPaddingLeft() < minimumLeftPadding) {
                text.setPadding(
                        minimumLeftPadding,
                        text.getPaddingTop(),
                        text.getPaddingRight(),
                        text.getPaddingBottom());
            }
            return text;
        }
    }
}
