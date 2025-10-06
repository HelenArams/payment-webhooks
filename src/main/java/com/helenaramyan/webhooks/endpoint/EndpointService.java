package com.helenaramyan.webhooks.endpoint;

import com.helenaramyan.webhooks.api.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {

    private final EndpointRepository endpoints;
    private final SecretGenerator secrets;
    private final Clock clock;

    public EndpointService(EndpointRepository endpoints, SecretGenerator secrets, Clock clock) {
        this.endpoints = endpoints;
        this.secrets = secrets;
        this.clock = clock;
    }

    @Transactional
    public Endpoint register(RegisterEndpointRequest request) {
        Endpoint endpoint = new Endpoint(
                UUID.randomUUID(), request.url(), secrets.generate(), request.eventTypes(), clock.instant());
        return endpoints.save(endpoint);
    }

    @Transactional(readOnly = true)
    public List<Endpoint> list() {
        return endpoints.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public Endpoint enable(UUID id) {
        Endpoint endpoint = get(id);
        endpoint.enable();
        return endpoint;
    }

    @Transactional(readOnly = true)
    public Endpoint get(UUID id) {
        return endpoints.findById(id).orElseThrow(() -> new ResourceNotFoundException("endpoint", id));
    }
}
