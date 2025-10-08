package com.helenaramyan.webhooks.delivery;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * webhook.attempts{outcome}       - one per HTTP attempt (success, http_error, timeout, connection_error)
 * webhook.attempt.duration{outcome}
 * webhook.deliveries{status}      - deliveries reaching succeeded / failed (retry scheduled) / dead
 */
@Component
public class DeliveryMetrics {

    private final Map<SendResult.Outcome, Counter> attempts = new EnumMap<>(SendResult.Outcome.class);
    private final Map<SendResult.Outcome, Timer> latency = new EnumMap<>(SendResult.Outcome.class);
    private final Map<DeliveryStatus, Counter> transitions = new EnumMap<>(DeliveryStatus.class);

    public DeliveryMetrics(MeterRegistry registry) {
        for (SendResult.Outcome outcome : SendResult.Outcome.values()) {
            String tag = outcome.name().toLowerCase();
            attempts.put(outcome, Counter.builder("webhook.attempts")
                    .description("HTTP attempts to receiver endpoints")
                    .tag("outcome", tag)
                    .register(registry));
            latency.put(outcome, Timer.builder("webhook.attempt.duration")
                    .description("Time from sending the request to receiving the status line")
                    .tag("outcome", tag)
                    .publishPercentileHistogram()
                    .register(registry));
        }
        for (DeliveryStatus status : DeliveryStatus.values()) {
            if (status == DeliveryStatus.PENDING) {
                continue;
            }
            transitions.put(status, Counter.builder("webhook.deliveries")
                    .description("Delivery state transitions after an attempt")
                    .tag("status", status.name().toLowerCase())
                    .register(registry));
        }
    }

    void recordAttempt(SendResult result) {
        attempts.get(result.outcome()).increment();
        latency.get(result.outcome()).record(result.elapsed());
    }

    void recordTransition(DeliveryStatus status) {
        Counter counter = transitions.get(status);
        if (counter != null) {
            counter.increment();
        }
    }
}
