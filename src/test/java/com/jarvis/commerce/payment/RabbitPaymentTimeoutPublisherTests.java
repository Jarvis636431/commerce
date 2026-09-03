package com.jarvis.commerce.payment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_COMMAND_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_SCHEDULE_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitPaymentTimeoutPublisherTests {

    private final RecordingRabbitTemplate rabbitTemplate = new RecordingRabbitTemplate();

    @AfterEach
    void clearTransactionContext() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesImmediatelyWithoutTransaction() {
        publisher().publishAfterCommit("PAY-1", expiresAt());
        assertPublishedOnce();
    }

    @Test
    void waitsUntilDatabaseTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher().publishAfterCommit("PAY-1", expiresAt());
        assertEquals(0, rabbitTemplate.calls);

        TransactionSynchronization synchronization = TransactionSynchronizationManager
                .getSynchronizations().getFirst();
        synchronization.afterCommit();
        assertPublishedOnce();
    }

    private RabbitPaymentTimeoutPublisher publisher() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T08:00:00Z"), ZoneOffset.UTC);
        return new RabbitPaymentTimeoutPublisher(rabbitTemplate, clock);
    }

    private OffsetDateTime expiresAt() {
        return OffsetDateTime.parse("2026-09-03T08:15:00Z");
    }

    private void assertPublishedOnce() {
        assertEquals(1, rabbitTemplate.calls);
        assertEquals(PAYMENT_COMMAND_EXCHANGE, rabbitTemplate.exchange);
        assertEquals(PAYMENT_TIMEOUT_SCHEDULE_KEY, rabbitTemplate.routingKey);
    }

    private static final class RecordingRabbitTemplate extends RabbitTemplate {
        private int calls;
        private String exchange;
        private String routingKey;

        @Override
        public void convertAndSend(String exchange, String routingKey, Object payload,
                                   MessagePostProcessor postProcessor, CorrelationData correlationData) {
            this.calls++;
            this.exchange = exchange;
            this.routingKey = routingKey;
        }
    }
}
