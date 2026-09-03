package com.jarvis.commerce.product;

import java.net.URL;
import java.time.OffsetDateTime;

public record ProductImageResponse(
        Long id,
        Long productId,
        String originalFilename,
        String contentType,
        long size,
        String etag,
        URL downloadUrl,
        OffsetDateTime downloadUrlExpiresAt,
        OffsetDateTime createdAt
) { }
