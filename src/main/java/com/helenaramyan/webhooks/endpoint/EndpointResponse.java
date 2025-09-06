package com.helenaramyan.webhooks.endpoint;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public record EndpointResponse(UUID id, String url, Set<String> eventTypes, boolean enabled, Instant createdAt) {

    static EndpointResponse from(Endpoint endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getUrl(),
                new TreeSet<>(endpoint.getEventTypes()),
                endpoint.isEnabled(),
                endpoint.getCreatedAt());
    }
}
