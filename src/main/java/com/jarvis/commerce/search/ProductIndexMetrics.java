package com.jarvis.commerce.search;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ProductIndexMetrics {
    private final Counter success;
    private final Counter failure;

    public ProductIndexMetrics(MeterRegistry registry) {
        success = registry.counter("commerce.search.index", "outcome", "success");
        failure = registry.counter("commerce.search.index", "outcome", "failure");
    }

    void recordSuccess() { success.increment(); }
    void recordFailure() { failure.increment(); }
}
