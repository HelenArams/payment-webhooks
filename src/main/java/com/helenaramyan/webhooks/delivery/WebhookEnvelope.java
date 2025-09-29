package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.event.PaymentEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Renders the body receivers get:
 * <pre>{"id": "...", "type": "payment.captured", "occurredAt": "...", "data": {...}}</pre>
 * The rendering is deterministic for a given event, so retries carry byte-identical bodies.
 */
@Component
public class WebhookEnvelope {

    private final ObjectMapper mapper;

    public WebhookEnvelope(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String render(PaymentEvent event) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("id", event.getId().toString());
            root.put("type", event.getType());
            root.put("occurredAt", event.getOccurredAt().toString());
            root.set("data", mapper.readTree(event.getPayload()));
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored payload of event " + event.getId() + " is not valid JSON", e);
        }
    }
}
