package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.api.ResourceNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

    private final DeliveryRepository deliveries;
    private final Clock clock;

    public DeliveryService(DeliveryRepository deliveries, Clock clock) {
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> list(DeliveryStatus status, UUID endpointId, int limit) {
        Specification<Delivery> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (endpointId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("endpoint").get("id"), endpointId));
        }
        PageRequest page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return deliveries.findAll(spec, page).map(DeliveryResponse::from).getContent();
    }

    @Transactional(readOnly = true)
    public DeliveryResponse get(UUID id) {
        return DeliveryResponse.from(load(id));
    }

    @Transactional
    public DeliveryResponse replay(UUID id) {
        Delivery delivery = load(id);
        delivery.replay(clock.instant());
        return DeliveryResponse.from(delivery);
    }

    private Delivery load(UUID id) {
        return deliveries.findById(id).orElseThrow(() -> new ResourceNotFoundException("delivery", id));
    }
}
