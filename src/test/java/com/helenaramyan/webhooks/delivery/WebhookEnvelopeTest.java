package com.helenaramyan.webhooks.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.helenaramyan.webhooks.event.PaymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookEnvelopeTest {

    private final WebhookEnvelope envelope = new WebhookEnvelope(new ObjectMapper());

    @Test
    void rendersStableCompactJsonWithPayloadUnderData() {
        PaymentEvent event = new PaymentEvent(
                UUID.fromString("a0f1e2d3-1111-2222-3333-444455556666"),
                "payment.captured",
                "{\"paymentId\":\"pay_42\",\"amount\":1999,\"currency\":\"EUR\"}",
                Instant.parse("2025-09-29T20:15:30.123456Z"),
                Instant.parse("2025-09-29T20:15:31Z"));

        String body = envelope.render(event);

        assertThat(body).isEqualTo(
                "{\"id\":\"a0f1e2d3-1111-2222-3333-444455556666\",\"type\":\"payment.captured\","
                        + "\"occurredAt\":\"2025-09-29T20:15:30.123456Z\","
                        + "\"data\":{\"paymentId\":\"pay_42\",\"amount\":1999,\"currency\":\"EUR\"}}");
        assertThat(envelope.render(event)).isEqualTo(body);
    }
}
