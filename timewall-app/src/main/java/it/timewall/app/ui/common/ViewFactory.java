package it.timewall.app.ui.common;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import it.timewall.app.configuration.Palette;

/** Stateless presentation helpers shared only inside the official application. */
public final class ViewFactory {
    private final Activity activity;
    private final Palette palette;
    private final Typeface dashboardTypeface, menuIcons;
    private final boolean darkTheme;

    public boolean isDarkTheme() {
        return darkTheme;
    }

    private final int accentColor;

    /**
     * Collega contesto, palette, font e glifi usati dai componenti della dashboard; non crea ancora
     * alcuna vista.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param palette palette dei ruoli cromatici della schermata
     * @param typeface font risolto per la dashboard
     * @param icons font dei glifi Font Awesome
     * @param dark true per una presentazione scura
     */
    public ViewFactory(
            Activity activity, Palette palette, Typeface typeface, Typeface icons, boolean dark) {
        this.activity = activity;
        this.palette = palette;
        this.dashboardTypeface = typeface;
        this.menuIcons = icons;
        this.darkTheme = dark;
        this.accentColor = palette.accent;
    }

    /**
     * Crea un LinearLayout verticale vuoto nel contesto dell’Activity, pronto a ricevere i
     * componenti della schermata.
     *
     * @return contenitore verticale o riga costruita
     */
    public LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    /**
     * Crea il disegno circolare con il colore del calendario e un bordo leggibile nel tema attivo.
     *
     * @param color colore ARGB da associare al contenuto
     * @return disegno di fondo o indicatore configurato
     */
    public GradientDrawable calendarDot(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setStroke(dp(1), darkTheme ? 0xffaaaaaa : 0xff555555);
        return dot;
    }

    /**
     * Associa al testo un pallino del colore richiesto, ne imposta la dimensione in dp e la
     * distanza dal testo.
     *
     * @param text testo da visualizzare o analizzare
     * @param color colore ARGB da associare al contenuto
     * @param size dimensione in sp per il testo o in dp per il pallino, secondo il controllo
     */
    public void setCalendarDot(TextView text, int color, int size) {
        GradientDrawable dot = calendarDot(color);
        dot.setBounds(0, 0, dp(size), dp(size));
        text.setCompoundDrawables(dot, null, null, null);
        text.setCompoundDrawablePadding(dp(8));
    }

    /**
     * Crea una label con dimensione in sp, colore del testo principale e font configurato. Il
     * grassetto usa il font di sistema o la simulazione sul font personalizzato.
     *
     * @param text testo da visualizzare o analizzare
     * @param size dimensione in sp per il testo o in dp per il pallino, secondo il controllo
     * @param bold true per una label in grassetto
     * @return label configurata secondo lo stile descritto
     */
    public TextView label(String text, int size, boolean bold) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(palette.text);
        if (dashboardTypeface == null) {
            label.setTypeface(
                    Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        } else {
            label.setTypeface(dashboardTypeface);
            label.getPaint().setFakeBoldText(bold);
        }
        return label;
    }

    /**
     * Crea una label non in grassetto usando il colore del testo secondario della palette.
     *
     * @param text testo da visualizzare o analizzare
     * @param size dimensione in sp per il testo o in dp per il pallino, secondo il controllo
     * @return label configurata secondo lo stile descritto
     */
    public TextView secondaryLabel(String text, int size) {
        TextView label = label(text, size, false);
        label.setTextColor(palette.secondaryText);
        return label;
    }

    /**
     * Converte una misura in dp in pixel arrotondati secondo la densità dello schermo.
     *
     * @param value valore da validare, formattare o mostrare
     * @return misura convertita in pixel e arrotondata alla densità del dispositivo
     */
    public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /**
     * Aggiunge all’header un pulsante con glifo Font Awesome, descrizione accessibile e callback.
     * Configura colore normale e disabilitato; l’eventuale rimozione dello sfondo resta al
     * chiamante.
     *
     * @param header contenitore orizzontale dei pulsanti dell’header
     * @param icon nome della risorsa grafica o glifo da visualizzare
     * @param description descrizione estesa o descrizione accessibile del controllo
     * @param listener destinatario delle callback eseguite sul thread di refresh
     * @return pulsante aggiunto al contenitore
     */
    public Button addHeaderButton(
            LinearLayout header, String icon, String description, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setTypeface(menuIcons);
        button.setText(icon);
        button.setTextColor(
                new ColorStateList(
                        new int[][] {{-android.R.attr.state_enabled}, {}},
                        new int[] {palette.secondaryText, palette.accent}));
        button.setTextSize(24);
        button.setContentDescription(description);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(listener);
        header.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    /**
     * Crea un glifo meteo Font Awesome con il colore secondario, il margine previsto e senza
     * descrizione accessibile duplicata.
     *
     * @param glyph glifo Font Awesome da visualizzare
     * @return label configurata secondo lo stile descritto
     */
    public TextView weatherGlyph(String glyph) {
        TextView icon = secondaryLabel(glyph, 20);
        icon.setTypeface(menuIcons);
        icon.setPadding(0, 0, dp(6), 0);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    /** Returns whichever of black and white has greater contrast against the background. */
    public static int contrastingText(int background) {
        double luminance =
                0.2126 * linear(android.graphics.Color.red(background))
                        + 0.7152 * linear(android.graphics.Color.green(background))
                        + 0.0722 * linear(android.graphics.Color.blue(background));
        return luminance > 0.179 ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
