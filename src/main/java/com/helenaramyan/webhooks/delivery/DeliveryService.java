package com.helenaramyan.webhooks.delivery;

import com.helenaramyan.webhooks.api.ResourceNotFoundException;
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

    public DeliveryService(DeliveryRepository deliveries) {
        this.deliveries = deliveries;
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
        return deliveries.findById(id)
                .map(DeliveryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("delivery", id));
    }
}
