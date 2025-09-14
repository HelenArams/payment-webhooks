package com.helenaramyan.webhooks.delivery;

public enum DeliveryStatus {
    /** Queued, no attempt made yet. */
    PENDING,
    /** Receiver answered 2xx. Terminal. */
    SUCCEEDED,
    /** Last attempt failed; a retry is scheduled at {@code nextAttemptAt}. */
    FAILED,
    /** Retries exhausted. Terminal unless replayed by an operator. */
    DEAD;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == DEAD;
    }
}
