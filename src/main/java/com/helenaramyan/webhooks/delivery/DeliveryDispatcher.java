package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.config.WebhookProperties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox. Each tick drains due deliveries in batches: claim (one transaction), send
 * concurrently (no transaction), record each outcome (one transaction each).
 */
@Component
public class DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);

    private final DeliveryClaimer claimer;
    private final WebhookSender sender;
    private final DeliveryOutcomeHandler outcomes;
    private final ExecutorService executor;
    private final int batchSize;

    public DeliveryDispatcher(
            DeliveryClaimer claimer,
            WebhookSender sender,
            DeliveryOutcomeHandler outcomes,
            ExecutorService webhookSendExecutor,
            WebhookProperties properties) {
        this.claimer = claimer;
        this.sender = sender;
        this.outcomes = outcomes;
        this.executor = webhookSendExecutor;
        this.batchSize = properties.dispatcher().batchSize();
    }

    @Scheduled(fixedDelayString = "${webhooks.dispatcher.poll-interval}")
    public void poll() {
        int processed;
        do {
            processed = runOnce();
        } while (processed == batchSize);
    }

    /** Processes at most one batch. Exposed for tests and for driving the dispatcher manually. */
    public int runOnce() {
        List<DeliveryTask> tasks = claimer.claim(batchSize);
        if (tasks.isEmpty()) {
            return 0;
        }
        CompletableFuture<?>[] inFlight = tasks.stream()
                .map(task -> CompletableFuture.runAsync(() -> deliver(task), executor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(inFlight).join();
        return tasks.size();
    }

    private void deliver(DeliveryTask task) {
        try {
            SendResult result = sender.send(task);
            outcomes.record(task.deliveryId(), result);
        } catch (RuntimeException e) {
            // the lease expires and the delivery is picked up again; nothing else to do here
            log.error("delivery {} attempt {} crashed, will be retried after lease expiry",
                    task.deliveryId(), task.attempt(), e);
        }
    }
}
