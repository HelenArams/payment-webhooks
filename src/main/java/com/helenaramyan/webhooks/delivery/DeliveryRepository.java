package com.helenaramyan.webhooks.delivery;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {
}
