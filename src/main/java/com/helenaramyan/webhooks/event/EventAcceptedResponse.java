package com.helenaramyan.webhooks.event;

import java.time.Instant;
import java.util.UUID;

/**
 * @param deliveries number of deliveries queued, one per enabled endpoint subscribed to the type
 */
public record EventAcceptedResponse(UUID eventId, String type, Instant occurredAt, int deliveries) {
}
