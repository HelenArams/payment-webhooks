package com.helenaramyan.webhooks.endpoint;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {

    List<Endpoint> findAllByOrderByCreatedAtAsc();

    @Query("select e from Endpoint e where e.enabled = true and :eventType member of e.eventTypes")
    List<Endpoint> findEnabledSubscribedTo(@Param("eventType") String eventType);
}
