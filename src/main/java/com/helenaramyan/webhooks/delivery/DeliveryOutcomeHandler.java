package com.helenaramyan.webhooks.delivery;

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
    private final RetrySchedule schedule;
    private final Clock clock;

    public DeliveryOutcomeHandler(DeliveryRepository deliveries, RetrySchedule schedule, Clock clock) {
        this.deliveries = deliveries;
        this.schedule = schedule;
        this.clock = clock;
    }

    @Transactional
    public DeliveryStatus record(UUID deliveryId, SendResult result) {
        Delivery delivery = deliveries.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("claimed delivery " + deliveryId + " vanished"));
        Instant now = clock.instant();

        if (result.succeeded()) {
            delivery.succeed(now, result.statusCode());
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
        }
        return delivery.getStatus();
    }
}
