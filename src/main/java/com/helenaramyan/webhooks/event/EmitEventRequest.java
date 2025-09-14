package com.helenaramyan.webhooks.event;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * @param type       dotted event type, e.g. {@code payment.captured}
 * @param payload    arbitrary JSON object forwarded to receivers under {@code data}
 * @param occurredAt when the event happened; defaults to now
 */
public record EmitEventRequest(
        @NotBlank
        @Size(max = EventType.MAX_LENGTH)
        @Pattern(regexp = EventType.PATTERN, message = "must look like payment.captured")
        String type,

        @NotNull
        ObjectNode payload,

        Instant occurredAt) {
}
