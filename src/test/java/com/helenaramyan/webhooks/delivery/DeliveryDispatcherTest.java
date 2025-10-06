package com.helenaramyan.webhooks.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.helenaramyan.webhooks.endpoint.Endpoint;
import com.helenaramyan.webhooks.endpoint.EndpointRepository;
import com.helenaramyan.webhooks.event.EmitEventRequest;
import com.helenaramyan.webhooks.event.EventAcceptedResponse;
import com.helenaramyan.webhooks.event.EventService;
import com.helenaramyan.webhooks.event.PaymentEventRepository;
import com.helenaramyan.webhooks.signature.WebhookSigner;
import com.helenaramyan.webhooks.support.MutableClock;
import com.helenaramyan.webhooks.support.TestClockConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class DeliveryDispatcherTest {

    private static final String SECRET = "whsec_dispatcher-test-secret";
    private static final WireMockServer receiver = new WireMockServer(WireMockConfiguration.options().dynamicPort());

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
    private WebhookSigner signer;

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
    }

    @Test
    void deliversSignedPayloadAndMarksDeliverySucceeded() {
        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(204)));
        Endpoint endpoint = subscribe(receiver.baseUrl() + "/hooks");
        EventAcceptedResponse accepted = emitCapture("pay_42");

        assertThat(dispatcher.runOnce()).isEqualTo(1);

        Delivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastStatusCode()).isEqualTo(204);
        assertThat(delivery.getLastError()).isNull();
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getLeasedUntil()).isNull();
        assertThat(delivery.getCompletedAt()).isEqualTo(clock.instant());
        assertThat(delivery.getEndpoint().getId()).isEqualTo(endpoint.getId());

        List<LoggedRequest> requests = receiver.findAll(postRequestedFor(urlEqualTo("/hooks")));
        assertThat(requests).hasSize(1);
        LoggedRequest request = requests.get(0);
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        assertThat(request.getHeader("User-Agent")).isEqualTo("payment-webhooks/0.1");
        assertThat(request.getHeader(WebhookSigner.HEADER_ID)).isEqualTo(accepted.eventId().toString());
        assertThat(request.getHeader(WebhookSigner.HEADER_TIMESTAMP))
                .isEqualTo(Long.toString(clock.instant().getEpochSecond()));

        String body = request.getBodyAsString();
        assertThat(body).startsWith("{\"id\":\"" + accepted.eventId() + "\",\"type\":\"payment.captured\",");
        assertThat(body).endsWith("\"data\":{\"paymentId\":\"pay_42\",\"amount\":1999,\"currency\":\"EUR\"}}");
        long timestamp = Long.parseLong(request.getHeader(WebhookSigner.HEADER_TIMESTAMP));
        String signature = request.getHeader(WebhookSigner.HEADER_SIGNATURE);
        assertThat(signature).startsWith("v1=");
        assertThat(signer.verify(SECRET, timestamp, body, signature, clock.instant(), Duration.ofMinutes(5))).isTrue();
        assertThat(signer.verify("whsec_wrong", timestamp, body, signature, clock.instant(), Duration.ofMinutes(5)))
                .isFalse();
    }

    @Test
    void serverErrorSchedulesRetryWithBackoffAndRetryCarriesTheSameWebhookIdAndBody() {
        receiver.stubFor(post(urlEqualTo("/hooks")).inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        receiver.stubFor(post(urlEqualTo("/hooks")).inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200)));
        subscribe(receiver.baseUrl() + "/hooks");
        emitCapture("pay_7");

        assertThat(dispatcher.runOnce()).isEqualTo(1);

        Delivery afterFirst = onlyDelivery();
        assertThat(afterFirst.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(afterFirst.getAttempts()).isEqualTo(1);
        assertThat(afterFirst.getLastStatusCode()).isEqualTo(500);
        assertThat(afterFirst.getLastError()).isEqualTo("HTTP 500");
        assertThat(afterFirst.getLeasedUntil()).isNull();
        // first backoff step is 10s with +/-20% jitter
        assertThat(afterFirst.getNextAttemptAt())
                .isBetween(clock.instant().plusSeconds(8), clock.instant().plusSeconds(12));

        // not due yet: nothing happens
        assertThat(dispatcher.runOnce()).isZero();

        clock.advance(Duration.ofSeconds(12));
        assertThat(dispatcher.runOnce()).isEqualTo(1);

        Delivery afterSecond = onlyDelivery();
        assertThat(afterSecond.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(afterSecond.getAttempts()).isEqualTo(2);
        assertThat(afterSecond.getLastStatusCode()).isEqualTo(200);
        assertThat(afterSecond.getLastError()).isNull();

        List<LoggedRequest> requests = receiver.findAll(postRequestedFor(urlEqualTo("/hooks")));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getHeader(WebhookSigner.HEADER_ID))
                .isEqualTo(requests.get(1).getHeader(WebhookSigner.HEADER_ID));
        assertThat(requests.get(0).getBodyAsString()).isEqualTo(requests.get(1).getBodyAsString());
        assertThat(requests.get(0).getHeader(WebhookSigner.HEADER_TIMESTAMP))
                .isNotEqualTo(requests.get(1).getHeader(WebhookSigner.HEADER_TIMESTAMP));
        assertThat(requests.get(0).getHeader(WebhookSigner.HEADER_SIGNATURE))
                .isNotEqualTo(requests.get(1).getHeader(WebhookSigner.HEADER_SIGNATURE));
    }

    @Test
    void slowReceiverTimesOutAndIsRetried() {
        // test profile sets the read timeout to 1s
        receiver.stubFor(post(urlEqualTo("/hooks")).willReturn(aResponse().withStatus(200).withFixedDelay(2_500)));
        subscribe(receiver.baseUrl() + "/hooks");
        emitCapture("pay_slow");

        assertThat(dispatcher.runOnce()).isEqualTo(1);

        Delivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastStatusCode()).isNull();
        assertThat(delivery.getLastError()).contains("HttpTimeoutException");
        assertThat(delivery.getNextAttemptAt()).isAfter(clock.instant());
    }

    @Test
    void redirectsAreNotFollowed() {
        receiver.stubFor(post(urlEqualTo("/hooks"))
                .willReturn(aResponse().withStatus(307).withHeader("Location", receiver.baseUrl() + "/elsewhere")));
        receiver.stubFor(post(urlEqualTo("/elsewhere")).willReturn(aResponse().withStatus(200)));
        subscribe(receiver.baseUrl() + "/hooks");
        emitCapture("pay_redirect");

        dispatcher.runOnce();

        Delivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getLastStatusCode()).isEqualTo(307);
        assertThat(receiver.findAll(postRequestedFor(urlEqualTo("/elsewhere")))).isEmpty();
    }

    @Test
    void unreachableReceiverIsAConnectionErrorNotACrash() {
        subscribe("http://127.0.0.1:1/hooks");
        emitCapture("pay_refused");

        assertThat(dispatcher.runOnce()).isEqualTo(1);

        Delivery delivery = onlyDelivery();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getLastStatusCode()).isNull();
        assertThat(delivery.getLastError()).isNotBlank();
    }

    @Test
    void nothingDueIsANoOp() {
        assertThat(dispatcher.runOnce()).isZero();
    }

    private Endpoint subscribe(String url) {
        return endpoints.save(new Endpoint(
                UUID.randomUUID(), url, SECRET, Set.of("payment.captured"), clock.instant()));
    }

    private EventAcceptedResponse emitCapture(String paymentId) {
        var payload = mapper.createObjectNode()
                .put("paymentId", paymentId)
                .put("amount", 1999)
                .put("currency", "EUR");
        return events.emit(new EmitEventRequest("payment.captured", payload, null));
    }

    private Delivery onlyDelivery() {
        List<Delivery> all = deliveries.findAll();
        assertThat(all).hasSize(1);
        return deliveries.findAllWithEndpointAndEvent(List.of(all.get(0).getId())).get(0);
    }
}
