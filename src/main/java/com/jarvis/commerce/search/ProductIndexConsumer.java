package com.jarvis.commerce.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.jarvis.commerce.messaging.RabbitTopology.PRODUCT_INDEX_QUEUE;

@Component
@ConditionalOnProperty(name = {"commerce.messaging.enabled", "commerce.search.enabled"},
        havingValue = "true", matchIfMissing = true)
public class ProductIndexConsumer {
    private static final Logger log = LoggerFactory.getLogger(ProductIndexConsumer.class);
    private final ProductSearchIndexer indexer;
    private final ProductIndexMetrics metrics;

    public ProductIndexConsumer(ProductSearchIndexer indexer, ProductIndexMetrics metrics) {
        this.indexer = indexer;
        this.metrics = metrics;
    }

    @RabbitListener(queues = PRODUCT_INDEX_QUEUE)
    public void consume(ProductIndexMessage message) {
        try {
            if (message.operation() == ProductIndexOperation.DELETE) {
                indexer.delete(message.productId());
            } else {
                indexer.index(message.productId());
            }
            metrics.recordSuccess();
            log.info("Synchronized product search index: productId={}, operation={}, eventId={}",
                    message.productId(), message.operation(), message.eventId());
        } catch (RuntimeException exception) {
            metrics.recordFailure();
            throw exception;
        }
    }
}
