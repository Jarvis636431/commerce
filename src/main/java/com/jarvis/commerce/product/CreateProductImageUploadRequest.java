package com.jarvis.commerce.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProductImageUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 100) String contentType,
        @Positive long size
) { }
