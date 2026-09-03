package com.jarvis.commerce.product;

import jakarta.validation.constraints.Min;

public record UpdateProductImageRequest(boolean primary, @Min(0) int sortOrder) { }
