package com.jarvis.commerce.messaging.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OutboxEventTests {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-03T08:00:00Z");

    @Test
    void retriesThenEventuallyFails() {
        OutboxEvent event = event();

        event.claim(NOW, Duration.ofSeconds(30));
        event.markDeliveryFailed("broker unavailable", NOW.plusSeconds(1), NOW.plusSeconds(3), 2);
        assertEquals(OutboxStatus.RETRY, event.getStatus());
        assertEquals(1, event.getAttempts());

        event.claim(NOW.plusSeconds(3), Duration.ofSeconds(30));
        event.markDeliveryFailed("still unavailable", NOW.plusSeconds(4), NOW.plusSeconds(8), 2);
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(2, event.getAttempts());
    }

    @Test
    void marksConfirmedEventAsSentAndReleasesLease() {
        OutboxEvent event = event();
        event.claim(NOW, Duration.ofSeconds(30));

        event.markSent(NOW.plusSeconds(1));

        assertEquals(OutboxStatus.SENT, event.getStatus());
        assertEquals(NOW.plusSeconds(1), event.getSentAt());
        assertNull(event.getLockedUntil());
    }

    private OutboxEvent event() {
        return new OutboxEvent("event-1", "PAYMENT", "PAY-1",
                OutboxEventTypes.PAYMENT_TIMEOUT_SCHEDULED, "{}", NOW);
    }
}
