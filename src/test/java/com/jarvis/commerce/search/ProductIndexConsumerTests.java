package com.jarvis.commerce.search;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductIndexConsumerTests {

    @Test
    void upsertsAndDeletesTheRequestedProduct() {
        FakeIndexer indexer = new FakeIndexer(false);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductIndexConsumer consumer = consumer(indexer, registry);

        consumer.consume(message(ProductIndexOperation.UPSERT));
        assertEquals(42L, indexer.indexedProductId);

        consumer.consume(message(ProductIndexOperation.DELETE));
        assertEquals(42L, indexer.deletedProductId);
        assertEquals(2, registry.counter("commerce.search.index", "outcome", "success").count());
    }

    @Test
    void recordsAndPropagatesFailureSoRabbitCanRetry() {
        FakeIndexer indexer = new FakeIndexer(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductIndexConsumer consumer = consumer(indexer, registry);

        assertThrows(IllegalStateException.class,
                () -> consumer.consume(message(ProductIndexOperation.UPSERT)));
        assertEquals(1, registry.counter("commerce.search.index", "outcome", "failure").count());
    }

    private ProductIndexConsumer consumer(FakeIndexer indexer, SimpleMeterRegistry registry) {
        return new ProductIndexConsumer(indexer, new ProductIndexMetrics(registry));
    }

    private ProductIndexMessage message(ProductIndexOperation operation) {
        return new ProductIndexMessage("event-1", 42L, operation,
                OffsetDateTime.parse("2026-09-03T10:00:00+08:00"));
    }

    private static final class FakeIndexer implements ProductSearchIndexer {
        private final boolean fail;
        private long indexedProductId;
        private long deletedProductId;

        private FakeIndexer(boolean fail) { this.fail = fail; }

        @Override
        public void index(long productId) {
            if (fail) throw new IllegalStateException("Elasticsearch unavailable");
            indexedProductId = productId;
        }

        @Override
        public void delete(long productId) { deletedProductId = productId; }

        @Override
        public long rebuild() { return 0; }
    }
}
