package com.helenaramyan.webhooks.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID endpointId,
        UUID eventId,
        String eventType,
        DeliveryStatus status,
        int attempts,
        Instant nextAttemptAt,
        Integer lastStatusCode,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getEndpoint().getId(),
                delivery.getEvent().getId(),
                delivery.getEvent().getType(),
                delivery.getStatus(),
                delivery.getAttempts(),
                delivery.getNextAttemptAt(),
                delivery.getLastStatusCode(),
                delivery.getLastError(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt(),
                delivery.getCompletedAt());
    }
}
