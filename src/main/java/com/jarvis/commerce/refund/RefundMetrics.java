package com.jarvis.commerce.refund;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RefundMetrics {
    private final Counter created;
    private final Counter succeeded;
    private final Counter failed;

    public RefundMetrics(MeterRegistry registry, RefundOrderRepository repository) {
        for (RefundStatus status : RefundStatus.values()) {
            registry.gauge("commerce.refund.orders", java.util.List.of(
                    io.micrometer.core.instrument.Tag.of("status", status.name())),
                    repository, ignored -> repository.countByStatus(status));
        }
        created = registry.counter("commerce.refund.operations", "outcome", "created");
        succeeded = registry.counter("commerce.refund.operations", "outcome", "success");
        failed = registry.counter("commerce.refund.operations", "outcome", "failure");
    }

    void recordCreated() { created.increment(); }
    void recordSuccess() { succeeded.increment(); }
    void recordFailure() { failed.increment(); }
}
