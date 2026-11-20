package it.timewall.app.configuration;

import android.content.Context;
import android.content.SharedPreferences;

/** Compatibility boundary for preferences written by every dashboard before 0.94. */
public final class SettingsStore {
    public static final String FILE_NAME = "it.timewall.diagnostics.DashboardActivity";

    /** Impedisce l’istanziazione della classe di sole operazioni statiche. */
    private SettingsStore() {}

    /**
     * Apre l’archivio storico delle preferenze della dashboard. Il nome resta indipendente dal
     * package dell’Activity per conservare le configurazioni precedenti.
     *
     * @param context contesto Android da cui leggere risorse o servizi
     * @param mode modalità di accesso alle preferenze richiesta dal chiamante
     * @return archivio di preferenze con il nome storico compatibile
     */
    public static SharedPreferences open(Context context, int mode) {
        return context.getSharedPreferences(FILE_NAME, mode);
    }
}
