package it.timewall.app.ui.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import it.timewall.app.localization.I18n;
import it.timewall.app.platform.images.ImageDocuments;
import it.timewall.app.ui.common.ViewFactory;

import java.util.concurrent.ExecutorService;

/** Owns background selection and validates images asynchronously until saved or disposed. */
public final class BackgroundImageEditor {

    private final Activity activity;
    private final ViewFactory views;
    private final ExecutorService worker;
    private boolean disposed;
    private static final int PICK_BACKGROUND = 101;
    private static final String BACKGROUND_URI = "dashboardBackgroundUri",
            BACKGROUND_NAME = "dashboardBackgroundName";

    /**
     * Segna l’editor come chiuso; i risultati asincroni ancora in arrivo non aggiorneranno più le
     * sue viste.
     */
    public void dispose() {
        disposed = true;
    }

    public final LinearLayout view;
    public final TextView status;
    public final TextView fileName;
    public final Button pick;
    public final Button remove;
    public final String original;
    public String selected;
    String selectedName;
    public boolean loading;
    public Runnable onChange = () -> {};

    /**
     * Inizializza la bozza dello sfondo dalle preferenze e crea i controlli di selezione/rimozione.
     * Recupera sul worker un eventuale nome file mancante senza salvare la bozza.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param views factory dei componenti coerenti con il tema
     * @param worker executor destinato alle operazioni bloccanti
     * @param preferences archivio delle preferenze da leggere o aggiornare
     */
    public BackgroundImageEditor(
            Activity activity,
            ViewFactory views,
            ExecutorService worker,
            SharedPreferences preferences) {
        this.activity = activity;
        this.views = views;
        this.worker = worker;
        view = views.column();
        status = views.secondaryLabel("", 14);
        fileName = views.secondaryLabel("", 14);
        pick = new Button(activity);
        remove = new Button(activity);
        original = preferences.getString(BACKGROUND_URI, "");
        selected = original;
        selectedName = preferences.getString(BACKGROUND_NAME, "");
        TextView heading = views.label(I18n.text(activity, "background"), 16, true);
        heading.setPadding(0, views.dp(12), 0, 0);
        view.addView(heading);
        LinearLayout buttons = new LinearLayout(activity);
        view.addView(buttons);
        pick.setText(I18n.text(activity, "choose_image"));
        pick.setOnClickListener(
                ignored -> {
                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("image/*")
                                    .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        activity.startActivityForResult(intent, PICK_BACKGROUND);
                    } catch (ActivityNotFoundException exception) {
                        status.setText(I18n.text(activity, "file_picker_unavailable"));
                    }
                });
        buttons.addView(pick);
        remove.setText(I18n.text(activity, "remove"));
        remove.setOnClickListener(
                ignored -> {
                    selected = "";
                    selectedName = "";
                    render();
                });
        buttons.addView(remove);
        view.addView(status);
        view.addView(fileName);
        render();
        if (!original.isEmpty() && selectedName.isEmpty()) {
            worker.execute(
                    () -> {
                        String name = ImageDocuments.displayName(activity, Uri.parse(original));
                        activity.runOnUiThread(
                                () -> {
                                    if (!disposed
                                            && !activity.isDestroyed()
                                            && original.equals(selected)
                                            && selectedName.isEmpty()) {
                                        selectedName = name;
                                        renderFileName();
                                    }
                                });
                    });
        }
    }

    /**
     * Verifica l’immagine scelta sul worker prima di accettarla nella bozza. Durante il caricamento
     * disabilita i pulsanti e il salvataggio; pubblica il risultato soltanto se l’editor è ancora
     * attivo.
     *
     * @param uri URI dell’immagine esposto dal provider documenti
     */
    public void select(Uri uri) {
        loading = true;
        pick.setEnabled(false);
        remove.setEnabled(false);
        status.setText(I18n.text(activity, "loading_image"));
        onChange.run();
        worker.execute(
                () -> {
                    Bitmap bitmap = ImageDocuments.readBackground(activity, uri);
                    boolean readable = bitmap != null;
                    if (readable) {
                        bitmap.recycle();
                    }
                    String name = readable ? ImageDocuments.displayName(activity, uri) : "";
                    activity.runOnUiThread(
                            () -> {
                                if (disposed || activity.isDestroyed()) {
                                    return;
                                }
                                loading = false;
                                pick.setEnabled(true);
                                if (readable) {
                                    selected = uri.toString();
                                    selectedName = name;
                                }
                                render();
                                if (!readable) {
                                    status.setText(I18n.text(activity, "image_failed"));
                                }
                            });
                });
    }

    /**
     * Aggiorna visibilità dei pulsanti, stato dell’immagine e nome del file, notificando al
     * contenitore che la validità della bozza può essere cambiata.
     */
    private void render() {
        remove.setEnabled(!loading);
        remove.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        status.setText(
                selected.isEmpty()
                        ? I18n.text(activity, "no_image")
                        : I18n.text(activity, "selected_image"));
        renderFileName();
        onChange.run();
    }

    /**
     * Mostra il nome del file selezionato e nasconde la label se il provider non ha fornito un
     * nome.
     */
    private void renderFileName() {
        fileName.setText(selectedName);
        fileName.setVisibility(selectedName.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Confronta l’URI selezionato con quello originale per stabilire se lo sfondo della bozza è
     * stato modificato.
     *
     * @return true se la bozza seleziona un URI diverso da quello salvato all’apertura
     */
    public boolean changed() {
        return !original.equals(selected);
    }

    /**
     * Acquisisce il permesso persistente di lettura della nuova immagine prima del salvataggio. Se
     * il provider lo rifiuta mostra l’errore e impedisce il salvataggio.
     *
     * @return true se non serve un nuovo permesso o se il provider lo ha concesso; false in caso di
     *     rifiuto
     */
    public boolean retainAccess() {
        if (changed() && !selected.isEmpty()) {
            try {
                activity.getContentResolver()
                        .takePersistableUriPermission(
                                Uri.parse(selected), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException exception) {
                status.setText(I18n.text(activity, "image_permission"));
                return false;
            }
        }
        return true;
    }

    /**
     * Scrive URI e nome dello sfondo nell’editor delle preferenze fornito, oppure rimuove entrambe
     * le chiavi quando lo sfondo è disattivato. Non applica da solo la transazione.
     *
     * @param editor transazione delle preferenze gestita dal chiamante
     */
    public void save(SharedPreferences.Editor editor) {
        if (selected.isEmpty()) {
            editor.remove(BACKGROUND_URI).remove(BACKGROUND_NAME);
        } else {
            editor.putString(BACKGROUND_URI, selected).putString(BACKGROUND_NAME, selectedName);
        }
    }

    /**
     * Dopo il salvataggio rilascia il permesso persistente della vecchia immagine se sostituita o
     * rimossa; tollera un permesso già revocato dal provider.
     */
    public void releasePrevious() {
        if (changed() && !original.isEmpty()) {
            try {
                activity.getContentResolver()
                        .releasePersistableUriPermission(
                                Uri.parse(original), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The document provider may have already removed access.
            }
        }
    }
}
