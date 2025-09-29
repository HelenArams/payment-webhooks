package com.helenaramyan.webhooks.delivery;

import java.time.Duration;

/** Outcome of one HTTP attempt, independent of how the delivery is then scheduled. */
public record SendResult(Outcome outcome, Integer statusCode, String error, Duration elapsed) {

    public enum Outcome {
        /** 2xx. */
        SUCCESS,
        /** Any non-2xx status, including redirects, which are deliberately not followed. */
        HTTP_ERROR,
        TIMEOUT,
        /** DNS failure, connection refused, TLS handshake failure, reset mid-response, ... */
        CONNECTION_ERROR
    }

    public static SendResult success(int statusCode, Duration elapsed) {
        return new SendResult(Outcome.SUCCESS, statusCode, null, elapsed);
    }

    public static SendResult httpError(int statusCode, Duration elapsed) {
        return new SendResult(Outcome.HTTP_ERROR, statusCode, "HTTP " + statusCode, elapsed);
    }

    public static SendResult timeout(String error, Duration elapsed) {
        return new SendResult(Outcome.TIMEOUT, null, error, elapsed);
    }

    public static SendResult connectionError(String error, Duration elapsed) {
        return new SendResult(Outcome.CONNECTION_ERROR, null, error, elapsed);
    }

    public boolean succeeded() {
        return outcome == Outcome.SUCCESS;
    }
}
