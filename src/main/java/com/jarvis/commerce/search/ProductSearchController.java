package com.jarvis.commerce.search;

import com.jarvis.commerce.common.PageResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

@Validated
@RestController
@RequestMapping("/api/search/products")
@ConditionalOnProperty(name = "commerce.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchController {
    private final ProductSearchService service;

    public ProductSearchController(ProductSearchService service) { this.service = service; }

    @GetMapping
    public PageResponse<ProductSearchResponse> search(
            @RequestParam("q") @NotBlank @Size(max = 100) String keyword,
            @RequestParam(required = false) @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
            BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
            BigDecimal maxPrice,
            @RequestParam(defaultValue = "RELEVANCE") ProductSearchSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(keyword, minPrice, maxPrice, sort, PageRequest.of(page, size));
    }
}
