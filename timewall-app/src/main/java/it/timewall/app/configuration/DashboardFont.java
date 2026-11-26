package it.timewall.app.configuration;

public enum DashboardFont {
    SYSTEM_ROBOTO("default_font", ""),
    EXO_2("Exo 2", "Exo2-Regular.otf"),
    FIRJAR("Firjar", "Firjar-Regular.ttf"),
    GONTSERRAT("Gontserrat", "Gontserrat-Regular.ttf"),
    MONTSERRAT("Montserrat", "Montserrat-Regular.ttf"),
    NOTO_SANS("Noto Sans", "NotoSans-Regular.ttf"),
    OPEN_SANS("Open Sans", "OpenSans-Regular.ttf"),
    RALEWAY("Raleway", "Raleway-Regular.ttf"),
    ROBOTO_CONDENSED("Roboto Condensed", "RobotoCondensed-Regular.ttf"),
    ROBOTO_SLAB("Roboto Slab", "RobotoSlab-Regular.ttf"),
    SHANTELL_SANS("Shantell Sans", "ShantellSans-Regular.ttf");

    public final String label;
    public final String file;

    /** Restituisce il font salvato per nome file; font rimossi o sconosciuti usano il sistema. */
    public static DashboardFont fromSavedFile(String file) {
        for (DashboardFont font : values()) {
            if (font.file.equals(file)) return font;
        }
        return SYSTEM_ROBOTO;
    }

    /**
     * Associa a una voce del selettore la label e il nome del file persistito; un file vuoto
     * identifica il font di sistema.
     *
     * @param label label della voce nel selettore
     * @param file nome dell’asset persistito; vuoto per il font di sistema
     */
    DashboardFont(String label, String file) {
        this.label = label;
        this.file = file;
    }
}
