package it.timewall.app.domain.weather;

import org.json.JSONException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/** Fixed diagnostic categories: never include exception text, remote data or user input. */
public enum WeatherFailure {
    TIMEOUT,
    DNS,
    TLS_CERTIFICATE,
    TLS_HANDSHAKE,
    TLS_ERROR,
    TLS_PROVIDER_UNAVAILABLE,
    INVALID_RESPONSE,
    NETWORK,
    UNKNOWN;

    /**
     * Inspects at most eight causes without reading their messages; nested certificate errors take
     * precedence.
     */
    public static WeatherFailure classify(Throwable failure) {
        WeatherFailure result = UNKNOWN;
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            if (failure instanceof CertificateException
                    || failure instanceof SSLPeerUnverifiedException) return TLS_CERTIFICATE;
            if (failure instanceof SocketTimeoutException) return TIMEOUT;
            if (failure instanceof UnknownHostException) return DNS;
            if (failure instanceof JSONException) return INVALID_RESPONSE;
            if (failure instanceof SSLHandshakeException) result = TLS_HANDSHAKE;
            else if (failure instanceof SSLException && (result == UNKNOWN || result == NETWORK))
                result = TLS_ERROR;
            else if ((failure instanceof GeneralSecurityException
                            || failure instanceof ReflectiveOperationException
                            || failure instanceof LinkageError)
                    && (result == UNKNOWN || result == NETWORK)) result = TLS_PROVIDER_UNAVAILABLE;
            else if (failure instanceof IOException && result == UNKNOWN) result = NETWORK;
        }
        return result;
    }
}
