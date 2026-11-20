package it.timewall.app.domain.weather;

import static org.junit.Assert.*;

import it.timewall.app.platform.logging.WeatherLog;

import org.json.JSONException;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;

public class WeatherFailureTest {
    /**
     * Verifies category selection for the expected transport, TLS, provider and parser failure
     * paths.
     */
    @Test
    public void classifiesKnownFailures() {
        assertEquals(WeatherFailure.TIMEOUT, WeatherFailure.classify(new SocketTimeoutException()));
        assertEquals(WeatherFailure.DNS, WeatherFailure.classify(new UnknownHostException()));
        assertEquals(
                WeatherFailure.TLS_PROVIDER_UNAVAILABLE,
                WeatherFailure.classify(new GeneralSecurityException()));
        assertEquals(
                WeatherFailure.TLS_PROVIDER_UNAVAILABLE,
                WeatherFailure.classify(new UnsatisfiedLinkError()));
        assertEquals(
                WeatherFailure.INVALID_RESPONSE,
                WeatherFailure.classify(new JSONException("invalid")));
        assertEquals(WeatherFailure.NETWORK, WeatherFailure.classify(new IOException()));
        assertEquals(WeatherFailure.UNKNOWN, WeatherFailure.classify(null));
    }

    /** Preserves certificate-specific diagnosis even when wrapped in an SSL handshake exception. */
    @Test
    public void findsNestedCertificateFailure() {
        SSLHandshakeException wrapper = new SSLHandshakeException("outer");
        wrapper.initCause(new CertificateException("inner"));
        assertEquals(WeatherFailure.TLS_CERTIFICATE, WeatherFailure.classify(wrapper));
    }

    /**
     * Checks both operations against URL, coordinate, event and JSON markers without exposing any
     * in the message.
     */
    @Test
    public void logMessagesNeverIncludePayload() {
        String secret =
                "https://calendar.invalid/private/AUDIT_SECRET?latitude=41.90&longitude=12.50"
                        + " event=AUDIT_TITLE";
        Throwable[] failures = {
            new JSONException(secret),
            new IOException(secret, new CertificateException(secret)),
            new IllegalStateException(secret)
        };
        for (WeatherLog.Operation operation : WeatherLog.Operation.values()) {
            for (Throwable failure : failures) {
                String result = WeatherLog.message(operation, failure);
                assertTrue(result.matches("operation=(FORECAST|LOCATION_SEARCH) category=[A-Z_]+"));
                assertFalse(result.contains("AUDIT_"));
                assertFalse(result.contains("https://"));
                assertFalse(result.contains("latitude"));
                assertFalse(result.contains("41.90"));
            }
        }
    }

    /** Ensures an unusual cyclic cause chain cannot hang the diagnostic path. */
    @Test(timeout = 1000)
    public void boundsCyclicCauses() {
        Exception first = new Exception("a");
        Exception second = new Exception("b");
        first.initCause(second);
        second.initCause(first);
        assertEquals(WeatherFailure.UNKNOWN, WeatherFailure.classify(first));
    }
}
