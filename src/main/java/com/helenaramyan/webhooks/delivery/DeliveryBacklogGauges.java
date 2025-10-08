package com.helenaramyan.webhooks.delivery;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** webhook.backlog{status} - current row count per status. Cheap enough with the status index. */
@Component
public class DeliveryBacklogGauges {

    public DeliveryBacklogGauges(MeterRegistry registry, DeliveryRepository deliveries) {
        for (DeliveryStatus status : DeliveryStatus.values()) {
            Gauge.builder("webhook.backlog", deliveries, repo -> repo.countByStatus(status))
                    .description("Deliveries currently in each status")
                    .tag("status", status.name().toLowerCase())
                    .register(registry);
        }
    }
}
