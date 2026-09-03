package com.jarvis.commerce.payment;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTimeoutConsumerTests {

    private static final Instant NOW = Instant.parse("2026-09-03T08:15:01Z");

    @Test
    void delegatesToIdempotentExpirationCheck() {
        FakePaymentService paymentService = new FakePaymentService(false);

        consumer(paymentService).consume(message());

        assertEquals("PAY-1", paymentService.paymentNo);
        assertEquals(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), paymentService.now);
    }

    @Test
    void propagatesFailureSoListenerContainerCanRetry() {
        FakePaymentService paymentService = new FakePaymentService(true);

        assertThrows(IllegalStateException.class, () -> consumer(paymentService).consume(message()));
    }

    private PaymentTimeoutConsumer consumer(PaymentService paymentService) {
        return new PaymentTimeoutConsumer(paymentService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PaymentTimeoutMessage message() {
        return new PaymentTimeoutMessage("event-1", "PAY-1",
                OffsetDateTime.parse("2026-09-03T08:15:00Z"),
                OffsetDateTime.parse("2026-09-03T08:00:00Z"));
    }

    private static final class FakePaymentService extends PaymentService {
        private final boolean fail;
        private String paymentNo;
        private OffsetDateTime now;

        private FakePaymentService(boolean fail) {
            super(null, null, null, null, Clock.systemUTC(), null, Duration.ofMinutes(15));
            this.fail = fail;
        }

        @Override
        public boolean expireIfDue(String paymentNo, OffsetDateTime now) {
            if (fail) throw new IllegalStateException("database unavailable");
            this.paymentNo = paymentNo;
            this.now = now;
            return true;
        }
    }
}
