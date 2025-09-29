package com.helenaramyan.webhooks.delivery;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {

    /**
     * Due, unleased deliveries, oldest first, locked for the calling transaction. The lock timeout
     * hint of -2 asks Hibernate for {@code SKIP LOCKED} where the database supports it (PostgreSQL),
     * so several instances can poll the same table without serialising on each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select d from Delivery d
            where d.status in :statuses
              and d.nextAttemptAt <= :now
              and (d.leasedUntil is null or d.leasedUntil <= :now)
            order by d.nextAttemptAt asc
            """)
    List<Delivery> findDue(
            @Param("statuses") Collection<DeliveryStatus> statuses, @Param("now") Instant now, Pageable page);

    @Query("select d from Delivery d join fetch d.endpoint join fetch d.event where d.id in :ids")
    List<Delivery> findAllWithEndpointAndEvent(@Param("ids") Collection<UUID> ids);
}
