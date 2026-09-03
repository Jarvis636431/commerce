package com.jarvis.commerce.payment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentTimeoutMetrics {

    private final Counter expired;
    private final Counter skipped;
    private final Counter failed;

    public PaymentTimeoutMetrics(MeterRegistry registry) {
        expired = counter(registry, "expired");
        skipped = counter(registry, "skipped");
        failed = counter(registry, "failed");
    }

    public void recordExpired() { expired.increment(); }
    public void recordSkipped() { skipped.increment(); }
    public void recordFailed() { failed.increment(); }

    private Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("commerce.payment.timeout")
                .tag("outcome", outcome).register(registry);
    }
}
