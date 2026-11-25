package it.timewall.app.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import it.timewall.app.configuration.HexColor;
import it.timewall.app.configuration.Palette;
import it.timewall.app.localization.I18n;
import it.timewall.app.ui.common.ViewFactory;

import java.util.Locale;

public final class CustomThemeEditor extends LinearLayout {
    public static final int LEGACY_COLOR_COUNT = 4;
    public static final int CLOCK_COLOR_INDEX = 6;
    public static final int DATE_COLOR_INDEX = 7;
    private static final String[] LABELS = {
        "primary_color", "background_color", "card_color", "highlight_color",
        "primary_text", "secondary_text", "clock_color", "date_color"
    };
    private static final String[] KEYS = {
        "customTheme.accent", "customTheme.background", "customTheme.card", "customTheme.highlight",
        "customTheme.text", "customTheme.secondaryText", "customTheme.clock", "customTheme.date"
    };

    private final EditText[] inputs = new EditText[KEYS.length];

    private Runnable onChange = () -> {};

    /**
     * Crea i campi HEX obbligatori e le anteprime dei colori personalizzati, ripristina i valori
     * salvati e collega validazione e color picker con canale alpha.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param views factory dello stile delle impostazioni
     * @param preferences archivio delle preferenze da leggere o aggiornare
     */
    public CustomThemeEditor(Activity activity, ViewFactory views, SharedPreferences preferences) {
        super(activity);
        setOrientation(VERTICAL);
        views.addSettingHeading(this, I18n.text(activity, "palette"), true);
        for (int index = 0; index < inputs.length; index++) {
            String name = I18n.text(activity, LABELS[index]);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            views.addSettingView(this, row, index == 0, 0);

            TextView label = views.settingHeading(name);
            row.addView(label, new LayoutParams(0, -2, 1));

            Button swatch = new Button(activity);
            swatch.setContentDescription(I18n.text(activity, "choose_color", name));
            LayoutParams swatchLayout = new LayoutParams(dp(48), dp(40));
            swatchLayout.rightMargin = dp(8);
            row.addView(swatch, swatchLayout);

            EditText input = new EditText(activity);
            inputs[index] = input;
            views.styleSettingInput(input);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setHint("#AARRGGBB");
            input.setContentDescription(I18n.text(activity, "hex_accessibility", name));
            input.setText(savedColor(preferences, index));
            row.addView(input, new LayoutParams(dp(156), -2));

            updateSwatch(swatch, input.getText().toString());
            swatch.setOnClickListener(view -> pickColor(activity, name, input));
            input.addTextChangedListener(
                    new TextWatcher() {
                        /**
                         * Callback richiesta da TextWatcher: non esegue azioni prima della
                         * modifica, perché validazione e anteprima vengono aggiornate dopo.
                         *
                         * @param text testo da visualizzare o analizzare
                         * @param start indice iniziale del testo modificato
                         * @param count conteggio per i plurali o numero di caratteri coinvolti
                         *     nella callback
                         * @param after numero di caratteri che sostituiranno il tratto corrente
                         */
                        @Override
                        public void beforeTextChanged(
                                CharSequence text, int start, int count, int after) {}

                        /**
                         * Callback intermedia di TextWatcher lasciata intenzionalmente vuota; il
                         * valore completo viene gestito in afterTextChanged.
                         *
                         * @param text testo da visualizzare o analizzare
                         * @param start indice iniziale del testo modificato
                         * @param before numero di caratteri sostituiti
                         * @param count conteggio per i plurali o numero di caratteri coinvolti
                         *     nella callback
                         */
                        @Override
                        public void onTextChanged(
                                CharSequence text, int start, int before, int count) {}

                        /**
                         * Valida il colore digitato, aggiorna l’errore e il campione di anteprima e
                         * notifica al contenitore di ricalcolare la possibilità di salvare.
                         *
                         * @param text testo da visualizzare o analizzare
                         */
                        @Override
                        public void afterTextChanged(Editable text) {
                            String value = text.toString();
                            input.setError(
                                    value.isEmpty() || validHex(value)
                                            ? null
                                            : I18n.text(activity, "hex_invalid"));
                            updateSwatch(swatch, value);
                            onChange.run();
                        }
                    });
        }
    }

    /**
     * Registra la callback richiamata quando un colore viene modificato, così il contenitore può
     * aggiornare il pulsante Salva.
     *
     * @param onChange callback per riallineare la validità del salvataggio
     */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Legge un colore salvato; per ora e data mancanti usa come fallback il colore principale delle
     * palette precedenti.
     *
     * @param preferences archivio delle preferenze da leggere o aggiornare
     * @param index indice del calendario o del blocco da modificare; negativo quando previsto per
     *     un’aggiunta
     * @return HEX salvato oppure fallback del colore principale per ora e data
     */
    private static String savedColor(SharedPreferences preferences, int index) {
        String fallback = index >= CLOCK_COLOR_INDEX ? preferences.getString(KEYS[0], "") : "";
        return preferences.getString(KEYS[index], fallback);
    }

    public static Palette customPalette(int[] colors) {
        int automaticText = ViewFactory.contrastingText(colors[1]);
        boolean legacy = colors.length == LEGACY_COLOR_COUNT;
        return new Palette(
                colors[1],
                colors[0],
                legacy ? automaticText : colors[4],
                legacy ? automaticText : colors[5],
                colors[2],
                colors[3],
                automaticText == Color.WHITE ? 0x55ffffff : 0x55000000);
    }

    /**
     * Carica e valida la palette personalizzata salvata. Restituisce null se i primi quattro colori
     * sono incompleti o il formato legacy a quattro colori se mancano le personalizzazioni
     * successive.
     *
     * @param preferences archivio delle preferenze da leggere o aggiornare
     * @return colori ARGB validi; null quando la palette obbligatoria non è valida
     */
    public static int[] loadColors(SharedPreferences preferences) {
        int[] colors = new int[KEYS.length];
        for (int index = 0; index < colors.length; index++) {
            String value = savedColor(preferences, index);
            if (!validHex(value)) {
                // Older palettes have only the four surface and accent colors.
                return index < LEGACY_COLOR_COUNT
                        ? null
                        : java.util.Arrays.copyOf(colors, LEGACY_COLOR_COUNT);
            }
            colors[index] = HexColor.parse(value);
        }
        return colors;
    }

    /**
     * Legge una rappresentazione confrontabile della bozza: normalizza i colori validi in HEX e
     * mantiene riconoscibili anche input non validi, così la conferma di uscita rileva modifiche
     * non salvate.
     *
     * @return valori della bozza normalizzati per il confronto, inclusi quelli non validi
     */
    public String[] inputValues() {
        String[] values = new String[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            String value = inputs[index].getText().toString().trim();
            values[index] =
                    validHex(value) ? hex(HexColor.parse(value)) : value.toUpperCase(Locale.ROOT);
        }
        return values;
    }

    /**
     * Converte tutti i campi obbligatori in ARGB. Restituisce null appena incontra un valore non
     * valido, impedendo il salvataggio della palette incompleta.
     *
     * @return colori ARGB validi; null quando la palette obbligatoria non è valida
     */
    int[] readColors() {
        int[] colors = new int[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            String value = inputs[index].getText().toString();
            if (!validHex(value)) {
                return null;
            }
            colors[index] = HexColor.parse(value);
        }
        return colors;
    }

    /**
     * Scrive tutti i colori nelle chiavi storiche dell’editor ricevuto, senza applicare
     * autonomamente la transazione delle preferenze.
     *
     * @param editor transazione delle preferenze gestita dal chiamante
     * @param colors colori ARGB delle voci o della palette nell’ordine previsto
     */
    public static void saveColors(SharedPreferences.Editor editor, int[] colors) {
        for (int index = 0; index < colors.length; index++) {
            editor.putString(KEYS[index], hex(colors[index]));
        }
    }

    /**
     * Delega la validazione al formato condiviso #RRGGBB o #AARRGGBB, mantenendo il canale alpha in
     * prima posizione.
     *
     * @param value valore da validare, formattare o mostrare
     * @return true se l’input rispetta uno dei due formati HEX ammessi
     */
    private static boolean validHex(String value) {
        return HexColor.isValid(value);
    }

    /**
     * Serializza un colore ARGB nel formato HEX condiviso, con alpha esplicito soltanto se non
     * opaco.
     *
     * @param color colore ARGB da associare al contenuto
     * @return HEX maiuscolo del colore, con alpha quando necessario
     */
    private static String hex(int color) {
        return HexColor.format(color);
    }

    /**
     * Aggiorna l’anteprima del colore; per un input non valido mostra un campione trasparente con
     * il simbolo di aggiunta.
     *
     * @param swatch pulsante che mostra l’anteprima del colore
     * @param value valore da validare, formattare o mostrare
     */
    private void updateSwatch(Button swatch, String value) {
        GradientDrawable preview = new GradientDrawable();
        preview.setCornerRadius(dp(5));
        preview.setStroke(dp(1), 0xff808080);
        boolean valid = validHex(value);
        preview.setColor(valid ? HexColor.parse(value) : Color.TRANSPARENT);
        swatch.setBackground(preview);
        swatch.setText(valid ? "" : "+");
    }

    /**
     * Apre il selettore dei canali rosso, verde, blu e alpha con anteprima su scacchiera. La
     * conferma scrive il colore nell’input collegato, facendo scattare la normale validazione.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param name nome della voce, del campo o della località
     * @param input campo HEX da aggiornare o reader del calendario nella diagnostica
     */
    private void pickColor(Activity activity, String name, EditText input) {
        String value = input.getText().toString();
        int start = validHex(value) ? HexColor.parse(value) : Color.BLACK;
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(VERTICAL);
        fields.setPadding(dp(16), 0, dp(16), 0);
        int square = dp(8);
        Bitmap tile = Bitmap.createBitmap(square * 2, square * 2, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(tile);
        canvas.drawColor(0xffeeeeee);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(0xffaaaaaa);
        canvas.drawRect(0, 0, square, square, paint);
        canvas.drawRect(square, square, 2 * square, 2 * square, paint);
        BitmapDrawable checkerboard = new BitmapDrawable(getResources(), tile);
        checkerboard.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        ColorDrawable sample = new ColorDrawable();
        android.view.View swatch = new android.view.View(activity);
        swatch.setBackground(
                new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[] {checkerboard, sample}));
        fields.addView(swatch, new LayoutParams(-1, dp(36)));
        TextView preview = new TextView(activity);
        preview.setGravity(Gravity.CENTER);
        fields.addView(preview, new LayoutParams(-1, -2));
        String[] names = {
            I18n.text(activity, "red"),
            I18n.text(activity, "green"),
            I18n.text(activity, "blue"),
            I18n.text(activity, "opacity")
        };
        int[] channels = {
            Color.red(start), Color.green(start), Color.blue(start), Color.alpha(start)
        };
        Runnable render =
                () -> {
                    int color = Color.argb(channels[3], channels[0], channels[1], channels[2]);
                    sample.setColor(color);
                    preview.setText(
                            hex(color) + " · " + Math.round(channels[3] * 100 / 255f) + "%");
                };
        for (int index = 0; index < channels.length; index++) {
            final int channel = index;
            TextView label = new TextView(activity);
            label.setText(names[index]);
            fields.addView(label);
            SeekBar slider = new SeekBar(activity);
            slider.setMax(255);
            slider.setProgress(channels[index]);
            slider.setContentDescription(names[index]);
            fields.addView(slider, new LayoutParams(-1, -2));
            slider.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        /**
                         * Callback di inizio trascinamento lasciata intenzionalmente vuota:
                         * l’anteprima segue direttamente ogni variazione del cursore.
                         *
                         * @param bar cursore modificato dall’utente
                         */
                        @Override
                        public void onStartTrackingTouch(SeekBar bar) {}

                        /**
                         * Callback di fine trascinamento lasciata intenzionalmente vuota: il colore
                         * è già stato aggiornato durante il movimento.
                         *
                         * @param bar cursore modificato dall’utente
                         */
                        @Override
                        public void onStopTrackingTouch(SeekBar bar) {}

                        /**
                         * Ricalcola il colore ARGB dai quattro cursori e aggiorna immediatamente
                         * anteprima e valore HEX del selettore.
                         *
                         * @param bar cursore modificato dall’utente
                         * @param progress valore corrente del cursore
                         * @param fromUser true se la modifica deriva da un gesto dell’utente
                         */
                        @Override
                        public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                            channels[channel] = progress;
                            render.run();
                        }
                    });
        }
        render.run();
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);
        new AlertDialog.Builder(activity)
                .setTitle(name)
                .setView(scroll)
                .setNegativeButton(I18n.text(activity, "cancel"), null)
                .setPositiveButton(
                        I18n.text(activity, "use_color"),
                        (dialog, which) -> input.setText(hex(sample.getColor())))
                .show();
    }

    /**
     * Sceglie testo nero o biin dp nei pixel della densità del dispositivo.
     *
     * @param value valore da validare, formattare o mostrare
     * @return misura in pixel alla densità corrente
     */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
