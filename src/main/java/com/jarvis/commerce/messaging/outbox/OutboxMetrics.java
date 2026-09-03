package com.jarvis.commerce.messaging.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private final Counter deliverySuccess;
    private final Counter deliveryFailure;

    public OutboxMetrics(MeterRegistry registry, OutboxEventRepository repository) {
        for (OutboxStatus status : OutboxStatus.values()) {
            registry.gauge("commerce.outbox.events", java.util.List.of(
                    io.micrometer.core.instrument.Tag.of("status", status.name())),
                    repository, value -> value.countByStatus(status));
        }
        deliverySuccess = Counter.builder("commerce.outbox.delivery")
                .tag("outcome", "success").register(registry);
        deliveryFailure = Counter.builder("commerce.outbox.delivery")
                .tag("outcome", "failure").register(registry);
    }

    public void recordSuccess() { deliverySuccess.increment(); }

    public void recordFailure() { deliveryFailure.increment(); }
}
