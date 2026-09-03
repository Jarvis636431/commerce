package com.jarvis.commerce.search;

import java.time.OffsetDateTime;

public record ProductIndexMessage(
        String eventId,
        long productId,
        ProductIndexOperation operation,
        OffsetDateTime occurredAt
) {}
