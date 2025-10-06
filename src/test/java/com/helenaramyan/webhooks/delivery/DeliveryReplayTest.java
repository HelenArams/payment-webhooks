package com.helenaramyan.webhooks.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helenaramyan.webhooks.endpoint.Endpoint;
import com.helenaramyan.webhooks.endpoint.EndpointRepository;
import com.helenaramyan.webhooks.event.EmitEventRequest;
import com.helenaramyan.webhooks.event.EventService;
import com.helenaramyan.webhooks.event.PaymentEventRepository;
import com.helenaramyan.webhooks.support.MutableClock;
import com.helenaramyan.webhooks.support.TestClockConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// short schedule so a delivery can actually die inside a test
@SpringBootTest(properties = {"webhooks.retry.max-attempts=2", "webhooks.retry.backoff=10s", "webhooks.retry.jitter=0"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class DeliveryReplayTest {

    private static final WireMockServer receiver = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DeliveryDispatcher dispatcher;

    @Autowired
    private EventService events;

    @Autowired
    private DeliveryRepository deliveries;

    @Autowired
    private EndpointRepository endpoints;

    @Autowired
    private PaymentEventRepository paymentEvents;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private MutableClock clock;

    @BeforeAll
    static void startReceiver() {
        receiver.start();
    }

    @AfterAll
    static void stopReceiver() {
        receiver.stop();
    }

    @BeforeEach
    void reset() {
        receiver.resetAll();
        deliveries.deleteAll();
        paymentEvents.deleteAll();
        endpoints.deleteAll();
        clock.set(TestClockConfig.START);
        endpoints.save(new Endpoint(
                UUID.randomUUID(), receiver.baseUrl() + "/hooks", "whsec_replay", Set.of("payment.refunded"),
                clock.instant()));
    }

    @Test
    void deadDeliveryCanBeReplayedAndThenSucceeds() throws Exception {
        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(503)));
        events.emit(new EmitEventRequest("payment.refunded", mapper.createObjectNode().put("paymentId", "pay_9"), null));

        dispatcher.runOnce();
        clock.advance(Duration.ofSeconds(10));
        dispatcher.runOnce();

        Delivery dead = deliveries.findAll().get(0);
        assertThat(dead.getStatus()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(dead.getAttempts()).isEqualTo(2);
        assertThat(dead.getNextAttemptAt()).isNull();
        assertThat(dead.getCompletedAt()).isNotNull();

        // receiver is fixed, operator replays
        receiver.resetAll();
        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(200)));
        clock.advance(Duration.ofHours(1));

        mvc.perform(post("/v1/deliveries/{id}/replay", dead.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.nextAttemptAt").value(clock.instant().toString()))
                .andExpect(jsonPath("$.lastStatusCode").value(503));

        assertThat(dispatcher.runOnce()).isEqualTo(1);

        Delivery replayed = deliveries.findById(dead.getId()).orElseThrow();
        assertThat(replayed.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(replayed.getAttempts()).isEqualTo(1);
        assertThat(replayed.getLastStatusCode()).isEqualTo(200);
        assertThat(receiver.findAll(postRequestedFor(urlEqualTo("/hooks")))).hasSize(1);
    }

    @Test
    void onlyDeadDeliveriesCanBeReplayed() throws Exception {
        events.emit(new EmitEventRequest("payment.refunded", mapper.createObjectNode().put("paymentId", "pay_1"), null));
        Delivery pending = deliveries.findAll().get(0);

        mvc.perform(post("/v1/deliveries/{id}/replay", pending.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "delivery " + pending.getId() + " is PENDING, only DEAD deliveries can be replayed"));

        mvc.perform(post("/v1/deliveries/{id}/replay", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
