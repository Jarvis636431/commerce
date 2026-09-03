package com.jarvis.commerce.product;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Map;

public record ProductImageUploadResponse(
        Long imageId,
        String objectKey,
        URL uploadUrl,
        Map<String, String> requiredHeaders,
        OffsetDateTime expiresAt
) { }
