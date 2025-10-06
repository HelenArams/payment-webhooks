package com.helenaramyan.webhooks.endpoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {

    List<Endpoint> findAllByOrderByCreatedAtAsc();

    @Query("select e from Endpoint e where e.enabled = true and :eventType member of e.eventTypes")
    List<Endpoint> findEnabledSubscribedTo(@Param("eventType") String eventType);

    // Counter updates are done in SQL rather than on the entity: outcomes for the same endpoint are
    // recorded concurrently from several sender threads, and read-modify-write would lose increments.

    @Modifying
    @Query("update Endpoint e set e.consecutiveDead = e.consecutiveDead + 1 where e.id = :id")
    int incrementConsecutiveDead(@Param("id") UUID id);

    @Modifying
    @Query("update Endpoint e set e.consecutiveDead = 0 where e.id = :id and e.consecutiveDead <> 0")
    int resetConsecutiveDead(@Param("id") UUID id);

    /** @return 1 if this call disabled the endpoint, 0 if below threshold or already disabled */
    @Modifying
    @Query("""
            update Endpoint e set e.enabled = false, e.disabledAt = :now
            where e.id = :id and e.enabled = true and e.consecutiveDead >= :threshold
            """)
    int disableIfOverThreshold(@Param("id") UUID id, @Param("threshold") int threshold, @Param("now") Instant now);
}
