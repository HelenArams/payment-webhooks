package com.helenaramyan.webhooks.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helenaramyan.webhooks.config.WebhookProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class RetryScheduleTest {

    private static final List<Duration> DEFAULT_BACKOFF = List.of(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10),
            Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4));

    private final Clock clock = Clock.fixed(Instant.parse("2025-09-14T18:00:00Z"), ZoneOffset.UTC);
    private final Instant now = clock.instant();

    @Test
    void withoutJitterFollowsTheConfiguredScheduleExactly() {
        RetrySchedule schedule = new RetrySchedule(DEFAULT_BACKOFF, 9, 0, new Random(1));

        for (int attempt = 1; attempt <= 8; attempt++) {
            assertThat(schedule.nextAttemptAfter(attempt, now))
                    .as("after attempt %d", attempt)
                    .contains(now.plus(DEFAULT_BACKOFF.get(attempt - 1)));
        }
    }

    @Test
    void deadAfterMaxAttempts() {
        RetrySchedule schedule = new RetrySchedule(DEFAULT_BACKOFF, 9, 0, new Random(1));

        assertThat(schedule.nextAttemptAfter(8, now)).isPresent();
        assertThat(schedule.nextAttemptAfter(9, now)).isEmpty();
        assertThat(schedule.nextAttemptAfter(10, now)).isEmpty();
    }

    @Test
    void lastStepRepeatsWhenScheduleIsShorterThanMaxAttempts() {
        RetrySchedule schedule = new RetrySchedule(
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(5)), 5, 0, new Random(1));

        assertThat(schedule.nextAttemptAfter(1, now)).contains(now.plusSeconds(1));
        assertThat(schedule.nextAttemptAfter(2, now)).contains(now.plusSeconds(5));
        assertThat(schedule.nextAttemptAfter(3, now)).contains(now.plusSeconds(5));
        assertThat(schedule.nextAttemptAfter(4, now)).contains(now.plusSeconds(5));
        assertThat(schedule.nextAttemptAfter(5, now)).isEmpty();
    }

    @Test
    void jitterStaysWithinBoundsAndActuallyVaries() {
        RetrySchedule schedule = new RetrySchedule(List.of(Duration.ofMinutes(10)), 100, 0.2, new Random(42));
        Instant min = now.plus(Duration.ofMinutes(8));
        Instant max = now.plus(Duration.ofMinutes(12));

        Set<Instant> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            Instant next = schedule.nextAttemptAfter(1, now).orElseThrow();
            assertThat(next).isBetween(min, max);
            seen.add(next);
        }
        assertThat(seen).hasSizeGreaterThan(900);
    }

    @Test
    void singleAttemptScheduleNeverRetries() {
        RetrySchedule schedule = new RetrySchedule(DEFAULT_BACKOFF, 1, 0.2, new Random(1));

        assertThat(schedule.nextAttemptAfter(1, now)).isEmpty();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RetrySchedule(List.of(), 3, 0, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrySchedule(DEFAULT_BACKOFF, 3, 1.5, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrySchedule(DEFAULT_BACKOFF, 3, 0, new Random()).nextAttemptAfter(0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultPropertiesMatchTheDocumentedSchedule() {
        WebhookProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("webhooks", WebhookProperties.class);

        assertThat(properties.retry().backoff()).isEqualTo(DEFAULT_BACKOFF);
        assertThat(properties.retry().maxAttempts()).isEqualTo(9);
        assertThat(properties.retry().jitter()).isEqualTo(0.2);

        RetrySchedule schedule = new RetrySchedule(properties);
        Duration total = Duration.ZERO;
        Instant cursor = now;
        int attempt = 1;
        while (true) {
            var next = schedule.nextAttemptAfter(attempt++, cursor);
            if (next.isEmpty()) {
                break;
            }
            total = total.plus(Duration.between(cursor, next.get()));
            cursor = next.get();
        }
        // 7h42m40s nominal, +/-20% jitter per step
        assertThat(total).isBetween(Duration.ofMinutes(370), Duration.ofMinutes(556));
        assertThat(attempt - 1).isEqualTo(9);
    }
}
