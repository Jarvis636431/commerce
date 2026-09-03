package com.jarvis.commerce.storage;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.jarvis.commerce.messaging.RabbitTopology.STORAGE_DELETE_QUEUE;

@Component
@ConditionalOnProperty(name = {"commerce.messaging.enabled", "commerce.storage.enabled"}, havingValue = "true",
        matchIfMissing = true)
public class ObjectDeletionConsumer {
    private final ObjectStorage storage;
    public ObjectDeletionConsumer(ObjectStorage storage) { this.storage = storage; }

    @RabbitListener(queues = STORAGE_DELETE_QUEUE)
    public void consume(ObjectDeletionMessage message) {
        message.objectKeys().forEach(storage::delete);
    }
}
