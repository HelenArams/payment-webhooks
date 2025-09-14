package com.helenaramyan.webhooks.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "webhooks")
@Validated
public record WebhookProperties(@Valid @DefaultValue Retry retry) {

    /**
     * @param backoff     delay before retry n is {@code backoff[n-1]}; the last entry repeats if
     *                    {@code maxAttempts} exceeds the list length
     * @param maxAttempts total attempts including the first one; a failure on the last attempt
     *                    marks the delivery DEAD
     * @param jitter      fraction of the delay randomly added or removed (0.2 = +/-20%) so a
     *                    receiver outage does not come back as a synchronised retry storm
     */
    public record Retry(
            @DefaultValue({"10s", "30s", "2m", "10m", "30m", "1h", "2h", "4h"}) @NotEmpty List<Duration> backoff,
            @DefaultValue("9") @Min(1) int maxAttempts,
            @DefaultValue("0.2") @DecimalMin("0") @DecimalMax("1") double jitter) {
    }
}
