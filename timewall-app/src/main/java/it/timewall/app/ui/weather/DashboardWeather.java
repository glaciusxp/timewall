package it.timewall.app.ui.weather;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import it.timewall.app.data.weather.OpenMeteoClient;
import it.timewall.app.data.weather.WeatherForecast;
import it.timewall.app.domain.model.WeatherConditions;
import it.timewall.app.domain.model.WeatherPlace;
import it.timewall.app.domain.weather.WeatherRefreshPolicy;
import it.timewall.app.localization.I18n;
import it.timewall.app.localization.TimeDisplay;
import it.timewall.app.platform.logging.WeatherLog;
import it.timewall.app.ui.common.ViewFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class DashboardWeather {
    private static final int[] REFRESH_MINUTES = {15, 30, 60, 120};
    private static final int DEFAULT_REFRESH_INDEX = 1;
    private static final String REFRESH_PREFERENCE = "weather.refreshMinutes";

    private final Activity activity;
    private final SharedPreferences preferences;
    private final View display;
    private final View refreshButton;
    private final int lastUpdateFormat, iconSize;
    private final TextView locationLabel,
            temperatureLabel,
            summaryLabel,
            statusLabel,
            lastUpdateLabel;
    private final TextView windLabel, apparentLabel, precipitationLabel, precipitationAmountLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService forecastWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService searchWorker = Executors.newSingleThreadExecutor();
    private final Runnable refresh = () -> requestWeather(false);
    private final SharedPreferences.OnSharedPreferenceChangeListener forecastChanged =
            (settings, key) -> {
                if (WeatherForecast.CACHE_KEY.equals(key)) restoreForecast();
            };
    private WeatherPlace place;
    private WeatherConditions conditions;
    private boolean enabled, fahrenheit, resumed, failed;
    private long nextRefresh, lastUpdate;
    private int refreshIndex;
    private int revision;
    private Future<?> forecastTask;
    private final Runnable onWeatherRendered;

    /**
     * Collega le viste del blocco meteo, ripristina località, unità e frequenza dalle preferenze e
     * prepara i worker. La richiesta viene pianificata soltanto quando il controller è attivo.
     *
     * @param activity Activity proprietaria delle viste e dei servizi Android
     * @param preferences archivio delle preferenze da leggere o aggiornare
     * @param display contenitore del blocco meteo
     * @param locationLabel label della località
     * @param temperatureLabel label della temperatura effettiva
     * @param summaryLabel label della descrizione delle condizioni
     * @param statusLabel label di stato o errore del meteo
     * @param windLabel label di velocità e direzione del vento
     * @param apparentLabel label della temperatura percepita
     * @param precipitationLabel label della probabilità di precipitazione
     * @param precipitationAmountLabel label dei millimetri di precipitazione
     * @param lastUpdateLabel label dell’ultima rilevazione o dell’aggiornamento in corso
     * @param lastUpdateFormat risorsa del formato localizzato dell’ultima rilevazione
     * @param refreshButton pulsante di aggiornamento da collegare al controller
     * @param iconSize dimensione di base dell’icona meteo in dp
     * @param onWeatherRendered callback eseguito dopo aver aggiornato le viste meteo
     */
    public DashboardWeather(
            Activity activity,
            SharedPreferences preferences,
            View display,
            TextView locationLabel,
            TextView temperatureLabel,
            TextView summaryLabel,
            TextView statusLabel,
            TextView windLabel,
            TextView apparentLabel,
            TextView precipitationLabel,
            TextView precipitationAmountLabel,
            TextView lastUpdateLabel,
            int lastUpdateFormat,
            View refreshButton,
            int iconSize,
            Runnable onWeatherRendered) {
        this.activity = activity;
        this.preferences = preferences;
        this.display = display;
        this.onWeatherRendered = onWeatherRendered;
        this.refreshButton = refreshButton;
        refreshButton.setOnClickListener(view -> requestWeather(true));
        this.locationLabel = locationLabel;
        this.lastUpdateLabel = lastUpdateLabel;
        this.lastUpdateFormat = lastUpdateFormat;
        this.iconSize = iconSize;
        this.windLabel = windLabel;
        this.apparentLabel = apparentLabel;
        this.precipitationAmountLabel = precipitationAmountLabel;
        this.precipitationLabel = precipitationLabel;
        this.temperatureLabel = temperatureLabel;
        this.summaryLabel = summaryLabel;
        this.statusLabel = statusLabel;
        place = WeatherForecast.location(preferences);
        enabled = preferences.getBoolean("weather.enabled", false);
        fahrenheit = preferences.getBoolean("weather.fahrenheit", false);
        refreshIndex = DEFAULT_REFRESH_INDEX;
        int savedMinutes =
                preferences.getInt(REFRESH_PREFERENCE, REFRESH_MINUTES[DEFAULT_REFRESH_INDEX]);
        for (int index = 0; index < REFRESH_MINUTES.length; index++) {
            if (REFRESH_MINUTES[index] == savedMinutes) {
                refreshIndex = index;
                break;
            }
        }
        restoreForecast();
    }

    /**
     * Riattiva lo scheduling del meteo quando la dashboard torna visibile; una richiesta scaduta
     * può partire subito.
     */
    public void resume() {
        if (!resumed) preferences.registerOnSharedPreferenceChangeListener(forecastChanged);
        resumed = true;
        restoreForecast();
    }

    /**
     * Sospende lo scheduling e invalida la richiesta meteo in corso, evitando che un risultato
     * obsoleto venga applicato alla schermata non attiva.
     */
    public void pause() {
        preferences.unregisterOnSharedPreferenceChangeListener(forecastChanged);
        resumed = false;
        handler.removeCallbacks(refresh);
        invalidateForecast();
    }

    /**
     * Sospende il controller e interrompe i worker di previsione e ricerca quando l’Activity viene
     * distrutta.
     */
    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        forecastWorker.shutdownNow();
        searchWorker.shutdownNow();
    }

    /**
     * Incrementa la revisione della previsione e annulla il task corrente; le callback già accodate
     * riconosceranno la revisione non più valida.
     */
    private void invalidateForecast() {
        revision++;
        if (forecastTask != null) {
            forecastTask.cancel(true);
            forecastTask = null;
            refreshButton.setEnabled(true);
            renderLastUpdate();
        }
    }

    /**
     * Rimuove la pianificazione precedente e programma il refresh secondo il tempo monotono,
     * soltanto se meteo abilitato, località presente e schermata attiva.
     */
    private void schedule() {
        handler.removeCallbacks(refresh);
        if (resumed && enabled && place != null && forecastTask == null) {
            handler.postDelayed(refresh, Math.max(0, nextRefresh - SystemClock.elapsedRealtime()));
        }
    }

    /**
     * Avvia una sola richiesta meteo sul worker se le condizioni di visibilità e configurazione lo
     * permettono. Scarta risposte di revisioni superate, salva l’ora del successo e pianifica
     * intervallo normale o retry.
     */
    private void requestWeather(boolean manual) {
        if (!resumed || !enabled || place == null || forecastTask != null) {
            return;
        }
        handler.removeCallbacks(refresh);
        refreshButton.setEnabled(false);
        WeatherPlace requested = place;
        int requestRevision = revision;
        forecastTask =
                forecastWorker.submit(
                        () -> {
                            boolean success =
                                    WeatherForecast.refresh(
                                            activity, preferences, requested, manual);
                            handler.post(
                                    () -> {
                                        if (revision != requestRevision) {
                                            return;
                                        }
                                        forecastTask = null;
                                        refreshButton.setEnabled(true);
                                        failed = !success;
                                        restoreForecast();
                                    });
                        });
        renderLastUpdate();
    }

    private void restoreForecast() {
        WeatherForecast forecast = WeatherForecast.cached(preferences, place);
        long timestamp = forecast == null ? 0 : forecast.updatedAt;
        if (timestamp != lastUpdate && timestamp > 0) failed = false;
        conditions = forecast == null ? null : forecast.conditions;
        lastUpdate = timestamp;
        long delay =
                failed
                        ? WeatherRefreshPolicy.delayMillis(REFRESH_MINUTES[refreshIndex], true)
                        : WeatherForecast.delay(preferences, forecast);
        nextRefresh = SystemClock.elapsedRealtime() + delay;
        render();
        schedule();
    }

    /**
     * Aggiorna visibilità, località, temperatura, descrizione, icona e misure opzionali con i dati
     * già ricevuti. Mantiene visibili i dati precedenti in caso di fallimento e non esegue
     * richieste di rete.
     */
    private void render() {
        int visibility = enabled && place != null ? View.VISIBLE : View.GONE;
        display.setVisibility(visibility);
        if (!enabled || place == null) {
            onWeatherRendered.run();
            return;
        }
        String summary =
                failed
                        ? I18n.text(activity, "weather_unavailable")
                        : I18n.text(activity, "loading_weather");
        temperatureLabel.setVisibility(conditions == null ? View.INVISIBLE : View.VISIBLE);
        WeatherText format = new WeatherText(activity, fahrenheit);
        showMetric(windLabel, conditions == null ? null : format.wind(conditions));
        Double apparent = conditions == null ? null : conditions.apparentCelsius;
        Double probability = conditions == null ? null : conditions.precipitationProbabilityPercent;
        Double amount = conditions == null ? null : conditions.precipitationMm;
        showMetric(apparentLabel, apparent == null ? null : format.temperature(apparent));
        showMetric(precipitationLabel, format.measure(probability, "%"));
        showMetric(precipitationAmountLabel, format.measure(amount, " mm"));
        if (conditions != null) {
            temperatureLabel.setText(format.temperature(conditions.celsius));
            summary = I18n.text(activity, conditions.descriptionKey);
        }
        locationLabel.setText(place.name);
        renderLastUpdate();
        summaryLabel.setText(summary);
        statusLabel.setText(I18n.text(activity, "refresh_failed"));
        statusLabel.setVisibility(failed && conditions != null ? View.VISIBLE : View.GONE);

        Drawable icon = null;
        if (conditions != null && conditions.icon != null) {
            int id =
                    activity.getResources()
                            .getIdentifier(conditions.icon, "drawable", activity.getPackageName());
            if (id != 0) {
                icon = activity.getResources().getDrawable(id).mutate();
                icon.setBounds(0, 0, dp(iconSize), dp(iconSize));
                icon.setColorFilter(
                        new PorterDuffColorFilter(
                                temperatureLabel.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));
            }
        }
        temperatureLabel.setCompoundDrawablePadding(icon == null ? 0 : dp(8));
        temperatureLabel.setCompoundDrawables(icon, null, null, null);
        onWeatherRendered.run();
    }

    /**
     * Aggiorna la label di una metrica e rende invisibile la relativa riga quando il valore è
     * assente, conservando lo spazio del layout.
     *
     * @param label label della voce nel selettore
     * @param text testo da visualizzare o analizzare
     */
    private static void showMetric(TextView label, String text) {
        label.setText(text);
        ((View) label.getParent())
                .setVisibility(text == null || text.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * Mostra l’ora dell’ultimo successo oppure lo stato di aggiornamento in corso mentre esiste una
     * richiesta attiva.
     */
    private void renderLastUpdate() {
        if (forecastTask != null) {
            lastUpdateLabel.setText(I18n.text(activity, "refreshing"));
            lastUpdateLabel.setVisibility(View.VISIBLE);
            return;
        }
        lastUpdateLabel.setVisibility(lastUpdate > 0 ? View.VISIBLE : View.GONE);
        if (lastUpdate > 0) {
            String time = TimeDisplay.time(activity, preferences, lastUpdate);
            lastUpdateLabel.setText(activity.getString(lastUpdateFormat, time));
        }
    }

    public final class Settings implements TextWatcher {
        private final CheckBox toggle = new CheckBox(activity);
        private final EditText query = new EditText(activity);
        private final Spinner unit = new Spinner(activity);
        private final Spinner frequency = new Spinner(activity);
        private final TextView selectedLabel = new TextView(activity);
        private final TextView searchStatus = new TextView(activity);
        private final ListView results = new ListView(activity);
        private final ArrayAdapter<String> labels =
                new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        private final Runnable search = this::searchPlaces;
        private WeatherPlace selected = place;
        private List<WeatherPlace> matches;
        private Future<?> searchTask;
        private int searchRevision;

        /**
         * Costruisce il form della bozza meteo con abilitazione, ricerca località, unità, frequenza
         * e indicazione del provider. La scelta di un risultato aggiorna la bozza senza salvarla.
         *
         * @param views factory dello stile delle impostazioni
         * @param locationIcon vista del glifo di localizzazione da aggiungere al form
         * @return vista costruita o aggiornata, pronta per il contenitore chiamante
         */
        public View createView(ViewFactory views, TextView locationIcon) {
            LinearLayout form = views.column();
            views.styleSettingInput(toggle);
            views.styleSettingInput(query);
            views.styleSettingInput(selectedLabel);

            views.addSettingHeading(form, I18n.text(activity, "weather_enable"), false);
            toggle.setText(I18n.text(activity, "enable_show"));
            toggle.setChecked(enabled);
            form.addView(toggle);

            views.addSettingHeading(form, I18n.text(activity, "place"), false);
            LinearLayout location = new LinearLayout(activity);
            location.setGravity(android.view.Gravity.CENTER_VERTICAL);
            location.addView(locationIcon);
            selectedLabel.setText(
                    selected == null ? I18n.text(activity, "no_place") : selected.label());
            location.addView(selectedLabel, new LinearLayout.LayoutParams(0, -2, 1));
            form.addView(location);
            query.setSingleLine(true);
            query.setHint(I18n.text(activity, "search_place"));
            query.addTextChangedListener(this);
            form.addView(query);
            searchStatus.setVisibility(View.GONE);
            form.addView(searchStatus);
            results.setAdapter(labels);
            results.setVisibility(View.GONE);
            form.addView(results, new LinearLayout.LayoutParams(-1, dp(144)));
            results.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        selected = matches.get(position);
                        query.setText("");
                        selectedLabel.setText(selected.label());
                        toggle.setChecked(true);
                    });

            views.addSettingHeading(form, I18n.text(activity, "temperature_unit"), false);
            ArrayAdapter<String> units =
                    views.settingChoices(
                            I18n.text(activity, "celsius"), I18n.text(activity, "fahrenheit"));
            unit.setAdapter(units);
            unit.setSelection(fahrenheit ? 1 : 0);
            form.addView(unit);

            views.addSettingHeading(form, I18n.text(activity, "refresh_frequency"), false);
            ArrayAdapter<String> frequencies =
                    views.settingChoices(
                            I18n.text(activity, "frequency_15"),
                            I18n.text(activity, "frequency_30"),
                            I18n.text(activity, "frequency_60"),
                            I18n.text(activity, "frequency_120"));
            frequency.setAdapter(frequencies);
            frequency.setSelection(refreshIndex);
            form.addView(frequency);

            TextView provider = views.secondaryLabel(I18n.text(activity, "provider"), 15);
            views.addSettingView(form, provider, false, 0);
            return form;
        }

        /**
         * Serializza le scelte effettive della bozza meteo in una stringa confrontabile. Il testo
         * transitorio della ricerca non è una configurazione salvata.
         *
         * @return rappresentazione confrontabile delle opzioni meteo della bozza
         */
        public String snapshot() {
            org.json.JSONArray values =
                    new org.json.JSONArray()
                            .put(toggle.isChecked())
                            .put(unit.getSelectedItemPosition())
                            .put(frequency.getSelectedItemPosition());
            if (selected != null) {
                values.put(selected.name)
                        .put(selected.detail)
                        .put(Double.doubleToLongBits(selected.latitude))
                        .put(Double.doubleToLongBits(selected.longitude));
            }
            return values.toString();
        }

        /**
         * Richiede una località quando il meteo è abilitato e mostra l’errore accanto alla ricerca;
         * il meteo disabilitato può essere salvato senza località.
         *
         * @return true se il meteo è disabilitato oppure è stata selezionata una località
         */
        public boolean validate() {
            if (toggle.isChecked() && selected == null) {
                query.setError(I18n.text(activity, "select_place"));
                return false;
            }
            return true;
        }

        /**
         * Persiste la bozza meteo e riallinea il controller. Invalida dati e richieste se cambia
         * località o abilitazione; altrimenti adegua la prossima scadenza alla nuova frequenza,
         * conservando il retry in errore.
         */
        public void save() {
            boolean changedPlace = place != selected;
            boolean changedEnabled = enabled != toggle.isChecked();
            refreshIndex = frequency.getSelectedItemPosition();
            place = selected;
            enabled = toggle.isChecked();
            fahrenheit = unit.getSelectedItemPosition() == 1;
            SharedPreferences.Editor editor =
                    preferences
                            .edit()
                            .putBoolean("weather.enabled", enabled)
                            .putBoolean("weather.fahrenheit", fahrenheit)
                            .putInt(REFRESH_PREFERENCE, REFRESH_MINUTES[refreshIndex]);
            if (place != null) {
                editor.putString("weather.name", place.name)
                        .putString("weather.detail", place.detail)
                        .putLong("weather.lat", Double.doubleToLongBits(place.latitude))
                        .putLong("weather.lon", Double.doubleToLongBits(place.longitude));
            }
            editor.apply();
            if (changedPlace || changedEnabled) {
                invalidateForecast();
                failed = false;
            }
            restoreForecast();
        }

        /**
         * Callback richiesta da TextWatcher lasciata vuota: le modifiche della query sono elaborate
         * in onTextChanged.
         *
         * @param text testo da visualizzare o analizzare
         * @param start indice iniziale del testo modificato
         * @param count conteggio per i plurali o numero di caratteri coinvolti nella callback
         * @param after numero di caratteri che sostituiranno il tratto corrente
         */
        @Override
        public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

        /**
         * Callback finale di TextWatcher lasciata vuota: la ricerca viene già pianificata in
         * onTextChanged.
         *
         * @param text testo da visualizzare o analizzare
         */
        @Override
        public void afterTextChanged(Editable text) {}

        /**
         * Annulla la ricerca precedente e azzera i risultati. Se la query contiene almeno tre
         * caratteri programma una nuova ricerca dopo 450 ms, evitando una chiamata per ogni tasto.
         *
         * @param text testo da visualizzare o analizzare
         * @param start indice iniziale del testo modificato
         * @param before numero di caratteri sostituiti
         * @param count conteggio per i plurali o numero di caratteri coinvolti nella callback
         */
        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {
            cancelSearch();
            labels.clear();
            results.setVisibility(View.GONE);
            setSearchStatus("");
            if (text.toString().trim().length() >= 3) {
                handler.postDelayed(search, 450);
            }
        }

        /**
         * Imposta il messaggio della ricerca e nasconde la label quando il testo è vuoto.
         *
         * @param message messaggio di stato; vuoto per nascondere la label
         */
        private void setSearchStatus(String message) {
            searchStatus.setText(message);
            searchStatus.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        }

        /**
         * Invalida la revisione della ricerca, rimuove il debounce pianificato e annulla il task
         * eventualmente in esecuzione.
         */
        public void cancelSearch() {
            searchRevision++;
            handler.removeCallbacks(search);
            if (searchTask != null) {
                searchTask.cancel(true);
                searchTask = null;
            }
        }

        /**
         * Interroga il geocoding sul worker e pubblica risultati o stato di errore sul thread UI
         * soltanto se la revisione corrisponde ancora alla query corrente.
         */
        private void searchPlaces() {
            String text = query.getText().toString().trim();
            int requested = searchRevision;
            searchStatus.setText(I18n.text(activity, "searching"));
            searchTask =
                    searchWorker.submit(
                            () -> {
                                List<WeatherPlace> found = null;
                                try {
                                    found = OpenMeteoClient.search(activity, text);
                                } catch (Exception | LinkageError exception) {
                                    WeatherLog.warn(
                                            WeatherLog.Operation.LOCATION_SEARCH, exception);
                                }
                                List<WeatherPlace> response = found;
                                handler.post(
                                        () -> {
                                            if (searchRevision != requested) {
                                                return;
                                            }
                                            if (response == null || response.isEmpty()) {
                                                setSearchStatus(
                                                        response == null
                                                                ? I18n.text(
                                                                        activity,
                                                                        "search_unavailable")
                                                                : I18n.text(
                                                                        activity, "no_results"));
                                                return;
                                            }
                                            matches = response;
                                            for (WeatherPlace match : matches) {
                                                labels.add(match.label());
                                            }
                                            setSearchStatus("");
                                            results.setVisibility(View.VISIBLE);
                                        });
                            });
        }
    }

    /**
     * Converte una dimensione in dp nei pixel del dispositivo.
     *
     * @param value valore da validare, formattare o mostrare
     * @return misura in pixel alla densità corrente
     */
    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
