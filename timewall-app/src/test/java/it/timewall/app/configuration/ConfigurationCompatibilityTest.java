package it.timewall.app.configuration;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConfigurationCompatibilityTest {
    /**
     * Verifica che gli ID dei temi rimangano quelli storici e che gli ID sconosciuti usino il
     * fallback previsto.
     */
    @Test
    public void themeIdsRemainCompatible() {
        DashboardTheme[] expected = {
            DashboardTheme.IVORY_PETROL,
            DashboardTheme.SLATE_AZURE,
            DashboardTheme.IVORY_PETROL_DARK,
            DashboardTheme.SLATE_AZURE_DARK,
            DashboardTheme.APRICOT_TANGERINE_LIGHT,
            DashboardTheme.APRICOT_TANGERINE_DARK,
            DashboardTheme.POWDER_FUCHSIA_LIGHT,
            DashboardTheme.POWDER_FUCHSIA_DARK,
            DashboardTheme.PORCELAIN_CHERRY_LIGHT,
            DashboardTheme.PORCELAIN_CHERRY_DARK,
            DashboardTheme.MINT_EMERALD_LIGHT,
            DashboardTheme.MINT_EMERALD_DARK,
            DashboardTheme.CUSTOM
        };
        for (int i = 0; i < expected.length; i++) {
            assertEquals(i + 3, expected[i].id);
            assertSame(expected[i], DashboardTheme.fromSavedId(i + 3));
        }
        for (int id : new int[] {-1, 0, 1, 2, 999})
            assertSame(DashboardTheme.SLATE_AZURE_DARK, DashboardTheme.fromSavedId(id));
    }

    /**
     * Verifica che il nome dell’archivio preferenze resti quello dell’Activity precedente al
     * refactoring.
     */
    @Test
    public void preferenceArchiveRemainsCompatible() {
        assertEquals("it.timewall.diagnostics.DashboardActivity", SettingsStore.FILE_NAME);
    }

    /** Verifica conversione RGB/ARGB e conservazione del canale alpha nella serializzazione. */
    @Test
    public void parsesAndPreservesAlpha() {
        assertEquals(0xffabcdef, HexColor.parse("#abcdef"));
        assertEquals(0x80123456, HexColor.parse("#80123456"));
        assertEquals("#00123456", HexColor.format(HexColor.parse("#00123456")));
        assertEquals("#ABCDEF", HexColor.format(HexColor.parse("#ffabcdef")));
    }

    /** Verifica il rifiuto di colori assenti, abbreviati, malformati o con spazi. */
    @Test
    public void rejectsInvalidColors() {
        for (String value :
                new String[] {
                    null, "", "fff", "#fff", "#12345g", "#1234567", " #123456", "#123456789"
                }) assertFalse(HexColor.isValid(value));
    }

    /** Verifica che il parser sollevi IllegalArgumentException per un HEX non valido. */
    @Test(expected = IllegalArgumentException.class)
    public void invalidColorCannotBeSaved() {
        HexColor.parse("#zzzzzz");
    }

    /** Verifica nomi e numero delle scelte font, inclusa la voce di sistema senza file. */
    @Test
    public void fontFilenamesAreStable() {
        assertEquals("", DashboardFont.SYSTEM_ROBOTO.file);
        assertEquals("Exo2-Regular.otf", DashboardFont.EXO_2.file);
        assertEquals("Gontserrat-Regular.ttf", DashboardFont.GONTSERRAT.file);
        assertSame(
                DashboardFont.SYSTEM_ROBOTO,
                DashboardFont.fromSavedFile("AlteHaasGroteskRegular.ttf"));
        assertSame(DashboardFont.SYSTEM_ROBOTO, DashboardFont.fromSavedFile(null));
        for (DashboardFont font : DashboardFont.values()) {
            assertSame(font, DashboardFont.fromSavedFile(font.file));
        }
        assertEquals(11, DashboardFont.values().length);
    }
}
