package com.helenaramyan.webhooks.event;

import com.helenaramyan.webhooks.delivery.Delivery;
import com.helenaramyan.webhooks.delivery.DeliveryRepository;
import com.helenaramyan.webhooks.endpoint.Endpoint;
import com.helenaramyan.webhooks.endpoint.EndpointRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final PaymentEventRepository events;
    private final EndpointRepository endpoints;
    private final DeliveryRepository deliveries;
    private final Clock clock;

    public EventService(
            PaymentEventRepository events,
            EndpointRepository endpoints,
            DeliveryRepository deliveries,
            Clock clock) {
        this.events = events;
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    /**
     * Persists the event and fans it out into one pending delivery per subscribed endpoint, in a
     * single transaction. Nothing leaves the process here; the dispatcher picks deliveries up
     * asynchronously, so a slow or down receiver never affects the producer's request.
     */
    // TODO: accept an Idempotency-Key so a producer retrying POST /v1/events doesn't fan out twice
    @Transactional
    public EventAcceptedResponse emit(EmitEventRequest request) {
        Instant now = clock.instant();
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : now;
        PaymentEvent event = events.save(
                new PaymentEvent(UUID.randomUUID(), request.type(), request.payload().toString(), occurredAt, now));

        List<Endpoint> targets = endpoints.findEnabledSubscribedTo(event.getType());
        List<Delivery> queued = targets.stream()
                .map(endpoint -> Delivery.pending(UUID.randomUUID(), endpoint, event, now))
                .toList();
        deliveries.saveAll(queued);

        log.info("event {} type={} queued {} deliveries", event.getId(), event.getType(), queued.size());
        return new EventAcceptedResponse(event.getId(), event.getType(), event.getOccurredAt(), queued.size());
    }
}
