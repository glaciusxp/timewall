package it.timewall.app.configuration;

public enum DashboardTheme {
    IVORY_PETROL(
            3,
            "theme_ivory_light",
            false,
            new Palette(
                    0xfff6f3ed,
                    0xff006b63,
                    0xff202925,
                    0xff506059,
                    0x80ffffff,
                    0x80ddefea,
                    0xffc6cec7),
            "ivory-petrol-light.png"),
    SLATE_AZURE(
            4,
            "theme_slate_light",
            false,
            new Palette(
                    0xfff1f4f9,
                    0xff315ba7,
                    0xff1f2937,
                    0xff4d5b70,
                    0x80ffffff,
                    0x80e3edfc,
                    0xffc5cfde),
            "slate-azure-light.png"),
    IVORY_PETROL_DARK(
            5,
            "theme_ivory_dark",
            true,
            new Palette(
                    0xff121917,
                    0xff80d5c6,
                    0xfff3efe5,
                    0xffb3c3ba,
                    0x801b2622,
                    0x80223c35,
                    0xff43574e),
            "ivory-petrol-dark.png"),
    SLATE_AZURE_DARK(
            6,
            "theme_slate_dark",
            true,
            new Palette(
                    0xff111823,
                    0xff9cbbff,
                    0xffedf2fc,
                    0xffafbed4,
                    0x801a2535,
                    0x80223854,
                    0xff465970),
            "slate-azure-dark.png"),
    APRICOT_TANGERINE_LIGHT(
            7,
            "theme_apricot_light",
            false,
            new Palette(
                    0xfffff6ee,
                    0xffb3440c,
                    0xff2c2724,
                    0xff554941,
                    0x80fffcf9,
                    0x80ffe2ca,
                    0xffd7bba8),
            "apricot-tangerine-light.png"),
    APRICOT_TANGERINE_DARK(
            8,
            "theme_apricot_dark",
            true,
            new Palette(
                    0xff211711,
                    0xffffab70,
                    0xfff3eee9,
                    0xffc7bbb2,
                    0x802d2018,
                    0x80482b1c,
                    0xff715440),
            "apricot-tangerine-dark.png"),
    POWDER_FUCHSIA_LIGHT(
            9,
            "theme_powder_light",
            false,
            new Palette(
                    0xfffff4f8,
                    0xffa62668,
                    0xff2c272b,
                    0xff544954,
                    0x80fffbfd,
                    0x80fbdcec,
                    0xffd4b7c8),
            "powder-fuchsia-light.png"),
    POWDER_FUCHSIA_DARK(
            10,
            "theme_powder_dark",
            true,
            new Palette(
                    0xff22141d,
                    0xffff93c4,
                    0xfff3edf1,
                    0xffc9bbc5,
                    0x80301d29,
                    0x80472437,
                    0xff715166),
            "powder-fuchsia-dark.png"),
    PORCELAIN_CHERRY_LIGHT(
            11,
            "theme_porcelain_light",
            false,
            new Palette(
                    0xfffaf6f4,
                    0xffae293a,
                    0xff2d2727,
                    0xff56494a,
                    0x80fffcfb,
                    0x80f9dfe2,
                    0xffd5b9bc),
            "porcelain-cherry-light.png"),
    PORCELAIN_CHERRY_DARK(
            12,
            "theme_porcelain_dark",
            true,
            new Palette(
                    0xff201619,
                    0xffff8c9a,
                    0xfff3eeee,
                    0xffc9bdbf,
                    0x802d2024,
                    0x8046262e,
                    0xff70525a),
            "porcelain-cherry-dark.png"),
    MINT_EMERALD_LIGHT(
            13,
            "theme_mint_light",
            false,
            new Palette(
                    0xfff2faf3,
                    0xff087044,
                    0xff252d28,
                    0xff48564d,
                    0x80fbfefb,
                    0x80d7f3de,
                    0xffaecbb6),
            "mint-emerald-light.png"),
    MINT_EMERALD_DARK(
            14,
            "theme_mint_dark",
            true,
            new Palette(
                    0xff111e17,
                    0xff72e6a2,
                    0xffedf3ee,
                    0xffb9c7be,
                    0x801a2a20,
                    0x80214432,
                    0xff456653),
            "mint-emerald-dark.png"),
    CUSTOM(15, "custom_theme", false, null, null);

    public final int id;
    public final String name;
    public final boolean dark;

    public final Palette palette;
    public final String backgroundAsset;

    /**
     * Definisce un tema con identificativo stabile, chiave della label, modalità chiara/scura e
     * palette; il tema personalizzato non ha una palette predefinita.
     *
     * @param id identificativo stabile persistito nelle preferenze
     * @param name nome della voce, del campo o della località
     * @param dark true per una presentazione scura
     * @param palette palette dei ruoli cromatici della schermata
     * @param backgroundAsset nome dell’immagine preimpostata, se presente
     */
    DashboardTheme(int id, String name, boolean dark, Palette palette, String backgroundAsset) {
        this.id = id;
        this.name = name;
        this.dark = dark;
        this.palette = palette;
        this.backgroundAsset = backgroundAsset;
    }

    /**
     * Risolve l’identificativo persistito del tema. I valori ritirati o sconosciuti restituiscono
     * Ardesia e Azzurro scuro senza riscrivere le preferenze.
     *
     * <p>IDs 0-2 are retired. Unknown values keep the established default.
     *
     * @param id identificativo del tema letto dalle preferenze
     * @return tema corrispondente oppure tema scuro predefinito per un ID sconosciuto
     */
    public static DashboardTheme fromSavedId(int id) {
        for (DashboardTheme theme : values()) if (theme.id == id) return theme;
        return SLATE_AZURE_DARK;
    }
}
