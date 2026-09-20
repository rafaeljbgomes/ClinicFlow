package com.clinicflow.appointment.adapters.outbound.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface SpringDataOutboxEventRepository extends JpaRepository<JpaOutboxEvent, UUID> {
    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE status IN ('PENDING', 'FAILED')
              AND next_attempt_at <= :now
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<JpaOutboxEvent> lockNextDue(@Param("now") Instant now);

    long countByStatus(OutboxEventStatus status);

    @Modifying
    @Query(value = """
            DELETE FROM outbox_events
            WHERE status = 'PUBLISHED'
              AND published_at < :cutoff
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
