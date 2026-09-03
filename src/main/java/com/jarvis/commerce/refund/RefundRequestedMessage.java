package com.jarvis.commerce.refund;

import java.time.OffsetDateTime;

public record RefundRequestedMessage(String eventId, String refundNo, OffsetDateTime requestedAt) {}
