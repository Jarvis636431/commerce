package com.jarvis.commerce.messaging.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    long countByStatus(OutboxStatus status);

    long countByAggregateTypeAndAggregateIdAndEventType(String aggregateType, String aggregateId, String eventType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from OutboxEvent event
            where (event.status in :readyStatuses and event.nextAttemptAt <= :now)
               or (event.status = :processingStatus and event.lockedUntil <= :now)
            order by event.id
            """)
    List<OutboxEvent> findClaimable(
            @Param("readyStatuses") Collection<OutboxStatus> readyStatuses,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("now") OffsetDateTime now,
            Pageable pageable);
}
