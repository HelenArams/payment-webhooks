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

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = 500)
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
