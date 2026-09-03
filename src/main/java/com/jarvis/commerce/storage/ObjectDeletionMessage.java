package com.jarvis.commerce.storage;

import java.time.OffsetDateTime;
import java.util.List;

public record ObjectDeletionMessage(String eventId, List<String> objectKeys, OffsetDateTime occurredAt) { }
