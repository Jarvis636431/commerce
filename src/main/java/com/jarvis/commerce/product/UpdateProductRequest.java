package com.jarvis.commerce.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description
) {
}
