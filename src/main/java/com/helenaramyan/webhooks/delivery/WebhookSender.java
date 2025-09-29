package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.config.WebhookProperties;
import com.helenaramyan.webhooks.signature.WebhookSigner;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Performs one signed HTTP POST and classifies the result. Never throws for receiver behaviour. */
@Component
public class WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);

    private final RestClient restClient;
    private final WebhookSigner signer;
    private final Clock clock;
    private final String userAgent;

    public WebhookSender(RestClient webhookRestClient, WebhookSigner signer, WebhookProperties properties, Clock clock) {
        this.restClient = webhookRestClient;
        this.signer = signer;
        this.clock = clock;
        this.userAgent = properties.dispatcher().userAgent();
    }

    public SendResult send(DeliveryTask task) {
        long timestamp = clock.instant().getEpochSecond();
        String signature = signer.sign(task.secret(), timestamp, task.body());
        long started = System.nanoTime();
        try {
            return restClient.post()
                    .uri(task.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .header(WebhookSigner.HEADER_ID, task.webhookId().toString())
                    .header(WebhookSigner.HEADER_TIMESTAMP, Long.toString(timestamp))
                    .header(WebhookSigner.HEADER_SIGNATURE, signature)
                    .body(task.body().getBytes(StandardCharsets.UTF_8))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        Duration elapsed = elapsedSince(started);
                        return response.getStatusCode().is2xxSuccessful()
                                ? SendResult.success(status, elapsed)
                                : SendResult.httpError(status, elapsed);
                    });
        } catch (ResourceAccessException e) {
            Duration elapsed = elapsedSince(started);
            String message = describe(e);
            if (isTimeout(e)) {
                log.debug("delivery {} to {} timed out after {}", task.deliveryId(), task.url(), elapsed);
                return SendResult.timeout(message, elapsed);
            }
            log.debug("delivery {} to {} failed to connect: {}", task.deliveryId(), task.url(), message);
            return SendResult.connectionError(message, elapsed);
        }
    }

    private static boolean isTimeout(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String describe(ResourceAccessException e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        return root.getClass().getSimpleName() + ": " + message;
    }

    private static Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
