package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.endpoint.Endpoint;
import com.helenaramyan.webhooks.event.PaymentEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One attempt-tracked delivery of an event to an endpoint: the outbox row. */
@Entity
@Table(name = "delivery")
public class Delivery {

    static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private Endpoint endpoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private PaymentEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "leased_until")
    private Instant leasedUntil;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Delivery() {
        // JPA
    }

    private Delivery(UUID id, Endpoint endpoint, PaymentEvent event, Instant now) {
        this.id = id;
        this.endpoint = endpoint;
        this.event = event;
        this.status = DeliveryStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Delivery pending(UUID id, Endpoint endpoint, PaymentEvent event, Instant now) {
        return new Delivery(id, endpoint, event, now);
    }

    /** Claims the delivery for an attempt: counts it and leases the row until {@code leasedUntil}. */
    void beginAttempt(Instant now, Instant leasedUntil) {
        this.attempts++;
        this.leasedUntil = leasedUntil;
        this.updatedAt = now;
    }

    void succeed(Instant now, int statusCode) {
        this.status = DeliveryStatus.SUCCEEDED;
        this.lastStatusCode = statusCode;
        this.lastError = null;
        this.nextAttemptAt = null;
        this.leasedUntil = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    void scheduleRetry(Instant now, Instant nextAttemptAt, Integer statusCode, String error) {
        this.status = DeliveryStatus.FAILED;
        this.lastStatusCode = statusCode;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttemptAt;
        this.leasedUntil = null;
        this.updatedAt = now;
    }

    void die(Instant now, Integer statusCode, String error) {
        this.status = DeliveryStatus.DEAD;
        this.lastStatusCode = statusCode;
        this.lastError = truncate(error);
        this.nextAttemptAt = null;
        this.leasedUntil = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }

    public UUID getId() {
        return id;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public PaymentEvent getEvent() {
        return event;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLeasedUntil() {
        return leasedUntil;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
