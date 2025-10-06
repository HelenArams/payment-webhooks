package com.helenaramyan.webhooks.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
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

// one attempt per delivery, endpoint off after 2 dead in a row
@SpringBootTest(properties = {"webhooks.retry.max-attempts=1", "webhooks.endpoints.disable-after-dead=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class EndpointAutoDisableTest {

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

    private Endpoint endpoint;

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
        endpoint = endpoints.save(new Endpoint(
                UUID.randomUUID(), receiver.baseUrl() + "/hooks", "whsec_x", Set.of("payment.captured"),
                clock.instant()));
    }

    @Test
    void successResetsTheCounter() {
        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(500)));
        emit();
        dispatcher.runOnce();
        assertThat(reload().getConsecutiveDead()).isEqualTo(1);

        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(200)));
        emit();
        dispatcher.runOnce();

        assertThat(reload().getConsecutiveDead()).isZero();
        assertThat(reload().isEnabled()).isTrue();
    }

    @Test
    void disabledAfterThresholdAndQueuedDeliveriesWaitUntilReEnabled() throws Exception {
        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(500)));
        emit();
        emit();
        dispatcher.runOnce();

        Endpoint disabled = reload();
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(disabled.getDisabledAt()).isEqualTo(clock.instant());

        // already queued for this endpoint before it was disabled: held back, not burned
        Delivery queued = Delivery.pending(UUID.randomUUID(), disabled, paymentEvents.findAll().get(0), clock.instant());
        deliveries.save(queued);
        assertThat(dispatcher.runOnce()).isZero();

        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(200)));
        mvc.perform(post("/v1/endpoints/{id}/enable", endpoint.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.consecutiveDead").value(0));

        assertThat(dispatcher.runOnce()).isEqualTo(1);
        assertThat(deliveries.findById(queued.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
    }

    private void emit() {
        events.emit(new EmitEventRequest("payment.captured", mapper.createObjectNode().put("paymentId", "p"), null));
    }

    private Endpoint reload() {
        return endpoints.findById(endpoint.getId()).orElseThrow();
    }
}
