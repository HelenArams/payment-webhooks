package com.helenaramyan.webhooks.endpoint;

import com.helenaramyan.webhooks.event.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RegisterEndpointRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "must be an absolute http(s) URL")
        String url,

        @NotEmpty
        Set<@NotBlank @Size(max = EventType.MAX_LENGTH)
            @Pattern(regexp = EventType.PATTERN, message = "must look like payment.captured") String> eventTypes) {
}
