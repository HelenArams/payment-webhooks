package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.config.WebhookProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a batch of due deliveries in one short transaction: lock the rows, count the attempt,
 * lease them, and snapshot what the sender needs. Nothing network-related happens while the
 * transaction (and the row locks) are held.
 */
@Component
public class DeliveryClaimer {

    private static final EnumSet<DeliveryStatus> CLAIMABLE = EnumSet.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED);

    private final DeliveryRepository deliveries;
    private final WebhookEnvelope envelope;
    private final WebhookProperties properties;
    private final Clock clock;

    public DeliveryClaimer(
            DeliveryRepository deliveries, WebhookEnvelope envelope, WebhookProperties properties, Clock clock) {
        this.deliveries = deliveries;
        this.envelope = envelope;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<DeliveryTask> claim(int limit) {
        Instant now = clock.instant();
        List<Delivery> due = deliveries.findDue(CLAIMABLE, now, PageRequest.of(0, limit));
        if (due.isEmpty()) {
            return List.of();
        }
        Instant leasedUntil = now.plus(properties.dispatcher().lease());
        List<UUID> ids = due.stream().map(Delivery::getId).toList();
        // one query initialises endpoint and event on the already-managed instances; iterate the
        // locked result rather than this one so the oldest-first order is kept
        Map<UUID, Delivery> loaded = deliveries.findAllWithEndpointAndEvent(ids).stream()
                .collect(Collectors.toMap(Delivery::getId, Function.identity()));
        return due.stream()
                .map(locked -> loaded.get(locked.getId()))
                .map(delivery -> {
                    delivery.beginAttempt(now, leasedUntil);
                    return new DeliveryTask(
                            delivery.getId(),
                            delivery.getEndpoint().getId(),
                            delivery.getEndpoint().getUrl(),
                            delivery.getEndpoint().getSecret(),
                            delivery.getEvent().getId(),
                            delivery.getEvent().getType(),
                            envelope.render(delivery.getEvent()),
                            delivery.getAttempts());
                })
                .toList();
    }
}
