package com.helenaramyan.webhooks.delivery;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/deliveries")
@Validated
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeliveryResponse> list(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return service.list(status, endpointId, limit);
    }

    @GetMapping("/{id}")
    public DeliveryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
