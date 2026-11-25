package it.timewall.app.ui.common;

import android.graphics.Typeface;

import it.timewall.app.configuration.DashboardFont;
import it.timewall.app.configuration.DashboardTheme;
import it.timewall.app.configuration.Palette;

/** Resolved presentation options passed to screens, independently of their draft settings. */
public final class DashboardStyle {
    public static final int FEATURED_TITLE_SP = 32;
    public static final int FEATURED_DETAIL_SP = 23;
    public final Palette palette;
    public final Typeface font, icons;
    public final DashboardTheme theme;
    public final DashboardFont selectedFont;
    public final boolean dark;

    /**
     * Raggruppa palette, font, icone e selezioni di tema in una configurazione di presentazione da
     * passare alle schermate.
     *
     * @param palette palette dei ruoli cromatici della schermata
     * @param font font risolto oppure scelta del font configurata
     * @param icons font dei glifi Font Awesome
     * @param theme tema selezionato
     * @param selectedFont voce del catalogo font selezionata
     * @param dark true per una presentazione scura
     */
    public DashboardStyle(
            Palette palette,
            Typeface font,
            Typeface icons,
            DashboardTheme theme,
            DashboardFont selectedFont,
            boolean dark) {
        this.palette = palette;
        this.font = font;
        this.icons = icons;
        this.theme = theme;
        this.selectedFont = selectedFont;
        this.dark = dark;
    }
}
