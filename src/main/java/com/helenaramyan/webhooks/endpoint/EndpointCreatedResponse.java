package com.helenaramyan.webhooks.endpoint;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Returned once, at registration time. The {@code secret} is never exposed again by the API;
 * the receiver must store it to verify signatures.
 */
public record EndpointCreatedResponse(
        UUID id, String url, Set<String> eventTypes, boolean enabled, Instant createdAt, String secret) {

    static EndpointCreatedResponse from(Endpoint endpoint) {
        return new EndpointCreatedResponse(
                endpoint.getId(),
                endpoint.getUrl(),
                new TreeSet<>(endpoint.getEventTypes()),
                endpoint.isEnabled(),
                endpoint.getCreatedAt(),
                endpoint.getSecret());
    }
}
