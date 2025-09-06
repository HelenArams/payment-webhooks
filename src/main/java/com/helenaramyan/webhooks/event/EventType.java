package com.helenaramyan.webhooks.event;

/**
 * Event type naming rules. Types are dotted, lower-case identifiers such as
 * {@code payment.authorized}, {@code payment.captured} or {@code payment.refunded}.
 */
public final class EventType {

    public static final String PATTERN = "^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$";
    public static final int MAX_LENGTH = 100;

    private EventType() {
    }
}
