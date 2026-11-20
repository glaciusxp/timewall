package it.timewall.audit;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import it.timewall.app.configuration.SettingsStore;
import it.timewall.app.data.weather.OpenMeteoClient;
import it.timewall.app.domain.model.WeatherConditions;
import it.timewall.app.domain.model.WeatherPlace;
import it.timewall.app.domain.weather.WeatherRefreshPolicy;
import it.timewall.app.platform.tls.OpenMeteoTrust;
import it.timewall.app.platform.tls.Tls12SocketFactory;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;

import javax.net.ssl.*;

/** Temporary same-signer instrumentation: uses production code, never exports personal data. */
public final class TlsProbe extends Instrumentation {
    private final StringBuilder report = new StringBuilder();
    private int failures;
    private String mode;

    /** Starts the audit worker; no application setting is modified by the runner. */
    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        mode = args == null ? "core" : args.getString("mode", "core");
        start();
    }

    /**
     * Runs a bounded set of production-client and TLS checks and returns only sanitized outcomes.
     */
    @Override
    public void onStart() {
        try {
            if ("cold".equals(mode)) {
                cold();
            } else if ("ui".equals(mode)) {
                ui();
            } else {
                core();
            }
        } catch (Throwable failure) {
            record("RUNNER", false, failure.getClass().getSimpleName());
        }
        Bundle result = new Bundle();
        result.putString("stream", report.toString());
        result.putInt("auditFailures", failures);
        finish(failures == 0 ? -1 : 0, result);
    }

    /**
     * Starts concurrent network paths before any legacy provider initialization in this process.
     */
    private void cold() throws Exception {
        SSLSocketFactory factory = HttpsURLConnection.getDefaultSSLSocketFactory();
        SSLContext context = SSLContext.getDefault();
        Provider[] providers = Security.getProviders();
        concurrentRequests(getTargetContext());
        record(
                "COLD_GLOBAL_FACTORY",
                factory == HttpsURLConnection.getDefaultSSLSocketFactory(),
                "same instance");
        record("COLD_GLOBAL_CONTEXT", context == SSLContext.getDefault(), "same instance");
        record(
                "COLD_PROVIDER_REGISTRY",
                Arrays.equals(providers, Security.getProviders()),
                "unchanged");
    }

    /** Reports a named check without exception messages, URLs, weather values or event content. */
    private void record(String name, boolean passed, String detail) {
        if (!passed) failures++;
        report.append(passed ? "PASS " : "FAIL ")
                .append(name)
                .append(" ")
                .append(detail)
                .append('\n');
    }

    /**
     * Exercises live Open-Meteo and geocoding with fixed public test coordinates, then local error
     * doubles.
     */
    private void core() throws Exception {
        Context context = getTargetContext();
        Map<String, ?> before =
                new HashMap<String, Object>(SettingsStore.open(context, 0).getAll());
        SSLSocketFactory defaults = HttpsURLConnection.getDefaultSSLSocketFactory();
        HostnameVerifier verifier = HttpsURLConnection.getDefaultHostnameVerifier();
        Provider[] providers = Security.getProviders();
        SSLContext originalContext = SSLContext.getDefault();
        record(
                "DEFAULT_FACTORY_BASELINE",
                true,
                "class="
                        + defaults.getClass().getName()
                        + " stableIdentity="
                        + (defaults == HttpsURLConnection.getDefaultSSLSocketFactory()));
        long started = System.nanoTime();
        try {
            WeatherConditions weather =
                    OpenMeteoClient.current(context, new WeatherPlace("Audit", "", 41.90, 12.50));
            record(
                    "LIVE_FORECAST",
                    !Double.isNaN(weather.celsius),
                    "production client; elapsedMs=" + (System.nanoTime() - started) / 1000000);
        } catch (Throwable failure) {
            record("LIVE_FORECAST", false, failure.getClass().getSimpleName());
        }
        try {
            record(
                    "LIVE_GEOCODING",
                    !OpenMeteoClient.search(context, "Rome").isEmpty(),
                    "fixed public query");
        } catch (Throwable failure) {
            record("LIVE_GEOCODING", false, failure.getClass().getSimpleName());
        }
        try {
            SSLSocketFactory factory =
                    new Tls12SocketFactory(OpenMeteoTrust.createSocketFactory(context));
            Socket transport = new Socket();
            transport.connect(new InetSocketAddress("api.open-meteo.com", 443), 8000);
            transport.setSoTimeout(8000);
            SSLSocket socket =
                    (SSLSocket) factory.createSocket(transport, "api.open-meteo.com", 443, true);
            try {
                socket.setSoTimeout(8000);
                socket.startHandshake();
                SSLSession session = socket.getSession();
                record(
                        "TLS_NEGOTIATION",
                        "TLSv1.2".equals(session.getProtocol()),
                        session.getProtocol() + " " + session.getCipherSuite());
                record(
                        "HOSTNAME_MATCH",
                        verifier.verify("api.open-meteo.com", session),
                        "default verifier");
                record(
                        "HOSTNAME_MISMATCH",
                        !verifier.verify("timewall-audit.invalid", session),
                        "default verifier rejects mismatch");
            } finally {
                socket.close();
            }
        } catch (Throwable failure) {
            record("TLS_NEGOTIATION", false, failure.getClass().getSimpleName());
        }
        rejectUntrusted(context);
        Context unavailable =
                new ContextWrapper(context) {
                    /**
                     * Simulates unavailable provider code without disabling the user's system
                     * services.
                     */
                    @Override
                    public Context createPackageContext(String name, int flags)
                            throws PackageManager.NameNotFoundException {
                        throw new PackageManager.NameNotFoundException("audit");
                    }
                };
        try {
            OpenMeteoTrust.createSocketFactory(unavailable);
            record("PROVIDER_UNAVAILABLE", false, "unexpected success");
        } catch (Exception expected) {
            record(
                    "PROVIDER_UNAVAILABLE",
                    true,
                    "fails closed: " + expected.getClass().getSimpleName());
        }
        Method get = OpenMeteoClient.class.getDeclaredMethod("get", Context.class, URL.class);
        get.setAccessible(true);
        checkHttp(get, context, "HTTP_200", 200, "{\"ok\":true}", false, false);
        checkHttp(get, context, "REDIRECT_REJECTED", 302, "{}", false, true);
        checkHttp(get, context, "HTTP_500", 500, "{}", false, true);
        checkHttp(get, context, "MALFORMED_JSON", 200, "AUDIT_ONLY_JSON_MARKER", false, true);
        char[] large = new char[65537];
        Arrays.fill(large, 'x');
        checkHttp(get, context, "BODY_LIMIT", 200, new String(large), false, true);
        checkHttp(get, context, "NETWORK_TIMEOUT", 200, "{}", true, true);
        concurrentRequests(context);
        String marker = "AUDIT_SECRET_ICAL_URL_COORDINATES";
        Exception payload = new org.json.JSONException(marker);
        it.timewall.app.platform.logging.WeatherLog.warn(
                it.timewall.app.platform.logging.WeatherLog.Operation.FORECAST, payload);
        String message =
                it.timewall.app.platform.logging.WeatherLog.message(
                        it.timewall.app.platform.logging.WeatherLog.Operation.FORECAST, payload);
        record(
                "SAFE_WEATHER_LOG",
                message.equals("operation=FORECAST category=INVALID_RESPONSE"),
                "allowlisted fields only; no payload or Throwable");
        boolean retry = true;
        for (int minutes : new int[] {15, 30, 60, 120})
            retry &=
                    WeatherRefreshPolicy.delayMillis(minutes, true) == 300000L
                            && WeatherRefreshPolicy.delayMillis(minutes, false) == minutes * 60000L;
        record("RETRY_POLICY", retry, "failure=5min; all four normal intervals");
        SSLSocketFactory after = HttpsURLConnection.getDefaultSSLSocketFactory();
        record(
                "GLOBAL_HTTPS_DEFAULTS",
                defaults == after
                        && defaults.getClass().equals(after.getClass())
                        && Arrays.equals(
                                defaults.getDefaultCipherSuites(), after.getDefaultCipherSuites())
                        && verifier == HttpsURLConnection.getDefaultHostnameVerifier(),
                "before="
                        + defaults.getClass().getName()
                        + " after="
                        + after.getClass().getName()
                        + " sameIdentity="
                        + (defaults == after));
        record(
                "PROVIDER_REGISTRY",
                Arrays.equals(providers, Security.getProviders()),
                "never modified");
        record("GLOBAL_SSL_CONTEXT", originalContext == SSLContext.getDefault(), "unchanged");
        record(
                "USER_PREFERENCES",
                before.equals(SettingsStore.open(context, 0).getAll()),
                "unchanged; values not exported");
    }

    /**
     * Runs actual weather, geocoding and a public Google iCal request concurrently without user
     * URLs.
     */
    private void concurrentRequests(final Context context) throws Exception {
        java.util.concurrent.ExecutorService workers =
                java.util.concurrent.Executors.newFixedThreadPool(3);
        java.util.List<java.util.concurrent.Callable<Boolean>> tasks =
                new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();
        tasks.add(
                new java.util.concurrent.Callable<Boolean>() {
                    /** Uses the production weather client with fixed public coordinates. */
                    public Boolean call() throws Exception {
                        return !Double.isNaN(
                                OpenMeteoClient.current(
                                                context, new WeatherPlace("Audit", "", 41.9, 12.5))
                                        .celsius);
                    }
                });
        tasks.add(
                new java.util.concurrent.Callable<Boolean>() {
                    /** Uses the production geocoding client with a fixed public city name. */
                    public Boolean call() throws Exception {
                        return !OpenMeteoClient.search(context, "Rome").isEmpty();
                    }
                });
        tasks.add(
                new java.util.concurrent.Callable<Boolean>() {
                    /**
                     * Downloads a public holidays feed through the production calendar TLS path.
                     */
                    public Boolean call() throws Exception {
                        byte[] body =
                                it.timewall.app.data.calendar.CalendarFeed.download(
                                        "https://calendar.google.com/calendar/ical/en.italian%23holiday%40group.v.calendar.google.com/public/basic.ics");
                        return new String(body, 0, Math.min(body.length, 80), "UTF-8")
                                .contains("BEGIN:VCALENDAR");
                    }
                });
        try {
            java.util.List<java.util.concurrent.Future<Boolean>> results =
                    workers.invokeAll(tasks, 30, java.util.concurrent.TimeUnit.SECONDS);
            String[] names = {
                "CONCURRENT_FORECAST", "CONCURRENT_GEOCODING", "CONCURRENT_GOOGLE_ICAL"
            };
            for (int index = 0; index < results.size(); index++) {
                try {
                    record(
                            names[index],
                            results.get(index).get(),
                            "production paths; public test inputs");
                } catch (Exception failure) {
                    record(names[index], false, failure.getClass().getSimpleName());
                }
            }
        } finally {
            workers.shutdownNow();
        }
    }

    /**
     * Confirms the production ISRG-scoped trust rejects a public self-signed server certificate.
     */
    private void rejectUntrusted(Context context) {
        HttpsURLConnection connection = null;
        try {
            connection =
                    (HttpsURLConnection)
                            new URL("https://self-signed.badssl.com/").openConnection();
            connection.setSSLSocketFactory(
                    new Tls12SocketFactory(OpenMeteoTrust.createSocketFactory(context)));
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.getResponseCode();
            record("UNTRUSTED_CERTIFICATE", false, "unexpected success");
        } catch (SSLHandshakeException expected) {
            record("UNTRUSTED_CERTIFICATE", true, "SSLHandshakeException");
        } catch (Exception inconclusive) {
            record(
                    "UNTRUSTED_CERTIFICATE",
                    false,
                    "inconclusive " + inconclusive.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Injects a fake HTTPS transport only through a URL handler; global networking stays unchanged.
     */
    private void checkHttp(
            Method get,
            Context context,
            String name,
            int status,
            String body,
            boolean timeout,
            boolean shouldFail)
            throws Exception {
        final FakeConnection connection =
                new FakeConnection(
                        new URL("https://timewall-audit.invalid/"), status, body, timeout);
        URL url =
                new URL(
                        null,
                        "https://timewall-audit.invalid/",
                        new URLStreamHandler() {
                            /** Supplies only this test's in-memory HTTPS connection. */
                            @Override
                            protected URLConnection openConnection(URL ignored) {
                                return connection;
                            }
                        });
        boolean failed = false;
        String kind = "success";
        try {
            get.invoke(null, context, url);
        } catch (InvocationTargetException expected) {
            failed = true;
            kind = expected.getCause().getClass().getSimpleName();
        }
        record(
                name,
                failed == shouldFail
                        && connection.disconnected
                        && !connection.getInstanceFollowRedirects()
                        && connection.getConnectTimeout() == 8000
                        && connection.getReadTimeout() == 8000,
                kind + "; timeout/redirect/cleanup checked");
        if ("MALFORMED_JSON".equals(name)) {
            try {
                get.invoke(null, context, url);
            } catch (InvocationTargetException expected) {
                record(
                        "JSON_EXCEPTION_RETAINS_PAYLOAD",
                        expected.getCause().toString().contains(body),
                        "synthetic marker only: raw exceptions can retain response text");
            }
        }
    }

    /**
     * Launches the actual dashboard and confirms the weather controller can be paused without
     * editing settings.
     */
    private void ui() throws Exception {
        Intent launch =
                getTargetContext()
                        .getPackageManager()
                        .getLaunchIntentForPackage("it.timewall.dashboard");
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity = startActivitySync(launch);
        waitForIdleSync();
        Thread.sleep(12000);
        record("DASHBOARD_LAUNCH", !activity.isFinishing(), "no settings edited");
        Field field = activity.getClass().getDeclaredField("weather");
        field.setAccessible(true);
        final Object weather = field.get(activity);
        Field task = weather.getClass().getDeclaredField("forecastTask");
        task.setAccessible(true);
        Field failed = weather.getClass().getDeclaredField("failed");
        failed.setAccessible(true);
        Field enabled = weather.getClass().getDeclaredField("enabled");
        enabled.setAccessible(true);
        Field conditions = weather.getClass().getDeclaredField("conditions");
        conditions.setAccessible(true);
        record(
                "CONFIGURED_WEATHER",
                !enabled.getBoolean(weather)
                        || (conditions.get(weather) != null && !failed.getBoolean(weather)),
                enabled.getBoolean(weather)
                        ? "enabled: check real result"
                        : "disabled by user: no request expected");
        runOnMainSync(
                new Runnable() {
                    /** Exercises the production pause path on the UI thread. */
                    public void run() {
                        try {
                            weather.getClass().getMethod("pause").invoke(weather);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        Field resumed = weather.getClass().getDeclaredField("resumed");
        resumed.setAccessible(true);
        record(
                "WEATHER_PAUSE",
                !resumed.getBoolean(weather) && task.get(weather) == null,
                "timer and active task stopped");
        final boolean[] disabledCheck = {false};
        runOnMainSync(
                new Runnable() {
                    /**
                     * Temporarily exercises disabled state in memory, restoring it without touching
                     * preferences.
                     */
                    public void run() {
                        try {
                            Field enabled = weather.getClass().getDeclaredField("enabled");
                            enabled.setAccessible(true);
                            boolean original = enabled.getBoolean(weather);
                            try {
                                enabled.setBoolean(weather, false);
                                weather.getClass().getMethod("resume").invoke(weather);
                                Method request =
                                        weather.getClass().getDeclaredMethod("requestWeather");
                                request.setAccessible(true);
                                request.invoke(weather);
                                Field future = weather.getClass().getDeclaredField("forecastTask");
                                future.setAccessible(true);
                                disabledCheck[0] = future.get(weather) == null;
                                weather.getClass().getMethod("pause").invoke(weather);
                            } finally {
                                enabled.setBoolean(weather, original);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        record(
                "DISABLED_WEATHER_NO_TASK",
                disabledCheck[0],
                "no request started; original in-memory state restored");
        runOnMainSync(
                new Runnable() {
                    /** Restores the controller to its initial foreground scheduling state. */
                    public void run() {
                        try {
                            weather.getClass().getMethod("resume").invoke(weather);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        record("WEATHER_RESUME", resumed.getBoolean(weather), "scheduling restored");
        unavailableController(activity);
    }

    /**
     * Verifies actual UI failure handling and retry using only the helper's disposable preferences.
     */
    private void unavailableController(final android.app.Activity real) throws Exception {
        final Object[] holder = new Object[1];
        final SharedPreferences isolated = getContext().getSharedPreferences("isolated-audit", 0);
        isolated.edit()
                .clear()
                .putBoolean("weather.enabled", true)
                .putString("weather.name", "Audit")
                .putLong("weather.lat", Double.doubleToLongBits(41.90))
                .putLong("weather.lon", Double.doubleToLongBits(12.50))
                .commit();
        runOnMainSync(
                new Runnable() {
                    /**
                     * Starts a disposable production controller whose provider code cannot be
                     * loaded.
                     */
                    public void run() {
                        MissingInstallerActivity fake = new MissingInstallerActivity(real);
                        android.widget.TextView[] labels = new android.widget.TextView[9];
                        for (int i = 0; i < labels.length; i++) {
                            labels[i] = new android.widget.TextView(real);
                            new android.widget.LinearLayout(real).addView(labels[i]);
                        }
                        it.timewall.app.ui.weather.DashboardWeather controller =
                                new it.timewall.app.ui.weather.DashboardWeather(
                                        fake,
                                        isolated,
                                        new android.view.View(real),
                                        labels[0],
                                        labels[1],
                                        labels[2],
                                        labels[3],
                                        labels[4],
                                        labels[5],
                                        labels[6],
                                        labels[7],
                                        labels[8],
                                        new android.widget.Button(real));
                        try {
                            Field data = controller.getClass().getDeclaredField("conditions");
                            data.setAccessible(true);
                            data.set(
                                    controller,
                                    new WeatherConditions(
                                            20,
                                            "unknown_weather",
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            0));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        holder[0] = controller;
                        controller.resume();
                    }
                });
        final Object controller = holder[0];
        Field failed = controller.getClass().getDeclaredField("failed");
        failed.setAccessible(true);
        for (int count = 0; count < 80; count++) {
            waitForIdleSync();
            if (failed.getBoolean(controller)) break;
            Thread.sleep(100);
        }
        final boolean[] outcomes = new boolean[3];
        runOnMainSync(
                new Runnable() {
                    /**
                     * Checks failed state, retained cached data and five-minute retry before
                     * removing all test state.
                     */
                    public void run() {
                        try {
                            Field failed = controller.getClass().getDeclaredField("failed");
                            failed.setAccessible(true);
                            Field next = controller.getClass().getDeclaredField("nextRefresh");
                            next.setAccessible(true);
                            Field data = controller.getClass().getDeclaredField("conditions");
                            data.setAccessible(true);
                            outcomes[0] = failed.getBoolean(controller);
                            outcomes[1] = data.get(controller) != null;
                            long remaining =
                                    next.getLong(controller)
                                            - android.os.SystemClock.elapsedRealtime();
                            outcomes[2] = remaining > 280000 && remaining <= 300000;
                            controller.getClass().getMethod("pause").invoke(controller);
                            controller.getClass().getMethod("destroy").invoke(controller);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        isolated.edit().clear().commit();
        record(
                "MISSING_PROVIDER_UI_NO_CRASH",
                outcomes[0],
                "disposable controller handled provider load failure");
        record("FAILURE_RETAINS_OLD_WEATHER", outcomes[1], "synthetic cached weather retained");
        record(
                "FAILURE_RETRY_SCHEDULED",
                outcomes[2],
                "production nextRefresh approximately five minutes");
    }

    /**
     * Activity context wrapper that simulates unavailable provider code without touching Google
     * Play Services.
     */
    private static final class MissingInstallerActivity extends android.app.Activity {
        /** Attaches the real Activity only as resource/service context for disposable widgets. */
        MissingInstallerActivity(android.app.Activity base) {
            attachBaseContext(base);
        }

        /** Rejects provider loading; no system package or device setting is modified. */
        @Override
        public Context createPackageContext(String name, int flags)
                throws PackageManager.NameNotFoundException {
            throw new PackageManager.NameNotFoundException("audit simulated unavailable provider");
        }
    }

    /** Minimal HTTPS test double, never used for live requests or certificate validation. */
    private static final class FakeConnection extends HttpsURLConnection {
        private final int status;
        private final String body;
        private final boolean timeout;
        boolean disconnected;

        /** Stores a controlled response without opening any socket. */
        FakeConnection(URL url, int status, String body, boolean timeout) {
            super(url);
            this.status = status;
            this.body = body;
            this.timeout = timeout;
        }

        /** Simulates either a bounded response or a network timeout. */
        public int getResponseCode() throws IOException {
            if (timeout) throw new SocketTimeoutException("audit");
            return status;
        }

        /** Exposes the test payload as UTF-8 bytes. */
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(body.getBytes("UTF-8"));
        }

        /** Records production cleanup, including on error paths. */
        public void disconnect() {
            disconnected = true;
        }

        /** No proxy is used by the in-memory transport. */
        public boolean usingProxy() {
            return false;
        }

        /** Intentionally does not open a connection. */
        public void connect() {}

        /** No TLS session exists for an in-memory HTTP behavior test. */
        public String getCipherSuite() {
            return "audit";
        }

        /** No client certificate is configured for the fake transport. */
        public Certificate[] getLocalCertificates() {
            return null;
        }

        /** No server certificate exists for the fake transport. */
        public Certificate[] getServerCertificates() {
            return null;
        }
    }
}
