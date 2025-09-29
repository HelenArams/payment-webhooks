package com.helenaramyan.webhooks.event;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helenaramyan.webhooks.delivery.DeliveryRepository;
import com.helenaramyan.webhooks.endpoint.Endpoint;
import com.helenaramyan.webhooks.endpoint.EndpointRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EndpointRepository endpoints;

    @Autowired
    private DeliveryRepository deliveries;

    @Autowired
    private PaymentEventRepository events;

    private Endpoint captures;
    private Endpoint refunds;

    @BeforeEach
    void setUp() {
        deliveries.deleteAll();
        events.deleteAll();
        endpoints.deleteAll();
        Instant now = Instant.parse("2025-09-14T10:00:00Z");
        captures = endpoints.save(new Endpoint(
                UUID.randomUUID(), "https://a.example/hooks", "whsec_a", Set.of("payment.captured"), now));
        refunds = endpoints.save(new Endpoint(
                UUID.randomUUID(), "https://b.example/hooks", "whsec_b", Set.of("payment.refunded"), now));
        Endpoint disabled = new Endpoint(
                UUID.randomUUID(), "https://c.example/hooks", "whsec_c", Set.of("payment.captured"), now);
        disabled.setEnabled(false);
        endpoints.save(disabled);
    }

    @Test
    void emitQueuesOneDeliveryPerEnabledSubscribedEndpoint() throws Exception {
        mvc.perform(post("/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"payment.captured","payload":{"paymentId":"pay_42","amount":1999,"currency":"EUR"}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").isNotEmpty())
                .andExpect(jsonPath("$.type").value("payment.captured"))
                .andExpect(jsonPath("$.occurredAt").isNotEmpty())
                .andExpect(jsonPath("$.deliveries").value(1));

        mvc.perform(get("/v1/deliveries").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].endpointId").value(captures.getId().toString()))
                .andExpect(jsonPath("$[0].eventType").value("payment.captured"))
                .andExpect(jsonPath("$[0].attempts").value(0))
                .andExpect(jsonPath("$[0].nextAttemptAt").isNotEmpty());

        mvc.perform(get("/v1/deliveries").param("endpointId", refunds.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void eventWithNoSubscribersIsStillAccepted() throws Exception {
        mvc.perform(post("/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"payment.authorized","payload":{"paymentId":"pay_1"}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveries").value(0));
    }

    @Test
    void rejectsMissingPayloadAndBadType() throws Exception {
        mvc.perform(post("/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PaymentCaptured"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/v1/deliveries").param("status", "LOST"))
                .andExpect(status().isBadRequest());
    }
}
