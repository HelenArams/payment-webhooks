package com.helenaramyan.webhooks.endpoint;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/v1/endpoints")
public class EndpointController {

    private final EndpointService service;

    public EndpointController(EndpointService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EndpointCreatedResponse> register(@Valid @RequestBody RegisterEndpointRequest request) {
        Endpoint endpoint = service.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(endpoint.getId())
                .toUri();
        return ResponseEntity.created(location).body(EndpointCreatedResponse.from(endpoint));
    }

    @GetMapping
    public List<EndpointResponse> list() {
        return service.list().stream().map(EndpointResponse::from).toList();
    }

    @GetMapping("/{id}")
    public EndpointResponse get(@PathVariable UUID id) {
        return EndpointResponse.from(service.get(id));
    }

    // re-enables an endpoint that was switched off after too many dead deliveries.
    // deliveries queued before it was disabled are still there and go out on the next poll
    @PostMapping("/{id}/enable")
    public EndpointResponse enable(@PathVariable UUID id) {
        return EndpointResponse.from(service.enable(id));
    }
}
