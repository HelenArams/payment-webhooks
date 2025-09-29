package com.helenaramyan.webhooks.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.helenaramyan.webhooks.endpoint.Endpoint;
import com.helenaramyan.webhooks.endpoint.EndpointRepository;
import com.helenaramyan.webhooks.event.PaymentEvent;
import com.helenaramyan.webhooks.event.PaymentEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DeliveryClaimerTest {

    @Autowired
    private DeliveryClaimer claimer;

    @Autowired
    private DeliveryRepository deliveries;

    @Autowired
    private EndpointRepository endpoints;

    @Autowired
    private PaymentEventRepository events;

    @Autowired
    private Clock clock;

    private Endpoint endpoint;
    private PaymentEvent event;

    @BeforeEach
    void setUp() {
        deliveries.deleteAll();
        events.deleteAll();
        endpoints.deleteAll();
        Instant now = clock.instant();
        endpoint = endpoints.save(new Endpoint(
                UUID.randomUUID(), "https://merchant.example/hooks", "whsec_x", Set.of("payment.captured"), now));
        event = events.save(new PaymentEvent(
                UUID.randomUUID(), "payment.captured", "{\"paymentId\":\"pay_1\"}", now, now));
    }

    @Test
    void claimsDueDeliveriesOldestFirstAndLeasesThem() {
        Instant now = clock.instant();
        Delivery older = deliveries.save(Delivery.pending(UUID.randomUUID(), endpoint, event, now.minusSeconds(60)));
        Delivery newer = deliveries.save(Delivery.pending(UUID.randomUUID(), endpoint, event, now.minusSeconds(1)));
        Delivery future = deliveries.save(Delivery.pending(UUID.randomUUID(), endpoint, event, now.plusSeconds(600)));

        List<DeliveryTask> tasks = claimer.claim(10);

        assertThat(tasks).extracting(DeliveryTask::deliveryId).containsExactly(older.getId(), newer.getId());
        DeliveryTask first = tasks.get(0);
        assertThat(first.url()).isEqualTo("https://merchant.example/hooks");
        assertThat(first.secret()).isEqualTo("whsec_x");
        assertThat(first.webhookId()).isEqualTo(event.getId());
        assertThat(first.eventType()).isEqualTo("payment.captured");
        assertThat(first.body()).contains("\"data\":{\"paymentId\":\"pay_1\"}");
        assertThat(first.attempt()).isEqualTo(1);

        Delivery claimed = deliveries.findById(older.getId()).orElseThrow();
        assertThat(claimed.getAttempts()).isEqualTo(1);
        assertThat(claimed.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(claimed.getLeasedUntil()).isAfter(now.plus(Duration.ofSeconds(4)));
        assertThat(deliveries.findById(future.getId()).orElseThrow().getAttempts()).isZero();
    }

    @Test
    void leasedDeliveriesAreInvisibleToTheNextClaimUntilTheLeaseExpires() {
        deliveries.save(Delivery.pending(UUID.randomUUID(), endpoint, event, clock.instant().minusSeconds(5)));

        assertThat(claimer.claim(10)).hasSize(1);
        assertThat(claimer.claim(10)).isEmpty();
    }

    @Test
    void respectsBatchSize() {
        Instant now = clock.instant();
        for (int i = 0; i < 5; i++) {
            deliveries.save(Delivery.pending(UUID.randomUUID(), endpoint, event, now.minusSeconds(10 - i)));
        }

        assertThat(claimer.claim(3)).hasSize(3);
        assertThat(claimer.claim(3)).hasSize(2);
        assertThat(claimer.claim(3)).isEmpty();
    }
}
