package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.config.WebhookProperties;
import com.helenaramyan.webhooks.endpoint.EndpointRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies the result of an attempt to the delivery row: success, retry later, or dead. */
@Component
public class DeliveryOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryOutcomeHandler.class);

    private final DeliveryRepository deliveries;
    private final EndpointRepository endpoints;
    private final RetrySchedule schedule;
    private final Clock clock;
    private final int disableAfterDead;

    public DeliveryOutcomeHandler(
            DeliveryRepository deliveries,
            EndpointRepository endpoints,
            RetrySchedule schedule,
            WebhookProperties properties,
            Clock clock) {
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.schedule = schedule;
        this.clock = clock;
        this.disableAfterDead = properties.endpoints().disableAfterDead();
    }

    @Transactional
    public DeliveryStatus record(UUID deliveryId, SendResult result) {
        Delivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("claimed delivery " + deliveryId + " vanished"));
        Instant now = clock.instant();

        if (result.succeeded()) {
            delivery.succeed(now, result.statusCode());
            endpoints.resetConsecutiveDead(endpointId(delivery));
            log.info("delivery {} succeeded with {} after {} attempt(s) in {} ms",
                    delivery.getId(), result.statusCode(), delivery.getAttempts(), result.elapsed().toMillis());
            return delivery.getStatus();
        }

        Optional<Instant> next = schedule.nextAttemptAfter(delivery.getAttempts(), now);
        if (next.isPresent()) {
            delivery.scheduleRetry(now, next.get(), result.statusCode(), result.error());
            log.warn("delivery {} attempt {} failed ({}), retry at {}",
                    delivery.getId(), delivery.getAttempts(), result.error(), next.get());
        } else {
            delivery.die(now, result.statusCode(), result.error());
            log.error("delivery {} dead after {} attempts ({})", delivery.getId(), delivery.getAttempts(), result.error());
            UUID endpointId = endpointId(delivery);
            endpoints.incrementConsecutiveDead(endpointId);
            if (endpoints.disableIfOverThreshold(endpointId, disableAfterDead, now) == 1) {
                log.error("endpoint {} disabled after {} consecutive dead deliveries", endpointId, disableAfterDead);
            }
        }
        return delivery.getStatus();
    }

    private static UUID endpointId(Delivery delivery) {
        return delivery.getEndpoint().getId(); // proxy id, no fetch
    }
}
