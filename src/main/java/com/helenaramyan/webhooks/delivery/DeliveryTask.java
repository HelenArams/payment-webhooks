package com.helenaramyan.webhooks.delivery;

import java.util.UUID;

/**
 * Everything the sender needs, snapshotted inside the claiming transaction so no entity or lazy
 * association leaks into the HTTP call.
 *
 * @param webhookId value of the {@code Webhook-Id} header: the event id, identical on every retry
 *                  so receivers can de-duplicate
 * @param body      exact JSON bytes to sign and send
 */
public record DeliveryTask(
        UUID deliveryId,
        UUID endpointId,
        String url,
        String secret,
        UUID webhookId,
        String eventType,
        String body,
        int attempt) {
}
