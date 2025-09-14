package com.helenaramyan.webhooks.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened to a payment. The event is stored exactly once; one {@code Delivery}
 * per subscribed endpoint is created in the same transaction, which is what makes this an outbox.
 */
@Entity
@Table(name = "payment_event")
public class PaymentEvent {

    @Id
    private UUID id;

    @Column(nullable = false, length = EventType.MAX_LENGTH)
    private String type;

    /** Raw JSON object as submitted by the producer, stored verbatim. */
    @Column(nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentEvent() {
        // JPA
    }

    public PaymentEvent(UUID id, String type, String payload, Instant occurredAt, Instant createdAt) {
        this.id = id;
        this.type = type;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
