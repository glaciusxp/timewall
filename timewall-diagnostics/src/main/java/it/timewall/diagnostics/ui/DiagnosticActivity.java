package it.timewall.diagnostics.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import it.timewall.diagnostics.calendar.IcalEventDump;
import it.timewall.diagnostics.network.CertificateProbe;
import it.timewall.diagnostics.network.OpenMeteoDiagnostic;
import it.timewall.diagnostics.platform.tls.OpenMeteoTrust;
import it.timewall.diagnostics.platform.tls.PlayServicesTls;
import it.timewall.diagnostics.platform.tls.Tls12SocketFactory;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

public final class DiagnosticActivity extends Activity {
    private static final String WEATHER_URL = OpenMeteoDiagnostic.URL;
    private static final String WTTR_URL = "https://wttr.in/41.90,12.50?m&format=%25t";
    private static final String MET_URL =
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=41.90&lon=12.50";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<String> calendarEvents = Collections.emptyList();
    private int calendarEventIndex;
    private TextView eventPosition, eventContent;
    private Button previousEvent, nextEvent;

    /**
     * Conserva il nome storico DiagnosticActivity dell’archivio delle preferenze anche dopo lo
     * spostamento della classe nel package ui.
     *
     * <p>Keep saved diagnostic inputs when the Activity implementation package changes.
     *
     * @param mode modalità di accesso alle preferenze richiesta dal chiamante
     * @return archivio di preferenze con il nome storico compatibile
     */
    @Override
    public android.content.SharedPreferences getPreferences(int mode) {
        return getSharedPreferences("DiagnosticActivity", mode);
    }

    /**
     * Costruisce la schermata diagnostica con input, pulsanti di prova dei provider e navigazione
     * dei blocchi iCal; ripristina le impostazioni locali e mantiene lo schermo acceso.
     *
     * @param state stato Android salvato; può essere null al primo avvio
     */
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setTextSize(22);
        title.setText("TimeWall Diagnostica 1.10");
        content.addView(title);

        TextView details = new TextView(this);
        details.setText(
                "Android "
                        + Build.VERSION.RELEASE
                        + " (API "
                        + Build.VERSION.SDK_INT
                        + ")\nTLS 1.2 · verifica certificati di sistema · schermo attivo");
        content.addView(details);

        Button weather = new Button(this);
        weather.setText("Prova Open-Meteo");
        content.addView(weather);
        TextView weatherResult = new TextView(this);
        weatherResult.setTextIsSelectable(true);
        content.addView(weatherResult);

        Button alternateWeather = new Button(this);
        alternateWeather.setText("Open-Meteo con Google Play Services");
        content.addView(alternateWeather);
        TextView alternateResult = new TextView(this);
        alternateResult.setTextIsSelectable(true);
        content.addView(alternateResult);
        alternateWeather.setOnClickListener(
                view -> runAlternateWeather(alternateWeather, alternateResult, false));

        Button rootedWeather = new Button(this);
        rootedWeather.setText("Dati Open-Meteo · Play Services + ISRG Root X1");
        content.addView(rootedWeather);
        TextView rootedResult = new TextView(this);
        rootedResult.setTextIsSelectable(true);
        content.addView(rootedResult);
        rootedWeather.setOnClickListener(
                view -> runAlternateWeather(rootedWeather, rootedResult, true));

        Button wttr = new Button(this);
        wttr.setText("Prova wttr.in");
        content.addView(wttr);
        TextView wttrResult = new TextView(this);
        content.addView(wttrResult);
        wttr.setOnClickListener(view -> runProbe(wttr, wttrResult, WTTR_URL, false));

        Button met = new Button(this);
        met.setText("Prova MET Norway");
        content.addView(met);
        TextView metResult = new TextView(this);
        content.addView(metResult);
        met.setOnClickListener(view -> askMetContact(met, metResult));
        Button inspectMet = new Button(this);
        inspectMet.setText("Certificati MET Norway");
        content.addView(inspectMet);
        TextView metCertificates = new TextView(this);
        metCertificates.setTextIsSelectable(true);
        content.addView(metCertificates);
        inspectMet.setOnClickListener(
                view -> {
                    inspectMet.setEnabled(false);
                    metCertificates.setText("Verifica in corso.");
                    worker.execute(
                            () -> {
                                String report = CertificateProbe.inspect(MET_URL);
                                runOnUiThread(
                                        () -> {
                                            if (!isDestroyed()) {
                                                metCertificates.setText(report);
                                                inspectMet.setEnabled(true);
                                            }
                                        });
                            });
                });

        EditText calendarUrl = new EditText(this);
        calendarUrl.setSingleLine(true);
        calendarUrl.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        calendarUrl.setSaveEnabled(false);
        calendarUrl.setHint("Indirizzo segreto iCal");
        content.addView(calendarUrl);

        Button calendar = new Button(this);
        calendar.setText("Prova Google Calendar");
        content.addView(calendar);
        TextView calendarResult = new TextView(this);
        content.addView(calendarResult);

        LinearLayout navigation = new LinearLayout(this);
        previousEvent = new Button(this);
        previousEvent.setText("Precedente");
        previousEvent.setOnClickListener(view -> showEvent(calendarEventIndex - 1));
        navigation.addView(previousEvent);
        eventPosition = new TextView(this);
        navigation.addView(eventPosition);
        nextEvent = new Button(this);
        nextEvent.setText("Successivo");
        nextEvent.setOnClickListener(view -> showEvent(calendarEventIndex + 1));
        navigation.addView(nextEvent);
        content.addView(navigation);
        eventContent = new TextView(this);
        eventContent.setTypeface(Typeface.MONOSPACE);
        eventContent.setTextIsSelectable(true);
        content.addView(eventContent);
        showEvent(0);

        weather.setOnClickListener(view -> runProbe(weather, weatherResult, WEATHER_URL, false));
        calendar.setOnClickListener(
                view -> {
                    String address = calendarUrl.getText().toString().trim();
                    if (!isGoogleCalendarUrl(address)) {
                        calendarResult.setText(
                                "Inserisci un URL iCal HTTPS di calendar.google.com.");
                        return;
                    }
                    calendarUrl.setText("");
                    calendarEvents = Collections.emptyList();
                    showEvent(0);
                    runProbe(calendar, calendarResult, address, true);
                });
    }

    /**
     * Mostra il blocco VEVENT richiesto e aggiorna contatore e pulsanti di navigazione. Gestisce
     * anche la lista vuota e gli indici fuori intervallo.
     *
     * @param index indice del calendario o del blocco da modificare; negativo quando previsto per
     *     un’aggiunta
     */
    private void showEvent(int index) {
        boolean hasEvents = !calendarEvents.isEmpty();
        calendarEventIndex = index;
        previousEvent.setEnabled(hasEvents && index > 0);
        nextEvent.setEnabled(hasEvents && index + 1 < calendarEvents.size());
        eventPosition.setText(hasEvents ? (index + 1) + " / " + calendarEvents.size() : "");
        eventContent.setText(hasEvents ? calendarEvents.get(index) : "");
    }

    /**
     * Verifica che l’indirizzo inserito appartenga al percorso HTTPS iCal di Google Calendar prima
     * di utilizzarlo nella specifica prova diagnostica.
     *
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @return true soltanto per una URL accettata dalla prova Google Calendar
     */
    private static boolean isGoogleCalendarUrl(String address) {
        try {
            URL url = new URL(address);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && "calendar.google.com".equalsIgnoreCase(url.getHost())
                    && (url.getPort() == -1 || url.getPort() == 443)
                    && url.getUserInfo() == null
                    && url.getRef() == null
                    && url.getPath().endsWith(".ics");
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Richiede il contatto da inserire nello User-Agent della prova MET Norway e avvia la sonda
     * solo con un valore accettato dal form.
     *
     * @param button pulsante da disabilitare durante la prova e riabilitare al completamento
     * @param result label del report della prova
     */
    private void askMetContact(Button button, TextView result) {
        EditText contact = new EditText(this);
        contact.setSingleLine(true);
        contact.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        contact.setSaveEnabled(false);
        contact.setHint("Email di contatto");
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("MET Norway")
                        .setMessage(
                                "MET Norway richiede un recapito nel User-Agent. L’email sarà"
                                    + " inviata al servizio per questa prova e non verrà salvata"
                                    + " dall’app.")
                        .setView(contact)
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Prova", null)
                        .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                        view -> {
                            String email = contact.getText().toString().trim();
                            if (email.length() > 254
                                    || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                contact.setError("Inserisci un indirizzo email valido.");
                                return;
                            }
                            dialog.dismiss();
                            runProbe(
                                    button,
                                    result,
                                    MET_URL,
                                    false,
                                    "TimeWallDiagnostics/1.10 (" + email + ")");
                        });
    }

    /**
     * Esegue sul worker la prova Open-Meteo con il provider Play Services e il trust selezionato.
     * Mostra report, suite TLS o errore e riabilita il pulsante soltanto se l’Activity è ancora
     * attiva.
     *
     * @param button pulsante da disabilitare durante la prova e riabilitare al completamento
     * @param result label del report della prova
     * @param useIsrgRoot true per usare la root ISRG dedicata con il provider alternativo
     */
    private void runAlternateWeather(Button button, TextView result, boolean useIsrgRoot) {
        button.setEnabled(false);
        result.setText("Richiesta in corso.");
        worker.execute(
                () -> {
                    String outcome;
                    try {
                        Tls12SocketFactory factory =
                                new Tls12SocketFactory(
                                        useIsrgRoot
                                                ? OpenMeteoTrust.createSocketFactory(this)
                                                : PlayServicesTls.createSocketFactory(this));
                        outcome =
                                "Provider GmsCore_OpenSSL · "
                                        + (useIsrgRoot ? "ISRG Root X1" : "CA di sistema")
                                        + "\n"
                                        + probe(
                                                WEATHER_URL,
                                                false,
                                                factory,
                                                new ArrayList<>(),
                                                null);
                        if (!useIsrgRoot) {
                            outcome += "\n\n" + CertificateProbe.inspect(WEATHER_URL, this);
                        }
                        outcome += factory.describeCipherSuites();
                    } catch (Exception | LinkageError exception) {
                        Throwable cause = exception.getCause();
                        outcome =
                                "Provider Google Play Services non disponibile.\n"
                                        + exception
                                        + (cause == null ? "" : "\n" + cause);
                    }
                    String report = outcome;
                    runOnUiThread(
                            () -> {
                                if (!isDestroyed()) {
                                    result.setText(report);
                                    button.setEnabled(true);
                                }
                            });
                });
    }

    /**
     * Disabilita il pulsante e avvia sul worker la prova HTTPS, con User-Agent facoltativo.
     * Pubblica l’esito sul thread UI e, per i calendari, aggiorna i blocchi iCal navigabili.
     *
     * @param button pulsante da disabilitare durante la prova e riabilitare al completamento
     * @param result label del report della prova
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @param calendar true per interpretare il corpo come iCal anziché risposta meteo
     */
    private void runProbe(Button button, TextView result, String address, boolean calendar) {
        runProbe(button, result, address, calendar, null);
    }

    /**
     * Disabilita il pulsante e avvia sul worker la prova HTTPS, con User-Agent facoltativo.
     * Pubblica l’esito sul thread UI e, per i calendari, aggiorna i blocchi iCal navigabili.
     *
     * @param button pulsante da disabilitare durante la prova e riabilitare al completamento
     * @param result label del report della prova
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @param calendar true per interpretare il corpo come iCal anziché risposta meteo
     * @param userAgent User-Agent della sonda; null se non richiesto
     */
    private void runProbe(
            Button button, TextView result, String address, boolean calendar, String userAgent) {
        button.setEnabled(false);
        result.setText("Richiesta in corso…");
        worker.execute(
                () -> {
                    List<String> events = new ArrayList<>();
                    String outcome = probe(address, calendar, events, userAgent);
                    runOnUiThread(
                            () -> {
                                if (!isDestroyed()) {
                                    result.setText(outcome);
                                    if (calendar) {
                                        calendarEvents = events;
                                        showEvent(0);
                                    }
                                    button.setEnabled(true);
                                }
                            });
                });
    }

    /**
     * Esegue la sonda HTTPS bloccante con TLS 1.2, timeout e redirect disabilitati. Restituisce
     * protocollo, suite, esito HTTP e report meteo o iCal; l’overload breve usa la factory di
     * sistema e aggiunge il riepilogo delle suite per Open-Meteo.
     *
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @param calendar true per interpretare il corpo come iCal anziché risposta meteo
     * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
     *     destinazione dei blocchi
     * @param userAgent User-Agent della sonda; null se non richiesto
     * @return report della sonda; i blocchi iCal sono anche inseriti nella lista events
     */
    private static String probe(
            String address, boolean calendar, List<String> events, String userAgent) {
        Tls12SocketFactory socketFactory =
                new Tls12SocketFactory(HttpsURLConnection.getDefaultSSLSocketFactory());
        String outcome = probe(address, calendar, socketFactory, events, userAgent);
        return WEATHER_URL.equals(address)
                ? outcome + socketFactory.describeCipherSuites()
                : outcome;
    }

    /**
     * Esegue la sonda HTTPS bloccante con TLS 1.2, timeout e redirect disabilitati. Restituisce
     * protocollo, suite, esito HTTP e report meteo o iCal; l’overload breve usa la factory di
     * sistema e aggiunge il riepilogo delle suite per Open-Meteo.
     *
     * @param address indirizzo HTTPS del feed o dell’endpoint da interrogare
     * @param calendar true per interpretare il corpo come iCal anziché risposta meteo
     * @param socketFactory factory TLS da applicare alla sola connessione della sonda
     * @param events occorrenze da elaborare o pubblicare; nella lettura iCal è la lista di
     *     destinazione dei blocchi
     * @param userAgent User-Agent della sonda; null se non richiesto
     * @return report della sonda; i blocchi iCal sono anche inseriti nella lista events
     */
    private static String probe(
            String address,
            boolean calendar,
            Tls12SocketFactory socketFactory,
            List<String> events,
            String userAgent) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(address).openConnection();
            connection.setSSLSocketFactory(socketFactory);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            if (userAgent != null) {
                connection.setRequestProperty("User-Agent", userAgent);
            }
            int status = connection.getResponseCode();
            if (status != 200) {
                String failure = "Connessione TLS 1.2 riuscita · HTTP " + status;
                if (WEATHER_URL.equals(address)) {
                    try (InputStream error = connection.getErrorStream()) {
                        if (error != null) {
                            char[] detail = new char[2048];
                            int length = new InputStreamReader(error, UTF_8).read(detail);
                            if (length > 0) {
                                failure += "\n" + new String(detail, 0, length);
                            }
                        }
                    }
                }
                return failure;
            }
            String success = "TLS 1.2 · HTTP 200 · " + connection.getCipherSuite() + "\n";
            try (BufferedReader input =
                    new BufferedReader(new InputStreamReader(connection.getInputStream(), UTF_8))) {
                if (calendar) {
                    return success + IcalEventDump.read(input, events);
                }
                StringBuilder body = new StringBuilder();
                char[] buffer = new char[2048];
                int maxBodyLength = MET_URL.equals(address) ? 512 * 1024 : 65536;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    body.append(buffer, 0, count);
                    if (body.length() > maxBodyLength) {
                        return success + "Risposta meteo troppo grande.";
                    }
                }
                if (WTTR_URL.equals(address)) {
                    String temperature = body.toString().trim();
                    return success
                            + (temperature.matches("[+-]?\\d+(?:\\.\\d+)?\\s*°C")
                                    ? "Temperatura a Roma: " + temperature
                                    : "Risposta meteo inattesa.");
                }
                try {
                    JSONObject json = new JSONObject(body.toString());
                    if (MET_URL.equals(address)) {
                        JSONObject firstForecast =
                                json.getJSONObject("properties")
                                        .getJSONArray("timeseries")
                                        .getJSONObject(0);
                        double temperature =
                                firstForecast
                                        .getJSONObject("data")
                                        .getJSONObject("instant")
                                        .getJSONObject("details")
                                        .getDouble("air_temperature");
                        return success
                                + "Temperatura prevista a Roma: "
                                + temperature
                                + " °C\n"
                                + "Validità: "
                                + firstForecast.getString("time")
                                + "\nDati meteo: MET Norway";
                    }
                    return success + OpenMeteoDiagnostic.describe(json);
                } catch (org.json.JSONException exception) {
                    return success + "JSON meteo inatteso.";
                }
            }
        } catch (IOException exception) {
            events.clear();
            return describe(exception, !calendar && userAgent == null, MET_URL.equals(address));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Trasforma un’eccezione di connessione in un report che distingue fallimenti TLS e di
     * certificato, includendo le cause utili alla diagnosi.
     *
     * @param exception errore di rete o TLS da descrivere
     * @param includeMessages true per includere i messaggi delle cause, limitati a 600 caratteri
     *     ciascuno
     * @param includeCertDetails true per includere i messaggi delle cause relative ai certificati
     * @return diagnosi testuale delle cause, con dettagli filtrati secondo gli argomenti
     */
    private static String describe(
            IOException exception, boolean includeMessages, boolean includeCertDetails) {
        StringBuilder details = new StringBuilder();
        boolean certificateError = false;
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (depth > 0) {
                details.append('\n');
            }
            details.append(cause.getClass().getSimpleName());
            boolean certificateCause =
                    cause instanceof CertificateException
                            || cause instanceof CertPathValidatorException;
            String message = cause.getMessage();
            if ((includeMessages || (includeCertDetails && certificateCause))
                    && message != null
                    && !message.isEmpty()) {
                details.append(": ").append(message.substring(0, Math.min(message.length(), 600)));
            }
            certificateError |= certificateCause;
        }
        if (certificateError) {
            return "Verifica del certificato fallita.\n" + details;
        }
        if (exception instanceof SSLException) {
            return "Errore TLS.\n" + details;
        }
        return "Richiesta non riuscita.\n" + details;
    }

    /**
     * Interrompe il worker delle prove e completa la distruzione dell’Activity senza lasciare nuovi
     * aggiornamenti della schermata.
     */
    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
