package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.config.WebhookProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Decides when (and whether) a failed delivery is tried again.
 *
 * <p>Attempt 1 happens as soon as the delivery is due. After attempt {@code n} fails the next one
 * is scheduled {@code backoff[n-1]} later (last step repeated), with symmetric jitter. Once
 * {@code maxAttempts} have been made the delivery is dead.
 */
@Component
public class RetrySchedule {

    private final List<Duration> backoff;
    private final int maxAttempts;
    private final double jitter;
    private final RandomGenerator random;

    @Autowired
    public RetrySchedule(WebhookProperties properties) {
        this(properties.retry().backoff(), properties.retry().maxAttempts(), properties.retry().jitter(),
                new SplittableRandom());
    }

    RetrySchedule(List<Duration> backoff, int maxAttempts, double jitter, RandomGenerator random) {
        if (backoff.isEmpty()) {
            throw new IllegalArgumentException("backoff schedule must not be empty");
        }
        if (jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("jitter must be within [0, 1]");
        }
        this.backoff = List.copyOf(backoff);
        this.maxAttempts = maxAttempts;
        this.jitter = jitter;
        this.random = random;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * @param attemptsMade number of attempts made so far, including the one that just failed
     * @return when to try again, or empty if the delivery should be marked dead
     */
    public Optional<Instant> nextAttemptAfter(int attemptsMade, Instant now) {
        if (attemptsMade < 1) {
            throw new IllegalArgumentException("attemptsMade must be >= 1");
        }
        if (attemptsMade >= maxAttempts) {
            return Optional.empty();
        }
        Duration base = backoff.get(Math.min(attemptsMade - 1, backoff.size() - 1));
        return Optional.of(now.plus(withJitter(base)));
    }

    Duration withJitter(Duration base) {
        if (jitter == 0) {
            return base;
        }
        double factor = 1 + (random.nextDouble() * 2 - 1) * jitter;
        return Duration.ofMillis(Math.round(base.toMillis() * factor));
    }
}
